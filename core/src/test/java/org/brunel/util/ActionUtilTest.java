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
package org.brunel.util;

import static org.junit.Assert.*;

import org.brunel.action.Action;
import org.brunel.action.ActionUtil;
import org.brunel.action.Param;
import org.junit.Test;

public class ActionUtilTest {

	@Test
	public void testDataParams() {
		String brunel = "data('a') x(x) y(y) + data('b') x(x) y(y)";
		Action a = Action.parse(brunel);
		Param[] p = ActionUtil.dataParameters(a);
		
		assertEquals(p.length,2);
		assertEquals(p[0].asString(), "a");
		assertEquals(p[1].asString(), "b");
		
	}

}
