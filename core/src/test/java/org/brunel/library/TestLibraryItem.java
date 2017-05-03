package org.brunel.library;

import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Not thorough.
 */
public class TestLibraryItem {

	@Test
	public void testCombos() {
		List<int[]> list = LibraryItem.makeCombinations(4, 3);
		assertEquals(24, list.size());

		Iterator<int[]> it = list.iterator();
		assertTrue(Arrays.equals(new int[]{0, 1, 2,}, it.next()));
		assertTrue(Arrays.equals(new int[]{0, 1, 3,}, it.next()));
		assertTrue(Arrays.equals(new int[]{0, 2, 1,}, it.next()));
		assertTrue(Arrays.equals(new int[]{0, 2, 3,}, it.next()));
		assertTrue(Arrays.equals(new int[]{0, 3, 1,}, it.next()));
		assertTrue(Arrays.equals(new int[]{0, 3, 2,}, it.next()));

		assertTrue(Arrays.equals(new int[]{1, 0, 2,}, it.next()));
		assertTrue(Arrays.equals(new int[]{1, 0, 3,}, it.next()));
		assertTrue(Arrays.equals(new int[]{1, 2, 0,}, it.next()));
		assertTrue(Arrays.equals(new int[]{1, 2, 3,}, it.next()));
		assertTrue(Arrays.equals(new int[]{1, 3, 0,}, it.next()));
		assertTrue(Arrays.equals(new int[]{1, 3, 2,}, it.next()));

		assertTrue(Arrays.equals(new int[]{2, 0, 1,}, it.next()));
		assertTrue(Arrays.equals(new int[]{2, 0, 3,}, it.next()));
		assertTrue(Arrays.equals(new int[]{2, 1, 0,}, it.next()));
		assertTrue(Arrays.equals(new int[]{2, 1, 3,}, it.next()));
		assertTrue(Arrays.equals(new int[]{2, 3, 0,}, it.next()));
		assertTrue(Arrays.equals(new int[]{2, 3, 1,}, it.next()));

		assertTrue(Arrays.equals(new int[]{3, 0, 1,}, it.next()));
		assertTrue(Arrays.equals(new int[]{3, 0, 2,}, it.next()));
		assertTrue(Arrays.equals(new int[]{3, 1, 0,}, it.next()));
		assertTrue(Arrays.equals(new int[]{3, 1, 2,}, it.next()));
		assertTrue(Arrays.equals(new int[]{3, 2, 0,}, it.next()));
		assertTrue(Arrays.equals(new int[]{3, 2, 1,}, it.next()));

		assertEquals(60, LibraryItem.makeCombinations(5, 3).size());
		assertEquals(840, LibraryItem.makeCombinations(7, 4).size());


	}
}
