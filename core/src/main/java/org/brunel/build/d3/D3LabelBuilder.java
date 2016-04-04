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
import org.brunel.build.element.ElementDetails;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ModelUtil.Size;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

import java.util.ArrayList;
import java.util.Collections;
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
            out.add("BrunelD3.label(selection, labels, labeling, 0)").endStatement();
            return;
        }

        // Text elements define labeling as the main item; they do not need labels attached, which is what this does
        if (vis.tElement != Element.text )
            out.add("BrunelD3.label(selection, labels, labeling, transitionMillis)").endStatement();
    }

    public static void addFontSizeAttribute(VisSingle vis, ScriptWriter out) {
        if (!vis.fSize.isEmpty()) {
            Size parts = ModelUtil.getFontSize(vis);
            if (parts == null) {
                out.addChained("style('font-size', function(d) { return (100*size(d)) + '%' })");
            } else {
                out.addChained("style('font-size', function(d) { return (", parts.value(), "* size(d)) +'" + parts.suffix() + "' })");
            }
        }
    }

    public void addTooltips(ElementDetails details) {
        if (vis.itemsTooltip.isEmpty()) return;
        out.onNewLine().ln();
        defineLabeling(details.deriveTooltipDetails(),
                prettify(vis.itemsTooltip, true), true);
        out.add("BrunelD3.addTooltip(selection, tooltipLabeling, geom)").endStatement();
    }

    public void defineLabeling(ElementDetails details, List<Param> items, boolean forTooltip) {
        if (vis.tElement != Element.text && items.isEmpty()) return;
        String name = forTooltip ? "tooltipLabeling" : "labeling";
        out.add("var", name, "= {").ln().indentMore();
        String textMethod = details.getTextMethod();
        if (textMethod.equals("geo")) {
            // We define a function to extract the coordinates from the geo, and project them
            String func = "function(box,text,d) {var p = projection([d.geo_properties.c, d.geo_properties.d]); return {box:box, x:p[0], y:p[1]}}";
            out.onNewLine().add("where:", func, ",");
        } else {
            out.onNewLine().add("method:", out.quote(textMethod), ", ");
        }
        out.onNewLine().add("fit:", details.textFitsShape(), ",");
        if (textMethod.equals("path") || textMethod.equals("wedge"))
            out.onNewLine().add("path: path,");

        // Write it out as a wrapped function
        out.onNewLine().add("content: function(d) {").indentMore();

        out.onNewLine().add("return d.row == null ? null : ");
        writeContent(items, forTooltip);
        out.indentLess().onNewLine().add("}");

        out.indentLess().onNewLine().add("}").endStatement();

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

    private void writeContent(List<Param> items, boolean forTooltip) {
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
        out.add("diagramLabels.attr('class', 'axis diagram treemap hierarchy')").endStatement();
        out.add("var treeLabeling = { method:'inner-left', fit:true,");
        out.indentMore().onNewLine().add("content:  function(d) { return d.innerNodeName }, ");
        out.indentMore().onNewLine().add("cssClass: function(d) { return 'axis label L' + d.depth }, ");
        out.indentMore().onNewLine().add("where :   function(box) { return {'x': box.x + 2, 'y': box.y, 'box': box} }").indentLess();
        out.add("}").endStatement();
        out.add("BrunelD3.label(selection, diagramLabels, treeLabeling, transitionMillis)").endStatement();
    }

}
