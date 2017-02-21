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

package org.brunel.action;

import org.brunel.build.controls.Controls;
import org.brunel.build.controls.FilterControl;
import org.brunel.build.VisualizationBuilder;
import org.brunel.data.CannedData;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.brunel.model.VisItem;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ControlsTest {

    private final Dataset bank = Dataset.make(CSV.read(CannedData.bank));

    @Test
    public void testCategoricalFilter() {
        VisualizationBuilder builder = VisualizationBuilder.make();
        Controls controls = getControls(bank, "x(gender) y(salary) filter(gender)", builder);
        assertTrue(controls.filters.size() > 0);

        List<FilterControl> filters = controls.filters;
        assertTrue(filters.size() == 1);

        FilterControl genderFilter = filters.get(0);

        assertTrue(genderFilter.categories != null);
        assertEquals(genderFilter.label, "Gender");

    }

    @Test
    public void testCategoricalFilterDefaults() {
        VisualizationBuilder builder = VisualizationBuilder.make();
        Controls controls = getControls(bank, "x(gender) y(salary) filter(gender:[male,Female])", builder);
        assertTrue(controls.filters.size() > 0);

        List<FilterControl> filters = controls.filters;
        assertTrue(filters.size() == 1);

        FilterControl genderFilter = filters.get(0);

        assertTrue(genderFilter.categories != null);
        assertEquals(genderFilter.label, "Gender");
        String[] expected = new String[] {"Male", "Female"};
        assertTrue(Arrays.equals(expected, genderFilter.selectedCategories));

        controls = getControls(bank, "x(gender) y(salary) filter(gender:Male)", builder);
        assertTrue(Arrays.equals(new String[] {"Male"}, controls.filters.get(0).selectedCategories));


    }

    @Test
    public void testContinuousFilterDefaults() {
        VisualizationBuilder builder = VisualizationBuilder.make();
        Controls controls = getControls(bank, "x(gender) y(salary) filter(salary:18000-20000)", builder);
        assertTrue(controls.filters.size() > 0);

        List<FilterControl> filters = controls.filters;
        assertTrue(filters.size() == 1);

        FilterControl salaryFilter = filters.get(0);

        assertTrue(salaryFilter.categories == null);
        assertEquals(salaryFilter.label, "Salary");
        assertEquals(salaryFilter.lowValue, new Double(18000.0));
        assertEquals(salaryFilter.highValue, new Double(20000.0));

        controls = getControls(bank, "x(gender) y(salary) filter(salary:18000)", builder);
        assertEquals(controls.filters.get(0).lowValue, new Double(18000));

        //Clip low at datamin
        controls = getControls(bank, "x(gender) y(salary) filter(salary:5)", builder);
        assertEquals(controls.filters.get(0).lowValue, new Double(16950));


    }

    private static Controls getControls(Dataset data, String actionText, VisualizationBuilder builder) {
        Action action = Action.parse(actionText);
        VisItem item = action.apply(data);
        builder.build(item, 100, 100);
        return builder.getControls();
    }

    @Test
    public void testContinuousFilter() {
        VisualizationBuilder builder = VisualizationBuilder.make();
        Controls controls = getControls(bank, "x(gender) y(salary) filter(salary)", builder);
        assertTrue(controls.filters.size() > 0);

        List<FilterControl> filters = controls.filters;
        assertTrue(filters.size() == 1);
        FilterControl salaryFilter = filters.get(0);

        assertTrue(salaryFilter.min != null);
        assertEquals(salaryFilter.label, "Salary");

    }

    @Test
    public void testNoFilter() {

        //Builder doesn't matter for controls
        VisualizationBuilder builder = VisualizationBuilder.make();
        Controls controls = getControls(bank, "x(gender) y(salary)", builder);
        assertTrue(controls.filters.isEmpty());
    }

}
