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

package org.brunel.match;

import org.brunel.action.Action;
import org.brunel.action.ActionUtil;
import org.brunel.action.Param;
import org.brunel.data.CannedData;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BestMatchTest {

    private final Dataset data = Dataset.make(CSV.read(CannedData.bank));
    private final Dataset data1 = Dataset.make(CSV.read(CannedData.whiskey));

	@Test
	public void testActionLength() {
		Action a = Action.parse("x(gender) y(salary) bar mean(educ) color(jobcat) + x(#row) y(minority)");
		Action b = BestMatch.match(data, data1, a);
		assertEquals(ActionUtil.parameters(a).length, ActionUtil.parameters(b).length);
	}

	@Test
	public void testFieldUniqueness() {
		Action a = Action.parse("x(gender) y(salary) bar mean(educ) color(jobcat) + x(#row) y(minority)");
		Action b = BestMatch.match(data, data1, a);
		assertEquals(ActionUtil.parameters(a).length, ActionUtil.parameters(b).length);

		ArrayList<String> fields = new ArrayList<String>();
		boolean duplicateFound = false;

		for (Param p : ActionUtil.parameters(b)) {
			if (p.isField()) {
				String f = p.asString();
				if (fields.contains(f)) {
					duplicateFound = true;
				}
				fields.add(f);
			}
		}
		assertFalse(duplicateFound);
	}

	@Test
	public void testDualEncoding() {
		Action a = Action.parse("x(gender) y(salary:nominal) bar mean(salary)");
		Action b = BestMatch.match(data, data1, a);

		Param[] parms1 = ActionUtil.parameters(a);
		Param[] parms2 = ActionUtil.parameters(b);
		assertEquals(parms1.length, parms2.length);

		ArrayList<String> fields = new ArrayList<String>();
		boolean dualEncodedFound = false;

		for (int i=0;i < parms1.length; i++) {
			Param p = parms2[i];
			if (p.isField()) {
				String f = p.asString();
				if (fields.contains(f)) {
					dualEncodedFound = true;
				}
				fields.add(f);
			}

			//Modifies should not change for dual encoded fields.
			assertArrayEquals(parms1[i].modifiers(), parms2[i].modifiers());
		}
		assertTrue(dualEncodedFound);


	}

	@Test
	public void testFieldPreferCategorical() {
		Action a = Action.parse("x(gender) y(salary) bar color(minority)");
		Action b = BestMatch.match(data, data1, a);


		Param[] parms1 = ActionUtil.parameters(a);
		Param[] parms2 = ActionUtil.parameters(b);
		assertEquals(parms1.length, parms2.length);

		for (int i=0; i < parms1.length; i++) {
			Param p1 = parms1[i];
			Param p2 = parms2[i];
			if (p1.isField()) {
				assertTrue (p2.isField());
				Field f1 = data.field(p1.asString(), true);
				Field f2 = data1.field(p2.asString(), true);
				assertEquals(f1.preferCategorical(), f2.preferCategorical());

			}
		}

	}

	@Test
	public void testSyntheticFieldsPreserved() {
		Action a = Action.parse("x(gender) y(#count) bar mean(#selection) color(jobcat) + x(#row) y(minority)");
		Action b = BestMatch.match(data, data1, a);

		Param[] parms1 = ActionUtil.parameters(a);
		Param[] parms2 = ActionUtil.parameters(b);
		assertEquals(parms1.length, parms2.length);

		for (int i=0; i < parms1.length; i++) {
			Param p1 = parms1[i];
			Param p2 = parms2[i];
			if (p1.isField()) {
				assertTrue (p2.isField());
				Field f1 = data.field(p1.asString(),true);
				Field f2 = data1.field(p2.asString(),true);
				assertEquals(f1.isSynthetic(), f2.isSynthetic());

			}
		}

	}

}
