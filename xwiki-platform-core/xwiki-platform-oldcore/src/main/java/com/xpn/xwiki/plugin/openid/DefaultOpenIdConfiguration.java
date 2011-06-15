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

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.configuration.ConfigurationSource;

/**
 * Default Open ID configuration based on the standard XWiki configuration source.
 */
@Component
public class DefaultOpenIdConfiguration implements OpenIdConfiguration
{
    /**
     * Used to access xwiki configuration details.
     */
    @Requirement
    private ConfigurationSource source;
    
    /**
     * Common prefix for all paypal express checkout property keys.
     */
    private static final String PREFIX = "openid.";
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSeamlessUserCreation()
    {
        return this.source.getProperty(PREFIX + "seamlessUserCreation", true);
    }

}
