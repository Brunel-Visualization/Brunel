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

}
