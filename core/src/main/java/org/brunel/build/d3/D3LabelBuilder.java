/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

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

    /* Call this after shapes have been defined to add the labels */
    public void addLabels(ElementDetails details) {

        String method = details.textMethod;

        // Offset to ensure text does not hit the shape to which it is attached
        String yOffset;
        if (method.equals("top")) yOffset = "-0.3em";
        else if (method.equals("bottom")) yOffset = "0.7em";
        else yOffset = "0.3em";

        // We define the transition tween function just to calculate relative to the color shape, wherever it is
        out.add("var labelGroup = labels.selectAll('text').data(" + details.dataSource + ")").endStatement();

        out.add("labelGroup.enter().append('text').attr('dy', '" + yOffset + "').attr('class', 'label')");
        if (method.equals("left"))
            out.addChained("style('text-anchor', 'end')");
        else if (method.equals("right"))
            out.addChained("style('text-anchor', 'start')");
        else
            out.addChained("style('text-anchor', 'middle')");
        out.endStatement();

        out.add("BrunelD3.tween(labelGroup,transitionMillis, function(d, i) {")
                .indentMore().onNewLine()
                .add("return BrunelD3.makeLabeling(this, selection[0][i], labeling, true)")
                .indentLess().onNewLine().add("})").endStatement();

        out.add("labelGroup.exit().remove()").endStatement();
    }

    public void addFontSizeAttribute(VisSingle vis) {
        if (!vis.fSize.isEmpty()) {
            ModelUtil.Size parts = ModelUtil.getFontSize(vis);
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
        defineLabeling(details.modifyForTooltip(vis.coords == VisTypes.Coordinates.transposed),
                prettify(vis.itemsTooltip, true), true);
        out.add("BrunelD3.addTooltip(selection, tooltipLabeling, geom)").endStatement();
    }

    /* Call to add labels for internal nodes of trees and treemaps */
    public void addTreeInternalLabels(ElementDetails details, String where) {
        out.add("diagramLabels.attr('class', 'axis diagram treemap hierarchy')").endStatement();
        out.add("var treeLabels = diagramLabels.selectAll('text').data(d3Data)").endStatement();

        out.add("treeLabels.enter().append('text')");
        out.addChained("attr('class', function(d) { return 'axis label L' + d.depth })");
        out.addChained("attr('dx', 2)").addChained("attr('dy', '0.85em')").endStatement();

        out.add("var treeLabeling = {method:'box', fit:true, content:function(d){return d.innerNodeName}, ");
        out.indentMore().ln().add("where : function(box) { return", where, "}").indentLess();
        out.add("}").endStatement();

        out.add("BrunelD3.tween(treeLabels,transitionMillis, function(d, i) { return BrunelD3.makeLabeling(this, selection[0][i], treeLabeling, false)})");
        out.endStatement();

        out.add("treeLabels.exit().remove()").endStatement();

    }

    public void defineLabeling(ElementDetails details, List<Param> items, boolean forTooltip) {
        String name = forTooltip ? "tooltipLabeling" : "labeling";
        out.add("var", name, "= {").ln().indentMore();
        String textMethod = details.textMethod;
        out.add("method:", out.quote(textMethod), ", fit:", details.textMustFit, ",");
        if (textMethod.equals("path") || textMethod.equals("wedge"))
            out.add("path: path,");

        // Write it out as a wrapped function
        out.onNewLine().add("content: function(d) {").indentMore();

        out.onNewLine().add("return d.row == null ? null : ");
        writeContent(items, forTooltip);
        out.indentLess().onNewLine().add("}");

        out.indentLess().onNewLine().add("}").endStatement();
    }

    public boolean needed() {
        return vis.itemsLabel.size() > 0 || vis.tElement == VisTypes.Element.text;
    }

    private List<Param> prettify(List<Param> items, boolean longForm) {

        // If we have nothing but field names, and at least two, we add separators
        if (items.size() < 2) return items;    // One item does not get prettified

        ArrayList<Param> result = new ArrayList<Param>();
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
}
