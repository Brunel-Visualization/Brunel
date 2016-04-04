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

package org.brunel.build.d3;

import org.brunel.build.element.ElementDefinition;
import org.brunel.build.element.ElementDefinition.ElementDimensionDefinition;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.element.ElementRepresentation;
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisSingle;

/**
 * This defines shapes ('marks') that will display the data.
 * The shapes are defined by a center point -- point elements
 * This class is used by Element and Diagram builders to create the raw shapes needed.
 */
public class D3PointBuilder {

    private final ScriptWriter out;

    public D3PointBuilder(ScriptWriter out) {
        this.out = out;
    }

    public void defineShapeGeometry(VisSingle vis, ElementDefinition elementDef, ElementDetails details) {
        // Must be a point
        if (details.representation == ElementRepresentation.rect)
            defineRect(elementDef);
        else if (details.representation == ElementRepresentation.text)
            defineText(elementDef, vis);
        else if (details.representation == ElementRepresentation.circle)
            defineCircle(elementDef);
        else
            throw new IllegalArgumentException("Cannot define as a point: " + details.representation);

    }

    private void defineText(ElementDefinition elementDef, VisSingle vis) {
        // If the center is not defined, this has been placed using a translation transform
        if (elementDef.x.center != null) out.addChained("attr('x'," + elementDef.x.center + ")");
        if (elementDef.y.center != null) out.addChained("attr('y'," + elementDef.y.center + ")");
        out.addChained("attr('dy', '0.35em').text(labeling.content)");
        D3LabelBuilder.addFontSizeAttribute(vis, out);
    }


    private void defineCircle(ElementDefinition elementDef) {
        // If the center is not defined, this has been placed using a translation transform
        if (elementDef.x.center != null) out.addChained("attr('cx'," + elementDef.x.center + ")");
        if (elementDef.y.center != null) out.addChained("attr('cy'," + elementDef.y.center + ")");
        out.addChained("attr('r'," + halve(elementDef.overallSize) + ")");
    }

    private String halve(String sizeText) {
        // Put the "/2" factor inside the function if needed
        String body = D3Util.stripFunction(sizeText);
        if (body.equals(sizeText))
            return body + " / 2";
        else
            return "function(d) { return " + body + " / 2 }";
    }

    private void defineRect(ElementDefinition elementDef) {
        defineHorizontalExtent(elementDef.x);
        defineVerticalExtent(elementDef.y);
    }

    void defineHorizontalExtent(ElementDimensionDefinition dimensionDef) {
        String left, width;
        if (dimensionDef.defineUsingExtent()) {
            // Use the left and right values
            left = "function(d) { return Math.min(x0(d), x1(d)) }";
            width = "function(d) { return Math.abs(x1(d) - x0(d)) }";
        } else if (dimensionDef.defineUsingCenter()) {
            // The width can either be a function or a numeric value
            if (dimensionDef.size.startsWith("function"))
                left = "function(d) { return x(d) - w(d)/2 }";
            else
                left = "function(d) { return x(d) - w/2 }";
            width = "w";
        } else {
            left = null;
            width = dimensionDef.size;
        }
        if (left != null) out.addChained("attr('x', ", left, ")");

        // Sadly, browsers are inconsistent in how they handle width. It can be considered either a style or a
        // positional attribute, so we need to specify as both to make all browsers happy
        out.addChained("attr('width', ", width, ")");
        out.addChained("style('width', ", width, ")");
    }

    private void defineVerticalExtent(ElementDimensionDefinition dimensionDef) {
        String top, height;
        if (dimensionDef.defineUsingExtent()) {
            // Use the left and right values
            top = "function(d) { return Math.min(y0(d), y1(d)) }";
            height = "function(d) { return Math.max(0.0001, Math.abs(y1(d) - y0(d))) }";
        } else if (dimensionDef.defineUsingCenter()) {
            // The height can either be a function or a numeric value
            if (dimensionDef.size.startsWith("function"))
                top = "function(d) { return y(d) - h(d)/2 }";
            else
                top = "function(d) { return y(d) - h/2 }";
            height = "h";
        } else {
            top = null;
            height = dimensionDef.size;
        }
        out.addChained("attr('y', ", top, ")");
        out.addChained("attr('height', ", height, ")");
    }

}
