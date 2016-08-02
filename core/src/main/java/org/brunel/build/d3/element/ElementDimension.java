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
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleTarget;

/**
 * Describes how we will define an element geometry dimension (usually by center + size, or left-right)
 */
public class ElementDimension {
    public final ModelUtil.Size sizeStyle;          // The size as defined by a style
    public final String sizeFunction;               // The size as modified by aesthetic function

    public GeomAttribute center;                    // Where the center is to be
    public GeomAttribute left;                      // Where the left is to be (right will also be defined)
    public GeomAttribute right;                     // Where the right is to be (left will also be defined)
    public GeomAttribute size;                      // What the size is to be
    public GeomAttribute clusterSize;               // What the size of a cluster is

    public ElementDimension(VisSingle vis, String sizeName, ElementRepresentation representation, String[] classes) {
        StyleTarget target = StyleTarget.makeElementTarget(representation.getMark(), classes);
        sizeStyle = ModelUtil.getSize(vis, target, sizeName);

        if (vis.fSize.isEmpty()) sizeFunction = null;                   // No sizing
        else if (vis.fSize.size() == 1) sizeFunction = "size(d)";       // Multiply by overall size
        else sizeFunction = sizeName + "(d)";                           // use width(d) or height(d)
    }

    public boolean defineUsingExtent() {
        if (center == null) return true;                                // This requires extent and ignores size
        if (left == null || right == null) return false;                // We need left and right
        return sizeStyle == null && sizeFunction == null;               // and no size definitions
    }
}
