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

package org.brunel.data.stats;

import org.brunel.data.Data;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestGranularity {

	@Test
	public void testGranularityEasy() {
		Field f = Fields.makeColumnField("a", "label", new Object[]{3, 5, 6, 12});
		Field g = Data.toNumeric(f);
		assertEquals(1.0, g.numProperty("granularity"), 1e-6);
	}

	@Test
	public void testGranularityOne() {
		Field f = Fields.makeColumnField("a", "label", new Object[]{3, 5, 10, 12});
		Field g = Data.toNumeric(f);
		assertEquals(1.0, g.numProperty("granularity"), 1e-6);
	}

	@Test
	public void testGranularityNone() {
		Field f = Fields.makeColumnField("a", "label", new Object[]{37.4, 38.6, 42.6, 37.75, 37.41});
		Field g = Data.toNumeric(f);
		assertEquals(0.0, g.numProperty("granularity"), 1e-6);
	}

}
