package org.brunel.build;

import org.brunel.model.VisElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keep information about chart nesting
 */
class NestingInfo {
  private final List<NestedItem> items = new ArrayList<>();
  private final Map<VisElement, int[]> elementIndices;              // tags each element with its [chart,element] index

  public NestingInfo(Map<VisElement, int[]> elementIndices) {
    this.elementIndices = elementIndices;
  }

  public void add(VisElement inner, VisElement outer) {
    items.add(new NestedItem(inner, outer));
  }

  public boolean facetsExist() {
    return !items.isEmpty();
  }

  public List<NestedItem> items() {
    return items;
  }

  public int chartIndexOf(VisElement e) {
    return elementIndices.get(e)[0];
  }

  public int elementIndexOf(VisElement e) {
    return elementIndices.get(e)[1];
  }

  public boolean isNested(VisElement element) {
    for (NestedItem item : items)
      if (item.inner == element) return true;
    return false;
  }

  public int nestedChartIndex(VisElement outer) {
    for (NestedItem item : items)
      if (item.outer == outer) return chartIndexOf(item.inner);
    throw new IllegalArgumentException("Element was not the outer element of a nest");
  }

  public boolean nestsOther(VisElement element) {
    for (NestedItem item : items)
      if (item.outer == element) return true;
    return false;
  }

  static class NestedItem {
    final VisElement inner;
    final VisElement outer;

    NestedItem(VisElement inner, VisElement outer) {
      this.inner = inner;
      this.outer = outer;
    }
  }
}
