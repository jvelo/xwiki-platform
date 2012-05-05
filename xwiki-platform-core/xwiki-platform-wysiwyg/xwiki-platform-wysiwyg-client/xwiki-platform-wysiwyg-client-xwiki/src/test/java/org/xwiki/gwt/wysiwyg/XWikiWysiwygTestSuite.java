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
package org.xwiki.gwt.wysiwyg;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.xwiki.gwt.wysiwyg.client.plugin.link.EmptyLinkFilterTest;
import org.xwiki.gwt.wysiwyg.client.plugin.link.LinkMetaDataExtractorTest;
import org.xwiki.gwt.wysiwyg.client.plugin.link.LinkConfigTest;

import com.google.gwt.junit.tools.GWTTestSuite;

/**
 * See {@link WysiwygTestSuite} for more information.
 * 
 * @version $Id$
 * @since 4.1-M1
 */
public class XWikiWysiwygTestSuite extends GWTTestSuite
{
    /**
     * @return The suite of all the client tests to be run.
     */
    public static Test suite()
    {
        TestSuite suite = new TestSuite("WYSIWYG Client Unit Tests");

        // syntax
        suite.addTestSuite(EmptyLinkFilterTest.class);
        suite.addTestSuite(LinkMetaDataExtractorTest.class);
        suite.addTestSuite(LinkConfigTest.class);

        return suite;
    }

}
