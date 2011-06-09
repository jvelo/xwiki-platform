/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */
package com.xpn.xwiki.web;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.openid.OpenIdHelper;

import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.Identifier;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.sreg.SRegMessage;
import org.openid4java.message.sreg.SRegRequest;
import org.openid4java.message.sreg.SRegResponse;
import org.openid4java.message.*;
import org.openid4java.OpenIDException;

/**
 * Register xwiki action.
 * 
 * @version $Id$
 */
public class RegisterAction extends XWikiAction
{
    /** Name of the corresponding template and URL parameter. */
    private static final String REGISTER = "register";

    private static final Log log = LogFactory.getLog(RegisterAction.class);

    private String template = "register";

    /**
     * {@inheritDoc}
     * 
     * @see com.xpn.xwiki.web.XWikiAction#action(com.xpn.xwiki.XWikiContext)
     */
    @Override
    public boolean action(XWikiContext context) throws XWikiException
    {
        XWiki xwiki = context.getWiki();
        XWikiRequest request = context.getRequest();
        XWikiResponse response = context.getResponse();

        template = "register";

        String register = request.getParameter(REGISTER);
        if ((register != null) && (register.equals("1"))) {
            // CSRF prevention
            if (!csrfTokenCheck(context)) {
                return false;
            }

            int useemail = xwiki.getXWikiPreferenceAsInt("use_email_verification", 0, context);
            int result;
            if (useemail == 1) {
                result = xwiki.createUser(true, "edit", context);
            } else {
                result = xwiki.createUser(context);
            }
            VelocityContext vcontext = (VelocityContext) context.get("vcontext");
            vcontext.put("reg", Integer.valueOf(result));

        } else if ((register != null) && (register.equals("openid-discover"))) {
            if (discoverOpenId(context))
                return false;
        } else if ((register != null) && (register.equals("openid-confirm"))) {
            String confirm = request.getParameter("register-confirm");
            if ("1".equals(confirm) == false) {
                confirmOpenIdRegistration(context);
                return true;
            } else {
                VelocityContext vcontext = (VelocityContext) context.get("vcontext");
                vcontext.put("reg", new Integer(registerOpenId(context)));
            }
        }
        // Redirect if a redirection parameter is passed.
        String redirect = Utils.getRedirect(request, null);
        if (redirect == null)
            return true;
        else {
            sendRedirect(response, redirect);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.xpn.xwiki.web.XWikiAction#render(com.xpn.xwiki.XWikiContext)
     */
    @Override
    public String render(XWikiContext context) throws XWikiException
    {
        return template;
    }

    /**
     * Starts registration of an OpenID user. The OpenID provider belonging to the entered OpenID identifier is searched
     * and the user is redirected to it to authenticate there. This processed is used to assure that the entered OpenID
     * is valid and in possession of that user. If discovery fails, an error message is shown.
     * 
     * @param context
     * @return returns true if a redirect was sent, otherwise false.
     * @author Markus Lanthaler
     */
    protected boolean discoverOpenId(XWikiContext context)
    {
        XWikiRequest request = context.getRequest();
        XWikiResponse response = context.getResponse();
        VelocityContext vcontext = (VelocityContext) context.get("vcontext");

        // Check for empty OpenID identifier
        String openid_identifier = request.getParameter("openid_identifier");
        if (openid_identifier == null || openid_identifier.equals("")) {
            vcontext.put("reg", new Integer(-14));
            return false;
        }

        try {
            ConsumerManager manager = OpenIdHelper.getConsumerManager();

            String return_to_url =
                context.getWiki().getExternalURL("Register", "register", "register=openid-confirm", context);

            // perform discovery on the user-supplied identifier
            List discoveries = manager.discover(openid_identifier);

            // attempt to associate with the OpenID provider and retrieve one service endpoint for authentication
            DiscoveryInformation discovered = manager.associate(discoveries);
            request.getSession().setAttribute("openid-discovery", discovered);

            // obtain a AuthRequest message to be sent to the OpenID provider
            AuthRequest auth_request = manager.authenticate(discovered, return_to_url);

            // set the realm
            auth_request.setRealm(((XWikiServletURLFactory) context.getURLFactory()).getServerURL(context).toString()
                + ((XWikiServletURLFactory) context.getURLFactory()).getContextPath());

            // attribute exchange (request user data from the OP to speed-up the registration process)
            FetchRequest att_exchange = FetchRequest.createFetchRequest();
            att_exchange.addAttribute("email", "http://schema.openid.net/contact/email", true);
            att_exchange.addAttribute("firstname", "http://axschema.org/namePerson/first", true);
            att_exchange.addAttribute("lastname", "http://axschema.org/namePerson/last", true);

            SRegRequest simple_reg_req = SRegRequest.createFetchRequest();
            simple_reg_req.addAttribute("fullname", true);
            simple_reg_req.addAttribute("firstname", true);
            simple_reg_req.addAttribute("lastname", true);
            simple_reg_req.addAttribute("nickname", true);
            simple_reg_req.addAttribute("email", true);

            auth_request.addExtension(att_exchange);
            auth_request.addExtension(simple_reg_req);

            if (discovered.isVersion2()) {
                // OpenID 2.0 supports HTML form redirection which allows payloads >2048 bytes
                vcontext.put("op_endpoint", auth_request.getDestinationUrl(false));
                vcontext.put("openid_parameters", auth_request.getParameterMap());
                template = "openid_form_redirect";
                return false;
            } else {
                // the only method supported in OpenID 1.x is a HTTP-redirect (GET) to the OpenID Provider endpoint (the
                // redirect-URL usually limited ~2048 bytes)
                sendRedirect(response, auth_request.getDestinationUrl(true));
                return true;
            }
        } catch (OpenIDException e) {
            context.put("message", "register_openid_discovery_failed");
        } catch (Exception e) {
            context.put("message", "register_openid_discovery_failed");
        }

        return false;
    }

    /**
     * Checks the response of the OpenID provider (OP) and outputs a confirmation form. If the OP returns an error or
     * the user cancelled at its site, an error message is shown to the user.
     * 
     * @param context
     * @throws XWikiException
     * @author Markus Lanthaler
     */
    protected void confirmOpenIdRegistration(XWikiContext context) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        VelocityContext vcontext = (VelocityContext) context.get("vcontext");

        try {
            ConsumerManager manager = OpenIdHelper.getConsumerManager();

            // extract the parameters from the authentication response
            // (which comes in as a HTTP request from the OpenID provider)
            ParameterList openid_response = new ParameterList(request.getParameterMap());

            // retrieve the previously stored discovery information
            DiscoveryInformation discovered =
                (DiscoveryInformation) request.getSession().getAttribute("openid-discovery");

            // extract the receiving URL from the HTTP request
            StringBuffer receiving_url = request.getRequestURL();
            String query_string = request.getQueryString();
            if (query_string != null && query_string.length() > 0)
                receiving_url.append("?").append(request.getQueryString());

            // verify the response; ConsumerManager needs to be the same
            // (static) instance used to place the authentication request
            VerificationResult verification = manager.verify(receiving_url.toString(), openid_response, discovered);

            // examine the verification result and extract the verified identifier
            Identifier verified = verification.getVerifiedId();

            if (verified != null) {
                // check if this OpenID is already registered
                if (OpenIdHelper.findUser(verified.getIdentifier(), context) != null) {
                    vcontext.put("reg", new Integer(-13));
                    vcontext.put("openid_identifier", verified.getIdentifier());
                    return;
                }

                // OpenID not used yet, continue with registration (ask the user for confirmation)
                vcontext.put("reg", new Integer(2));
                vcontext.put("openid_identifier", verified.getIdentifier());

                AuthSuccess auth_success = (AuthSuccess) verification.getAuthResponse();

                String email = null;
                String firstname = null;
                String lastname = null;

                if (auth_success.hasExtension(AxMessage.OPENID_NS_AX)) {
                    FetchResponse fetch_resp = (FetchResponse) auth_success.getExtension(AxMessage.OPENID_NS_AX);
                    email = (String) fetch_resp.getAttributeValues("email").get(0);
                    firstname = (String) fetch_resp.getAttributeValues("firstname").get(0);
                    lastname = (String) fetch_resp.getAttributeValues("lastname").get(0);
                }

                if (auth_success.getExtension(SRegMessage.OPENID_NS_SREG) instanceof SRegResponse) {
                    SRegResponse simple_reg_resp = (SRegResponse) auth_success.getExtension(SRegMessage.OPENID_NS_SREG);

                    if (email == null)
                        email = simple_reg_resp.getAttributeValue("email");

                    if (firstname == null && lastname == null)
                        firstname = simple_reg_resp.getAttributeValue("fullname");
                }

                vcontext.put("email", email);
                vcontext.put("first_name", firstname);
                vcontext.put("last_name", lastname);
            } else {
                // authentication failed, show and log error message
                if (openid_response.getParameter("openid.mode") != null
                    && openid_response.getParameter("openid.mode").getValue().equals("cancel")) {
                    context.put("message", "register_openid_discovery_cancelled");
                } else {
                    if (log.isInfoEnabled() && openid_response.getParameter("error") != null) {
                        log.info("OpenID login failed (error: "
                            + openid_response.getParameter("openid.error").getValue() + ")");
                    }
                    context.put("message", "register_openid_discovery_failed");
                }
            }
        } catch (OpenIDException e) {
            context.put("message", "register_openid_discovery_failed");
        }
    }

    /**
     * Completes the registration of an OpenID user. The user name is created automatically based on the OpenID
     * identifier by {@link OpenIdHelper#openIdIdentifierToUsername}.
     * 
     * @param context
     * @return an status code describing the success or failure of the registration
     * @throws XWikiException
     * @see OpenIdHelper#openIdIdentifierToUsername
     */
    protected int registerOpenId(XWikiContext context) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        String openid_identifier = request.getParameter("openid_identifier");
        String firstname = request.getParameter("register_first_name");
        String lastname = request.getParameter("register_last_name");
        String email = request.getParameter("register_email");

        int result = OpenIdHelper.createUser(openid_identifier, firstname, lastname, email, context);
        if (result == 3) {
            // user registration successful
            VelocityContext vcontext = (VelocityContext) context.get("vcontext");
            vcontext.put("username",
                context.getWiki().getUserName(OpenIdHelper.findUser(openid_identifier, context), context));
            vcontext.put("openid_identifier", openid_identifier);
        }

        return result;
    }
}
