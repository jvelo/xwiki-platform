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
package com.xpn.xwiki.plugin.openid;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.server.ServerManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.syntax.Syntax;

import com.ibm.icu.text.MessageFormat;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.web.XWikiServletURLFactory;

/**
 * OpenID helper class. This singleton class contains various helper methods used for OpenID related tasks.
 * 
 * @since 2.3-milestone-2
 * @version $Id$
 */
public final class OpenIdHelper
{

    /** The fullName of the document that contains the class definition of an OpenID identifier. */
    private static final String XWIKI_OPENIDIDENTIFIER_CLASSNAME = "XWiki.OpenIdIdentifier";

    /** The name of the field representing the user password in the XWiki.XWikiUsers class. */
    private static final String PASSWORD_FIELD_NAME = "password";

    /** The name of the field representing the identifier in the XWiki.OpenIdIdentifier class. */
    private static final String IDENTIFIER_FIELD_NAME = "identifier";

    /** String representing a dot (.). */
    private static final String DOT = ".";

    /** The name of the XWiki space. */
    private static final String XWIKI_SPACENAME = "XWiki";

    /** The name of the XWiki.XWikiUsers document. */
    private static final String XWIKIUSERS_DOCUMENT_NAME = "XWiki.XWikiUsers";

    /** The log used by this class. */
    private static final Log LOG = LogFactory.getLog(OpenIdHelper.class);

    /** Our instance. */
    private static OpenIdHelper instance;

    /** The Open Id consumer manager this class uses. */
    private ConsumerManager consumerManager;

    /** The Open Id server manager this class uses. */
    private ServerManager serverManager;

    /**
     * The constructor instantiates a ConsumerManager and a ServerManager object.
     * 
     * @throws ConsumerException when the consumer manager failed to initialize.
     */
    private OpenIdHelper() throws ConsumerException
    {
        consumerManager = new ConsumerManager();
        serverManager = new ServerManager();
    }

    /**
     * Gets the unique ConsumerManager class.
     * 
     * @return the unique ConsumerManager instance
     * @throws ConsumerException when the consumer manager failed to initialize.
     */
    public static ConsumerManager getConsumerManager() throws ConsumerException
    {
        if (instance == null) {
            instance = new OpenIdHelper();
        }

        return instance.consumerManager;
    }

    /**
     * Gets the unique ServerManager class.
     * 
     * @param context the context
     * @return the unique ServerManager instance
     * @throws ConsumerException when the consumer manager failed to initialize.
     */
    public static ServerManager getServerManager(XWikiContext context) throws ConsumerException
    {
        if (instance == null) {
            instance = new OpenIdHelper();
            instance.serverManager.setOPEndpointUrl(getOpenIdServerURL(context));
        }

        return instance.serverManager;
    }

    /**
     * Finds the user belonging to a specific OpenID identifier.
     * 
     * @param openidIdentifier the OpenID identifier to search for
     * @param context the context
     * @return the full document name for the user belonging to the OpenID identifier or <code>null</code> if the OpenID
     *         identifier was not found.
     */
    public static String findUser(String openidIdentifier, XWikiContext context)
    {
        XWiki xwiki = context.getWiki();

        QueryManager qm = xwiki.getStore().getQueryManager();
        Query searchUser;
        try {
            searchUser = qm.getNamedQuery("getUserDocByOpenIdIdentifier");
        } catch (QueryException e) {
            throw new RuntimeException("Named query 'getUserDocByOpenIdIdentifier' was not found!", e);
        }

        searchUser.bindValue(IDENTIFIER_FIELD_NAME, openidIdentifier);

        try {
            List<String> foundUsers = searchUser.setLimit(1).execute();

            if (foundUsers.size() > 0) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(MessageFormat.format("OpenID {0} already registered.", new String[] {openidIdentifier}));
                }
                return foundUsers.get(0);
            }

        } catch (QueryException e) {
            throw new RuntimeException("Failed to execute query getUserDocByOpenIdIdentifier", e);
        }

        return null;
    }

    /**
     * Creates a new OpenID user with a random password. The user doesn't need to know the password - in fact he even
     * doesn't need to know of its existence. It's just used to use the {@link PersistentLoginManager} and the
     * Authenticator as they are implemented at the moment.
     * 
     * @param openidIdentifier the OpenID identifier
     * @param firstname users first name
     * @param lastname users last name
     * @param email users email address
     * @param context the context
     * @return a code which describes the success or failure of the method
     * @throws XWikiException when the user creation failed
     */
    public static int createUser(String openidIdentifier, String firstname, String lastname, String email,
        XWikiContext context) throws XWikiException
    {
        XWiki xwiki = context.getWiki();
        String xwikiname = xwiki.clearName(MessageFormat.format("{0}{1}", new String[] {firstname, lastname}), context);

        DocumentReference userdocRef = new DocumentReference(context.getDatabase(), XWIKI_SPACENAME, xwikiname);
        // Generate a unique document name for the new user
        XWikiDocument userdoc = xwiki.getDocument(userdocRef, context);
        while (!userdoc.isNew()) {
            userdocRef =
                new DocumentReference(context.getDatabase(), XWIKI_SPACENAME, xwikiname
                    + xwiki.generateRandomString(5));
            userdoc = xwiki.getDocument(userdocRef, context);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating user for OpenID " + openidIdentifier);
        }

        String content = "{{include document=\"XWiki.XWikiUserSheet\" /}}";
        String parent = XWIKIUSERS_DOCUMENT_NAME;
        Map<String, String> map = new HashMap<String, String>();
        map.put("active", "1");
        map.put("first_name", StringUtils.isEmpty(firstname) ? openidIdentifier : firstname);

        if (lastname != null) {
            map.put("last_name", lastname);
        }
        if (email != null) {
            map.put("email", email);
        }

        String password = xwiki.generateRandomString(255);
        map.put(PASSWORD_FIELD_NAME, password);

        int result = xwiki.createUser(xwikiname, map, parent, content, Syntax.XWIKI_2_0.toIdString(), "edit", context);
        if (result == 1) {
            // change the return value to output a different message for OpenID users
            result = 3;

            userdoc = xwiki.getDocument(XWIKI_SPACENAME + DOT + xwikiname, context);

            if (!attachOpenIdToUser(userdoc, openidIdentifier, context)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Deleting previously created document for OpenID user " + openidIdentifier
                        + ". OpenID identifier is already in use.");
                }
                xwiki.deleteDocument(userdoc, false, context);
                result = -13;
            }

        }
        return result;
    }

    /**
     * Attaches an OpenID identifier to a document. The method assures that the identifier is unique. If the OpenID
     * identifier is already used, the method fails and returns <code>false</code>. If the the document has already
     * attached an OpenID it will be replaced by this method. <strong>The document is automatically saved by this
     * method.</strong>
     * 
     * @param doc document to which the OpenID identifier should be attached
     * @param openidIdentifier the OpenID identifier to attach
     * @param context the context
     * @return <code>true</code> if attaching the OpenID identifier was successful, otherwise <code>false</code>.
     * @throws XWikiException when saving the user document fails
     */
    public static synchronized boolean attachOpenIdToUser(XWikiDocument doc, String openidIdentifier,
        XWikiContext context) throws XWikiException
    {
        XWiki xwiki = context.getWiki();

        if (findUser(openidIdentifier, context) != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("OpenID " + openidIdentifier + " is already registered");
            }
            return false;
        }

        BaseClass baseclass = getOpenIdIdentifierClass(context);

        BaseObject object = doc.getObject(XWIKI_OPENIDIDENTIFIER_CLASSNAME);
        if (object == null) {
            object = (BaseObject) baseclass.newObject(context);
            object.setName(doc.getFullName());
            doc.addObject(baseclass.getName(), object);
        }
        object.setStringValue(IDENTIFIER_FIELD_NAME, openidIdentifier);

        xwiki.saveDocument(doc, context.getMessageTool().get("core.comment.addedOpenIdIdentifier"), context);

        return true;
    }

    /**
     * Retrieves the password for an OpenID identifier. Since for all OpenID users a random password which the user
     * doesn't know is created automatically, we need some way to retrieve it. This method does exactly that.
     * 
     * @param openidIdentifier the OpenID identifier
     * @param context the context
     * @return the internal used password for the OpenID user or <code>null</code> if the user was not found
     * @throws XWikiException when the user document could not be retrieve against the wiki.
     */
    public static String getOpenIdUserPassword(String openidIdentifier, XWikiContext context) throws XWikiException
    {
        String xwikiname = findUser(openidIdentifier, context);

        XWikiDocument doc = context.getWiki().getDocument(xwikiname, context);
        // We only allow empty password from users having a XWikiUsers object.
        if (doc.getObject(XWIKI_OPENIDIDENTIFIER_CLASSNAME) != null 
            && doc.getObject(XWIKIUSERS_DOCUMENT_NAME) != null) {
            return doc.getStringValue(XWIKIUSERS_DOCUMENT_NAME, PASSWORD_FIELD_NAME);
        }

        return null;
    }

    /**
     * Retrieves the OpenID identifier for an user.
     * 
     * @param username the user
     * @param context the context
     * @return the OpenID identifier for the passed user name or <code>null</code> if the user was not found
     * @throws XWikiException when the user document could not be retrieve against the wiki.
     */
    public static String getUserOpenId(String username, XWikiContext context) throws XWikiException
    {
        XWikiDocument doc = context.getWiki().getDocument(username, context);
        if (doc != null && doc.getObject(XWIKIUSERS_DOCUMENT_NAME) != null) {
            return getOpenIdServerURL(context) + doc.getName();
        }

        return null;
    }

    /**
     * Returns the URL of the OpenID server.
     * 
     * @param context the context
     * @return the URL of the OpenID server endpoint.
     */
    public static String getOpenIdServerURL(XWikiContext context)
    {
        try {
            return ((XWikiServletURLFactory) context.getURLFactory()).getServerURL(context).toString()
                + ((XWikiServletURLFactory) context.getURLFactory()).getContextPath() + "openid/";
        } catch (MalformedURLException e) {
            // should not happen :-)
            return null;
        }
    }

    /**
     * Gets the OpenIDIdentifer class. Verifies if the <code>XWiki.OpenIdIdentifier</code> page exists and that it
     * contains all the required configuration properties to make the OpenID feature work properly. If some properties
     * are missing they are created and saved in the database.
     * 
     * @param context the XWiki Context
     * @return the OpenIdIdentifier Base Class object containing the properties
     * @throws XWikiException if an error happens while saving
     */
    public static BaseClass getOpenIdIdentifierClass(XWikiContext context) throws XWikiException
    {
        XWiki xwiki = context.getWiki();
        XWikiDocument doc;
        boolean needsUpdate = false;

        doc = xwiki.getDocument(XWIKI_OPENIDIDENTIFIER_CLASSNAME, context);

        BaseClass bclass = doc.getxWikiClass();
        bclass.setName(XWIKI_OPENIDIDENTIFIER_CLASSNAME);

        needsUpdate |= bclass.addTextField(IDENTIFIER_FIELD_NAME, "Identifier", 2048);
        needsUpdate |= bclass.addTextField(PASSWORD_FIELD_NAME, "Password", 255);

        if (needsUpdate) {
            xwiki.saveDocument(doc, context);
        }

        return bclass;
    }
}
