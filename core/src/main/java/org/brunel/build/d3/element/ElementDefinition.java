/*
 * Copyright (c) 2016 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.build.d3.element;

import org.brunel.build.util.ModelUtil;
import org.brunel.data.Data;
import org.brunel.model.VisSingle;

/**
 * Stores the functionality needed to build an element.
 * This is a struct-like object that is constructed using the scales and then used to write out
 * the required definitions. Any field that is defined may be used
 */
public class ElementDefinition {

    public final ElementDimensionDefinition x, y;       // Definitions for x and y fields
    public String overallSize;                          // A general size for the whole item
    private String refLocation;                          // Defines the location using a reference to another element
    public String clusterSize;                          // The size of a cluster

    public ElementDefinition(VisSingle vis) {
        x = new ElementDimensionDefinition(vis, "width");
        y = new ElementDimensionDefinition(vis, "height");
    }

    public String getRefLocation() {
        return refLocation;
    }

    public void setReferences(String[] references) {
        this.refLocation = "[" + Data.join(references) + "]";
    }

    public static class ElementDimensionDefinition {
        public final ModelUtil.Size sizeStyle;          // The size as defined by a style
        public final String sizeFunction;               // The size as modified by aesthetic function

        public String center;                          // Where the center is to be
        public String left;                            // Where the left is to be (right will also be defined)
        public String right;                           // Where the right is to be (left will also be defined)
        public String size;                            // What the size is to be

        public ElementDimensionDefinition(VisSingle vis, String sizeName) {
            sizeStyle = ModelUtil.getElementSize(vis, sizeName);
            if (vis.fSize.isEmpty()) sizeFunction = null;                   // No sizing
            else if (vis.fSize.size() == 1) sizeFunction = "size(d)";       // Multiply by overall size
            else sizeFunction = sizeName + "(d)";                           // use width(d) or height(d)

        }

        public boolean defineUsingCenter() {
            return center != null;
        }

        public boolean defineUsingExtent() {
            return left != null;
        }
    }
}
