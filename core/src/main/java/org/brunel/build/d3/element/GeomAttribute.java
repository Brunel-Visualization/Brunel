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

/**
 * Defines an attribute we will use in D3. Might be a constant or a function.
 * If it is a function, it takes the standard parameter 'd', so examples are
 * "12.0", "geom.inner_width" -- constants
 * "size(d)",  "12.0 * (d ? size(d) : 1)" -- functions
 */
public class GeomAttribute {

    private final String def;
    private final boolean func;

    private GeomAttribute(String def, boolean func) {
        this.def = def;
        this.func = func;
    }

    public static GeomAttribute makeFunction(String def) {
        return new GeomAttribute(def, true);
    }

    public static GeomAttribute makeConstant(String def) {
        return new GeomAttribute(def, false);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeomAttribute that = (GeomAttribute) o;

        return func == that.func && def.equals(that.def);

    }

    public int hashCode() {
        int result = def.hashCode();
        result = 31 * result + (func ? 1 : 0);
        return result;
    }

    public String definition() {
        return def;
    }

    public boolean isFunc() {
        return func;
    }

    public String toString() {
        return func ? "function(d) { return " + def + "}" : def;
    }
}
