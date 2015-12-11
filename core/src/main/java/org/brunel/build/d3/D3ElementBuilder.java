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

package org.brunel.build.d3;

import org.brunel.build.ElementDependency;
import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

class D3ElementBuilder {

    private final ScriptWriter out;                             // To write code out to
    private final VisSingle vis;                                // Element definition
    private final Dataset data;                                 // Data this element uses
    private final ElementDependency dependency;                    // Our keys link to keys in this element

    private final PositionFields positionFields;                // Defines how our positions work
    private final D3ScaleBuilder scales;                        // Helper to build scales
    private final D3LabelBuilder labelBuilder;                  // Helper to build labels
    private final D3Diagram diagram;                            // Helper to build diagrams

    public D3ElementBuilder(VisSingle vis, ScriptWriter out, D3ScaleBuilder scales,
                            PositionFields positionFields, Dataset data, ElementDependency dependency) {
        this.vis = vis;
        this.out = out;
        this.scales = scales;
        this.positionFields = positionFields;
        this.data = data;
        this.dependency = dependency;
        this.labelBuilder = new D3LabelBuilder(vis, out, data);
        this.diagram = D3Diagram.make(vis, data, out, dependency, scales);
    }

    public void generate(int elementIndex) {
        out.add("element = elements[" + elementIndex + "]").endStatement();

        if (diagram != null) out.onNewLine().comment("Data structures for a", vis.tDiagram, "diagram");

        ElementDetails details = makeDetails();                     // Create the details of what the element should be
        ElementDefinition elementDef = buildElementDefinition();    // And the coordinate definitions

        // Define paths needed in the element, and make data splits
        if (details.producesPath) definePathsAndSplits(elementDef);

        if (labelBuilder.needed()) labelBuilder.defineLabeling(details, vis.itemsLabel, false);   // Labels

        modifyGroupStyleName();             // Diagrams change the name so CSS style sheets will work well

        // Set the values of things known to this element
        out.add("d3Data =", details.dataSource).endStatement();
        out.add("selection = main.selectAll('*').data(d3Data,", getKeyFunction(), ")").endStatement();

        // Define what happens when data is added ('enter')
        out.add("selection.enter().append('" + details.elementType + "')");
        out.add(".attr('class', ", details.classes, ")");

        if (diagram != null) diagram.writeDiagramEnter();
        else writeCoordEnter();

        // When data changes (including being added) update the items
        // These fire for both 'enter' and 'update' data

        if (diagram != null) {
            diagram.writePreDefinition(details, elementDef);
            out.add("BrunelD3.trans(selection,transitionMillis)");
            diagram.writeDefinition(details, elementDef);
        } else {
            writeCoordinateDefinition(details, elementDef);
            writeCoordinateLabelingAndAesthetics(details);
        }

        // This fires when items leave the system
        out.onNewLine().ln().add("BrunelD3.trans(selection.exit(),transitionMillis/3)");
        out.addChained("style('opacity', 0.5).remove()").endStatement();
    }

    public void preBuildDefinitions() {
        if (diagram != null) diagram.preBuildDefinitions();
    }

    private String getSize(String aestheticFunctionCall, ModelUtil.Size size, Field[] fields, String extent, String scaleName) {

        boolean needsFunction = aestheticFunctionCall != null;
        String baseAmount;
        if (size != null && !size.isPercent()) {
            // Absolute size overrides everything
            baseAmount = "" + size.value();
        } else if (fields.length == 0) {
            if (vis.tDiagram != null) {
                // Default point size for diagrams
                baseAmount = "geom.default_point_size";
            } else {
                // If there are no fields, then fill the extent completely
                baseAmount = extent;
            }
        } else {
            // Use size of categories
            int categories = scales.getCategories(fields).size();
            Double granularity = scales.getGranularitySuitableForSizing(fields);
            if (vis.tDiagram != null) {
                // Diagrams do not define these things
                granularity = null;
                categories = 0;
            }
            if (categories > 0) {
                baseAmount = (categories == 1) ? extent : extent + "/" + categories;
                // Fill a category span (or 90% of it for categorical fields when percent not defined)
                if ((size == null || !size.isPercent()) && !scales.allNumeric(fields))
                    baseAmount = "0.9 * " + baseAmount;
            } else if (granularity != null) {
                baseAmount = "Math.abs( " + scaleName + "(" + granularity + ") - " + scaleName + "(0) )";
            } else {
                baseAmount = "geom.default_point_size";
            }
        }

        // If the size definition is a percent, use that to scale by
        if (size != null && size.isPercent())
            baseAmount = size.value() + " * " + baseAmount;

        // If we need a function, wrap it up as required
        if (needsFunction) {
            return "function(d) { return " + aestheticFunctionCall + " * " + baseAmount + "}";
        } else {
            return baseAmount;
        }

    }

    public static String getOverallSize(VisSingle vis, ElementDefinition def) {
        ModelUtil.Size size = ModelUtil.getElementSize(vis, "size");
        boolean needsFunction = vis.fSize.size() == 1;

        if (size != null && !size.isPercent()) {
            // Just multiply by the aesthetic if needed
            if (needsFunction)
                return "function(d) { return size(d) * " + size.value() + " }";
            else
                return "" + size.value();
        }

        // Use the X and Y extents to define the overall one

        String x = def.x.size;
        String y = def.y.size;
        if (x.equals(y)) return x;          // If they are both the same, use that

        String xBody = stripFunction(x);
        String yBody = stripFunction(y);

        // This will already have the size function factored in if defined
        String content = "Math.min(" + xBody + ", " + yBody + ")";

        // if the body is different from the whole item for x or y, then we have a function and must return a function
        if (!xBody.equals(x) || !yBody.equals(y)) {
            return "function(d) { return " + content + " }";
        } else {
            return content;
        }
    }

    private static String stripFunction(String item) {
        // remove function wrapper if present
        int p = item.indexOf("return");
        int q = item.lastIndexOf("}");
        if (p > 0 && q > 0)
            return item.substring(p + 7, q).trim();
        else
            return item;
    }

    private ElementDefinition buildElementDefinition() {
        ElementDefinition e = new ElementDefinition();
        Field[] x = positionFields.getX(vis);
        Field[] y = positionFields.getY(vis);
        Field[] keys = new Field[vis.fKeys.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = data.field(vis.fKeys.get(i).asField());
        ModelUtil.Size sizeWidth = ModelUtil.getElementSize(vis, "width");
        ModelUtil.Size sizeHeight = ModelUtil.getElementSize(vis, "height");
        if (scales.isGeo()) {
            // Maps with feature data do not need the geo coordinates set
            if (vis.tDiagram != VisTypes.Diagram.map)
                setGeoLocations(e, x, y, keys);
            // Just use the default point size
            e.x.size = getSize(getSizeCall(0), sizeWidth, new Field[0], "geom.default_point_size", null);
            e.y.size = getSize(getSizeCall(1), sizeHeight, new Field[0], "geom.default_point_size", null);
        } else {
            setLocations(e.x, "x", x, keys, positionFields.xCategorical);
            setLocations(e.y, "y", y, keys, positionFields.yCategorical);
            e.x.size = getSize(getSizeCall(0), sizeWidth, x, "geom.inner_width", "scale_x");
            e.y.size = getSize(getSizeCall(1), sizeHeight, y, "geom.inner_height", "scale_y");
        }
        e.overallSize = getOverallSize(vis, e);
        return e;
    }

    private void setGeoLocations(ElementDefinition def, Field[] x, Field[] y, Field[] keys) {

        int n = x.length;
        if (y.length != n)
            throw new IllegalStateException("X and Y dimensions do not match in geographic maps");
        if (dependency.isEdge(vis)) {
            throw new IllegalStateException("Cannot handle edged dependencies in geographic maps");
        } else if (dependency.isDependent(vis)) {
            throw new IllegalStateException("Cannot handle positional dependencies in geographic maps");
        }

        if (n == 0) {
            def.x.center = "null";
            def.y.center = "null";
        } else if (n == 1) {
            String xFunction = D3Util.writeCall(x[0]);
            String yFunction = D3Util.writeCall(y[0]);
            def.x.center = "function(d) { return projection([" + xFunction + "," + yFunction + "])[0] }";
            def.y.center = "function(d) { return projection([" + xFunction + "," + yFunction + "])[1] }";
        } else if (n == 2) {
            String xLow = D3Util.writeCall(x[0]);          // A call to the low field using the datum 'd'
            String xHigh = D3Util.writeCall(x[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(x[0])) xLow += ".low";
            if (isRange(x[1])) xHigh += ".high";

            String yLow = D3Util.writeCall(y[0]);          // A call to the low field using the datum 'd'
            String yHigh = D3Util.writeCall(y[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(y[0])) yLow += ".low";
            if (isRange(y[1])) yHigh += ".high";

            def.x.left = "function(d) { return projection([" + xLow + "," + yLow + "])[0] }";
            def.x.right = "function(d) { return projection([" + xHigh + "," + yHigh + "])[0] }";
            def.y.left = "function(d) { return projection([" + xLow + "," + yLow + "])[1] }";
            def.y.right = "function(d) { return projection([" + xHigh + "," + yHigh + "])[1] }";
        }

    }

    private String getSizeCall(int dim) {
        if (vis.fSize.isEmpty()) return null;                   // No sizing
        if (vis.fSize.size() == 1) return "size(d)";            // Use this for both
        return dim == 0 ? "width(d)" : "height(d)";            // Different for x and y dimensions
    }

    private void setLocations(ElementDefinition.ElementDimensionDefinition dim, String dimName, Field[] fields, Field[] keys, boolean categorical) {

        String scaleName = "scale_" + dimName;

        if (dependency.isEdge(vis)) {
            // These are edges in a network layout
            dim.left = "function(d) { return scale_" + dimName + "(d.source." + dimName + ") }";
            dim.right = "function(d) { return scale_" + dimName + "(d.target." + dimName + ") }";
        } else if (dependency.isDependent(vis)) {
            // Use the keys to get the X and Y locations from other items
            if (keys.length == 1) {
                // One key gives the center
                dim.center = "function(d) { return " + scaleName + "(" + dependency.keyedLocation(dimName, keys[0]) + ") }";
            } else {
                // Two keys give ends
                dim.left = "function(d) { return " + scaleName + "(" + dependency.keyedLocation(dimName, keys[0]) + ") }";
                dim.right = "function(d) { return " + scaleName + "(" + dependency.keyedLocation(dimName, keys[1]) + ") }";
                dim.center = "function() { return " + scaleName + "(0.5) }";        // Not sure what is best here -- should not get used
            }
        } else if (fields.length == 0) {
            // There are no fields -- we have a notional [0,1] extent, so use the center of that
            dim.center = "function() { return " + scaleName + "(0.5) }";
            dim.left = "function() { return " + scaleName + "(0) }";
            dim.right = "function() { return " + scaleName + "(1) }";
        } else if (fields.length == 1) {
            Field field = fields[0];                                // The single field
            String dataFunction = D3Util.writeCall(field);          // A call to that field using the datum 'd'

            if (isRange(field)) {
                // This is a range field, but we have not been asked to show both ends,
                // so we use the difference between the top and bottom
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ".extent()) }";
                // Left and Right are not defined
            } else if (field.isBinned() && !categorical) {
                // A Binned value on a non-categorical axes
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ".mid) }";
                dim.left = "function(d) { return " + scaleName + "(" + dataFunction + ".low) }";
                dim.right = "function(d) { return " + scaleName + "(" + dataFunction + ".high) }";

            } else {
                // Nothing unusual -- just define the center
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ") }";
            }
        } else {
            // The dimension contains two fields: a range
            String lowDataFunc = D3Util.writeCall(fields[0]);          // A call to the low field using the datum 'd'
            String highDataFunc = D3Util.writeCall(fields[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(fields[0])) lowDataFunc += ".low";
            if (isRange(fields[1])) highDataFunc += ".high";

            dim.left = "function(d) { return " + scaleName + "(" + lowDataFunc + ") }";
            dim.right = "function(d) { return " + scaleName + "(" + highDataFunc + ") }";
            dim.center = "function(d) { return " + scaleName + "( (" + highDataFunc + " + " + lowDataFunc + " )/2) }";
        }

    }

    private void modifyGroupStyleName() {
        // Define the main element class
        if (diagram != null)
            out.add("main.attr('class',", diagram.getStyleClasses(), ")").endStatement();
    }

    private void writeCoordEnter() {
        // Added rounded styling if needed
        ModelUtil.Size size = ModelUtil.getRoundRectangleRadius(vis);
        if (size != null)
            out.addChained("attr('rx'," + size.valueInPixels(8) + ").attr('ry', " + size.valueInPixels(8) + ")").ln();
        out.endStatement().onNewLine().ln();
    }

    private void constructSplitPath() {
        // We add the x function to signal we need the paths sorted
        String params = "data, path";
        if (vis.tElement == VisTypes.Element.line || vis.tElement == VisTypes.Element.area)
            params += ", x";
        out.add("var splits = BrunelD3.makePathSplits(" + params + ");").ln();
    }

    private void defineEdge(String basicDef, ElementDefinition elementDef) {
        out.add(basicDef);
        out.addChained("attr('x1'," + elementDef.x.left + ")");
        out.addChained("attr('y1'," + elementDef.y.left + ")");
        out.addChained("attr('x2'," + elementDef.x.right + ")");
        out.addChained("attr('y2'," + elementDef.y.right + ")");
    }

    private void defineCircle(String basicDef, ElementDefinition elementDef) {
        out.add(basicDef);
        out.addChained("attr('cx'," + elementDef.x.center + ")");
        out.addChained("attr('cy'," + elementDef.y.center + ")");
        out.addChained("attr('r'," + halve(elementDef.overallSize) + ")");
    }

    private String halve(String sizeText) {
        // Put the "/2" factor inside the function if needed
        String body = stripFunction(sizeText);
        if (body.equals(sizeText))
            return body + " / 2";
        else
            return "function(d) { return " + body + " / 2 }";
    }

    private void definePathsAndSplits(ElementDefinition elementDef) {

        // Define y or (y0, y1)
        defineVerticalExtentFunctions(elementDef, false);

        // First deal with the case of wedges (polar intervals)
        if (vis.tElement == VisTypes.Element.bar && vis.coords == VisTypes.Coordinates.polar) {
            out.add("var path = d3.svg.arc().outerRadius(geom.inner_radius).innerRadius(0)").ln();
            out.addChained("outerRadius(geom.inner_radius).innerRadius(0)").ln();
            if (vis.fRange == null && !vis.stacked)
                out.addChained("startAngle(0).endAngle(y)");
            else
                out.addChained("startAngle(y0).endAngle(y1)");
            out.endStatement();
            return;
        }

        // Now actual paths

        // Define the x function
        out.add("var x =", elementDef.x.center).endStatement();

        if (vis.tElement == VisTypes.Element.area) {
            if (vis.fRange == null && !vis.stacked)
                out.add("var path = d3.svg.area().x(x).y1(y).y0(function(d) { return scale_y(0) })");
            else
                out.add("var path = d3.svg.area().x(x).y1(y1).y0(y0)");
        } else if (vis.tElement.producesSingleShape) {
            // Choose the top line if there is a range (say for stacking)
            String yDef = elementDef.y.right == null ? "y" : "y1";
            if (vis.fSize.size() == 1) {
                out.add("var path = BrunelD3.sizedPath().x(x).y(" + yDef + ")");
                String size = elementDef.y.size != null ? elementDef.y.size : elementDef.overallSize;
                out.addChained("r(" + size + ")");
            } else {
                out.add("var path = d3.svg.line().x(x).y(" + yDef + ")");
            }
        }
        if (vis.tUsing == VisTypes.Using.interpolate) {
            out.add(".interpolate('basis')");
        }
        out.endStatement();
        constructSplitPath();
    }

    private void defineRect(String basicDef, ElementDefinition elementDef) {
        defineVerticalExtentFunctions(elementDef, true);
        defineHorizontalExtentFunctions(elementDef, true);
        out.add(basicDef);
        defineHorizontalExtent(elementDef);
        defineVerticalExtent(elementDef);
    }

    private void defineBar(String basicDef, ElementDefinition elementDef) {
        if (vis.fRange != null || vis.stacked) {
            // Stacked or range element goes from higher of the pair of values to the lower
            out.add("var y0 =", elementDef.y.left).endStatement();
            out.add("var y1 =", elementDef.y.right).endStatement();
            defineHorizontalExtentFunctions(elementDef, true);
            out.add(basicDef);
            out.addChained("attr('y', function(d) { return Math.min(y0(d), y1(d)) } )");
            out.addChained("attr('height', function(d) {return Math.abs(y0(d) - y1(d)) })");
        } else {
            // Simple element; drop from the upper value to the baseline
            out.add("var y =", elementDef.y.center).endStatement();
            defineHorizontalExtentFunctions(elementDef, true);
            out.add(basicDef);
            if (vis.coords == VisTypes.Coordinates.transposed) {
                out.addChained("attr('y', 0)")
                        .addChained("attr('height', function(d) { return Math.max(0,y(d)) })");
            } else {
                out.addChained("attr('y', y)")
                        .addChained("attr('height', function(d) {return Math.max(0,geom.inner_height - y(d)) }) ");
            }
        }
        defineHorizontalExtent(elementDef);
    }

    private void defineHorizontalExtentFunctions(ElementDefinition elementDef, boolean withWidth) {
        if (elementDef.x.left != null) {
            // Use the left and right values
            out.add("var x0 =", elementDef.x.left).endStatement();
            out.add("var x1 =", elementDef.x.right).endStatement();
        } else {
            out.add("var x =", elementDef.x.center).endStatement();
            if (withWidth) out.add("var w =", elementDef.x.size).endStatement();
        }
    }

    private void defineVerticalExtentFunctions(ElementDefinition elementDef, boolean withHeight) {
        if (elementDef.y.left != null) {
            // Use the left and right values
            out.add("var y0 =", elementDef.y.left).endStatement();
            out.add("var y1 =", elementDef.y.right).endStatement();
        } else {
            out.add("var y =", elementDef.y.center).endStatement();
            if (withHeight) out.add("var h =", elementDef.y.size).endStatement();
        }
    }

    private void defineHorizontalExtent(ElementDefinition elementDef) {
        String left, width;
        if (elementDef.x.left != null) {
            // Use the left and right values
            left = "function(d) { return Math.min(x0(d), x1(d)) }";
            width = "function(d) { return Math.abs(x1(d) - x0(d)) }";
        } else {
            // The width can either be a function or a numeric value
            if (elementDef.x.size.startsWith("function"))
                left = "function(d) { return x(d) - w(d)/2 }";
            else
                left = "function(d) { return x(d) - w/2 }";
            width = "w";
        }
        out.addChained("attr('x', ", left, ")");

        // Sadly, browsers are inconsistent in how they handle width. It can be considered either a style or a
        // positional attribute, so we need to specify as both to make all browsers happy
        out.addChained("attr('width', ", width, ")");
        out.addChained("style('width', ", width, ")");
    }

    private void defineVerticalExtent(ElementDefinition elementDef) {
        String top, height;
        if (elementDef.y.left != null) {
            // Use the left and right values
            top = "function(d) { return Math.min(y0(d), y1(d)) }";
            height = "function(d) { return Math.abs(y1(d) - y0(d)) }";
        } else {
            // The height can either be a function or a numeric value
            if (elementDef.y.size.startsWith("function"))
                top = "function(d) { return y(d) - h(d)/2 }";
            else
                top = "function(d) { return y(d) - h/2 }";
            height = "h";
        }
        out.addChained("attr('y', ", top, ")");
        out.addChained("attr('height', ", height, ")");
    }

    private void defineText(String basicDef, ElementDefinition elementDef) {
        out.add(basicDef);
        out.addChained("attr('x'," + elementDef.x.center + ")");
        out.addChained("attr('y'," + elementDef.y.center + ")");
        out.addChained("attr('dy', '0.35em').text(labeling.content)");
        labelBuilder.addFontSizeAttribute(vis);
    }

    /* The key function ensure we have object constancy when animating */
    private String getKeyFunction() {
        String content = diagram != null ? diagram.getRowKey() : "d.key";
        return "function(d) { return " + content + "}";
    }

    private String getSymbol() {
        String result = ModelUtil.getElementSymbol(vis);
        if (result != null) return result;
        // We default to a rectangle if all the scales are categorical or binned, otherwise we return a point
        boolean cat = allShowExtent(positionFields.allXFields) && allShowExtent(positionFields.allYFields);
        return cat ? "rect" : "point";
    }

    private boolean allShowExtent(Field[] fields) {
        // Categorical and numeric fields both show elements as extents on the axis
        for (Field field : fields) {
            if (field.isNumeric() && !field.isBinned()) return false;
        }
        return true;
    }

    private boolean isRange(Field field) {
        String s = field.stringProperty("summary");
        return s != null && (s.equals("iqr") || s.equals("range"));
    }

    private ElementDetails makeDetails() {
        // When we create diagrams this has the side effect of writing the data calls needed
        if (dependency.isEdge(vis)) {
            return ElementDetails.makeForDiagram("chart.graph.links", "line", "edge", "box", false);
        } else if (diagram == null)
            return ElementDetails.makeForCoordinates(vis, getSymbol());
        else
            return diagram.writeDataConstruction();
    }

    private void writeCoordinateDefinition(ElementDetails details, ElementDefinition elementDef) {

        // This starts the transition or update going
        String basicDef = "BrunelD3.trans(selection,transitionMillis)";

        if (details.splitIntoShapes)
            out.add(basicDef).addChained("attr('d', function(d) { return d.path })");     // Split path -- get it from the split
        else if (details.producesPath)
            out.add(basicDef).addChained("attr('d', path)");                              // Simple path -- just util it
        else {
            if (vis.tElement == VisTypes.Element.text)
                defineText(basicDef, elementDef);
            else if (vis.tElement == VisTypes.Element.bar)
                defineBar(basicDef, elementDef);
            else if (vis.tElement == VisTypes.Element.edge)
                defineEdge(basicDef, elementDef);
            else {
                // Must be a point
                if (details.elementType.equals("rect"))
                    defineRect(basicDef, elementDef);
                else
                    defineCircle(basicDef, elementDef);
            }
        }
    }

    private void writeCoordinateLabelingAndAesthetics(ElementDetails details) {
        // Define colors using the color function
        if (!vis.fColor.isEmpty()) out.addChained("style('" + details.colorAttribute + "', color)");

        // Define line width if needed
        if (details.needsStrokeSize)
            out.addChained("style('stroke-width', size)");

        // Define opacity
        if (!vis.fOpacity.isEmpty()) {
            out.addChained("style('fill-opacity', opacity)").addChained("style('stroke-opacity', opacity)");
        }

        out.endStatement();

        labelBuilder.addTooltips(details);

        // We do not add labels if the element IS a label
        if (labelBuilder.needed() && vis.tElement != VisTypes.Element.text) {
            labelBuilder.addLabels(details);
        }
    }

}
