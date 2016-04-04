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

import static org.brunel.build.element.ElementRepresentation.makeForCoordinateElement;
import static org.brunel.build.element.ElementRepresentation.rect;
import static org.brunel.build.element.ElementRepresentation.segment;

/**
 * Encapsulate information on how we want to represent different types of element
 */
public class ElementDetails {

    public static ElementDetails makeForCoordinates(VisSingle vis, String symbol) {
        Element element = vis.tElement;
        String dataSource = element.producesSingleShape ? "splits" : "data._rows";
        ElementRepresentation representation = makeForCoordinateElement(element, symbol, vis);

        // Work out if the element is filled
        boolean filled = element.filled || (!vis.fSize.isEmpty() && (element == Element.line || element == Element.path));

        return new ElementDetails(representation, element.name(), dataSource,
                !filled, ModelUtil.getLabelPosition(vis));
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
        return new ElementDetails(representation, elementClass, dataSource, representation != segment,
                ModelUtil.getLabelPosition(vis)
        );
    }

    public final String dataSource;                     // Where the data for d3 lives
    public final ElementRepresentation representation;  // The type of element produced
    public final String classes;                        // Class names for this item
    private final String userDefinedLabelPosition;      // Custom override for the label position
    private final boolean strokedShape;                 // If true, the shape is to be stroked, not filled

    public ElementDetails(ElementRepresentation representation, String classes, String dataSource, boolean stroked,
                          String userDefinedLabelPosition) {
        if (!stroked) classes += " filled";
        this.strokedShape = stroked;
        this.dataSource = dataSource;
        this.representation = representation;
        this.classes = "'element " + classes + "'";
        this.userDefinedLabelPosition = userDefinedLabelPosition;
    }

    public String getTextMethod() {
        return userDefinedLabelPosition != null ? userDefinedLabelPosition : representation.getDefaultTextMethod();
    }

    public boolean requiresSplitting() {
        return dataSource.equals("splits");
    }

    public boolean isStroked() {
        return strokedShape;
    }

    /* Modify the method to give better text location for tooltips */
    public ElementDetails deriveTooltipDetails() {
        String method = getTextMethod().equals("box") ? "top" : getTextMethod();
        if (method.equals("left") || method.equals("right") || method.equals("bottom")) method = "top";
        return new ElementDetails(rect, "point", dataSource, false, method);
    }

    public boolean textFitsShape() {
        return "inside".equals(userDefinedLabelPosition) || representation.textFitsShape();
    }
}
