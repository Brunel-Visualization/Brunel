package org.brunel.build;

import org.brunel.model.VisElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Keep information about chart nesting
 */
class NestingInfo {
  final List<NestedItem> items = new ArrayList<>();

  public void add(VisElement inner, VisElement outer) {
    items.add(new NestedItem(inner, outer));
  }

  public boolean facetsExist() {
    return !items.isEmpty();
  }

  public boolean isNested(VisElement element) {
    for (NestedItem item : items)
      if (item.inner == element) return true;
    return false;
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
