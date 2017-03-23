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

package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuildUtil;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ModelUtil.Size;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisElement;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;
import org.brunel.model.style.StyleTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and defines labels for a chart element
 */
public class LabelBuilder {

	public static void addFontSizeAttribute(VisElement vis, ScriptWriter out) {
		if (!vis.fSize.isEmpty()) {
			StyleTarget target = StyleTarget.makeElementTarget("text", "label");
			Size parts = ModelUtil.getSize(vis, target, "font-size");
			if (parts == null) {
				out.addChained("style('font-size', function(d) { return (100*size(d)) + '%' })");
			} else {
				out.addChained("style('font-size', function(d) { return (", parts.value(12), "* size(d)) +'" + parts.suffix() + "' })");
			}
		}
	}

	public static int estimateLabelLength(List<Param> itemsLabel, Dataset data) {
		int size = 0;
		for (Param p : itemsLabel) {
			if (p.isField()) {
				Field f = data.field(p.asField());
				if (f.isDate()) size += 8;
				else if (f.preferCategorical()) size += maxLength(f.categories()) + 1;
				else size += 6;
			} else {
				// Text
				size += p.asString().length() + 1;
			}
		}
		return size;
	}

	private static int maxLength(Object[] categories) {
		int max = 0;
		for (Object o : categories) max = Math.max(max, o.toString().length());
		return max;
	}

	private final VisElement vis;
	private final ScriptWriter out;
	private final ElementStructure structure;

	public LabelBuilder(ElementStructure structure, ScriptWriter out) {
		this.structure = structure;
		this.vis = structure.vis;
		this.out = out;
	}

	public void addElementLabeling() {
		if (!structure.needsLabels()) return;

		// Networks are updated on ticks., so just attach once -- no transitions
		if (vis.tDiagram == Diagram.network) {
			out.add("BrunelD3.label(selection, labels, 0, geom, labeling)").endStatement();
			return;
		}

		// Text elements define labeling as the main item; they do not need labels attached, which is what this does
		if (vis.tElement != Element.text)
			out.add("BrunelD3.label(selection, labels, transitionMillis, geom, labeling)").endStatement();
	}

	/* Call to add labels for the rough centers to a grid layout */
	public void addGridLabels() {

		out.add("var gridLabelSel = diagramExtras.selectAll('text.title').data(gridLabels, function(d) { return d.label })")
				.endStatement();

		out.add("var gridLabelAdd = gridLabelSel.enter().append('text').attr('class', 'diagram gridded hierarchy title')")
				.addChained("text(function(d) { return d.label }).attr('dy', '0.3em').style('text-anchor', 'middle')")
				.endStatement();

		out.add("BrunelD3.transition(gridLabelAdd.merge(gridLabelSel))")
				.addChained("attr('x', function(d) { return scale_x(d.x) } ).attr('y', function(d) { return scale_y(d.y) } )")
				.endStatement();
	}

	public void addTooltips(ElementDetails details) {
		if (!structure.needsTooltips()) return;
		out.onNewLine().ln();
		defineLabeling(prettify(vis.itemsTooltip, true), details.representation.getTooltipTextMethod(), true, true, null, 0, Collections.<Param>emptyList(), 0);
		out.add("BrunelD3.addTooltip(selection, tooltipLabeling, geom)").endStatement();
	}

	/* Call to add labels for internal nodes of trees and treemaps */
	public void addTreeInternalLabelsInsideNode() {
		out.add("diagramLabels.attr('class', 'axis diagram treemap hierarchy')").endStatement()
				.add("var treeLabeling = { method:'inner-left', fit:true, dy:0.83, align:'start', ")
				.indentMore()
				.onNewLine().add("content:  function(d) { return d.data.innerNodeName },")
				.onNewLine().add("cssClass: function(d) { return 'axis label L' + d.depth + ' H' + d.height }, ")
				.onNewLine().add("where :   function(box) { return {'x': box.x + 2, 'y': box.y, 'box': box} }")
				.indentLess().onNewLine().add("}").endStatement();
		out.add("BrunelD3.label(selection.filter(function(d) {return d.data.key}), diagramLabels, treeLabeling, transitionMillis, geom)").endStatement();
	}

	/* Call to add labels for internal nodes of trees and treemaps */
	public void addTreeInternalLabelsOutsideNode(String vertical) {
		String dy = vertical.equals("bottom") ? "0.75" : "0.25";
		out.add("diagramLabels.attr('class', 'axis diagram tree hierarchy')").endStatement()
				.add("var treeLabeling = { location:['center', '" + vertical + "'], fit:false, dy:" + dy + ", align:'middle', granularity:1, ")
				.indentMore()
				.onNewLine().add("content:  function(d) { return d.data.innerNodeName },")
				.onNewLine().add("cssClass: function(d) { return 'axis label L' + d.depth + ' H' + d.height } ")
				.indentLess().onNewLine().add("}").endStatement();
		out.add("BrunelD3.label(selection.filter(function(d) {return d.data.innerNodeName}), diagramLabels, treeLabeling, transitionMillis, geom)").endStatement();
	}

	/**
	 * Define a structure to be used to label
	 *
	 * @param items                the items to form the content
	 * @param textMethod           method for placing text relative to the object it is attached to
	 * @param forTooltip           true if it is for tooltips
	 * @param fitsShape            true if the text is to fit inside the shape (if the shape wants it)
	 * @param alignment            left | right | center
	 * @param padding              numeric amount
	 * @param cssFunctions,        list of functions to use for css class
	 * @param hitDetectGranularity if strictly positive, the pixel level granularity to use for hit detection. If zero, none will be done
	 */
	public void defineLabeling(List<Param> items, String textMethod, boolean forTooltip,
							   boolean fitsShape, String alignment, double padding,
							   List<Param> cssFunctions, int hitDetectGranularity) {
		if (vis.tElement != Element.text && items.isEmpty()) return;
		out.add("var", (forTooltip ? "tooltipLabeling" : "labeling"), " = ");

		if (forTooltip) {
			defineSingleLabeling(items, textMethod, -1, fitsShape, alignment, padding, cssFunctions, hitDetectGranularity);
		} else {
			Collection<List<Param>> split = splitByLocation(items);
			out.add("[");
			if (split.size() > 1) out.indentMore().onNewLine();

			int index = 0;
			for (List<Param> part : split) {
				if (index > 0) out.add(", ");
				defineSingleLabeling(part, textMethod, index, fitsShape, alignment, padding, cssFunctions, hitDetectGranularity);
				index++;
			}

			if (split.size() > 1) out.indentLess().onNewLine();
			out.add("]");

		}
		out.endStatement();

	}

	private void defineSingleLabeling(List<Param> items, String textMethod, int index, boolean fitsShape, String alignment, double padding, List<Param> cssFunctions, int hitDetectGranularity) {

		// Override the text method if the items define it (they will all have the same text modifier)
		String definedTextMethod = items.get(0).firstTextModifier();
		if (definedTextMethod != null) textMethod = definedTextMethod;


		out.add("{").ln().indentMore();

		boolean fit = true;

		if (textMethod.equals("geo")) {
			// We define a function to extract the coordinates from the geo, and project them
			if (!isCustomMap()) {
				//We cannot assume the center location has been calculated for custom maps
				String func = "function(box,text,d) {var p = project_center(d.geo_properties); return {box:box, x:p[0], y:p[1]}}";
				out.onNewLine().add("where:", func, ",");
			} else {
				out.onNewLine().add("method: 'poly',");
				fit = false;
			}
		} else {
			HashSet<String> parts = new HashSet<>(Arrays.asList(textMethod.split("-")));
			boolean inside = isInside(parts, fitsShape);
			String method = getMethod(parts);
			String location = getLocation(parts);
			String align = alignment != null ? alignment : getAlignment(parts, inside);
			double offset = getOffset(parts, inside);
			fit = inside && fitsShape;
			out.onNewLine()
					.add("index:", index)
					.add(", method:", Data.quote(method))
					.add(", location:", location)
					.add(", inside:", inside)
					.add(", align:", Data.quote(align))
					.add(", pad:", padding)
					.add(", dy:", offset, ",");
		}

		out.onNewLine().add("fit:", fit, ", granularity:", hitDetectGranularity, ",");
		if (textMethod.equals("path") || textMethod.equals("wedge"))
			out.onNewLine().add("path: path,");

		// CSS functions were defined on the element, so we use them also on the label
		// If there is only one, it is called 'css', else they are  'css_1', 'css_2' etc.
		if (!cssFunctions.isEmpty()) {
			out.onNewLine().add(" cssClass: function(d) { return 'label '");
			if (cssFunctions.size() == 1) {
				// Easy case -- just use the label as defined
				out.add(" + css(d)");
			} else
				for (int i = 0; i < cssFunctions.size(); i++) {
					if (i > 0) out.add(" + ' '");
					out.add(" + css_" + (i + 1) + "(d)");
				}
			out.add("},");

		}

		//                 .onNewLine().add("cssClass: function(d) { return 'axis label L' + d.depth + ' H' + d.height } ")

		// Write it out as a wrapped function
		out.onNewLine().add("content: function(d) {").indentMore();

		// If we need data, guard against not having any
		if (needsData(items))
			out.onNewLine().add("return d.row == null ? null : ");
		else
			out.onNewLine().add("return ");

		writeContent(items, index < 0);                        // Index == -1 for tooltips
		out.indentLess().onNewLine().add("}");

		out.indentLess().onNewLine().add("}");
	}

	public Collection<List<Param>> splitByLocation(List<Param> all) {
		Map<String, List<Param>> map = new LinkedHashMap<>();
		for (Param p : all) {
			String place = p.firstTextModifier();
			if (place == null) place = "*";
			List<Param> list = map.get(place);
			if (list == null) {
				list = new ArrayList<>();
				map.put(place, list);
			}
			list.add(p);
		}
		return map.values();
	}

	public void writeContent(List<Param> items, boolean forTooltip) {
		// We must have some content
		if (items.isEmpty()) {
			// The position fields for a diagram
			if (vis.tDiagram != null) items = Param.makeFields(vis.positionFields());
			if (items.isEmpty())                                                    // Default is to use the row value
				items = Collections.singletonList(Param.makeField("#row"));
		}

		// Tooltips are in HTML format
		boolean first = true;
		for (Param p : prettify(items, false)) {
			if (!first) out.add("\n\t\t\t+ ");
			if (p.isField()) {
				Field f = structure.data.field(p.asField());
				if (forTooltip) out.add("'<span class=\"field\">' + ");

				// We look for a modifier that is numeric; if we find it, we wrap the format code with the shorten call
				Double restrict = p.firstNumericModifier();
				if (restrict != null) out.add("BrunelD3.shorten(");
				out.add("data." + BuildUtil.baseFieldID(f) + "_f(d)");
				if (restrict != null) out.add(",", restrict.intValue(), ")");

				if (forTooltip) out.add(" + '</span>'");
			} else {
				String o = p.asString();
				if (forTooltip) o = o.replaceAll("\\\\n", "&#10;"); // Add a new line character
				out.add(Data.quote(o));
			}
			first = false;
		}
	}

	private boolean isCustomMap() {
		return vis.tDiagramParameters.length == 1 && vis.tDiagramParameters[0].asString().contains("#");
	}

	// Gets the text alignment based on where to draw relative to the shape
	private String getAlignment(HashSet<String> parts, boolean inside) {
		if (parts.contains("left")) return inside ? "start" : "end";
		if (parts.contains("right")) return inside ? "end" : "start";
		return "middle";
	}

	// returns a two part array with the horizontal and vertical method locations
	private String getLocation(HashSet<String> parts) {
		String h = "center", v = "center";
		if (parts.contains("left")) h = "left";
		if (parts.contains("right")) h = "right";
		if (parts.contains("top")) v = "top";
		if (parts.contains("bottom")) v = "bottom";
		return "['" + h + "', '" + v + "']";
	}

	// Returns the important function that defines how we will find the shape
	private String getMethod(HashSet<String> parts) {
		for (String s : parts)
			if (s.equals("path") || s.equals("wedge") || s.equals("area") || s.equals("poly") || s.equals("geo"))
				return s;
		return "box";
	}

	// How to offset the text so it fits correctly
	private double getOffset(HashSet<String> parts, boolean inside) {
		if (parts.contains("top")) return inside ? 0.7 : -0.25;
		if (parts.contains("bottom")) return inside ? -0.25 : 0.7;
		return 0.3;

	}

	private boolean isInside(HashSet<String> parts, boolean fitsShape) {
		// The user request  is checked first. Then, if asked to fit, we assume inside
		if (parts.contains("inside")) return true;
		else if (parts.contains("outside")) return false;
		else return fitsShape;
	}

	private boolean needsData(List<Param> items) {
		for (Param p : prettify(items, false))
			if (p.isField()) return true;
		return false;
	}

	private List<Param> prettify(List<Param> items, boolean longForm) {

		// If we have nothing but field names, and at least two, we add separators
		if (items.size() < 2) return items;    // One item does not get prettified

		ArrayList<Param> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			Param p = items.get(i);
			if (!p.isField()) return items;            // Any non-field and we do not prettify
			Field f = structure.data.field(p.asField());
			if (i > 0) result.add(Param.makeString(longForm ? "<br/>" : ", "));
			if (longForm)
				result.add(Param.makeString("<span class=\"title\">" + f.label + ": </span>"));
			result.add(p);
		}
		return result;
	}

}
