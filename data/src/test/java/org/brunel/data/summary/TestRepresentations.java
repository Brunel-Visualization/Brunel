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

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestRepresentations {

    @Test
    public void testFieldRepresentations() {
        Field basic = Data.makeIndexingField("basic", null, 1);

        assertEquals("basic", new DimensionField(basic, null).toString());
        assertEquals("basic[->bob]", new DimensionField(basic, "bob").toString());
        assertEquals("Count()[->count]", new MeasureField(null, null, "count").toString());
        assertEquals("Count()[->cal]", new MeasureField(null, "cal", "count").toString());
        assertEquals("Mean(basic)", new MeasureField(basic, null, "mean").toString());
        assertEquals("Mean(basic)[->cal]", new MeasureField(basic, "cal", "mean").toString());
    }
}
