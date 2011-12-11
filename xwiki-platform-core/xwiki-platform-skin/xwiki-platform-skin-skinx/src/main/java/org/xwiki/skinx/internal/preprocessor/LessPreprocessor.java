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
package org.xwiki.skinx.internal.preprocessor;

import javax.inject.Inject;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.skinx.PreprocessingException;
import org.xwiki.skinx.SkinExtensionPreprocessor;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;

/**
 * LESS dynamic stylesheet preprocessor.
 * 
 * @version $Id$
 * @since 3.4-M1
 */
@Component("less")
public class LessPreprocessor implements SkinExtensionPreprocessor
{

    /** A worker with a hipster jacket. */
    @Inject
    private Logger logger;

    @Override
    public String process(String source) throws PreprocessingException
    {
        LessEngine engine = new LessEngine();
        try {
            return StringEscapeUtils.unescapeJava(engine.compile(StringUtils.replace(source, "\n", "")));
        } catch (LessException e) {
            this.logger.warn("Failed to compile extension with LESS preprocessor", e);
            throw new PreprocessingException(e);
        }
    }

}
