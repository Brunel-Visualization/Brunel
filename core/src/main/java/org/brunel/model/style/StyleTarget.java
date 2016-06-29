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

package org.brunel.model.style;

/**
 * This is something that can be targeted by a style
 */
public class StyleTarget {

    /* Top level style target -- all targets should parent to this */
    private static final StyleTarget STYLE_TOP = new StyleTarget("svg", null, "brunel");

    /* Targets the current element */
    private static final StyleTarget STYLE_ELEMENT = new StyleTarget(null, STYLE_TOP, "currentElement");



    public final String element;            // Type of element (text, label, ...)
    public final String[] classes;          // Classes this belongs to
    public final StyleTarget parent;        // parent class


    public static StyleTarget makeTopLevelTarget(String element, String... classes) {
        return new StyleTarget(element, STYLE_TOP, classes);
    }

    public static StyleTarget makeElementTarget(String element, String... classes) {
        return new StyleTarget(element, STYLE_ELEMENT, classes);
    }

    public static StyleTarget makeTarget(String element, StyleTarget parent, String... classes) {
        return new StyleTarget(element, parent, classes);
    }


    StyleTarget(String element, StyleTarget parent, String... classes) {
        this.element = element;
        this.classes = classes;
        this.parent = parent;
    }

}
