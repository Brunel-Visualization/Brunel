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

/**
 * Defines how we will represent a graphic element geometrically
 */
public enum ElementRepresentation {
    area("path", true),
    polygon("path", true),
    generalPath("path", true),
    wedge("path", true),
    segment("line", false),
    text("text", false),
    rect("rect", false),
    circle("circle", false);

    public final String mark;                   // The graphic element this represents
    public final boolean drawnAsPath;           // If true, represented as a path

    ElementRepresentation(String mark, boolean drawnAsPath) {
        this.mark = mark;
        this.drawnAsPath = drawnAsPath;
    }

}

