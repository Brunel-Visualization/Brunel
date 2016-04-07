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
import org.brunel.data.Fields;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestRepresentations {

    @Test
    public void testFieldRepresentations() {
        Field basic = Fields.makeIndexingField("basic", null, 1);
        Field count = Fields.makeIndexingField("#count", null, 1);

        assertEquals("basic", new DimensionField(basic, null).label());
        assertEquals("basic", new DimensionField(basic, "bob").label());
        assertEquals("Count", new MeasureField(count, null, "count").label());
        assertEquals("Count", new MeasureField(basic, "cal", "count").label());
        assertEquals("Percent", new MeasureField(count, null, "percent").label());
        assertEquals("Percent(basic)", new MeasureField(basic, "cal", "percent").label());
        assertEquals("Mean(basic)", new MeasureField(basic, null, "mean").label());
        assertEquals("Mean(basic)", new MeasureField(basic, "cal", "mean").label());
    }
}
