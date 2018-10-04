package org.brunel.build;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.brunel.build.info.ChartStructure;

/**
 * Run through all geometrries to see if they can be nudged into better alignment with each other
 */
public class GeometryAlignment {
  // No more than 20% total reduction to align axes
  private static final double ALIGNMENT_MAX_CHANGE = 0.20;

  // Create a map to keep track of total amount we have reduced a chart size by already
  Map<ChartStructure, int[]> structures = new LinkedHashMap<>();

  public GeometryAlignment(Collection<ChartBuilder> values) {
    // Initialize the map with no reductions
    for (ChartBuilder builder : values) {
      structures.put(builder.structure, new int[]{0, 0});
    }
  }

  public void align() {
    // Loop through all pairs and see if they can be reduced to fit each other
    // We only reduce sizes, never grow them
    for (ChartStructure a : structures.keySet()) {
      for (ChartStructure b : structures.keySet()) {
        alignByReduction(a, b, structures.get(b));
      }
    }
  }

  // Try and reduce 'c' to fit to 'ref', keeping in mind the reduction we have already performed
  private void alignByReduction(ChartStructure ref, ChartStructure c, int[] currentReduction) {
    int[] base = ref.location.innerRectangle();       // T L B R order
    ChartLocation location = c.location;
    int[] trial = location.innerRectangle();          // T L B R order

    alignTop(base, trial, currentReduction, location);
    alignLeft(base, trial, currentReduction, location);
    alignBottom(base, trial, currentReduction, location);
    alignRight(base, trial, currentReduction, location);

  }

  private void alignTop(int[] base, int[] trial, int[] currentReduction, ChartLocation location) {
    // Align left
    int diff = base[0] - trial[0];
    int currentSize = trial[2] - trial[0];
    int originalSize = currentSize + currentReduction[0];

    // We want to reduce the size and it is allowable given our tolerances
    if (diff > 0 && (diff + currentReduction[0]) < ALIGNMENT_MAX_CHANGE * originalSize) {
      location.axisTop += diff;
      currentReduction[0] += diff;
    }
  }

  private void alignLeft(int[] base, int[] trial, int[] currentReduction, ChartLocation location) {
    // Align left
    int diff = base[1] - trial[1];
    int currentSize = trial[3] - trial[1];
    int originalSize = currentSize + currentReduction[1];

    // We want to reduce the size and it is allowable given our tolerances
    if (diff > 0 && (diff + currentReduction[1]) < ALIGNMENT_MAX_CHANGE * originalSize) {
      location.axisLeft += diff;
      currentReduction[1] += diff;
    }
  }

  private void alignBottom(int[] base, int[] trial, int[] currentReduction, ChartLocation location) {
    // Align left
    int diff = trial[2] - base[2];
    int currentSize = trial[2] - trial[0];
    int originalSize = currentSize + currentReduction[0];

    // We want to reduce the size and it is allowable given our tolerances
    if (diff > 0 && (diff + currentReduction[0]) < ALIGNMENT_MAX_CHANGE * originalSize) {
      location.axisBottom += diff;
      currentReduction[1] += diff;
    }
  }

  private void alignRight(int[] base, int[] trial, int[] currentReduction, ChartLocation location) {
    // Align left
    int diff = trial[3] - base[3];
    int currentSize = trial[3] - trial[1];
    int originalSize = currentSize + currentReduction[1];

    // We want to reduce the size and it is allowable given our tolerances
    if (diff > 0 && (diff + currentReduction[1]) < ALIGNMENT_MAX_CHANGE * originalSize) {
      location.axisRight += diff;
      currentReduction[1] += diff;
    }
  }



}
