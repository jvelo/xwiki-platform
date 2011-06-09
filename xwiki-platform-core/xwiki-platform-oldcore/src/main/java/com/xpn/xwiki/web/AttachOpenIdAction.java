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

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.openid.OpenIdHelper;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;

import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.Identifier;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.ParameterList;
import org.openid4java.OpenIDException;

/**
 * Action used to attach an OpenID to already existing users.
 * 
 * @since 2.3-milestone-2
 * @version $Id$
 */
public class AttachOpenIdAction extends XWikiAction
{
    /** The name of the OpenId parameter representing the Open Id mode. */
    private static final String OPENID_MODE_PARAMETER = "openid.mode";

    /** The fullName of the document holding the class definition of an Open ID identifier. */
    private static final String XWIKI_OPENID_IDENTIFIER_CLASSNAME = "XWiki.OpenIdIdentifier";

    /** The name of the velocity context key for the username variable. */
    private static final String USERNAME_VCONTEXT_KEY = "username";

    /** The name of the velocity context key for the Open Id identifier variable. */
    private static final String OPENID_IDENTIFIER_VCONTEXT_KEY = "openid_identifier";

    /** The context key for an error or information message. */
    private static final String MESSAGE_CONTEXT_KEY = "message";

    /** The message when Open ID discovery failed. */
    private static final String REGISTER_OPENID_DISCOVERY_FAILED_MESSAGE_VALUE = "register_openid_discovery_failed";
    
    /** The name of the session attribute representing OpenId discovery information. */
    private static final String OPENID_DISCOVERY_SESSION_KEY = "openid-discovery";

    /** The key associated with the velocity context in the xwiki context map. */
    private static final String VCONTEXT_CONTEXT_KEY = "vcontext";

    /** The name of the 'status' variable pushed in the velocity context. */
    private static final String STATUS_VCONTEXT_KEY = "status";
    
    /** The name of the velocity template for attaching OpenId. */
    private static final String ATTACH_OPENID_TEMPLATE_NAME = "attach_openid";

    /** The log used by this class. */
    private static final Log LOG = LogFactory.getLog(AttachOpenIdAction.class);

    /** The name of the velocity template rendered by this servlet. */
    private String template = ATTACH_OPENID_TEMPLATE_NAME;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean action(XWikiContext context) throws XWikiException
    {
        // Only the user itself or an administrator is allowed to attach an OpenID
        if (!(context.getDoc().getFullName().equals(context.getXWikiUser().getUser()))
            && !context.getWiki().getRightService().hasAdminRights(context)) {
            template = "accessdenied";
            return true;
        }

        XWikiRequest request = context.getRequest();
        XWikiResponse response = context.getResponse();
        VelocityContext vcontext = (VelocityContext) context.get(VCONTEXT_CONTEXT_KEY);

        template = ATTACH_OPENID_TEMPLATE_NAME;

        String attachOpenid = request.getParameter(ATTACH_OPENID_TEMPLATE_NAME);
        if (StringUtils.equals("discover-openid", attachOpenid)) {
            if (discoverOpenID(context)) {
                return false;
            }
        } else if (StringUtils.equals("confirm", attachOpenid)) {
            vcontext.put(STATUS_VCONTEXT_KEY, new Integer(confirmAttachingOpenId(context)));
        } else if (StringUtils.equals("attach-openid", attachOpenid)) {
            vcontext.put(STATUS_VCONTEXT_KEY, new Integer(attachOpenID(context)));
        }

        String redirect = Utils.getRedirect(request, null);
        if (redirect == null) {
            return true;
        } else {
            sendRedirect(response, redirect);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String render(XWikiContext context) throws XWikiException
    {
        return template;
    }

    /**
     * Starts the process of attaching an OpenID to an existing user. The OpenID provider belonging to the entered
     * OpenID identifier is searched and the user is redirected to it to authenticate there. This processed is used to
     * assure that the entered OpenID is valid and in possession of that user. If discovery fails, an error message is
     * shown.
     * 
     * @param context the XWiki context when discovering OpenId.
     * @return returns true if a redirect was sent, otherwise false.
     */
    protected boolean discoverOpenID(XWikiContext context)
    {
        XWikiRequest request = context.getRequest();
        XWikiResponse response = context.getResponse();
        VelocityContext vcontext = (VelocityContext) context.get(VCONTEXT_CONTEXT_KEY);

        // Check for empty OpenID identifier
        String openidIdentifier = request.getParameter(OPENID_IDENTIFIER_VCONTEXT_KEY);
        if (openidIdentifier == null || openidIdentifier.equals("")) {
            vcontext.put(STATUS_VCONTEXT_KEY, new Integer(-14));
            return false;
        }

        try {
            ConsumerManager manager = OpenIdHelper.getConsumerManager();

            String returnToUrl = context.getDoc().getExternalURL("attachopenid", "attach_openid=confirm", context);

            // perform discovery on the user-supplied identifier
            List discoveries = manager.discover(openidIdentifier);

            // attempt to associate with the OpenID provider and retrieve one service endpoint for authentication
            DiscoveryInformation discovered = manager.associate(discoveries);
            request.getSession().setAttribute(OPENID_DISCOVERY_SESSION_KEY, discovered);

            // obtain a AuthRequest message to be sent to the OpenID provider
            AuthRequest authRequest = manager.authenticate(discovered, returnToUrl);

            // set the realm
            authRequest.setRealm(((XWikiServletURLFactory) context.getURLFactory()).getServerURL(context).toString()
                + ((XWikiServletURLFactory) context.getURLFactory()).getContextPath());

            if (discovered.isVersion2()) {
                // OpenID 2.0 supports HTML form redirection which allows payloads >2048 bytes
                vcontext.put("op_endpoint", authRequest.getDestinationUrl(false));
                vcontext.put("openid_parameters", authRequest.getParameterMap());
                template = "openid_form_redirect";
                return false;
            } else {
                // the only method supported in OpenID 1.x is a HTTP-redirect (GET) to the OpenID Provider endpoint (the
                // redirect-URL usually limited ~2048 bytes)
                sendRedirect(response, authRequest.getDestinationUrl(true));
                return true;
            }
        } catch (OpenIDException e) {
            context.put(MESSAGE_CONTEXT_KEY, REGISTER_OPENID_DISCOVERY_FAILED_MESSAGE_VALUE);
        } catch (Exception e) {
            context.put(MESSAGE_CONTEXT_KEY, REGISTER_OPENID_DISCOVERY_FAILED_MESSAGE_VALUE);
        }

        return false;
    }

    /**
     * Checks the response of the OpenID provider (OP) and outputs a confirmation form. If the OP returns an error or
     * the user cancelled at its site, an error message is shown to the user.
     * 
     * @param context the XWiki context when confirming attaching open ID.
     * 
     * @return a code associated with the result of the operation.
     */
    protected int confirmAttachingOpenId(XWikiContext context) 
    {
        XWikiRequest request = context.getRequest();
        VelocityContext vcontext = (VelocityContext) context.get(VCONTEXT_CONTEXT_KEY);

        try {
            ConsumerManager manager = OpenIdHelper.getConsumerManager();

            // extract the parameters from the authentication response
            // (which comes in as a HTTP request from the OpenID provider)
            ParameterList openidResponse = new ParameterList(request.getParameterMap());

            // retrieve the previously stored discovery information
            DiscoveryInformation discovered =
                (DiscoveryInformation) request.getSession().getAttribute(OPENID_DISCOVERY_SESSION_KEY);

            // extract the receiving URL from the HTTP request
            StringBuffer receivingUrl = request.getRequestURL();
            String queryString = request.getQueryString();
            if (!StringUtils.isBlank(queryString)) {
                receivingUrl.append("?").append(request.getQueryString());
            }

            // verify the response; ConsumerManager needs to be the same
            // (static) instance used to place the authentication request
            VerificationResult verification = manager.verify(receivingUrl.toString(), openidResponse, discovered);

            // examine the verification result and extract the verified identifier
            Identifier verified = verification.getVerifiedId();

            if (verified != null) {
                vcontext.put(USERNAME_VCONTEXT_KEY, 
                    context.getWiki().getUserName(context.getDoc().getFullName(), context));

                // check if this OpenID is already registered
                if (OpenIdHelper.findUser(verified.getIdentifier(), context) != null) {
                    vcontext.put(OPENID_IDENTIFIER_VCONTEXT_KEY, verified.getIdentifier());
                    return -3;
                }
                vcontext.put(OPENID_IDENTIFIER_VCONTEXT_KEY, verified.getIdentifier());

                // Check if the user has already attached an OpenID
                if (context.getDoc().getObject(XWIKI_OPENID_IDENTIFIER_CLASSNAME) != null) {
                    vcontext.put("current_openid_identifier", 
                        context.getDoc().getStringValue(XWIKI_OPENID_IDENTIFIER_CLASSNAME, "identifier"));
                    return -1;
                }

                // Ask the user for confirmation
                return 1;
            } else {
                // authentication failed, show and log error message
                if (openidResponse.getParameter(OPENID_MODE_PARAMETER) != null 
                    && StringUtils.equals("cancel", openidResponse.getParameter(OPENID_MODE_PARAMETER).getValue())) {
                    // OpenID discovery cancelled
                    return -4; 
                } else {
                    if (LOG.isInfoEnabled() && openidResponse.getParameter("error") != null) {
                        LOG.info("OpenID login failed (error: "
                            + openidResponse.getParameter("openid.error").getValue() + ")");
                    }
                    // OpenID discovery failed
                    return -5; 
                }
            }
        } catch (OpenIDException e) {
            // Internal error
            return -6;
        }
    }
    
    /**
     * Attaches an OpenID to an existing user. The method checks if the password is correct and if so the OpenID is
     * attached to the user.
     * 
     * @param context the XWiki context when attaching open Id.
     * @return an status code describing the success or failure of the process
     * @throws XWikiException when attaching Open Id to user failed.
     */
    protected int attachOpenID(XWikiContext context) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        VelocityContext vcontext = (VelocityContext) context.get(VCONTEXT_CONTEXT_KEY);
        String openidIdentifier = request.getParameter(OPENID_IDENTIFIER_VCONTEXT_KEY);

        if (openidIdentifier == null || openidIdentifier.length() == 0) {
            // No OpenID passed
            return -2; 
        }

        // Attach the OpenID
        if (!OpenIdHelper.attachOpenIdToUser(context.getDoc(), openidIdentifier, context)) {
            // Internal error
            return -6; 
        }

        // Successfully attached the OpenID to the user account
        vcontext.put(USERNAME_VCONTEXT_KEY, context.getWiki().getUserName(context.getXWikiUser().getUser(), context));
        vcontext.put(OPENID_IDENTIFIER_VCONTEXT_KEY, openidIdentifier);
        return 2;
    }
}
