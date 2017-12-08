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

/**
 * Simple padding structure
 */
public class Padding {
  public int top;
  public int left;
  public int bottom;
  public int right;

  public Padding(int top, int left, int bottom, int right) {
    this.top = top;
    this.left = left;
    this.bottom = bottom;
    this.right = right;
  }

  public int horizontal() {
    return left + right;
  }

  public String topModifier() {
    return asModifier(top);
  }

  public String bottomModifier() {
    return asModifier(-bottom);
  }

  public String leftModifier() {
    return asModifier(left);
  }

  public String heightModifier() {
    // We subtract the vertical pad from the height
    return asModifier(-vertical());
  }

  public String widthModifier() {
    // We subtract the horizontal pad from the width
    return asModifier(-horizontal());
  }

  // Return something that looks like an empty string, +x, or -x
  private String asModifier(int v) {
    return v == 0 ? "" : (v < 0 ? "" + v : "+" + v);
  }

  public int vertical() {
    return top + bottom;
  }
}
