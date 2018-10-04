package org.brunel.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.brunel.build.info.ChartStructure;

/**
 * Run through all geometrries to see if they can be nudged into better alignment with each other
 */
public class GeometryAlignment {
  private final List<ChartStructure> structures = new ArrayList<>();

  public GeometryAlignment(Collection<ChartBuilder> values) {
    for (ChartBuilder builder : values) {
      structures.add(builder.structure);
    }
  }

  public void align() {
    System.out.println(structures);
  }
}
