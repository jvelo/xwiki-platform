package com.xpn.xwiki.plugin.openid;

import java.util.Map;

/**
 * Simple POJO reprensenting an OpenID user.
 * 
 * @version $Id$
 */
public class OpenIdUser
{
    private Map<String,String> metadata;
    
    private String wikiName;
    
    private String identifier;
    
    public OpenIdUser()
    {        
    }
    
    public String getIdentifier()
    {
        return identifier;
    }

    public void setIdentifier(String identifier)
    {
        this.identifier = identifier;
    }
    
    public Map<String, String> getMetadata()
    {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata)
    {
        this.metadata = metadata;
    }

    public String getWikiName()
    {
        return wikiName;
    }

    public void setWikiName(String wikiName)
    {
        this.wikiName = wikiName;
    }
    
    public boolean existsInWiki()
    {
        return this.wikiName != null;
    }
    
}
