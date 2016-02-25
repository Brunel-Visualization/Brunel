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

package org.brunel.data.summary;

import org.brunel.data.Field;
import org.brunel.data.modify.Transform;
import org.brunel.translator.JSTranslation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MeasureField extends DimensionField {

    public final String method;                                // Defines the function
    public String option;                                               // Option for it
    public final Map<String, Fit> fits = new HashMap<String, Fit>();    // Per-group fits

    public MeasureField(Field field, String rename, String measureFunction) {
        super(field, rename == null && field == null ? measureFunction : rename);

        // If we are asked for the mean of a field that cannot be numeric, we return the mode instead
        if (field != null && measureFunction.equals("mean") && !field.isNumeric())
            this.method = "mode";
        else
            this.method = measureFunction;
    }

    /**
     * Find the fit function for the given group
     *
     * @param groupFields fields used to define g
     * @param index       the row to find the group fit for
     * @return defined fit (or null if none yet created)
     */
    public Fit getFit(ArrayList<Field> groupFields, int index) {
        return this.fits.get(Transform.makeKey(groupFields, index));
    }

    /**
     * Define the fit function for the given group
     *
     * @param groupFields fields used to define g
     * @param index       the row to find the group fit for
     * @param fit         the fit to use for this group
     */
    public void setFit(ArrayList<Field> groupFields, int index, Fit fit) {
        this.fits.put(Transform.makeKey(groupFields, index), fit);
    }

    public boolean isPercent() {
        return method.equals("percent");
    }

    @JSTranslation(ignore = true)
    public String toString() {
        if (field != null && field.name.equals(rename)) return label();
        return label() + "[->" + rename + "]";
    }

    public String label() {
        if (method.equals("list")) return field.label;
        if (method.equals("count")) return "Count";
        if (field == null|| field.name.equals("#count")) {
            if (method.equals("sum")) return field.label;
            if (method.equals("percent")) return "Percent";
        }
        String a = method.substring(0, 1).toUpperCase();
        String b = method.substring(1);
        return a + b + "(" + field.label + ")";
    }

}
