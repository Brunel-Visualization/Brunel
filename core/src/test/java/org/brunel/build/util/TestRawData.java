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

package org.brunel.build.util;

import org.brunel.data.Dataset;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Tests for reading raw data
 */
public class TestRawData {

    @Test
    public void testFunnyCharactersInRaw() throws Exception {
        Dataset a = DataCache.get("raw:a,b;1,hello;2,hey there");
        assertEquals(2, a.rowCount());
        assertEquals("hey there", a.field("b").value(1));

        Dataset b = DataCache.get("raw:a,b;1,hello;2,hey%there");
        assertEquals(2, b.rowCount());
        assertEquals("hey%there", b.field("b").value(1));
    }
}
