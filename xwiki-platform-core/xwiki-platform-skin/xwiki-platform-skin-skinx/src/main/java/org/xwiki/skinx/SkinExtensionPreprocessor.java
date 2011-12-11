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
package org.xwiki.skinx;

import org.xwiki.component.annotation.ComponentRole;

/**
 * Represents an extension preprocessor. Think CoffeeScript, SASS, LESS, HAML, etc.
 * 
 * @version $Id$
 * @since 2.4-M1
 */
@ComponentRole
public interface SkinExtensionPreprocessor
{

    /**
     * Processes an extension source to transform it to the target source/file format.
     * @param source the source extension to transform.
     * @return the transformed source
     * @throws PreprocessingException when the processing operation fails.
     */
    String process(String source) throws PreprocessingException;
}
