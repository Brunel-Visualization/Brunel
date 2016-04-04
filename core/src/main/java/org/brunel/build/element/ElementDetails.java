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
import org.brunel.model.VisTypes.Element;

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
    public final ElementRepresentation representation;  // The type of element produced
    public final String classes;                        // Class names for this item
    public boolean needsStrokeSize;                     // If we must define stroke-size using the "size" aesthetic
    private String userDefinedLabelPosition;

    private ElementDetails(VisSingle vis, String symbol) {
        Element element = vis.tElement;
        String classList = "element " + element.name();

        // Work out if the element is filled
        boolean filled = element.filled;
        if (!vis.fSize.isEmpty() && (element == Element.line || element == Element.path)) {
            filled = true;
            classList += " filled";
        }

        this.classes = "'" + classList + "'";
        this.splitIntoShapes = element.producesSingleShape;
        this.colorAttribute = filled ? "fill" : "stroke";
        this.dataSource = element.producesSingleShape ? "splits" : "data._rows";
        this.representation = ElementRepresentation.makeForCoordinateElement(element, symbol, vis);

        userDefinedLabelPosition = ModelUtil.getLabelPosition(vis);

        // Only edges need the stroke width setting
        this.needsStrokeSize = !vis.fSize.isEmpty() && vis.tElement == Element.edge;
    }

    private ElementDetails(VisSingle vis, String dataSource, ElementRepresentation elementType, String elementClass) {
        this.splitIntoShapes = false;
        this.colorAttribute = elementType == ElementRepresentation.segment ? "stroke" : "fill";
        this.dataSource = dataSource;
        this.representation = elementType;
        this.classes = "'element " + elementClass + "'";
        this.userDefinedLabelPosition =   ModelUtil.getLabelPosition(vis);
    }

    public String getTextMethod() {
        return userDefinedLabelPosition != null ? userDefinedLabelPosition : representation.getDefaultTextMethod();
    }

    public boolean textFitsShape() {
        return "inside".equals(userDefinedLabelPosition) || representation.textFitsShape();
    }

    /* Modify the method to give better text location for tooltips */
    public ElementDetails modifyForTooltip() {
        ElementDetails details = makeForDiagram(null, ElementRepresentation.rect, dataSource, "point");
        String method = getTextMethod().equals("box") ? "top" : getTextMethod();
        if (method.equals("left") || method.equals("right") || method.equals("bottom")) method = "top";
        details.userDefinedLabelPosition = method;
        return details;
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
     * @param elementClass the name of the element class for CSS purposes (polygon, path, point, etc.)
     */
    public static ElementDetails makeForDiagram(VisSingle vis, ElementRepresentation representation, String dataSource, String elementClass) {
        return new ElementDetails(vis, dataSource, representation, elementClass);
    }
}
