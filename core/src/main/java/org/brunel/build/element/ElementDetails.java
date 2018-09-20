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

package org.brunel.build.element;

import org.brunel.build.info.ChartCoordinates;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.data.Field;
import org.brunel.model.VisElement;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Element;


import static org.brunel.build.element.ElementRepresentation.largeCircle;
import static org.brunel.build.element.ElementRepresentation.pointLikeCircle;
import static org.brunel.build.element.ElementRepresentation.spaceFillingCircle;

/**
 * Encapsulate information on how we want to represent different types of element
 */
public class ElementDetails {

  public static ElementDetails makeForCoordinates(ElementStructure structure) {
    ElementRepresentation representation = ElementRepresentation.makeForCoordinateElement(structure, getCommonSymbol(structure));

    Element element = structure.vis.tElement;
    String dataSource = element.producesSingleShape ? "splits" : "data._rows";
    boolean filled = element.filled || (!structure.vis.fSize.isEmpty() && (element == Element.line || element == Element.path));
    return new ElementDetails(structure.vis, representation, element.name(), dataSource, filled);
  }

  /**
   * Define details about the element.
   * The elementType defines the type of graphical object that will be created
   * The textMethod defines how we place text around or inside the shape:
   * The methods 'wedge', 'poly', 'area', 'path', 'box' wrap the text somewhere useful in the center of the shape,
   * and will also remove the label if it does not fit.
   * a box around the shape (and so will work bets for convex shapes like rectangles and circles)
   *
   * @param structure      the target element
   * @param representation the mark or appearance fo the element
   * @param elementClass   the name of the element class for CSS purposes (polygon, path, point, etc.)
   * @param dataSource     the javascript name of the element's data
   * @return Definition of the element
   */
  public static ElementDetails makeForDiagram(ElementStructure structure, ElementRepresentation representation,
                                              String elementClass, String dataSource) {

    // we override the suggested representation if we have a symbol defined
    if (representation == spaceFillingCircle || representation == largeCircle || representation == pointLikeCircle) {
      if (!structure.vis.fSymbol.isEmpty() || structure.styleSymbol != null) {
        representation = "rect".equals(structure.styleSymbol)
          ? ElementRepresentation.rect
          : ElementRepresentation.symbol;
      }
    }

    // Only these items are unfilled
    boolean filled = representation != ElementRepresentation.segment
      && representation != ElementRepresentation.curvedPath
      && representation != ElementRepresentation.generalPath;

    // Exception! Points in parallel coordinates are drawn as circles, and so although as paths, do need filling
    if (structure.vis.tElement == Element.point && structure.chart.diagram == VisTypes.Diagram.parallel) {
      filled = true;
    }

    return new ElementDetails(structure.vis, representation, elementClass, dataSource, filled);
  }

  private static boolean anyNumeric(Field[] fields) {
    for (Field field : fields) {
      if (!field.isBinned() && field.isNumeric()) {
        return true;
      }
    }
    return false;
  }

  private static String getCommonSymbol(ElementStructure structure) {
    // If we have a defined symbol, we use that
    if (structure.styleSymbol != null) {
      return structure.styleSymbol;
    }

    // Geo ignores symbols
    if (structure.geo != null) {
      return null;
    }

    // Some point elements are shown as rectangles
    if (structure.vis.tElement == VisTypes.Element.point && showAsRectangle(structure)) {
      return "rect";
    }

    // No knowledge
    return null;
  }

  private static boolean showAsRectangle(ElementStructure element) {
    ChartStructure chart = element.chart;
    if (chart == null) {
      return false;                            // Happens in test code only
    }

    // Any numeric means we are not categorical
    ChartCoordinates coordinates = chart.getCoordinates(element.index);
    return !anyNumeric(coordinates.allXFields) && !anyNumeric(coordinates.allYFields);

  }

  public final String dataSource;                     // Where the data for d3 lives
  public final ElementRepresentation representation;  // The type of element produced
  public final String[] classes;                      // Class names for this item
  public final ElementDimension x, y;                 // Definitions for x and y fields
  private final String userDefinedLabelPosition;      // Custom override for the label position
  private final boolean strokedShape;                 // If true, the shape is to be stroked, not filled
  private final boolean allowTextOverlap;             // If true, allow text labels to overlap
  private final double labelPadding;                  // How much to pad labels by
  private final String labelAlignment;                // User defined label alignment (may be null)
  public GeomAttribute overallSize;                   // A general size for the whole item

  public ElementDetails(VisElement vis, ElementRepresentation representation, String className, String dataSource, boolean filled) {
    if (className == null || className.contains(" ")) {
      throw new IllegalArgumentException("Class name must be a single word");
    }
    classes = filled ? new String[]{"element", className, "filled"} : new String[]{"element", className};
    x = new ElementDimension(vis, "width", representation, classes);
    y = new ElementDimension(vis, "height", representation, classes);
    this.strokedShape = !filled;
    this.dataSource = dataSource;
    this.representation = representation;
    this.userDefinedLabelPosition = ModelUtil.getLabelPosition(vis);
    this.labelPadding = ModelUtil.getLabelPadding(vis, 3);
    this.labelAlignment = ModelUtil.getLabelAlignment(vis);
    this.allowTextOverlap = vis.tDiagram == VisTypes.Diagram.network;                       // Diagrams can overlap text
  }

  public String getAlignment() {
    return labelAlignment;
  }

  public double getPadding() {
    return labelPadding;
  }

  public String getTextMethod() {
    return userDefinedLabelPosition != null ? userDefinedLabelPosition : representation.getDefaultTextMethod();
  }

  public boolean isPath() {
    return representation.isDrawnAsPath();
  }

  public boolean isStroked() {
    return strokedShape;
  }

  public boolean requiresSplitting() {
    return dataSource.equals("splits");
  }

  public boolean textCanOverlap() {
    return allowTextOverlap;
  }

  public boolean textFitsShape() {
    return "inside".equals(userDefinedLabelPosition) || representation.textFitsShape();
  }
}
