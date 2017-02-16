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

import org.brunel.build.d3.D3Interaction;
import org.brunel.build.d3.D3LabelBuilder;
import org.brunel.build.d3.D3ScaleBuilder;
import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.ScalePurpose;
import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.d3.diagrams.GeoMap;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.Accessibility;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ModelUtil.Size;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;
import org.brunel.model.VisTypes.Using;
import org.brunel.model.style.StyleTarget;

import static org.brunel.build.d3.element.ElementRepresentation.symbol;
import static org.brunel.model.VisTypes.Diagram.tree;

public class D3ElementBuilder {

	private static final String BAR_SPACING = "0.9";            // Spacing between categorical bars

	protected final ScriptWriter out;                             // To write code out to
	protected final VisSingle vis;                                // Element definition

	private final D3ScaleBuilder scales;                        // Helper to build scales
	private final D3Interaction interaction;
	private final D3LabelBuilder labelBuilder;                  // Helper to build labels
	private final D3Diagram diagram;                            // Helper to build diagrams
	protected final ElementStructure structure;

	public D3ElementBuilder(ElementStructure structure, ScriptWriter out, D3ScaleBuilder scales, D3Interaction interaction) {
		this.structure = structure;
		this.vis = structure.vis;
		this.out = out;
		this.scales = scales;
		this.interaction = interaction;
		this.labelBuilder = new D3LabelBuilder(vis, out, structure.data);
		this.diagram = D3Diagram.make(structure, interaction, out);
	}

	public void generate(int elementIndex) {
		out.add("element = elements[" + elementIndex + "]").endStatement();

		ElementDetails details = makeDetails();         // Create the details of what the element should be
		setGeometry(details);                           // And the coordinate definitions

		defineAllElementFeatures(details);                // Features for the entire element -- paths, etc.
		defineLabelSettings(details);                    // Defines the 'labeling' settings object
		defineInitialState(details);                    // Define function to initialize element
		defineUpdateState(details);                        // Define function to update element (acts on initial+update)
		defineLabeling(details);                        // Defines the labeling  function

		// Define the selections
		out.onNewLine().comment("Create selections, set the initial state and transition updates");
		out.add("selection = main.selectAll('.element').data(" + details.dataSource + ",", getKeyFunction(), ")")
				.endStatement();
		out.add("var added = selection.enter().append('" + details.representation.getMark() + "')").endStatement();
		out.add("merged = selection.merge(added)").endStatement();

		// Set initial state and selection status (which cannot be applied to a transition)
		// Then call update and label functions which will transition to new state
		out.add("initialState(added)").endStatement();
		out.add("selection.filter(hasData).classed('selected', isSelected(data)).filter(isSelected(data)).raise()")
				.endStatement();
		out.add("updateState(BrunelD3.transition(merged, transitionMillis))").endStatement();
		out.add("label(merged, transitionMillis)").endStatement();

		// Define the function to fade out any item leaving the selection
		writeRemovalOnExit(out, "selection");
	}

	private void defineAllElementFeatures(ElementDetails details) {
		if (diagram == null) {
			writeCoordinateFunctions(details);
			if (details.representation == ElementRepresentation.wedge) {
				// Deal with the case of wedges (polar intervals)
				out.onNewLine().comment("Define the path for pie wedge shapes");
				defineWedgePath();
			} else if (details.requiresSplitting()) {
				// Define paths needed in the element, and make data splits
				out.onNewLine().comment("Define paths");
				definePathsAndSplits(details);
			}
		} else {
			// Set the diagram group class for CSS
			out.add("main.attr('class',", diagram.getStyleClasses(), ")").endStatement();
			diagram.defineCoordinateFunctions(details);
		}
	}

	private void defineUpdateState(ElementDetails details) {
		// Define the update to the merged data
		out.onNewLine().ln().comment("Define selection update operations on merged data")
				.onNewLine().add("function updateState(selection) {").indentMore()
				.onNewLine().add("selection").onNewLine();
		if (diagram == null || diagram instanceof GeoMap) {
			writeCoordinateDefinition(details);
			writeElementAesthetics(details, true, vis, out);
		}
		if (diagram != null) diagram.writeDiagramUpdate(details);

		out.indentLess().onNewLine().add("}").ln();
	}

	private void defineInitialState(ElementDetails details) {
		// Define the initial placement of the items
		out.onNewLine().ln().comment("Define selection entry operations")
				.onNewLine().add("function initialState(selection) {").indentMore()
				.onNewLine().add("selection").onNewLine();
		out.addChained("attr('class', '" + Data.join(details.classes, " ") + "')");
		addStylingForRoundRectangle();
		if (diagram != null)
			diagram.writeDiagramEnter(details);
		if (!interaction.hasElementInteraction(structure))
			out.addChained("style('pointer-events', 'none')");
		Accessibility.addAccessibilityLabels(structure, out, labelBuilder);
		out.indentLess().onNewLine().add("}").ln();
	}

	protected void defineLabeling(ElementDetails details) {
		out.onNewLine().ln().comment("Define labeling for the selection")
				.onNewLine().add("function label(selection, transitionMillis) {")
				.indentMore().onNewLine();
		if (diagram == null)
			writeElementLabelsAndTooltips(details, labelBuilder);
		else
			diagram.writeLabelsAndTooltips(details, labelBuilder);
		out.indentLess().onNewLine().add("}").ln();
	}

	protected void defineLabelSettings(ElementDetails details) {
		int collisionDetectionGranularity;
		if (details.textCanOverlap()) {
			collisionDetectionGranularity = 0;
		} else {
			// Set the grid size as a power of 2 depending how much data we have seen
			int n = structure.data.rowCount();
			double pow = Math.log((n / 10)) / Math.log(4);
			collisionDetectionGranularity = (int) Math.pow(2, Math.floor(pow));
			collisionDetectionGranularity = Math.min(16, Math.max(1, collisionDetectionGranularity));
		}

		labelBuilder.defineLabeling(vis.itemsLabel, details.getTextMethod(), false, details.textFitsShape(),
				details.getAlignment(), details.getPadding(), vis.fCSS,
				collisionDetectionGranularity);   // Labels
	}

	public static void writeRemovalOnExit(ScriptWriter out, String selection) {
		// This fires when items leave the system
		// It removes the item and any associated labels
		out.onNewLine().ln().add("BrunelD3.transition(" + selection + ".exit(), transitionMillis/3)");
		out.addChained("style('opacity', 0.5).each( function() {")
				.indentMore().indentMore().onNewLine()
				.add("this.remove(); if (this.__label__) this.__label__.remove()")
				.indentLess().indentLess().onNewLine()
				.add("})").endStatement();
	}

	private void writeCoordinateFunctions(ElementDetails details) {

		writeDimLocations(details.x, "x", "w");
		writeDimLocations(details.y, "y", "h");

		if (details.getRefLocation() != null) {
			// This will be used to ensure missing references are not displayed with junk locations
			out.add("function validReference(r) {");
			if (details.representation == ElementRepresentation.segment)
				out.add("return r[0][0] != null && r[0][1] != null && r[1][0] != null && r[1][1] != null");
			else
				out.add("return r[0][0] != null && r[0][1] != null");
			out.add("}").endStatement();
		}
	}

	/**
	 * This writes the coordinate functions out. They will be used in the element definition
	 *
	 * @param dim      the necessary functional components are dound in this object
	 * @param mainName the name of the dimension ('x' or 'y')
	 * @param sizeName the name of the sie function ('width' or 'height')
	 */
	private void writeDimLocations(ElementDimension dim, String mainName, String sizeName) {
		if (dim.clusterSize != null) out.add("var clusterWidth =", dim.clusterSize).endStatement();
		if (dim.size != null) out.add("var", sizeName, "=", dim.size).endStatement();

		if (dim.left != null && dim.right != null) {
			// Use the left and right values to define x1,x2 (or y1, y2)
			out.add("var", mainName + "1 =", dim.left).endStatement();
			out.add("var", mainName + "2 =", dim.right).endStatement();
		}

		// Define the center and size
		if (dim.center != null) out.add("var", mainName, "=", dim.center).endStatement();
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

	protected ElementDetails makeDetails() {
		// When we create diagrams this has the side effect of writing the data calls needed
		if (diagram == null) {
			return ElementDetails.makeForCoordinates(vis, getCommonSymbol());
		} else {
			out.onNewLine().comment("Data structures for a", vis.tDiagram, "diagram");
			return diagram.initializeDiagram(getCommonSymbol());
		}
	}

	protected void setGeometry(ElementDetails e) {

		Field[] x = structure.chart.coordinates.getX(vis);
		Field[] y = structure.chart.coordinates.getY(vis);
		Field[] keys = new Field[vis.fKeys.size()];
		for (int i = 0; i < keys.length; i++) keys[i] = structure.data.field(vis.fKeys.get(i).asField());

		if (structure.isDependent()) {
			defineReferenceFunctions(e, keys);
			DefineLocations.setDependentLocations(structure, e);
		}

		if (structure.chart.geo != null) {
			e.x.size = getSize(new Field[0], "geom.default_point_size", null, e.x, e.representation);
			e.y.size = getSize(new Field[0], "geom.default_point_size", null, e.x, e.representation);

			// Maps with feature data do not need the geo coordinates set
			if (vis.tDiagram != Diagram.map)
				setLocationsByProjection(e, x, y);
			else if (e.representation != ElementRepresentation.geoFeature)
				setLocationsByGeoPropertiesCenter(e);
			// Just use the default point size
		} else {
			// Must define cluster size before anything else, as other things use it
			if (structure.isClustered()) {
				e.x.clusterSize = getSize(x, "geom.inner_width", ScalePurpose.x, e.x, e.representation);
				e.x.size = getSize(x, "geom.inner_width", ScalePurpose.inner, e.x, e.representation);
			} else {
				e.x.size = getSize(x, "geom.inner_width", ScalePurpose.x, e.x, e.representation);
			}
			e.y.size = getSize(y, "geom.inner_height", ScalePurpose.y, e.y, e.representation);
			DefineLocations.setLocations(e.representation, structure, e.x, "x", x, structure.chart.coordinates.xCategorical);
			DefineLocations.setLocations(e.representation, structure, e.y, "y", y, structure.chart.coordinates.yCategorical);
			if (e.representation == ElementRepresentation.area && e.y.right == null) {
				// Area needs to have two functions even when not defined as such -- make the second the base
				e.y.right = e.y.center;
				e.y.left = GeomAttribute.makeConstant("scale_y.range()[0]");
				e.y.center = null;
			}
		}
		e.overallSize = getOverallSize(vis, e);
	}

	private void defineReferenceFunctions(ElementDetails e, Field[] keys) {
		// This element's locations depends on another element
		String[] references = structure.makeReferences(keys);
		if (structure.chart.geo != null) {
			// Wrap the locations in the projection
			for (int i = 0; i < references.length; i++)
				references[i] = "projection(" + references[i] + ")";
			e.setReferences(references);
		} else if (structure.chart.diagram != Diagram.network && structure.chart.diagram != tree) {
			// Trees and Networks do their own thing
			e.setReferences(references);
		}
	}

	private void definePathsAndSplits(ElementDetails elementDef) {

		// Now actual paths
		if (vis.tElement == Element.area) {
			out.add("var path = d3.area().x(x).y1(y2).y0(y1)");
		} else if (vis.tElement.producesSingleShape) {
			// Choose the top line if there is a range (say for stacking)
			String yDef = elementDef.y.right == null ? "y" : "y2";
			if (vis.fSize.size() == 1) {
				out.add("var path = BrunelD3.sizedPath().x(x).y(" + yDef + ")");
				GeomAttribute size = elementDef.y.size != null ? elementDef.y.size : elementDef.overallSize;
				out.addChained("r( function(d) { return " + size.definition() + "})");
			} else {
				out.add("var path = d3.line().x(x).y(" + yDef + ")");
			}
		}
		if (vis.tUsing == Using.interpolate) {
			out.add(".curve(d3.curveCatmullRom)");
		}
		out.endStatement();
		constructSplitPath();
	}

	private void defineWedgePath() {
		out.add("var path = d3.arc().innerRadius(0)");
		if (vis.fSize.isEmpty())
			out.add(".outerRadius(geom.inner_radius)");
		else
			out.add(".outerRadius(function(d) { return size(d) * geom.inner_radius })");
		if (vis.fRange == null && !vis.stacked)
			out.addChained("startAngle(0).endAngle(y)");
		else
			out.addChained("startAngle(y1).endAngle(y2)");
		out.endStatement();
	}

	/* The key function ensure we have object constancy when animating */
	private String getKeyFunction() {
		if (diagram != null) return diagram.getRowKeyFunction();
		return "function(d) { return d.key }";
	}

	private void addStylingForRoundRectangle() {
		// Added rounded styling if needed
		StyleTarget target = StyleTarget.makeElementTarget("rect", "element", "point");
		Size size = ModelUtil.getSize(vis, target, "border-radius");
		if (size != null)
			out.addChained("attr('rx'," + size.value(8.0) + ").attr('ry', " + size.value(8.0) + ")");
	}

	private void writeCoordinateDefinition(ElementDetails details) {
		// If we need reference locations, write them in first
		if (details.getRefLocation() != null) {
			out.addChained("each(function(d) { this.r = " + details.getRefLocation().definition() + "})");
			out.addChained("style('visibility', function() { return validReference(this.r) ? 'visible' : 'hidden'})");
		}

		if (details.requiresSplitting())
			out.addChained("attr('d', function(d) { return d.path })");     // Split path -- get it from the split
		else if (details.isPath()) {
			// Annoyingly, D3 adds a comma before L commands for geo maps, which is illegal and Firefox chokes on it
			if (vis.tDiagram == Diagram.map)
				out.addChained("attr('d', function(d) { return path(d).replace(/,L/g, 'L').replace(/,Z/g, 'Z') })");
			else
				out.addChained("attr('d', path)");
		} else if (details.representation == ElementRepresentation.rect)
			defineRect(details, out);
		else if (details.representation == ElementRepresentation.segment) {
			out.addChained("attr('x1', x1).attr('y1', y1).attr('x2', x2).attr('y2', y2)");
		} else if (details.representation == ElementRepresentation.text)
			defineText(details, vis);
		else if (details.representation == symbol)
			defineSymbol(details, vis, out);
		else if (details.representation == ElementRepresentation.pointLikeCircle
				|| details.representation == ElementRepresentation.spaceFillingCircle
				|| details.representation == ElementRepresentation.largeCircle)
			defineCircle(details, out);
	}

	public static void writeElementLabelsAndTooltips(ElementDetails details, D3LabelBuilder labelBuilder) {
		labelBuilder.addElementLabeling();
		labelBuilder.addTooltips(details);
	}

	public static void writeElementAesthetics(ElementDetails details, boolean filterToDataOnly, VisSingle vis, ScriptWriter out) {
		boolean showsColor = !vis.fColor.isEmpty();
		boolean showsStrokeSize = details.isStroked() && !vis.fSize.isEmpty();
		boolean showsOpacity = !vis.fOpacity.isEmpty();
		boolean showsCSS = !vis.fCSS.isEmpty();
		boolean showsSymbol = !vis.fSymbol.isEmpty();

		if (filterToDataOnly && (showsColor || showsOpacity || showsStrokeSize || showsCSS|| showsSymbol)) {
			// Filter only to show the data based items
			out.addChained("filter(hasData)").at(50).comment("following only performed for data items");
		}

		int n = vis.fCSS.size();
		for (int i = 0; i < n; i++) {
			String suffix = n > 1 ? "_" + (n + 1) : "";                        // Only need suffixes for multiples
			String base = Data.join(details.classes, " ") + " ";
			out.addChained("attr('class', function(d) { return " + Data.quote(base) + " + css" + suffix + "(d) } )");
		}

		// Define colors using the color function
		if (showsColor) {
			String colorType = details.isStroked() ? "stroke" : "fill";
			out.addChained("style('" + colorType + "', color)");
		}

		// Define line width if needed
		if (showsStrokeSize)
			out.addChained("style('stroke-width', size)");

		// Define symbols
		if (showsSymbol)
			out.addChained("attr('xlink:href', function(d) { return '#' + symbolID(d) })");

		// Define opacity
		if (showsOpacity) {
			out.addChained("style('fill-opacity', opacity)").
					addChained("style('stroke-opacity', opacity)");
		}

		out.endStatement();
	}

	private void defineText(ElementDetails elementDef, VisSingle vis) {
		// If the center is not defined, this has been placed using a translation transform
		if (elementDef.x.center != null) out.addChained("attr('x'," + elementDef.x.center + ")");
		if (elementDef.y.center != null) out.addChained("attr('y'," + elementDef.y.center + ")");
		out.addChained("attr('dy', '0.35em').text(labeling.content)");
		D3LabelBuilder.addFontSizeAttribute(vis, out);
	}

	private static void defineCircle(ElementDetails elementDef, ScriptWriter out) {
		// If the center is not defined, this has been placed using a translation transform
		if (centerDefined(elementDef)) {
			out.addChained("attr('cx'," + elementDef.x.center + ")")
					.addChained("attr('cy'," + elementDef.y.center + ")");
		}
		out.addChained("attr('r'," + elementDef.overallSize.halved() + ")");
	}

	private static boolean centerDefined(ElementDetails elementDef) {
		return elementDef.x.center != null && elementDef.y.center != null;
	}

	public static void definePointLikeMark(ElementDetails elementDef, VisSingle single, ScriptWriter out) {
		if (elementDef.representation == symbol) defineSymbol(elementDef, single, out);
		else defineCircle(elementDef, out);
	}

	private static void defineSymbol(ElementDetails elementDef, VisSingle vis, ScriptWriter out) {
		// If the center is not defined, this has been placed using a translation transform already
		if (centerDefined(elementDef)) {
			if (vis.fSymbol.isEmpty()) {
				defineFixedCommonSymbol(elementDef, vis, out);
			} else {
				defineUsingSymbolAesthetic(elementDef, out);
			}
		}
	}

	private static void defineUsingSymbolAesthetic(ElementDetails elementDef, ScriptWriter out) {
		out.addChained("attr('xlink:href', function(d) { return '#' + symbolID(d) })");
		defineRect(elementDef, out);
	}

	private static void defineFixedCommonSymbol(ElementDetails elementDef, VisSingle vis, ScriptWriter out) {
		// Add a translate to place it in the right location
		out.addChained("attr('transform', function(d) { return 'translate(' + "
				+ elementDef.x.center.definition() + " + ', ' + "
				+ elementDef.y.center.definition() + " + ')' })");


		String symbolName = ModelUtil.getElementSymbol(vis);
		symbolName = Data.quote(symbolName == null ? "circle" : symbolName);

		GeomAttribute size = elementDef.overallSize.halved();
		if (size.isFunc()) {
			// The size changes, so we must call the function
			out.addChained("attr('d', function(d) { return BrunelD3.symbol(" + symbolName + ", " +
					size.definition() + ") })");
		} else {
			// Fixed symbol -- no function needed
			out.addChained("attr('d', BrunelD3.symbol(" + symbolName + ", " + size + "))");
		}
	}

	private String getCommonSymbol() {
		String result = ModelUtil.getElementSymbol(vis);
		if (result != null) return result;
		if (structure.chart.geo != null) return "circle";             // Geo charts default to circles
		// We default to a rectangle if all the scales are categorical or binned, otherwise we return a point
		boolean cat = allShowExtent(structure.chart.coordinates.allXFields) && allShowExtent(structure.chart.coordinates.allYFields);
		return cat ? "rect" : "circle";
	}

	private void setLocationsByProjection(ElementDetails def, Field[] x, Field[] y) {

		int n = x.length;
		if (y.length != n)
			throw new IllegalStateException("X and Y dimensions do not match in geographic maps");
		if (structure.isDependentEdge()) {
			throw new IllegalStateException("Cannot handle edged dependencies in geographic maps");
		}

		// Already defined
		if (structure.isDependent()) return;

		if (n == 0) {
			def.x.center = GeomAttribute.makeConstant("null");
			def.y.center = GeomAttribute.makeConstant("null");
		} else if (n == 1) {
			String xFunction = D3Util.writeCall(x[0]);
			String yFunction = D3Util.writeCall(y[0]);
			if (DefineLocations.isRange(x[0])) xFunction += ".mid";
			if (DefineLocations.isRange(y[0])) yFunction += ".mid";

			def.x.center = GeomAttribute.makeFunction("projection([" + xFunction + "," + yFunction + "])[0]");
			def.y.center = GeomAttribute.makeFunction("projection([" + xFunction + "," + yFunction + "])[1]");
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

			def.x.left = GeomAttribute.makeFunction("projection([" + xLow + "," + yLow + "])[0]");
			def.x.right = GeomAttribute.makeFunction("projection([" + xHigh + "," + yHigh + "])[0]");
			def.y.left = GeomAttribute.makeFunction("projection([" + xLow + "," + yLow + "])[1]");
			def.y.right = GeomAttribute.makeFunction("projection([" + xHigh + "," + yHigh + "])[1]");
		}
	}

	private void setLocationsByGeoPropertiesCenter(ElementDetails e) {
		// We use the embedded centers in the geo properties to place the items
		// TODO: make this better ...
		e.x.center = GeomAttribute.makeFunction("project_center(d.geo_properties)[0]");
		e.y.center = GeomAttribute.makeFunction("project_center(d.geo_properties)[1]");
	}

	private GeomAttribute getSize(Field[] fields, String extent, ScalePurpose purpose, ElementDimension dim, ElementRepresentation rep) {
		boolean needsFunction = dim.sizeFunction != null;
		String baseAmount;
		if (dim.sizeStyle != null && !dim.sizeStyle.isPercent()) {
			// Absolute size overrides everything
			baseAmount = "" + dim.sizeStyle.value(100);
		} else if (fields.length == 0) {
			if (vis.tDiagram != null || rep == symbol) {
				// Default point size for diagrams
				baseAmount = "geom.default_point_size";
			} else {
				// If there are no fields, then fill the extent completely
				baseAmount = extent;
			}
		} else if (dim.left != null) {
			// Use the left and right functions to get the size
			baseAmount = "Math.abs(" + dim.left.definition() + "-" + dim.right.definition() + ")";
			needsFunction = true;
		} else {
			String scaleName = "scale_" + purpose.name();

			// Use size of categories
			Field[] baseFields = fields;
			if (purpose == ScalePurpose.x) baseFields = new Field[]{fields[0]};             // Just the X
			if (purpose == ScalePurpose.inner) baseFields = new Field[]{fields[1]};         // Just the cluster

			int categories = scales.getCategories(baseFields).size();
			Double granularity = scales.getGranularitySuitableForSizing(baseFields);
			if (purpose == ScalePurpose.x || purpose == ScalePurpose.y || purpose == ScalePurpose.inner) {
				if (baseFields.length == 1 && baseFields[0].isNumeric()) categories = 0;
			}

			if (vis.tDiagram != null) {
				// Diagrams do not define these things
				granularity = null;
				categories = 0;
			}
			// Use the categories to define the size to fill if there are any categories
			if (categories > 0) {
				if (categories > 1) {
					// Distance between two categories
					baseAmount = "Math.abs(" + scaleName + "(" + scaleName + ".domain()[1])"
							+ " - " + scaleName + "(" + scaleName + ".domain()[0])"
							+ " )";
				} else {
					// The whole space
					baseAmount = extent;
				}

				// Create some spacing between categories -- ONLY if we have all categorical data,
				if (purpose == ScalePurpose.x && fields.length > 1)
					baseAmount = DefineLocations.CLUSTER_SPACING + " * " + baseAmount;
				else if (purpose != ScalePurpose.inner && !scales.allNumeric(baseFields)
						&& (dim.sizeStyle == null || !dim.sizeStyle.isPercent()))
					baseAmount = BAR_SPACING + " * " + baseAmount;
			} else if (granularity != null) {
				baseAmount = "Math.abs( " + scaleName + "(" + scaleName + ".domain()[0] + " + granularity + ") - " + scaleName + ".range()[0] )";
			} else {
				baseAmount = "geom.default_point_size";
			}
		}

		// If the size definition is a percent, use that to scale by
		if (dim.sizeStyle != null && dim.sizeStyle.isPercent())
			baseAmount = dim.sizeStyle.value(1) + " * " + baseAmount;

		// Multiple by the size of the cluster
		if (dim.clusterSize != null) {
			if (dim.clusterSize.isFunc()) baseAmount += " * clusterWidth(d)";
			else baseAmount += " * clusterWidth";
		}

		if (dim.sizeFunction != null) baseAmount = dim.sizeFunction + " * " + baseAmount;

		// If we need a function, wrap it up as required
		if (needsFunction) {
			return GeomAttribute.makeFunction(baseAmount);
		} else {
			return GeomAttribute.makeConstant(baseAmount);
		}

	}

	private static GeomAttribute getOverallSize(VisSingle vis, ElementDetails def) {
		StyleTarget target = StyleTarget.makeElementTarget(null, def.classes);
		Size size = ModelUtil.getSize(vis, target, "size");
		boolean needsFunction = vis.fSize.size() == 1;

		if (size != null && !size.isPercent()) {
			// Just multiply by the aesthetic if needed
			if (needsFunction)
				return GeomAttribute.makeFunction("size(d) * " + size.value(1));
			else
				return GeomAttribute.makeConstant(Double.toString(size.value(1)));
		}

		// Use the X and Y extents to define the overall one

		GeomAttribute x = def.x.size;
		GeomAttribute y = def.y.size;
		if (x.equals(y)) return x;          // If they are both the same, use that

		// This will already have the size function factored in if defined
		String content = "Math.min(" + x.definition() + ", " + y.definition() + ")";

		// if the body is different from the whole item for x or y, then we have a function and must return a function
		if (x.isFunc() || y.isFunc()) {
			return GeomAttribute.makeFunction(content);
		} else {
			return GeomAttribute.makeConstant(content);
		}
	}

	private void constructSplitPath() {
		// We add the x function to signal we need the paths sorted
		String params = "data, path";
		if (vis.tElement == Element.line || vis.tElement == Element.area)
			params += ", x";
		out.add("var splits = BrunelD3.makePathSplits(" + params + ");").ln();
	}

	private static void defineRect(ElementDetails details, ScriptWriter out) {
		// Rectangles must have extents > 0 to display, so we need to write that code in
		out.addChained("each(function(d) {").indentMore().indentMore().onNewLine();
		if (details.x.defineUsingExtent()) {
			out.add("var a =", details.x.left.call("x1"), ", b =", details.x.right.call("x2"),
					", left = Math.min(a,b), width = Math.max(1e-6, Math.abs(a-b)), ");
		} else {
			out.add("var width =", details.x.size.call("w"), ", left =",
					details.x.center.call("x"), "- width/2, ");
		}
		out.onNewLine();
		if (details.y.defineUsingExtent()) {
			out.add("c =", details.y.left.call("y1"), ", d =", details.y.right.call("y2"),
					", top = Math.min(c,d), height = Math.max(1e-6, Math.abs(c-d))").endStatement();
		} else {
			out.add("height =", details.y.size.call("h"), ", top =",
					details.y.center.call("y"), "- height/2").endStatement();
		}
		out.onNewLine().add("this.r = {x:left, y:top, w:width, h:height}").endStatement().indentLess()
				.onNewLine().add("})").indentLess();

		// Sadly, browsers are inconsistent in how they handle width. It can be considered either a style or a
		// positional attribute, so we need to specify as both to make all browsers happy
		out.addChained("attr('x', function(d) { return this.r.x })")
				.addChained("attr('y', function(d) { return this.r.y })")
				.addChained("attr('width', function(d) { return this.r.w })")
				.addChained("attr('height', function(d) { return this.r.h })");
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
