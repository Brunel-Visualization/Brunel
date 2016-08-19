/*
 * Copyright (c) 2015 IBM Corporation and others.
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

package org.brunel.model;

public class VisTypes {

    /* Axes types */
    public enum Axes {
        none, x, y
    }

    /* Element usage modifiers */
    public enum Using {
        none, dodge, interpolate
    }

    /* How we compose visualizations */
    public enum Composition {
        inside, nested, overlay, tile
    }

    /* Coordinate methods */
    public enum Coordinates {
        regular, transposed, coords, polar
    }

    /* Our diagram layouts; each may have an option default element to use */
    public enum Diagram {
        bubble(Element.point), chord(Element.edge), cloud(Element.text), tree(Element.point),
        table(Element.text), parallel(Element.line), treemap(Element.bar), network(Element.point), map(Element.polygon);

        public final Element defaultElement;

        Diagram(Element defaultElement) {
            this.defaultElement = defaultElement;
        }

    }

    /* Element types */
    public enum Element {
        area(true, true, true), bar(false, true, true), edge(false, false, true), line(true, false, false),
        path(true, false, false), point(false, true, false), polygon(true, true, false), text(false, true, false);

        public final boolean producesSingleShape;       // If true, multiple rows make a single shape
        public final boolean filled;                    // If true, element typically has fill and stroke
        public final boolean showsRange;                // If true, the element spans a range of 2 y values

        Element(boolean producesSingleShape, boolean filled, boolean showsRange) {
            this.producesSingleShape = producesSingleShape;
            this.filled = filled;
            this.showsRange = showsRange;
        }
    }

    /* Axes types */
    public enum Legends {
        all, auto, none
    }

    /* Interaction types */
    public enum Interaction {
        select,                     // Select items using mouse
        panzoom,                    // Allow panning and zooming using the mouse
        filter,                     // Apply filtering to the data
        call,                       // A custom call to user code
        auto,                       // automatic interaction behavior
        none                        // turn off interactivity
    }

}
