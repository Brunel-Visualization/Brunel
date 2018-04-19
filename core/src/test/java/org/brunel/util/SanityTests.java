package org.brunel.util;

import java.util.Locale;

/**
 * These are tests that just check that Java is doing what we expect
 */
public class SanityTests {

  public static void main(String[] args) {
    Locale.setDefault(Locale.GERMAN);
    System.out.println(new Double(100.222));
    System.out.println(String.format("%f",100.222));
    System.out.println(Double.parseDouble("2.1e12"));
  }

}
