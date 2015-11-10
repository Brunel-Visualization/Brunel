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
 * StyleSelector matches against a CSS-like style key. It supports the following CSS syntax.
 *
 * *               anything
 * .class          class
 * element         element like text, rect, circle, path
 * e1, e2          element e1 and e2
 * e1 e2           element e1 inside and element e1 [descendent]
 *
 * Note that the comma operator is taken care of outside of this class
 */
public abstract class StyleSelector implements Comparable<StyleSelector> {

    final int specificity;

    StyleSelector(int specificity) {
        this.specificity = specificity;
    }

    public int compareTo(StyleSelector o) {
        return o.specificity - specificity;
    }


    public StyleSelector containedIn(StyleSelector[] sel) {
        StyleSelector[] all = new StyleSelector[sel.length+1];
        System.arraycopy(sel, 0, all, 0, sel.length);
        all[sel.length] = this;
        return new MultiComponentSelector(all);
    }

    public abstract String debug();

    public abstract boolean match(StyleTarget target);

    public abstract StyleSelector replaceClass(String target, String replace);
}
