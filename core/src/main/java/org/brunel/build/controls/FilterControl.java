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

package org.brunel.build.controls;

import org.brunel.data.Dataset;
import org.brunel.data.Field;

/**
 * Java object containing the state of the filters
 *
 * @author drope, gwills
 */
public class FilterControl {

    /**
     * Given a field, make the information for a valid filter for it
     *
     * @param data    base data to filter
     * @param fieldID identifier of the field to filter using
     * @return built Filter description
     */
    public static FilterControl makeForField(Dataset data, String fieldID) {
        Field field = data.field(fieldID);
        if (field.preferCategorical())
            return new FilterControl(data.name(), fieldID, field.label, field.categories(), null, null);
        else
            return new FilterControl(data.name(), fieldID, field.label, null, field.min(), field.max());

    }

    public final String data;
    public final String id;
    public final String label;
    public final Object[] categories;
    public final Double min;
    public final Double max;

    private FilterControl(String data, String id, String label, Object[] categories, Double min, Double max) {
        this.data = data;
        this.id = id;
        this.label = label;
        this.categories = categories;
        this.min = min;
        this.max = max;
    }
}
