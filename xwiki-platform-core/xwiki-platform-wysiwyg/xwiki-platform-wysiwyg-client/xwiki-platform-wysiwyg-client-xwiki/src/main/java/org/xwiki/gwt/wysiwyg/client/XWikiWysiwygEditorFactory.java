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
 */
package org.xwiki.gwt.wysiwyg.client;

import org.xwiki.gwt.wysiwyg.client.plugin.image.ImagePluginFactory;
import org.xwiki.gwt.wysiwyg.client.plugin.importer.ImportPluginFactory;
import org.xwiki.gwt.wysiwyg.client.plugin.link.LinkPluginFactory;
import org.xwiki.gwt.wysiwyg.client.plugin.macro.MacroPluginFactory;

/**
 * The XWiki-specific factory for creating the WYSIWYG editor.
 * 
 * @version $Id$
 * @since 4.1-M1
 */
public final class XWikiWysiwygEditorFactory extends StandaloneWysiwygEditorFactory implements WysiwygEditorFactory
{
    /**
     * The singleton factory instance.
     */
    private static XWikiWysiwygEditorFactory instance;

    /**
     * Initializes the {@link SyntaxValidatorManager} and {@link PluginFactoryManager} instances that will be injected
     * in the future editors.
     */
    private XWikiWysiwygEditorFactory()
    {
        super();

        // XWiki-specific plugins
        pfm.addPluginFactory(LinkPluginFactory.getInstance());
        pfm.addPluginFactory(ImagePluginFactory.getInstance());
        pfm.addPluginFactory(ImportPluginFactory.getInstance());
        pfm.addPluginFactory(MacroPluginFactory.getInstance());
    }

    /**
     * @return the singleton factory instance.
     */
    public static synchronized XWikiWysiwygEditorFactory getInstance()
    {
        if (instance == null) {
            instance = new XWikiWysiwygEditorFactory();
        }
        return instance;
    }

}
