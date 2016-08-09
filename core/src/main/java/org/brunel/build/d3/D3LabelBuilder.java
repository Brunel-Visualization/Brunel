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

import org.brunel.action.Param;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ModelUtil.Size;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;
import org.brunel.model.style.StyleTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Creates and defines labels for a chart element
 */
public class D3LabelBuilder {

    private final VisSingle vis;
    private final ScriptWriter out;
    private final Dataset data;

    public D3LabelBuilder(VisSingle vis, ScriptWriter out, Dataset data) {
        this.vis = vis;
        this.out = out;
        this.data = data;
    }

    public void addElementLabeling() {
        if (vis.itemsLabel.isEmpty()) return;
        // Networks are updated on ticks., so just attach once -- no transitions
        if (vis.tDiagram == Diagram.network) {
            out.add("BrunelD3.label(merged, labels, labeling, 0, geom)").endStatement();
            return;
        }

        // Text elements define labeling as the main item; they do not need labels attached, which is what this does
        if (vis.tElement != Element.text)
            out.add("BrunelD3.label(merged, labels, labeling, transitionMillis, geom)").endStatement();
    }

    public static void addFontSizeAttribute(VisSingle vis, ScriptWriter out) {
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

    public void addTooltips(ElementDetails details) {
        if (vis.itemsTooltip.isEmpty()) return;
        out.onNewLine().ln();
        defineLabeling(prettify(vis.itemsTooltip, true), details.representation.getTooltipTextMethod(), true, true, null, 0, 0);
        out.add("BrunelD3.addTooltip(merged, tooltipLabeling, geom)").endStatement();
    }

    /**
     * Define a structure to be used to label
     *
     * @param items                the items to form the content
     * @param textMethod           method for placing text relative to the object it is attached to
     * @param forTooltip           true if this is for a tooltip
     * @param fitsShape            true if the text is to fit inside the shape (if the shape wants it)
     * @param alignment            left | right | center
     * @param padding              numeric amount
     * @param hitDetectGranularity if >0, the pixel level granularity to use for hit detection. If zero, none will be done
     */
    public void defineLabeling(List<Param> items, String textMethod, boolean forTooltip, boolean fitsShape, String alignment, double padding, int hitDetectGranularity) {
        if (vis.tElement != Element.text && items.isEmpty()) return;
        String name = forTooltip ? "tooltipLabeling" : "labeling";
        out.add("var", name, "= {").ln().indentMore();

        boolean fit = true;

        if (textMethod.equals("geo")) {
            // We define a function to extract the coordinates from the geo, and project them
            String func = "function(box,text,d) {var p = projection([d.geo_properties.c, d.geo_properties.d]); return {box:box, x:p[0], y:p[1]}}";
            out.onNewLine().add("where:", func, ",");
        } else {
            HashSet<String> parts = new HashSet<>(Arrays.asList(textMethod.split("-")));
            boolean inside = isInside(parts, fitsShape);
            String method = getMethod(parts);
            String location = getLocation(parts);
            String align = alignment != null ? alignment : getAlignment(parts, inside);
            double offset = getOffset(parts, inside);
            fit = inside && fitsShape;
            out.onNewLine()
                    .add("method:", Data.quote(method))
                    .add(", location:", location)
                    .add(", inside:", inside)
                    .add(", align:", Data.quote(align))
                    .add(", pad:", padding)
                    .add(", dy:", offset, ",");
        }

        out.onNewLine().add("fit:", fit, ", granularity:", hitDetectGranularity, ",");
        if (textMethod.equals("path") || textMethod.equals("wedge"))
            out.onNewLine().add("path: path,");

        // Write it out as a wrapped function
        out.onNewLine().add("content: function(d) {").indentMore();

        // If we need data, guard against not having any
        if (needsData(items))
            out.onNewLine().add("return d.row == null ? null : ");
        else
            out.onNewLine().add("return ");

        writeContent(items, forTooltip);
        out.indentLess().onNewLine().add("}");

        out.indentLess().onNewLine().add("}").endStatement();

    }

    public int estimateLabelLength() {
        int size = 0;
        for (Param p : vis.itemsLabel) {
            if (p.isField()) {
                Field f = data.field(p.asField());
                if (f.isDate()) size += 8;
                else if (f.preferCategorical()) size += maxLength(f.categories()) +1;
                else size += 6;
            } else {
                // Text
                size += p.asString().length() + 1;
            }
        }
        return size;
    }

    private int maxLength(Object[] categories) {
        int max = 0;
        for (Object o : categories) max = Math.max(max, o.toString().length());
        return max;
    }

    private boolean needsData(List<Param> items) {
        for (Param p : prettify(items, false))
            if (p.isField()) return true;
        return false;
    }

    // How to offset the text so it fits correctly
    private double getOffset(HashSet<String> parts, boolean inside) {
        if (parts.contains("top")) return inside ? 0.7 : -0.25;
        if (parts.contains("bottom")) return inside ? -0.25 : 0.7;
        return 0.3;

    }

    // Gets the text alignment based on where to draw relative to the shape
    private String getAlignment(HashSet<String> parts, boolean inside) {
        if (parts.contains("left")) return inside ? "start" : "end";
        if (parts.contains("right")) return inside ? "end" : "start";
        return "middle";
    }

    private boolean isInside(HashSet<String> parts, boolean fitsShape) {
        // The user request  is checked first. Then, if asked to fit, we assume inside
        if (parts.contains("inside")) return true;
        else if (parts.contains("outside")) return false;
        else return fitsShape;
    }

    // Returns the important function that defiens how we will find the shape
    private String getMethod(HashSet<String> parts) {
        for (String s : parts)
            if (s.equals("path") || s.equals("wedge") || s.equals("area") || s.equals("poly") || s.equals("geo"))
                return s;
        return "box";
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

    private List<Param> prettify(List<Param> items, boolean longForm) {

        // If we have nothing but field names, and at least two, we add separators
        if (items.size() < 2) return items;    // One item does not get prettified

        ArrayList<Param> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Param p = items.get(i);
            if (!p.isField()) return items;            // Any non-field and we do not prettify
            Field f = data.field(p.asField());
            if (i > 0) result.add(Param.makeString(longForm ? "<br/>" : ", "));
            if (longForm)
                result.add(Param.makeString("<span class=\"title\">" + f.label + ": </span>"));
            result.add(p);
        }
        return result;
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
                Field f = data.field(p.asField());
                if (forTooltip) out.add("'<span class=\"field\">' + ");
                if (p.hasModifiers()) out.add("BrunelD3.shorten(");
                out.add("data." + D3Util.baseFieldID(f) + "_f(d)");
                if (p.hasModifiers()) out.add(",", (int) p.firstModifier().asDouble(), ")");
                if (forTooltip) out.add(" + '</span>'");
            } else {
                String o = p.asString();
                if (forTooltip) o = o.replaceAll("\\\\n", "&#10;"); // Add a new line character
                out.add(Data.quote(o));
            }
            first = false;
        }
    }

    /* Call to add labels for internal nodes of trees and treemaps */
    public void addTreeInternalLabels() {
        out.add("diagramLabels.attr('class', 'axis diagram treemap hierarchy')").endStatement()
                .add("var treeLabeling = { method:'inner-left', fit:true, dy:0.83,")
                .indentMore()
                .onNewLine().add("content:  function(d) { return d.data.innerNodeName }, align:'start', ")
                .onNewLine().add("cssClass: function(d) { return 'axis label L' + d.depth + ' H' + d.height }, ")
                .onNewLine().add("where :   function(box) { return {'x': box.x + 2, 'y': box.y, 'box': box} }")
                .indentLess().onNewLine().add("}").endStatement();
        out.add("BrunelD3.label(merged.filter(function(d) {return d.height}), diagramLabels, treeLabeling, transitionMillis, geom)").endStatement();
    }

}
