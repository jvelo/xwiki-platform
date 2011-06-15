package com.xpn.xwiki.plugin.openid;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.sreg.SRegMessage;
import org.openid4java.message.sreg.SRegResponse;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.logging.AbstractLogEnabled;
import org.xwiki.context.Execution;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Default Open ID response handler.
 * 
 * Takes care of verifying the validity of the response, and create an XWiki user corresponding to the response OpenId user,
 * when required.
 * 
 * @version $Id$
 *
 */
@Component
public class DefaultOpenIdResponseHandler extends AbstractLogEnabled implements OpenIdResponseHandler
{
    @Requirement
    private OpenIdConfiguration configuration;
    
    @Requirement
    private Execution execution;
    
    /**
     * {@inheritDoc}
     */
    @Override
    public OpenIdUser handleResponse(boolean forceCreation) throws OpenIdException
    {
        XWikiContext context = (XWikiContext) this.execution.getContext().getProperty("xwikicontext");
        XWikiRequest request = context.getRequest();

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

                AuthSuccess auth_success = (AuthSuccess) verification.getAuthResponse();

                String email = null;
                String firstname = null;
                String lastname = null;
                String nickname = "";

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

                    if (firstname == null && lastname == null) {
                        firstname = simple_reg_resp.getAttributeValue("fullname");
                        lastname = "";
                    }
                    
                    nickname = simple_reg_resp.getAttributeValue("nickname");
                }

                OpenIdUser user = new OpenIdUser();
                String username = OpenIdHelper.findUser(verified.getIdentifier(), context);
                Map<String,String> metadata = new HashMap<String,String>();
                metadata.put(OpenIdHelper.METADATA_EMAIL, email);
                metadata.put(OpenIdHelper.METADATA_FIRST_NAME, firstname);
                metadata.put(OpenIdHelper.METADATA_LAST_NAME, lastname);
                metadata.put(OpenIdHelper.METADATA_NICKNAME, nickname);
                user.setMetadata(metadata);
                user.setIdentifier(verified.getIdentifier());
                
                if (username == null && this.configuration.isSeamlessUserCreation()) {

                    int result = OpenIdHelper.createUser(user, context);
                    if (result == 3) {
                        username = OpenIdHelper.findUser(verified.getIdentifier(), context);
                        getLogger().debug(MessageFormat.format("Successfully created OpenId user {0} with identifier {1}", new String[]{verified.getIdentifier(), username}));
                    } 
                    else {
                        getLogger().error(MessageFormat.format("Failed to create OpenId user with identifier {0}", new String[]{verified.getIdentifier()}));
                    }
                }
                user.setWikiName(username);
                return user;
                
            } else {
                // authentication failed, show and log error message
                if (openid_response.getParameter("openid.mode") != null
                    && openid_response.getParameter("openid.mode").getValue().equals("cancel")) {
                    context.put("message", "register_openid_discovery_cancelled");
                } else {
                    if (getLogger().isInfoEnabled() && openid_response.getParameter("error") != null) {
                        getLogger().info("OpenID login failed (error: "
                            + openid_response.getParameter("openid.error").getValue() + ")");
                    }
                    context.put("message", "register_openid_discovery_failed");
                }
                return null;
            }
        } catch (OpenIDException e) {
            throw new OpenIdException("Handling of Open Id response failed", e);
        } catch (XWikiException ex) {
            throw new OpenIdException("Handling of Open Id response failed", ex);
        }
    }

}
