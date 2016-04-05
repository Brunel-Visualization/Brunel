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
import org.brunel.build.util.ScriptWriter;
import org.brunel.model.VisSingle;

/**
 * This defines shapes ('marks') that will display the data.
 * The shapes are defined by a center point -- point elements
 * This class is used by Element and Diagram builders to create the raw shapes needed.
 */
public class PointBuilder {

    private final ScriptWriter out;

    public PointBuilder(ScriptWriter out) {
        this.out = out;
    }

    public void defineShapeGeometry(VisSingle vis, ElementDetails details) {
        // Must be a point
        if (details.representation == ElementRepresentation.rect)
            defineRect(details);
        else if (details.representation == ElementRepresentation.text)
            defineText(details, vis);
        else if (details.representation == ElementRepresentation.pointLikeCircle
                || details.representation == ElementRepresentation.spaceFillingCircle
        || details.representation == ElementRepresentation.largeCircle)
            defineCircle(details);
        else
            throw new IllegalArgumentException("Cannot define as a point: " + details.representation);

    }

    private void defineText(ElementDetails elementDef, VisSingle vis) {
        // If the center is not defined, this has been placed using a translation transform
        if (elementDef.x.center != null) out.addChained("attr('x'," + elementDef.x.center + ")");
        if (elementDef.y.center != null) out.addChained("attr('y'," + elementDef.y.center + ")");
        out.addChained("attr('dy', '0.35em').text(labeling.content)");
        D3LabelBuilder.addFontSizeAttribute(vis, out);
    }

    private void defineCircle(ElementDetails elementDef) {
        // If the center is not defined, this has been placed using a translation transform
        if (elementDef.x.center != null) out.addChained("attr('cx'," + elementDef.x.center + ")");
        if (elementDef.y.center != null) out.addChained("attr('cy'," + elementDef.y.center + ")");
        out.addChained("attr('r'," + elementDef.overallSize.halved() + ")");
    }


    private void defineRect(ElementDetails elementDef) {
        defineHorizontalExtent(elementDef.x);
        defineVerticalExtent(elementDef.y);
    }

    void defineHorizontalExtent(ElementDimension dimensionDef) {
        GeomAttribute left, width;
        if (dimensionDef.defineUsingExtent()) {
            // Use the left and right values
            left = GeomAttribute.makeFunction("Math.min(x0(d), x1(d))");
            width = GeomAttribute.makeFunction("Math.abs(x1(d) - x0(d))");
        } else if (dimensionDef.defineUsingCenter()) {
            // The width can either be a function or a numeric value
            if (dimensionDef.size.isFunc())
                left = GeomAttribute.makeFunction("x(d) - w(d)/2 ");
            else
                left = GeomAttribute.makeFunction("x(d) - w/2");
            width = GeomAttribute.makeConstant("w");
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

    private void defineVerticalExtent(ElementDimension dimensionDef) {
        GeomAttribute top, height;
        if (dimensionDef.defineUsingExtent()) {
            // Use the left and right values
            top = GeomAttribute.makeFunction("Math.min(y0(d), y1(d))");
            height = GeomAttribute.makeFunction("Math.max(0.0001, Math.abs(y1(d) - y0(d)))");
        } else if (dimensionDef.defineUsingCenter()) {
            // The height can either be a function or a numeric value
            if (dimensionDef.size.isFunc())
                top = GeomAttribute.makeFunction("y(d) - h(d)/2");
            else
                top = GeomAttribute.makeFunction("y(d) - h/2");
            height = GeomAttribute.makeConstant("h");
        } else {
            top = null;
            height = dimensionDef.size;
        }
        out.addChained("attr('y', ", top, ")");
        out.addChained("attr('height', ", height, ")");
    }

}
