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

package org.brunel.build.element;

import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

/**
 * Defines how we will represent a graphic element geometrically
 */
public enum ElementRepresentation {
    area("path", "area"),
    polygon("path", "poly"),
    geoFeature("path", "geo"),
    generalPath("path", "path"),
    wedge("path", "wedge"),
    symbol("path", "right"),
    segment("line", "box"),
    text("text", "box"),
    rect("rect", "box"),
    smallCircle("circle", "right"),
    bigCircle("circle", "box");

    static ElementRepresentation makeForCoordinateElement(VisTypes.Element element, String symbol, VisSingle vis) {
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
        else if ("rect".equals(symbol))
            return rect;
        else
            return smallCircle;
    }

    private final String mark;                   // The graphic element this represents
    private final String defaultTextMethod;

    ElementRepresentation(String mark, String textMethod) {
        this.mark = mark;
        defaultTextMethod = textMethod;
    }

    public String getDefaultTextMethod() {
        return defaultTextMethod;
    }

    public String getMark() {
        return mark;
    }

    public boolean isDrawnAsPath() {
        return mark.equals("path");
    }
}

