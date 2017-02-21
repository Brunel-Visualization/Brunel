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
import org.brunel.build.data.DataTransformations;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Test the ability to retrieve built data
 */
public class TestDataReturned {

    @Test
    public void testGetSimpleData() {
        String command = "data('sample:US States.csv') x(region) y(population)";
        VisSingle vis = Action.parse(command).apply().getSingle().makeCanonical();
        DataTransformations builder = new DataTransformations(vis);
        Dataset d = builder.build();
        assertEquals(50, d.rowCount());
        assertEquals(1.0, d.field("#count").value(0));
    }

    @Test
    public void testGetSummarizedData() {
        String command = "data('sample:US States.csv') x(region) y(population) sum(population)";
        VisSingle vis = Action.parse(command).apply().getSingle().makeCanonical();

        Dataset d = DataTransformations.getTransformedData(vis);
        assertEquals(6, d.rowCount());
        assertEquals(12.0, d.field("#count").value(0));
    }
}
