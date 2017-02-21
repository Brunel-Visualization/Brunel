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

package org.brunel.build;

import org.brunel.action.Action;
import org.brunel.build.data.DataBuilder;
import org.brunel.build.data.DatasetBuilder;
import org.brunel.build.info.ElementStructure;
import org.brunel.data.Data;
import org.brunel.model.VisSingle;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Test the ability to retrieve built data
 */
public class TestMakeKeyFields {

    @Test
    public void testKeysReturned() {

        // A defined key wins
        assertEquals("Income", getElementKeys("x(region) key(income)"));
        assertEquals("Region, Income", getElementKeys("x(region) key(region, income)"));

        // If we only have an x-axis, that will win unless it is numeric
        assertEquals("Region", getElementKeys("x(region) y(population) mean(population)"));
        assertEquals("Date", getElementKeys("x(date) y(population) mean(population)"));
        assertEquals("#row", getElementKeys("x(income) y(population) mean(population)"));
        assertEquals("Income", getElementKeys("x(income) y(population) bin(income) mean(population)"));
        assertEquals("Region", getElementKeys("line x(Summer) y(Density) color(region) size(#selection)"));


        // Trickier case -- should grab the aesthetic
        assertEquals("Region", getElementKeys("x(Summer) y(Winter) color(region) filter(winter) mean(summer, winter)"));
    }

    private String getElementKeys(String commands) {
        String command = "data('sample:US States.csv') " + commands;
        VisSingle vis = Action.parse(command).apply().getSingle().makeCanonical();
        ElementStructure structure = new ElementStructure(null, 0, vis, DatasetBuilder.getTransformedData(vis), null);
        DataBuilder builder = new DataBuilder(structure, null, 0);
        return Data.join(builder.makeKeyFields());
    }

}
