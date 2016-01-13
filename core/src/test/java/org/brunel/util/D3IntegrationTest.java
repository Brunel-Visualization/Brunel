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

package org.brunel.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class D3IntegrationTest {

	private static final String csv = "A,B,C\n1,2,3\n3,4,5";
	
	@Test
	public void testCSV() {
		D3Integration.cacheData("data", csv);
		String action = "data('data') x(A) y(B)";
		String json = D3Integration.createBrunelJSON(null, action, 100, 100, "visid", null);
		assertNotNull(json);
	}

}
