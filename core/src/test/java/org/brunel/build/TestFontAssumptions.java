package org.brunel.build;

import org.junit.Test;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.assertTrue;

/**
 * Checks assumptions on how we measure fonts
 */
public class TestFontAssumptions {

	@Test
	public void testFontMeasurements() {
		String[] names = "Foo;Helvetica;Arial;Times;Times New Roman;Courier;Courier New;Verdana;Tahoma;Palatino;Garamond;Georgia;Impact".split(";");
		String[] phrases = "0123456;1.1;hello;MASSES;Once upon a time, in a far away land".split(";");

		FontRenderContext frc = new FontRenderContext(null, true, true);
		List<Double> aspects = new ArrayList<>();

		for (String n : names) {
			for (int i = 12; i < 20; i += 2) {
				Font f = new Font(n, Font.PLAIN, i);
				for (String phrase : phrases) {
					double width = f.getStringBounds(phrase, frc).getWidth();
					double charAspect = width / phrase.length() / i;
					aspects.add(charAspect);
				}
			}
		}
		int N = aspects.size() - 1;
		Collections.sort(aspects);

		Double max = aspects.get(N);
		Double ninetyPercent = aspects.get(N * 9 / 10);

//		System.out.println("MIN = " + aspects.get(0));
//		System.out.println("MAX = " + max);
//		System.out.println("MED = " + aspects.get(N / 2));
//		System.out.println("90% = " + ninetyPercent);

		// Two-thirds is our "generous estimate"; make sure the max is no more than 10% worse than that
		assertTrue(ninetyPercent < 2.0 / 3.0);
		assertTrue(max < 2.0 / 3.0 * 1.1);
	}

}
