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

package org.brunel.color;

import java.awt.*;
import org.brunel.data.Field;

/**
 * How to map a field to a color
 */
public class ColorMapping {
  public final Object[] values;
  public final String[] colors;

  public ColorMapping(Object[] values, String[] colors) {
    this.values = values;
    this.colors = colors;
  }

  public ColorMapping fitColorsToCategories(Field f) {
    Object[] newValues = f.categories();
    int n = newValues.length;
    String[] newColors = new String[n];
    for (int i = 0; i < n; i++) {
      newColors[i] = interpolateMapping(i / (n - 1.0));
    }
    return new ColorMapping(newValues, newColors);
  }

  public ColorMapping reduceColorsIfTooMany() {
    // If we have enough values, no work is needed
    int nValues = values.length;
    if (nValues >= colors.length) {
      return this;
    }

    if (nValues < 2) {
      // Deal with degenerate mapping
      return new ColorMapping(values, new String[]{colors[colors.length - 1]});
    }

    String[] newColors = new String[nValues];
    for (int i = 0; i < newColors.length; i++) {
      newColors[i] = colors[i * (colors.length - 1) / (newColors.length - 1)];
    }

    return new ColorMapping(values, newColors);

  }

  private String interpolateMapping(double v) {
    int n = colors.length - 1;
    int a = (int) Math.floor(v * n);
    int b = (int) Math.ceil(v * n);
    double r = v * n - a;
    return Palette.mid(colors[a], colors[b], r);
  }

  public ColorMapping mute(int muted) {
    float amount = (float) Math.pow(0.7, muted);
    String[] newColors = new String[colors.length];
    for (int i = 0; i < colors.length; i++) {
      newColors[i] = mute(colors[i], amount);
    }
    return new ColorMapping(values, newColors);
  }

  private String mute(String color, float r) {
    Color base = Color.decode(color);
    float[] hsv = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), new float[3]);
    Color a = Color.getHSBColor(hsv[0], r * hsv[1], (1 - r) + r * hsv[2]);
    return String.format("#%02X%02X%02X", a.getRed(), a.getGreen(), a.getBlue());
  }

}
