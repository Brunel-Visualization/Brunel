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

import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

/**
 * Defines how we will represent a graphic element geometrically
 */
 public enum ElementRepresentation {
    pointLikeCircle("circle", "right", false),          // A circle with text drawn to the right
    spaceFillingCircle("circle", "box", true),          // A circle with text clipped to a box
    largeCircle("circle", "center", false),             // A circle with text centered, but not clipped

    segment("line", "box", false),                      // A line segment. Text is drawn centrally, but not clipped
    curvedPath("path", "box", false),                   // A line segment displayed as a curved path
    text("text", "box", true),                          // Text -- should not be labelled usually
    rect("rect", "box", true),                          // A rectangle with  text clipped to a box

    generalPath("path", "path", false),                 // A usually unfilled path, text drawn on that path
    polygon("path", "poly", false),                     // A usually filled polygon, text drawn at central point
    area("path", "area", true),                         // A usually filled path with text drawn in the middle
    geoFeature("path", "geo", false),                   // A usually filled polygon, with predefined label location
    symbol("path", "right", false),                     // A symbol drawn as path, but behaves like a small circle
    wedge("path", "wedge", false);                      // A pie chart wedge gets a special labeling location

    static ElementRepresentation makeForCoordinateElement(VisTypes.Element element, String symbolName, VisSingle vis) {
        VisTypes.Coordinates coords = vis.coords;
        if (element == VisTypes.Element.bar && coords == VisTypes.Coordinates.polar)
            return wedge;
        else if (element == VisTypes.Element.area)
            return area;
        else if (element == VisTypes.Element.line)
            return generalPath;
        else if (element == VisTypes.Element.path)
            return generalPath;
        else if (element == VisTypes.Element.polygon)
            return polygon;
        else if (element == VisTypes.Element.edge)
            return segment;
        else if (element == VisTypes.Element.text)
            return text;
        else if (element == VisTypes.Element.bar)
            return rect;
        else if ("rect".equals(symbolName))
            return rect;
        else if (symbolName == null || symbolName.equals("circle"))
            return pointLikeCircle;
        else
            return symbol;
    }

    private final String mark;                   // The graphic element this represents
    private final String defaultTextMethod;
    private final boolean textFitsShape;

    ElementRepresentation(String mark, String textMethod, boolean textFitsShape) {
        this.mark = mark;
        defaultTextMethod = textMethod;
        this.textFitsShape = textFitsShape;
    }

    public String getDefaultTextMethod() {
        return defaultTextMethod;
    }

    public String getMark() {
        return mark;
    }

    public String getTooltipTextMethod() {
        if (this == area) return "poly";
        if (isDrawnAsPath() || this == segment) return getDefaultTextMethod();
        return "top";
    }

    public boolean isDrawnAsPath() {
        return mark.equals("path") && this != symbol;
    }

    public boolean textFitsShape() {
        return textFitsShape;
    }
}

