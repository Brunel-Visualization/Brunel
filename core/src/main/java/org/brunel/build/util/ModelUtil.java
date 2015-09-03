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

package org.brunel.build.util;

import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.style.StyleSheet;
import org.brunel.model.style.StyleTarget;

/**
 * Utilities for VisItem and model items
 */
public class ModelUtil {

    /**
     * Determine if the fields are best represented by a categorical (as opposed to numeric) scale
     *
     * @param fields fields to analyze
     * @return true if we should be categorical, false if not.
     */
    public static boolean combinationIsCategorical(Field[] fields) {
        boolean allCanBeNumeric = true;
        boolean allPreferCategorical = true;
        for (Field f : fields) {
            if (f == null) {
                System.out.println("eek");
            }
            if (!f.preferCategorical()) allPreferCategorical = false;
            if (!f.hasProperty("numeric")) allCanBeNumeric = false;
        }

        // If everything wants to be categorical, or some field cannot be, we choose categorical
        return allPreferCategorical || !allCanBeNumeric;
    }

    /**
     * Get the value of a style item
     *
     * @param vis    which visualization to look at styles for
     * @param target the target style to look for
     * @param key    which value to return
     * @return the found value in either the vis styles, or the default styles, or null if not found in either
     */
    public static String getStyle(VisSingle vis, StyleTarget target, String key) {
        String result = vis.styles == null ? null : vis.styles.get(target, key);
        return result == null ? StyleSheet.getBrunelDefault(target, key) : result;
    }

}
