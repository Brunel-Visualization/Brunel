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

package org.brunel.build.data;

import org.brunel.action.Action;
import org.brunel.model.VisElement;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Tests the building information
 */
public class TestDataBuilder {

    @Test
    public void testSummarize() {
        assertEquals("",
                getSummarizeCommands("x(region) y(population)"));

        assertEquals("#count=#count:sum; Region=Region:base",
                getSummarizeCommands("x(region) y(#count)"));

        assertEquals("Population=Population:mean; Region=Region",
                getSummarizeCommands("color(region) y(population) mean(population)"));

        assertEquals("Population=Population:mean; Region=Region:base",
                getSummarizeCommands("x(region) y(population) mean(population)"));

        assertEquals("Population=Population:mean; Income=Income:base; Region=Region",
                getSummarizeCommands("x(income) color(region) y(population) mean(population)"));

        assertEquals("Population=Population:mean; Income=Income:mean; Region=Region",
                getSummarizeCommands("x(income) color(region) y(population) mean(population,income)"));
    }

    private String getSummarizeCommands(String brunel) {
        String command = "data('sample:US States.csv') " + brunel;
        VisElement vis = Action.parse(command).apply().getSingle().makeCanonical();
        return new TransformParameterBuilder(vis).makeSummaryCommands();
    }
}
