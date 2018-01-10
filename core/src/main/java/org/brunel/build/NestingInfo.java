package org.brunel.build;

import org.brunel.model.VisElement;
import org.brunel.model.VisItem;
import org.brunel.model.VisTypes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keep information about chart nesting
 */
class NestingInfo {
  private final Map<VisElement, VisElement> nestsWithin;          // identifies outer elements for each inner one
  private final Map<VisElement, Integer> chartIndices;            // tags each element with its chart index

  public NestingInfo(VisItem top) {
    nestsWithin = new LinkedHashMap<>();
    chartIndices = new LinkedHashMap<>();
    identifyItems(top, chartIndices, 0);
  }

  /**
   * Return true if this element is nested with another
   *
   * @param element element to consider
   * @return true if nested
   */
  public boolean isNested(VisElement element) {
    return nestsWithin.containsKey(element);
  }

  /**
   * All the nested elements
   *
   * @return a possibly empty list
   */
  public Collection<VisElement> nestedElements() {
    return nestsWithin.keySet();
  }

  /**
   * Returns the index of the chart that is nested within this element
   *
   * @param outer target outer element
   * @return index (throws exception if this is not an outer element)
   */
  public int indexOfChartNestedWithin(VisElement outer) {
    for (Map.Entry<VisElement, VisElement> e : nestsWithin.entrySet())
      if (e.getValue() == outer) return chartIndices.get(e.getKey());
    throw new IllegalArgumentException("Element was not the outer element of a nest");
  }

  /**
   * Test if this is an 'outer' element that contains another element
   *
   * @param element element to examine
   * @return true if it is the outer member of a nest composition
   */
  public boolean nestsOther(VisElement element) {
    return nestsWithin.values().contains(element);
  }

  private void identifyItems(VisItem item, Map<VisElement, Integer> map, int nextChartIndex) {
    VisTypes.Composition method = item.compositionMethod();
    VisItem[] children = item.children();
    if (method == VisTypes.Composition.single) {
      map.put(item.getSingle(), nextChartIndex);
    } else if (method == VisTypes.Composition.tile) {
      // Each item starts a new chart
      for (VisItem child : children)
        identifyItems(child, map, nextChartIndex++);
    } else if (method == VisTypes.Composition.overlay) {
      // Each item starts a new element
      for (VisItem child : children)
        identifyItems(child, map, nextChartIndex);
    } else if (method == VisTypes.Composition.nested || method == VisTypes.Composition.inside) {
      // First item is a new element within the same chart
      identifyItems(children[0], map, nextChartIndex);
      // Second element is a new chart
      identifyItems(children[1], map, ++nextChartIndex);
      // Record the nesting of the second element within the first
      nestsWithin.put((VisElement) children[1], (VisElement) children[0]);
    } else {
      throw new IllegalStateException("Unknown composition method: " + method);
    }
  }

}
