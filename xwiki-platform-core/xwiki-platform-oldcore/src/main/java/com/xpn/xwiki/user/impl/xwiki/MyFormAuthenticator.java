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

package com.xpn.xwiki.user.impl.xwiki;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.ParameterList;
import org.securityfilter.authenticator.Authenticator;
import org.securityfilter.authenticator.FormAuthenticator;
import org.securityfilter.filter.SecurityRequestWrapper;
import org.securityfilter.filter.URLPatternMatcher;
import org.securityfilter.realm.SimplePrincipal;
import org.xwiki.container.servlet.filters.SavedRequestManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.openid.OpenIdHelper;
import com.xpn.xwiki.web.XWikiServletURLFactory;

public class MyFormAuthenticator extends FormAuthenticator implements XWikiAuthenticator
{
    private static final Log LOG = LogFactory.getLog(MyFormAuthenticator.class);

    /**
     * Show the login page.
     * 
     * @param request the current request
     * @param response the current response
     */
    public void showLogin(HttpServletRequest request, HttpServletResponse response, XWikiContext context)
        throws IOException
    {
        if ("1".equals(request.getParameter("basicauth"))) {
            String realmName = context.getWiki().Param("xwiki.authentication.realmname");
            if (realmName == null) {
                realmName = "XWiki";
            }
            MyBasicAuthenticator.showLogin(request, response, realmName);
        } else {
            showLogin(request, response);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.securityfilter.authenticator.Authenticator#showLogin(HttpServletRequest, HttpServletResponse)
     */
    @Override
    public void showLogin(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        String savedRequestId = request.getParameter(SavedRequestManager.getSavedRequestIdentifier());
        if (StringUtils.isEmpty(savedRequestId)) {
            // Save this request
            savedRequestId = SavedRequestManager.saveRequest(request);
        }
        String sridParameter = SavedRequestManager.getSavedRequestIdentifier() + "=" + savedRequestId;

        // Redirect to login page
        StringBuilder redirectBack = new StringBuilder(request.getRequestURI());
        redirectBack.append('?');
        String delimiter = "";
        if (StringUtils.isNotEmpty(request.getQueryString())) {
            redirectBack.append(request.getQueryString());
            delimiter = "&";
        }
        if (!request.getParameterMap().containsKey(SavedRequestManager.getSavedRequestIdentifier())) {
            redirectBack.append(delimiter);
            redirectBack.append(sridParameter);
        }
        response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + this.loginPage
            + "?" + sridParameter + "&xredirect=" + URLEncoder.encode(redirectBack.toString(), "UTF-8")));

        return;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.securityfilter.authenticator.FormAuthenticator#processLogin(org.securityfilter.filter.SecurityRequestWrapper,
     *      javax.servlet.http.HttpServletResponse)
     */
    @Override
    public boolean processLogin(SecurityRequestWrapper request, HttpServletResponse response) throws Exception
    {
        return processLogin(request, response, null);
    }

    private String convertUsername(String username, XWikiContext context)
    {
        return context.getWiki().convertUsername(username, context);
    }

    /**
     * Process any login information that was included in the request, if any. Returns true if SecurityFilter should
     * abort further processing after the method completes (for example, if a redirect was sent as part of the login
     * processing).
     * 
     * @param request
     * @param response
     * @return true if the filter should return after this method ends, false otherwise
     */
    public boolean processLogin(SecurityRequestWrapper request, HttpServletResponse response, XWikiContext context)
        throws Exception
    {
        try {
            Principal principal = MyBasicAuthenticator.checkLogin(request, response, context);
            if (principal != null) {
                return false;
            }
            if ("1".equals(request.getParameter("basicauth"))) {
                return true;
            }
        } catch (Exception e) {
            // in case of exception we continue on Form Auth.
            // we don't want this to interfere with the most common behaviour
        }

        // process any persistent login information, if user is not already logged in,
        // persistent logins are enabled, and the persistent login info is present in this request
        if (this.persistentLoginManager != null) {
            String username =
                    convertUsername(this.persistentLoginManager.getRememberedUsername(request, response), context);
            String password = this.persistentLoginManager.getRememberedPassword(request, response);
            Principal principal = request.getUserPrincipal();

            // 1) if user is not already authenticated, authenticate
            // 2) if authenticated user for this session does not have the same name, authenticate
            // 3) if xwiki.authentication.always is set to 1 in xwiki.cfg file, authenticate
            if (principal == null || !StringUtils.endsWith(principal.getName(), "XWiki." + username)
                || context.getWiki().ParamAsLong("xwiki.authentication.always", 0) == 1) {
                principal = authenticate(username, password, context);

                if (principal != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("User " + principal.getName() + " has been authentified from cookie");
                    }

                    // make sure the Principal contains wiki name information
                    if (!StringUtils.contains(principal.getName(), ':')) {
                        principal = new SimplePrincipal(context.getDatabase() + ":" + principal.getName());
                    }

                    request.setUserPrincipal(principal);
                } else {
                    // Failed to authenticate, better cleanup the user stored in the session
                    request.setUserPrincipal(null);
                    if (username != null || password != null) {
                        // Failed authentication with remembered login, better forget login now
                        this.persistentLoginManager.forgetLogin(request, response);
                    }
                }
            }
        }

        // process login form data
        if ((this.loginSubmitPattern != null) && request.getMatchableURL().endsWith(this.loginSubmitPattern)) {
            if (StringUtils.equalsIgnoreCase(request.getParameter("authentication_method") ,"openid")) {
                // OpenID login
                String openid_identifier = request.getParameter("openid_identifier");
                String rememberme = request.getParameter(FORM_REMEMBERME);
                rememberme = (rememberme == null) ? "false" : rememberme;
                return processOpenIdLogin(openid_identifier, rememberme, request, response, context);
            }
            else if (!StringUtils.isBlank(request.getParameter("openid.mode"))) {
                // The open ID provider gave a response. Let's process it.
                return processOpenIdLoginResponse(request, response, context);
            }
            else {
                // Normal user account login
                String username = convertUsername(request.getParameter(FORM_USERNAME), context);
                String password = request.getParameter(FORM_PASSWORD);
                String rememberme = request.getParameter(FORM_REMEMBERME);
                rememberme = (rememberme == null) ? "false" : rememberme;
                return processLogin(username, password, rememberme, request, response, context);
            } 
        }
        return false;
    }

    /**
     * Process any login information passed in parameter (username, password). Returns true if SecurityFilter should
     * abort further processing after the method completes (for example, if a redirect was sent as part of the login
     * processing).
     * 
     * @param username
     * @param password
     * @param rememberme
     * @param request
     * @param response
     * @return true if the filter should return after this method ends, false otherwise
     */
    public boolean processLogin(String username, String password, String rememberme, SecurityRequestWrapper request,
        HttpServletResponse response, XWikiContext context) throws Exception
    {
        Principal principal = authenticate(username, password, context);
        if (principal != null) {
            // login successful
            if (LOG.isInfoEnabled()) {
                LOG.info("User " + principal.getName() + " has been logged-in");
            }

            // invalidate old session if the user was already authenticated, and they logged in as a
            // different user
            if (request.getUserPrincipal() != null && !username.equals(request.getRemoteUser())) {
                request.getSession().invalidate();
            }

            // manage persistent login info, if persistent login management is enabled
            if (this.persistentLoginManager != null) {
                // did the user request that their login be persistent?
                if (rememberme != null) {
                    // remember login
                    this.persistentLoginManager.rememberLogin(request, response, username, password);
                } else {
                    // forget login
                    this.persistentLoginManager.forgetLogin(request, response);
                }
            }

            // make sure the Principal contains wiki name information
            if (!StringUtils.contains(principal.getName(), ':')) {
                principal = new SimplePrincipal(context.getDatabase() + ":" + principal.getName());
            }

            request.setUserPrincipal(principal);
            Boolean bAjax = (Boolean) context.get("ajax");
            if ((bAjax == null) || (!bAjax.booleanValue())) {
                String continueToURL = getContinueToURL(request);
                // This is the url that the user was initially accessing before being prompted for
                // login.
                response.sendRedirect(response.encodeRedirectURL(continueToURL));
            }
        } else {
            // login failed
            // set response status and forward to error page
            if (LOG.isInfoEnabled()) {
                LOG.info("User " + username + " login has failed");
            }

            String returnCode = context.getWiki().Param("xwiki.authentication.unauthorized_code");
            int rCode = HttpServletResponse.SC_UNAUTHORIZED;
            if ((returnCode != null) && (!returnCode.equals(""))) {
                try {
                    rCode = Integer.parseInt(returnCode);
                } catch (Exception e) {
                    rCode = HttpServletResponse.SC_UNAUTHORIZED;
                }
            }
            response.setStatus(rCode); // TODO: Does this work? (200 in case of error)
        }

        return true;
    }

    /**
     * Processes an OpenID login. It redirects the user as part of a normal OpenID login process to the OpenID provider.
     * The response is handled by {@link MyFormAuthenticator#processOpenIdLoginResponse processOpenIdLoginResponse}.
     * Returns true if SecurityFilter should abort further processing after the method completes (for example, if a
     * redirect was sent as part of the login processing which is the normal behaviour).
     * 
     * @param openid_identifier the OpenID identifier
     * @param rememberme <code>"true"</code> if the login should be persistent, <code>null</code> or
     *            <code>"false"</code> otherwise.
     * @param request the request object
     * @param response the response object
     * @return true if the filter should return after this method ends, false otherwise
     */
    public boolean processOpenIdLogin(String openid_identifier, String rememberme, SecurityRequestWrapper request,
        HttpServletResponse response, XWikiContext context) throws Exception
    {
        if (StringUtils.isBlank("openid_identifier")) {
            context.put("message", "noopenid");
            return false;
        }

        try {
            ConsumerManager manager = OpenIdHelper.getConsumerManager();

            String return_to_url =
                context.getWiki().getExternalURL("XWiki.XWikiLogin", "loginsubmit", "rememberme=" + rememberme, context);

            List discoveries = manager.discover(openid_identifier);
            DiscoveryInformation discovered = manager.associate(discoveries);

            // store the discovery information in the user's session
            request.getSession().setAttribute("openid-discovery", discovered);

            AuthRequest auth_request = manager.authenticate(discovered, return_to_url);

            // set the realm
            auth_request.setRealm(((XWikiServletURLFactory) context.getURLFactory()).getServerURL(context).toString()
                + ((XWikiServletURLFactory) context.getURLFactory()).getContextPath());

            if (LOG.isInfoEnabled()) {
                LOG.info("Redirecting user to OP (OpenID identifier: " + openid_identifier + ")");
            }

            if (discovered.isVersion2()) {
                // OpenID 2.0 supports HTML FORM Redirection which allows payloads >2048 bytes
                VelocityContext vcontext = (VelocityContext) context.get("vcontext");
                vcontext.put("op_endpoint", auth_request.getDestinationUrl(false));
                vcontext.put("openid_parameters", auth_request.getParameterMap());

                String redirect_form = context.getWiki().parseTemplate("openid_form_redirect.vm", context);

                response.getOutputStream().print(redirect_form);

                // Close the output stream - otherwise the LOGin form documented is also written to it
                response.getOutputStream().close();
            } else {
                // The only method supported in OpenID 1.x is a HTTP-redirect (GET) to the OpenID Provider endpoint (the
                // redirect-URL usually limited ~2048 bytes)
                response.sendRedirect(auth_request.getDestinationUrl(true));
            }
        } catch (OpenIDException e) {
            if (LOG.isInfoEnabled()) {
                LOG.info("OpenID discovery failed: " + e.getMessage());
            }

            // present error to the user
            context.put("message", "LOGinfailed");
        }

        return true;
    }

    /**
     * Processes the response of an OpenID provider to complete the LOGin process. Checks the response of the OP and in
     * case of success it LOGs in the user. Otherwise an error message is put into the context and shown to the user
     * afterwards.
     * 
     * @param request the request object
     * @param response the response object
     * @param context the context
     * @return true if the filter should return after this method ends, false otherwise
     */
    public boolean processOpenIdLoginResponse(SecurityRequestWrapper request, HttpServletResponse response,
        XWikiContext context) throws Exception
    {
        try {
            ConsumerManager manager = OpenIdHelper.getConsumerManager();

            // extract the parameters from the authentication response which come in as a HTTP request from the OpenID
            // provider
            ParameterList openid_response = new ParameterList(request.getParameterMap());

            // retrieve the previously stored discovery information
            DiscoveryInformation discovered =
                (DiscoveryInformation) request.getSession().getAttribute("openid-discovery");

            // verify the response
            StringBuffer receivingURL = request.getRequestURL();
            String queryString = request.getQueryString();
            if (!StringUtils.isEmpty(queryString)) {
                receivingURL.append("?").append(request.getQueryString());
            }

            VerificationResult verification = manager.verify(receivingURL.toString(), openid_response, discovered);
            Identifier verified = verification.getVerifiedId();

            if (verified != null) {
                String username = OpenIdHelper.findUser(verified.getIdentifier(), context);

                if (username == null) {
                    // no user was found for this OpenID identifier
                    if (LOG.isInfoEnabled()) {
                        LOG.info("No user for OpenID " + verified.getIdentifier() + " found.");
                    }

                    context.put("message", "openid_not_associated");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return true;
                }

                // The current authentication mechanisms is implemented in a very restrictive manner, we have to
                // retrieve the user password (which is generated randomly during registration for OpenID users) and use
                // it in order to authenticate the user.
                String password = OpenIdHelper.getOpenIdUserPassword(verified.getIdentifier(), context);
                Principal principal = authenticate(username, password, context);
                if (principal != null) {
                    // invalidate old session if the user was already authenticated and LOGged in as a different user
                    if (request.getUserPrincipal() != null) {
                        request.getSession().invalidate();
                    }

                    // manage persistent LOGin info if persistent LOGin management is enabled
                    String rememberme = request.getParameter(FORM_REMEMBERME);
                    rememberme = (rememberme == null) ? "false" : rememberme;

                    if (this.persistentLoginManager != null) {
                        if (rememberme != null) {
                            this.persistentLoginManager.rememberLogin(request, response, username, password);
                        } else {
                            this.persistentLoginManager.forgetLogin(request, response);
                        }
                    }

                    request.setUserPrincipal(principal);

                    String continueToURL = getContinueToURL(request);
                    response.sendRedirect(response.encodeRedirectURL(continueToURL));
                }
            } else {
                // authentication failed, show and LOG error message
                if (openid_response.getParameter("openid.mode") != null
                    && openid_response.getParameter("openid.mode").getValue().equals("cancel")) {
                    context.put("message", "openidLOGin_cancelled");
                } else {
                    if (LOG.isInfoEnabled() && openid_response.getParameter("error") != null) {
                        LOG.info("OpenID LOGin failed (error: "
                            + openid_response.getParameter("openid.error").getValue() + ")");
                    }
                    context.put("message", "LOGinfailed");
                }
            }
        } catch (OpenIDException e) {
            context.put("message", "LOGinfailed");
        }

        return true;
    }

    /**
     * FormAuthenticator has a special case where the user should be sent to a default page if the user spontaneously
     * submits a LOGin request.
     * 
     * @param request
     * @return a URL to send the user to after LOGging in
     */
    private String getContinueToURL(HttpServletRequest request)
    {
        String savedURL = request.getParameter("xredirect");
        if (StringUtils.isEmpty(savedURL)) {
            savedURL = SavedRequestManager.getOriginalUrl(request);
        }

        if (!StringUtils.isEmpty(savedURL)) {
            return savedURL;
        }
        return request.getContextPath() + this.defaultPage;
    }

    public static Principal authenticate(String username, String password, XWikiContext context) throws XWikiException
    {
        return context.getWiki().getAuthService().authenticate(username, password, context);
    }

    /**
     * {@inheritDoc}
     * 
     * @see Authenticator#processLogout(SecurityRequestWrapper, HttpServletResponse, URLPatternMatcher)
     */
    @Override
    public boolean processLogout(SecurityRequestWrapper securityRequestWrapper,
        HttpServletResponse httpServletResponse, URLPatternMatcher urlPatternMatcher) throws Exception
    {
        boolean result = super.processLogout(securityRequestWrapper, httpServletResponse, urlPatternMatcher);
        if (result == true) {
            if (this.persistentLoginManager != null) {
                this.persistentLoginManager.forgetLogin(securityRequestWrapper, httpServletResponse);
            }
        }
        return result;
    }
}
