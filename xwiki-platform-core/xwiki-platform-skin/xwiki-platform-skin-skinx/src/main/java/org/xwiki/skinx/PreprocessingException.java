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

/**
 * Represents an error preprocessing an extension.
 *
 * @version $Id$
 * @since 3.4-M1
 */
public class PreprocessingException extends Exception
{

    /**
     * Generated serial UID. Change when the serialization of this classe changes.
     */
    private static final long serialVersionUID = 7085135808720491787L;

    /**
     * Constructor of this exception.
     */
    public PreprocessingException()
    {
        super();
    }
    
    /**
     * Constructor of this exception.
     * 
     * @param t the original, underlying exception
     */
    public PreprocessingException(Throwable t)
    {
        super(t);
    }
    
    /**
     * Constructor of this exception.
     * 
     * @param message an human-readable message associated with this exception
     * @param t the original, underlying exception
     */
    public PreprocessingException(String message, Throwable t)
    {
        super(message, t);
    }    
}
