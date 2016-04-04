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

import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.ScalePurpose;
import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.d3.element.ElementDefinition.ElementDimensionDefinition;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ModelUtil.Size;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Coordinates;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;
import org.brunel.model.VisTypes.Using;

public class D3ElementBuilder {

    private static final String BAR_SPACING = "0.9";            // Spacing between categorical bars

    private final ScriptWriter out;                             // To write code out to
    private final VisSingle vis;                                // Element definition

    private final D3ScaleBuilder scales;                        // Helper to build scales
    private final D3LabelBuilder labelBuilder;                  // Helper to build labels
    private final D3Diagram diagram;                            // Helper to build diagrams
    private final ElementStructure structure;

    public D3ElementBuilder(ElementStructure structure, ScriptWriter out, D3ScaleBuilder scales) {
        this.structure = structure;
        this.vis = structure.vis;
        this.out = out;
        this.scales = scales;
        this.labelBuilder = new D3LabelBuilder(vis, out, structure.data);
        this.diagram = D3Diagram.make(structure, out);
    }

    public void generate(int elementIndex) {
        out.add("element = elements[" + elementIndex + "]").endStatement();

        ElementDetails details = makeDetails();                     // Create the details of what the element should be
        ElementDefinition elementDef = buildElementDefinition(details.representation);    // And the coordinate definitions

        // Define paths needed in the element, and make data splits
        if (diagram == null && details.representation.isDrawnAsPath())
            definePathsAndSplits(elementDef);

        labelBuilder.defineLabeling(details, vis.itemsLabel, false);   // Labels

        modifyGroupStyleName();             // Diagrams change the name so CSS style sheets will work well

        // Set the values of things known to this element
        out.add("d3Data =", details.dataSource).endStatement();
        out.add("selection = main.selectAll('*').data(d3Data,", getKeyFunction(), ")").endStatement();

        // Define what happens when data is added ('enter')
        out.add("selection.enter().append('" + details.representation.getMark() + "')");
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
        // It removes the item and any associated labels
        out.onNewLine().ln().add("BrunelD3.trans(selection.exit(),transitionMillis/3)");
        out.addChained("style('opacity', 0.5).each( function() {")
                .indentMore().indentMore()
                .add("this.remove(); if (this.__label__) this.__label__.remove()")
                .indentLess().indentLess()
                .add("})").endStatement();
    }

    public boolean needsDiagramExtras() {
        return diagram != null && diagram.needsDiagramExtras();
    }

    public boolean needsDiagramLabels() {
        return diagram != null && diagram.needsDiagramLabels();
    }

    public void writeBuildCommands() {
        if (diagram != null) diagram.writeBuildCommands();
    }

    public void writePerChartDefinitions() {
        if (diagram != null) diagram.writePerChartDefinitions();
    }

    private ElementDetails makeDetails() {
        // When we create diagrams this has the side effect of writing the data calls needed
        if (structure.isGraphEdge()) {
            out.onNewLine().comment("Data structures for a", vis.tDiagram, "diagram");
            return ElementDetails.makeForDiagram(vis, ElementRepresentation.segment, "graph.links", "edge");
        } else if (diagram == null) {
            return ElementDetails.makeForCoordinates(vis, getSymbol());
        } else {
            out.onNewLine().comment("Data structures for a", vis.tDiagram, "diagram");
            return diagram.initializeDiagram();
        }
    }

    private ElementDefinition buildElementDefinition(ElementRepresentation representation) {
        ElementDefinition e = new ElementDefinition(vis);

        Field[] x = structure.chart.coordinates.getX(vis);
        Field[] y = structure.chart.coordinates.getY(vis);
        Field[] keys = new Field[vis.fKeys.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = structure.data.field(vis.fKeys.get(i).asField());

        if (structure.dependent)  defineReferenceFunctions(e, keys);

        if (structure.chart.geo != null) {
            // Maps with feature data do not need the geo coordinates set
            if (vis.tDiagram != Diagram.map)
                setGeoLocations(e, x, y, keys);
            // Just use the default point size
            e.x.size = getSize(new Field[0], "geom.default_point_size", null, e.x);
            e.y.size = getSize(new Field[0], "geom.default_point_size", null, e.x);
        } else {
            DefineLocations.setLocations(structure, e.x, "x", x, keys, structure.chart.coordinates.xCategorical);
            DefineLocations.setLocations(structure, e.y, "y", y, keys, structure.chart.coordinates.yCategorical);
            e.x.size = getSize(x, "geom.inner_width", ScalePurpose.x, e.x);
            e.y.size = getSize(y, "geom.inner_height", ScalePurpose.y, e.y);
            if (x.length > 1)
                e.clusterSize = getSize(x, "geom.inner_width", ScalePurpose.inner, e.x);
        }
        e.overallSize = getOverallSize(vis, e);
        return e;
    }

    private void defineReferenceFunctions(ElementDefinition e, Field[] keys) {
        // This element's locations depends on another element
        String[] references = structure.makeReferences(keys);
        if (structure.chart.geo != null) {
            // Wrap the locations in the projection
            for (int i = 0; i < references.length; i++)
                references[i] = "proj(" + references[i] + ")";
            e.setReferences(references);
        } else if (!structure.isGraphEdge()) {
            // Just as they are
            e.setReferences(references);
        }
    }

    private void definePathsAndSplits(ElementDefinition elementDef) {

        // Define y or (y0, y1)
        defineVerticalExtentFunctions(false, elementDef.y);

        // First deal with the case of wedges (polar intervals)
        if (vis.tElement == Element.bar && vis.coords == Coordinates.polar) {
            out.add("var path = d3.svg.arc().innerRadius(0)");
            if (vis.fSize.isEmpty())
                out.addChained("outerRadius(geom.inner_radius)");
            else
                out.addChained("outerRadius(function(d) {return size(d)*geom.inner_radius})");
            if (vis.fRange == null && !vis.stacked)
                out.addChained("startAngle(0).endAngle(y)");
            else
                out.addChained("startAngle(y0).endAngle(y1)");
            out.endStatement();
            return;
        }

        // Add definition for the internal width of a cluster category
        if (elementDef.clusterSize != null)
            out.add("var w1 =", elementDef.clusterSize).endStatement();

        // Define the x function
        out.add("var x =", elementDef.x.center).endStatement();

        // Now actual paths
        if (vis.tElement == Element.area) {
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
        if (vis.tUsing == Using.interpolate) {
            out.add(".interpolate('basis')");
        }
        out.endStatement();
        constructSplitPath();
    }

    private void modifyGroupStyleName() {
        // Define the main element class
        if (diagram != null)
            out.add("main.attr('class',", diagram.getStyleClasses(), ")").endStatement();
    }

    /* The key function ensure we have object constancy when animating */
    private String getKeyFunction() {
        String content = diagram != null ? diagram.getRowKey() : "d.key";
        return "function(d) { return " + content + "}";
    }

    private void writeCoordEnter() {
        // Added rounded styling if needed
        Size size = ModelUtil.getRoundRectangleRadius(vis);
        if (size != null)
            out.addChained("attr('rx'," + size.valueInPixels(8) + ").attr('ry', " + size.valueInPixels(8) + ")").ln();
        out.endStatement().onNewLine().ln();
    }

    private void writeCoordinateDefinition(ElementDetails details, ElementDefinition elementDef) {

        // This starts the transition or update going
        String basicDef = "BrunelD3.trans(selection,transitionMillis)";

        if (details.requiresSplitting())
            out.add(basicDef).addChained("attr('d', function(d) { return d.path })");     // Split path -- get it from the split
        else if (details.representation.isDrawnAsPath())
            out.add(basicDef).addChained("attr('d', path)");                              // Simple path -- just util it
        else {
            // Add definition for the internal width of a cluster category
            if (elementDef.clusterSize != null) {
                out.add("var w1 =", elementDef.clusterSize).endStatement();
            }

            if (vis.tElement == Element.bar)
                defineBar(basicDef, elementDef);
            else if (vis.tElement == Element.edge)
                defineEdge(basicDef, elementDef);
            else {
                // Handles points (as circles, rects, etc.) and text
                PointBuilder pointBuilder = new PointBuilder(out);
                if (details.representation == ElementRepresentation.rect) {
                    defineVerticalExtentFunctions(true, elementDef.y);
                    defineHorizontalExtentFunctions(elementDef.x);
                }

                out.add(basicDef);
                pointBuilder.defineShapeGeometry(vis, elementDef, details);
            }
        }
    }

    private void defineHorizontalExtentFunctions(ElementDimensionDefinition x) {
        // We only use the [left,right] version if we have no size to worry about
        if (x.defineUsingExtent()) {
            // Use the left and right values
            out.add("var x0 =", x.left).endStatement();
            out.add("var x1 =", x.right).endStatement();
        } else {
            out.add("var x =", x.center).endStatement();
            out.add("var w =", x.size).endStatement();
        }
    }

    private void defineVerticalExtentFunctions(boolean withHeight, ElementDimensionDefinition y) {
        // We only use the [left,right] version if we have no size to worry about
        if (y.defineUsingExtent()) {
            // Use the left and right values
            out.add("var y0 =", y.left).endStatement();
            out.add("var y1 =", y.right).endStatement();
        } else {
            out.add("var y =", y.center).endStatement();
            if (withHeight) out.add("var h =", y.size).endStatement();
        }
    }

    private void writeCoordinateLabelingAndAesthetics(ElementDetails details) {
        // Define colors using the color function
        if (!vis.fColor.isEmpty()) {
            String colorType = details.isStroked() ? "stroke" : "fill";
            out.addChained("style('" + colorType + "', color)");
        }

        // Define line width if needed
        if (details.isStroked() && !vis.fSize.isEmpty())
            out.addChained("style('stroke-width', size)");

        // Define opacity
        if (!vis.fOpacity.isEmpty()) {
            out.addChained("style('fill-opacity', opacity)").addChained("style('stroke-opacity', opacity)");
        }

        out.endStatement();

        labelBuilder.addElementLabeling();

        labelBuilder.addTooltips(details);

    }

    private String getSymbol() {
        String result = ModelUtil.getElementSymbol(vis);
        if (result != null) return result;
        // We default to a rectangle if all the scales are categorical or binned, otherwise we return a point
        boolean cat = allShowExtent(structure.chart.coordinates.allXFields) && allShowExtent(structure.chart.coordinates.allYFields);
        return cat ? "rect" : "point";
    }

    private void setGeoLocations(ElementDefinition def, Field[] x, Field[] y, Field[] keys) {

        int n = x.length;
        if (y.length != n)
            throw new IllegalStateException("X and Y dimensions do not match in geographic maps");
        if (structure.isGraphEdge()) {
            throw new IllegalStateException("Cannot handle edged dependencies in geographic maps");
        }

        if (structure.dependent) {
            DefineLocations.setDependentLocations(structure, def.x, "x", keys, "");
            DefineLocations.setDependentLocations(structure, def.y, "y", keys, "");
        } else if (n == 0) {
            def.x.center = "null";
            def.y.center = "null";
        } else if (n == 1) {
            String xFunction = D3Util.writeCall(x[0]);
            String yFunction = D3Util.writeCall(y[0]);
            def.x.center = "function(d) { return proj([" + xFunction + "," + yFunction + "])[0] }";
            def.y.center = "function(d) { return proj([" + xFunction + "," + yFunction + "])[1] }";
        } else if (n == 2) {
            String xLow = D3Util.writeCall(x[0]);          // A call to the low field using the datum 'd'
            String xHigh = D3Util.writeCall(x[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (DefineLocations.isRange(x[0])) xLow += ".low";
            if (DefineLocations.isRange(x[1])) xHigh += ".high";

            String yLow = D3Util.writeCall(y[0]);          // A call to the low field using the datum 'd'
            String yHigh = D3Util.writeCall(y[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (DefineLocations.isRange(y[0])) yLow += ".low";
            if (DefineLocations.isRange(y[1])) yHigh += ".high";

            def.x.left = "function(d) { return proj([" + xLow + "," + yLow + "])[0] }";
            def.x.right = "function(d) { return proj([" + xHigh + "," + yHigh + "])[0] }";
            def.y.left = "function(d) { return proj([" + xLow + "," + yLow + "])[1] }";
            def.y.right = "function(d) { return proj([" + xHigh + "," + yHigh + "])[1] }";
        }

    }

    private String getSize(Field[] fields, String extent, ScalePurpose purpose, ElementDimensionDefinition dim) {

        boolean needsFunction = dim.sizeFunction != null;
        String baseAmount;
        if (dim.sizeStyle != null && !dim.sizeStyle.isPercent()) {
            // Absolute size overrides everything
            baseAmount = "" + dim.sizeStyle.value();
        } else if (fields.length == 0) {
            if (vis.tDiagram != null) {
                // Default point size for diagrams
                baseAmount = "geom.default_point_size";
            } else {
                // If there are no fields, then fill the extent completely
                baseAmount = extent;
            }
        } else if (dim.left != null) {
            // Use the left and right functions to get the size
            String a = D3Util.stripFunction(dim.left);
            String b = D3Util.stripFunction(dim.right);
            baseAmount = "Math.abs(" + a + "-" + b + ")";
            needsFunction = true;
        } else {
            // Use size of categories
            Field[] baseFields = fields;
            if (purpose == ScalePurpose.x || purpose == ScalePurpose.inner) {
                // Do not count the other fields
                baseFields = new Field[]{fields[0]};
            }
            int categories = scales.getCategories(baseFields).size();
            if (purpose == ScalePurpose.x && fields.length > 1) {
                // We want the size of the bars for a clustered chart
                // Each major cluster is divided into subclusters so we multiply to find the number
                // of paired categories
                Object[] cats = fields[1].categories();
                if (cats != null) categories *= cats.length;
            }
            Double granularity = scales.getGranularitySuitableForSizing(baseFields);
            if (vis.tDiagram != null) {
                // Diagrams do not define these things
                granularity = null;
                categories = 0;
            }
            // Use the categories to define the size to fill if there are any categories
            if (categories > 0) {
                // divide up the space by the number of categories
                baseAmount = (categories == 1) ? extent : extent + "/" + categories;

                // Create some spacing between categories -- ONLY if we have all categorical data,
                // or if we are clustering (in which case a larger gap is better)

                if (purpose == ScalePurpose.inner || purpose == ScalePurpose.x && fields.length > 1)
                    baseAmount = DefineLocations.CLUSTER_SPACING + " * " + baseAmount;
                else if ((dim.sizeStyle == null || !dim.sizeStyle.isPercent()) && !scales.allNumeric(baseFields))
                    baseAmount = BAR_SPACING + " * " + baseAmount;

            } else if (granularity != null) {
                String scaleName = "scale_" + purpose.name();
                baseAmount = "Math.abs( " + scaleName + "(" + granularity + ") - " + scaleName + "(0) )";
                needsFunction = true;
            } else {
                baseAmount = "geom.default_point_size";
            }
        }

        // If the size definition is a percent, use that to scale by
        if (dim.sizeStyle != null && dim.sizeStyle.isPercent())
            baseAmount = dim.sizeStyle.value() + " * " + baseAmount;

        if (dim.sizeFunction != null) baseAmount = dim.sizeFunction + " * " + baseAmount;

        // If we need a function, wrap it up as required
        if (needsFunction) {
            return "function(d) { return " + baseAmount + "}";
        } else {
            return baseAmount;
        }

    }

    public static String getOverallSize(VisSingle vis, ElementDefinition def) {
        Size size = ModelUtil.getElementSize(vis, "size");
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

        String xBody = D3Util.stripFunction(x);
        String yBody = D3Util.stripFunction(y);

        // This will already have the size function factored in if defined
        String content = "Math.min(" + xBody + ", " + yBody + ")";

        // if the body is different from the whole item for x or y, then we have a function and must return a function
        if (!xBody.equals(x) || !yBody.equals(y)) {
            return "function(d) { return " + content + " }";
        } else {
            return content;
        }
    }

    private void constructSplitPath() {
        // We add the x function to signal we need the paths sorted
        String params = "data, path";
        if (vis.tElement == Element.line || vis.tElement == Element.area)
            params += ", x";
        out.add("var splits = BrunelD3.makePathSplits(" + params + ");").ln();
    }

    private void defineBar(String basicDef, ElementDefinition elementDef) {
        if (vis.fRange != null || vis.stacked) {
            // Stacked or range element goes from higher of the pair of values to the lower
            out.add("var y0 =", elementDef.y.left).endStatement();
            out.add("var y1 =", elementDef.y.right).endStatement();
            defineHorizontalExtentFunctions(elementDef.x);
            out.add(basicDef);
            out.addChained("attr('y', function(d) { return Math.min(y0(d), y1(d)) } )");
            out.addChained("attr('height', function(d) {return Math.max(0.001, Math.abs(y0(d) - y1(d))) })");
        } else {
            // Simple element; drop from the upper value to the baseline
            out.add("var y =", elementDef.y.center).endStatement();
            defineHorizontalExtentFunctions(elementDef.x);
            out.add(basicDef);
            if (vis.coords == Coordinates.transposed) {
                out.addChained("attr('y', 0)")
                        .addChained("attr('height', function(d) { return Math.max(0,y(d)) })");
            } else {
                out.addChained("attr('y', y)")
                        .addChained("attr('height', function(d) {return Math.max(0,geom.inner_height - y(d)) }) ");
            }
        }
        new PointBuilder(out).defineHorizontalExtent(elementDef.x);
    }

    private void defineEdge(String basicDef, ElementDefinition elementDef) {
        out.add(basicDef);
        if (elementDef.getRefLocation() != null) {
            out.addChained("each(function(d) { this.__edge = " + elementDef.getRefLocation() + "})");
            if (structure.chart.geo != null) {
                // geo does not need scales
                out.addChained("attr('x1', function() { return this.__edge[0][0]})");
                out.addChained("attr('y1', function() { return this.__edge[0][1]})");
                out.addChained("attr('x2', function() { return this.__edge[1][0]})");
                out.addChained("attr('y2', function() { return this.__edge[1][1]})");
            } else {
                out.addChained("attr('x1', function() { return this.__edge[0] ? scale_x(this.__edge[0][0]) : null })");
                out.addChained("attr('y1', function() { return this.__edge[0] ? scale_y(this.__edge[0][1]) : null })");
                out.addChained("attr('x2', function() { return this.__edge[1] ? scale_x(this.__edge[1][0]) : null })");
                out.addChained("attr('y2', function() { return this.__edge[1] ? scale_y(this.__edge[1][1]) : null })");
            }
            out.addChained("each(function() { if (!this.__edge[0][0] || !this.__edge[1][0]) this.style.visibility = 'hidden'})");
        } else {
            out.addChained("attr('x1'," + elementDef.x.left + ")");
            out.addChained("attr('y1'," + elementDef.y.left + ")");
            out.addChained("attr('x2'," + elementDef.x.right + ")");
            out.addChained("attr('y2'," + elementDef.y.right + ")");
        }
    }

    private boolean allShowExtent(Field[] fields) {
        // Categorical and numeric fields both show elements as extents on the axis
        for (Field field : fields) {
            if (field.isNumeric() && !field.isBinned()) return false;
        }
        return true;
    }

    public void preBuildDefinitions() {
        if (diagram != null) diagram.preBuildDefinitions();
    }

}
