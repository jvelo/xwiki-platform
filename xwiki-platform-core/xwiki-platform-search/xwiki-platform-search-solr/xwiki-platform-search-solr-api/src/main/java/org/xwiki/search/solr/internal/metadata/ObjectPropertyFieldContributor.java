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
package org.xwiki.search.solr.internal.metadata;

import java.util.List;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.ObjectPropertyReference;

import com.xpn.xwiki.objects.BaseProperty;

/**
 * Object property fields contributors allow external module to contribute to solr document metadata for individual
 * object properties.
 * 
 * @version $Id$
 */
@Role
public interface ObjectPropertyFieldContributor
{
    /**
     * Simple key/value pair representing a Solr document field.
     */
    public class Field
    {
        /**
         * The name of the field.
         */
        private String name;

        /**
         * The value of the field.
         */
        private Object value;

        /**
         * Field constructor that takes a name/value pair
         * 
         * @param name this field's name
         * @param value this field's value
         */
        public Field(String name, Object value)
        {
            this.name = name;
            this.value = value;
        }

        /**
         * @return the name of this field.
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the value of this field.
         */
        public Object getValue()
        {
            return value;
        }
    }

    /**
     * Contribute metadata to a solr document for an object property
     * 
     * @param objectProperty the object property for which to contribute
     * @return the contributed fields or an empty list of no field is contributed
     */
    List<Field> contribute(BaseProperty<ObjectPropertyReference> objectProperty);
}
