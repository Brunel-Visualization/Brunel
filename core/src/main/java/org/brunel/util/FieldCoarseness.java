
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

package org.brunel.util;

import org.brunel.data.Field;

import java.util.Arrays;
import java.util.Comparator;

 class FieldCoarseness implements Comparator<Field> {

    public static void sort(Field[] items) {
        Arrays.sort(items, new FieldCoarseness());
    }

    public int compare(Field o1, Field o2) {
        return categories(o1) - categories(o2);
    }

    private int categories(Field f) {
        if (f.preferCategorical())
            return f.categories().length;
        if (f.hasProperty("numeric")) {
            Double g = f.getNumericProperty("granularity");
            if (g != null && g > 0) return (int) ((f.max() - f.min()) / g + 1);
            return 1000;
        }
        return 10000;
    }

}
