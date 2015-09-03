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

package org.brunel.data.auto;

import org.brunel.data.Field;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This can either be numeric or categorical, and is used to determine what values a field covers
 */
class Domain {
    private final Set<Object> nominalValues = new HashSet<Object>();
    private Double min = null;
    private Double max = null;

    public Domain add(Field field) {
        // Ignore empty fields
        if (field == null) return this;
        // Add either to categories or ranges
        if (field.preferCategorical())
            Collections.addAll(nominalValues, field.categories());
        else {
            if (min == null) {
                min = field.min();
                max = field.max();
            } else {
                min = Math.min(field.min(), min);
                max = Math.max(field.min(), max);
            }
        }
        return this;
    }

    /**
     * The merged unwasted space is the space in the merged domain that is used by either of the
     * domains passed into the merge.
     */
    public double mergedUnwastedSpace(Domain other) {
        // If they do not org.brunel.app.match as numeric values, total failure
        if ((min == null) != (other.min == null)) return 0;

        // If they do not org.brunel.app.match as categorical values, total failure
        if ((nominalValues.size() == 0) != (other.nominalValues.size() == 0)) return 0;

        double similarity = 1.0;

        // Compare numeric ranges -- the mergedUnwastedSpace is the fraction the biggest one is of the unioned size
        if (min != null) {
            double unionMin = Math.min(min, other.min);
            double unionMax = Math.max(max, other.max);
            if (unionMax == unionMin) {
                // test for degenerate ranges
                similarity *= (min.equals(other.min) ? 1.0 : 0.0);
            } else
                similarity *= Math.max(max - min, other.max - other.min) / (unionMax - unionMin);
        }

        // Compare numeric ranges -- the mergedUnwastedSpace is the fraction the larger set is of the unioned size
        if (nominalValues.size() > 0) {
            Set<Object> combined = new HashSet<Object>();
            combined.addAll(nominalValues);
            combined.addAll(other.nominalValues);
            similarity *= Math.max(nominalValues.size(), other.nominalValues.size()) / (double) combined.size();
        }

        return similarity;
    }
}
