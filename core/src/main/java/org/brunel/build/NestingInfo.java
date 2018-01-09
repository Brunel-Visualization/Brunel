package org.brunel.build;

import org.brunel.build.info.ChartStructure;
import org.brunel.model.VisElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Keep information about chart nesting
 */
class NestingInfo {
  final List<NestedItem> items = new ArrayList<>();

  public void add(VisElement inner, VisElement outer, ChartStructure outerStructure) {
    items.add(new NestedItem(inner, outer, outerStructure));
  }

//        visStructure.nesting.add(chartIndex + 1);
//  double[] loc = new ChartLayout(width, height, inner).getLocation(0);
//      new ChartBuilder(visStructure, options, loc, out)
//        .buildNestedInner(chartIndex + 1, outerStructure, inner);


  static class NestedItem {
    final VisElement inner;
    final VisElement outer;
    final ChartStructure outerStructure;

    NestedItem(VisElement inner, VisElement outer, ChartStructure outerStructure) {
      this.inner = inner;
      this.outer = outer;
      this.outerStructure = outerStructure;
    }
  }
}
