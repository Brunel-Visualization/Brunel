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

package org.brunel.build.element;

import org.brunel.build.util.ModelUtil;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

/**
 * Encapsulate information on how we want to represent different types of element
 */
public class ElementDetails {

    public static ElementDetails makeForCoordinates(VisSingle vis, String symbol) {
        return new ElementDetails(vis, symbol);
    }

    public final boolean splitIntoShapes;               // Will produce one shape per split
    public final String colorAttribute;                 // 'fill' or 'stroke' as appropriate
    public final String dataSource;                     // Where the data for d3 lives
    public final boolean producesPath;                  // If true, this produces a path eventually
    public final String elementType;                    // The type of element produced
    public final String classes;                        // Class names for this item
    public final String textMethod;                     // How to fit text to the shape
    public final boolean textMustFit;                   // If true, text must fit inside
    public boolean needsStrokeSize;                     // If we must define stroke-size using the "size" aesthetic

    private ElementDetails(VisSingle vis, String symbol) {
        VisTypes.Element element = vis.tElement;
        String classList = "element " + element.name();

        // Work out if the element is filled
        boolean filled = element.filled;
        if (!vis.fSize.isEmpty() && (element == VisTypes.Element.line || element == VisTypes.Element.path)) {
            filled = true;
            classList += " filled";
        }

        this.classes = "'" + classList + "'";
        this.splitIntoShapes = element.producesSingleShape;
        this.colorAttribute = filled ? "fill" : "stroke";
        this.dataSource = element.producesSingleShape ? "splits" : "data._rows";
        this.producesPath = element.producesSingleShape ||
                (element == VisTypes.Element.bar && vis.coords == VisTypes.Coordinates.polar);
        if (producesPath)
            this.elementType = "path";
        else if (element == VisTypes.Element.edge)
            this.elementType = "line";
        else if (element == VisTypes.Element.text)
            this.elementType = "text";
        else if (element == VisTypes.Element.bar)
            this.elementType = "rect";
        else
            this.elementType = "rect".equals(symbol) ? "rect" : "circle";

        String textLocation = ModelUtil.getLabelPosition(vis);
        if (textLocation != null) {
            this.textMethod = textLocation;
        } else if (producesPath) {
            if (element == VisTypes.Element.bar) this.textMethod = "wedge";
            else if (element == VisTypes.Element.area) this.textMethod = "area";
            else if (filled) this.textMethod = "poly";
            else this.textMethod = "path";
        } else if (elementType.equals("circle")) {
            this.textMethod = "right";
        } else {
            this.textMethod = "box";
        }

        this.textMustFit = filled && element != VisTypes.Element.point || "box".equals(textLocation);

        // Only edges need the stroke width setting
        this.needsStrokeSize = !vis.fSize.isEmpty() && vis.tElement == VisTypes.Element.edge;
    }

    private ElementDetails(String dataSource, String elementType, String elementClass, String textMethod, boolean textFits) {
        this.splitIntoShapes = false;
        this.colorAttribute = elementType.equals("line") ? "stroke" : "fill";
        this.dataSource = dataSource;
        this.producesPath = false;
        this.elementType = elementType;
        this.classes = "'element " + elementClass + "'";
        this.textMethod = textMethod;
        this.textMustFit = textFits;
    }

    /* Modify the method to give better text location for tooltips */
    public ElementDetails modifyForTooltip(boolean transposed) {
        String method = textMethod.equals("box") ? "top" : textMethod;
        if (method.equals("left") || method.equals("right")) method = "top";
        if (transposed) {
            if (method.equals("top")) method = "left";
            if (method.equals("bottom")) method = "right";
        }
        return makeForDiagram(dataSource, elementType, "point", method, false);
    }

    /**
     * Define details about the element.
     * The elementType defines the type of graphical object that will be created
     * The textMethod defines how we place text around or inside the shape:
     * The methods 'wedge', 'poly', 'area', 'path', 'box' wrap the text somewhere useful in the center of the shape,
     * and will also remove the label if it does not fit.
     * a box around the shape (and so will work bets for convex shapes like rectangles and circles)
     *
     * @param dataSource   the javascript name of the element's data
     * @param elementType  path, line, text, rect or circle
     * @param elementClass the name of the element class for CSS purposes (polygon, path, point, etc.)
     * @param textMethod   wedge, poly, area, path, box, left, right, top, bottom
     * @param textFits     true if text must fit within the shape
     */
    public static ElementDetails makeForDiagram(String dataSource, String elementType, String elementClass, String textMethod, boolean textFits) {
        return new ElementDetails(dataSource, elementType, elementClass, textMethod, textFits);
    }
}
