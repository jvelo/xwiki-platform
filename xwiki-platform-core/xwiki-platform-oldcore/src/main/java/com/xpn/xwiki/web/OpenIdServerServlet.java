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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.openid.OpenIdHelper;
import com.xpn.xwiki.user.api.XWikiUser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;

import org.openid4java.consumer.ConsumerException;
import org.openid4java.message.DirectError;
import org.openid4java.message.Message;
import org.openid4java.message.ParameterList;
import org.openid4java.server.ServerManager;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletContainerInitializer;
import org.xwiki.context.Execution;
import org.xwiki.velocity.VelocityManager;

/**
 * Action that implements the OpenID server support.
 * 
 * @since 2.3-milestone-2
 * @version $Id$
 */
public final class OpenIdServerServlet extends HttpServlet
{
    /** The log used by this class. */
    private static final Log log = LogFactory.getLog(OpenIdServerServlet.class);

    /** The name of the velocity template rendererd by this servlet. */
    private String template = "attach_openid";

    /** The address to use as a home page where the users are redirected. */
    private String home = "bin/view/Main/";

    /**
     * {@inheritDoc}
     * 
     * @see javax.servlet.GenericServlet#init()
     */
    @Override
    public void init() throws ServletException
    {
        super.init();
        // TODO: we cannot use the XWiki API to determine the right URL, because this is a servlet and the core
        // is reachable mainly from Struts. Getting access to the core requires too much duplication, so for the
        // moment we're going the easy way: hardcoded values.
        String homeParameter = getInitParameter("homePage");
        if (homeParameter != null) {
            this.home = homeParameter;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        handleRequest(request, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        handleRequest(request, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        handleRequest(request, response);
    }

    /**
     * Sets up a context for the request. It's more or less the same as
     * {@link com.xpn.xwiki.web.XWikiAction#action(XWikiContext)}.
     * 
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @see
     * @link com.xpn.xwiki.web.XWikiAction#action(XWikiContext)
     */
    protected void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException,
        IOException
    {
        XWikiRequest xwiki_request = new XWikiServletRequest(request);
        XWikiResponse xwiki_response = new XWikiServletResponse(response);
        XWikiContext context = null;

        try {
            context =
                Utils.prepareContext("openid", xwiki_request, xwiki_response, new XWikiServletContext(this
                    .getServletContext()));

            // Initialize the Container component which is the new of transporting the Context in the new
            // component architecture.
            ServletContainerInitializer containerInitializer =
                (ServletContainerInitializer) Utils.getComponent(ServletContainerInitializer.class);

            containerInitializer.initializeRequest(context.getRequest().getHttpServletRequest(), context);
            containerInitializer.initializeResponse(context.getResponse().getHttpServletResponse());
            containerInitializer.initializeSession(context.getRequest().getHttpServletRequest());

            // Verify that the requested wiki exists
            XWiki xwiki;
            try {
                xwiki = XWiki.getXWiki(context);
            } catch (XWikiException e) {
                // Should we output an error message here?
                return;
            }

            XWikiURLFactory urlf = xwiki.getURLFactoryService().createURLFactory(context.getMode(), context);
            context.setURLFactory(urlf);

            context.put("ajax", false);

            VelocityManager velocityManager = (VelocityManager) Utils.getComponent(VelocityManager.class);
            VelocityContext vcontext = velocityManager.getVelocityContext();

            XWikiDocument doc;
            context.getWiki().prepareResources(context);
            context.getWiki().setPhonyDocument("openid", context, vcontext);
            doc = context.getDoc();

            context.put("doc", doc);
            vcontext.put("doc", doc.newDocument(context));
            vcontext.put("cdoc", vcontext.get("doc"));
            XWikiDocument tdoc = doc.getTranslatedDocument(context);
            context.put("tdoc", tdoc);
            vcontext.put("tdoc", tdoc.newDocument(context));

            // Execute business logic
            if (action(context) == true)
                render(context);

            // Let's make sure we have flushed content and closed
            try {
                context.getResponse().getWriter().flush();
            } catch (Throwable e) {
                // This might happen if the connection was closed, for example.
                // If we can't flush, then there's nothing more we can send to the client.
            }

            // Make sure we cleanup database connections
            // There could be cases where we have some
            if ((context != null) && (xwiki != null)) {
                xwiki.getStore().cleanUp(context);
            }

        } catch (Exception e) {
            // do nothing...
        } finally {

            if (context != null) {
                // Cleanup components
                Container container = (Container) Utils.getComponent(Container.class);
                Execution execution = (Execution) Utils.getComponent(Execution.class);

                // We must ensure we clean the ThreadLocal variables located in the Container and Execution
                // components as otherwise we will have a potential memory leak.
                container.removeRequest();
                container.removeResponse();
                container.removeSession();
                execution.removeContext();
            }
        }
    }

    /**
     * Executes all actions.
     * 
     * @param context the used context
     * @return <code>TRUE</code> if a template should be rendered, <code>FALSE</code> if no template should be
     *         rendered or a redirect was sent.
     * @throws XWikiException
     */
    private boolean action(XWikiContext context) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        XWikiResponse response = context.getResponse();
        boolean enabled = "1".equals(context.getWiki().Param("xwiki.authentication.openid.server.enabled"));

        // Check if a XRDS document was requested
        String req_user = request.getRequestURI().substring(request.getContextPath().length() + 8);
        if ("xrds.xml".equals(req_user)) {
            outputXRDS(null, context); // global XRDS document
            return true;
        } else if ("xrds.xml".equals(req_user.substring(req_user.indexOf("/") + 1))) {
            outputXRDS(req_user.substring(0, req_user.indexOf("/")), context); // user XRDS document
            return true;
        } else if (req_user.length() > 0 && req_user.contains("/") == false) {
            showUserPage(req_user, context);
            return true;
        }

        ServerManager manager = null;
        try {
            manager = OpenIdHelper.getServerManager(context);
        } catch (ConsumerException e) {
            if (log.isInfoEnabled()) {
                log.error("Couldn't get a ServerManager instance.");
            }

            template = "exception";
            return true;
        }

        ParameterList request_parameters = new ParameterList(request.getParameterMap());
        Message response_message;
        String response_text = null;

        String mode = request.getParameter("openid.mode");
        if (enabled == false || request_parameters.getParameters().size() == 0) {
            // No OpenID action, show homepage and publish XRDS location via HTTP header
            VelocityContext vcontext = (VelocityContext) context.get("vcontext");
            vcontext.put("enabled", enabled);
            vcontext.put("openid_server_url", OpenIdHelper.getOpenIdServerURL(context));

            response.setHeader("X-XRDS-Location", OpenIdHelper.getOpenIdServerURL(context) + "xrds.xml");
            template = "openid_server_homepage";
            return true;

        } else if ("associate".equals(mode)) {
            // Process an association request
            response_message = manager.associationResponse(request_parameters);
            response_text = response_message.keyValueFormEncoding();

        } else if ("checkid_setup".equals(mode)) {
            // Process an authentication request setup request
            return processCheckIdSetup(manager, context);

        } else if ("checkid_immediate".equals(mode)) {
            // Immediate authentication fails always since we don't support it at the moment
            response_message =
                manager.authResponse(request_parameters, request.getParameter("openid.claimed_id"), request
                    .getParameter("openid.identity"), false);

            sendRedirect(response, response_message.getDestinationUrl(true));
            return false;

        } else if ("check_authentication".equals(mode)) {
            // Processing a verification request
            response_message = manager.verify(request_parameters);
            response_text = response_message.keyValueFormEncoding();

        } else {
            // Error response
            response_message = DirectError.createDirectError("Unknown request");
            response_text = response_message.keyValueFormEncoding();
        }

        // Try to directly output the response
        try {
            context.getResponse().getWriter().write(response_text);
            return false;
        } catch (IOException e) {
            if (log.isInfoEnabled()) {
                log.error("Couldn't output the response.");
            }

            template = "exception";
            return true;
        }
    }

    /**
     * Renders the template and outputs it.
     * 
     * @param context the context
     * @throws XWikiException
     */
    protected void render(XWikiContext context) throws XWikiException
    {
        String page = Utils.getPage(context.getRequest(), template);
        Utils.parseTemplate(page, true, context);
    }

    /**
     * Helper method to process checkid_setup requests.
     * 
     * @param manager the OpenID server manager instance
     * @param context the context
     * @return <code>TRUE</code> if a template should be rendered, <code>FALSE</code> if no template should be
     *         rendered or a redirect was sent.
     * @throws XWikiException
     */
    private boolean processCheckIdSetup(ServerManager manager, XWikiContext context) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        XWikiResponse response = context.getResponse();
        ParameterList request_parameters = new ParameterList(request.getParameterMap());
        Message response_message;

        // First check if the user is logged in. If not show the login form
        XWikiUser user = context.getWiki().checkAuth(context);
        if ((user == null) && ((user = context.getXWikiUser()) == null)) {
            context.getWiki().getAuthService().showLogin(context);
            return false;
        }

        if (request.getParameter("confirmation") == null) {
            // ask the user to confirm the authentication request
            VelocityContext vcontext = (VelocityContext) context.get("vcontext");
            vcontext.put("openid_server_url", OpenIdHelper.getOpenIdServerURL(context));
            vcontext.put("openid_identifier", OpenIdHelper.getUserOpenId(user.getUser(), context));
            if (request_parameters.hasParameter("openid.realm")) {
                vcontext.put("site", request_parameters.getParameterValue("openid.realm"));
            } else {
                vcontext.put("site", request_parameters.getParameterValue("openid.return_to"));
            }
            template = "confirm_openid_authentication_request";

            // store the request parameters because they are needed to create the response afterwards
            request.getSession().setAttribute("openid-authentication-parameterlist", request_parameters);
            return true;

        } else if (request.getParameter("login") != null) {
            // the user confirmed the authentication request
            String identity = OpenIdHelper.getUserOpenId(user.getUser(), context);
            String claimed_id = OpenIdHelper.getUserOpenId(user.getUser(), context);

            request_parameters =
                (ParameterList) request.getSession().getAttribute("openid-authentication-parameterlist");
            request.getSession().removeAttribute("openid-authentication-parameterlist");

            response_message = manager.authResponse(request_parameters, identity, claimed_id, true);
            sendRedirect(response, response_message.getDestinationUrl(true));
            return false;
            

        } else {
            // the user cancelled the authentication request
            String identity = OpenIdHelper.getUserOpenId(user.getUser(), context);
            String claimed_id = OpenIdHelper.getUserOpenId(user.getUser(), context);

            request_parameters =
                (ParameterList) request.getSession().getAttribute("openid-authentication-parameterlist");
            request.getSession().removeAttribute("openid-authentication-parameterlist");

            response_message = manager.authResponse(request_parameters, identity, claimed_id, false);
            sendRedirect(response, response_message.getDestinationUrl(true));
            return false;
        }
    }

    /**
     * Helper method to output the gloabal or a user specific XRDS document.
     * 
     * @param req_user username if the XRDS of a specific user is requested, otherwise <code>null</code>.
     * @param context
     * @return
     */
    private void outputXRDS(String username, XWikiContext context) throws XWikiException
    {
        VelocityContext vcontext = (VelocityContext) context.get("vcontext");
        vcontext.put("openid_server_url", OpenIdHelper.getOpenIdServerURL(context));

        // Set the action to openid_xrds so that com.xpn.xwiki.web.Utils#parseTemplate uses the right content type
        context.setAction("openid_xrds");

        if (username == null) {
            // Output XRDS file
            template = "openid_xrds";
        } else {
            // Output user XRDS file
            template = "openid_user_xrds";
            vcontext.put("openid_identifier", OpenIdHelper.getUserOpenId("XWiki." + username, context));
        }
    }

    /**
     * Helper method to display an user profile page.
     * 
     * @param username the username
     * @param context the context
     * @throws XWikiException
     */
    private void showUserPage(String username, XWikiContext context) throws XWikiException
    {
        // Publish the XRDS location via HTTP header
        context.getResponse().setHeader("X-XRDS-Location",
            OpenIdHelper.getOpenIdServerURL(context) + username + "/xrds.xml");

        
        VelocityContext vcontext = (VelocityContext) context.get("vcontext");
        username = "XWiki." + username;
        vcontext.put("username", context.getWiki().getUserName(username, context));

        template = "openid_user_profile";
    }

    /**
     * Redirects to another page.
     * 
     * @param response XWikiResponse object
     * @param page redirection target
     * @throws XWikiException
     */
    private void sendRedirect(XWikiResponse response, String page) throws XWikiException
    {
        try {
            if (page != null) {
                response.sendRedirect(page);
            }
        } catch (IOException e) {
            Object[] args = {page};
            throw new XWikiException(XWikiException.MODULE_XWIKI_APP,
                XWikiException.ERROR_XWIKI_APP_REDIRECT_EXCEPTION,
                "Exception while sending redirect to page {0}",
                e,
                args);
        }
    }
}
