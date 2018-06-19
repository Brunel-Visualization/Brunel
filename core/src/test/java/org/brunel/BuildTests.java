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

package org.brunel;

import org.brunel.action.Action;
import org.brunel.build.VisualizationBuilder;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.model.VisItem;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Testing build process
 */
public class BuildTests {

  private Dataset data;
  private VisualizationBuilder builder;

  @Before
  public void setUp() throws Exception {
    data = DataCache.get("sample://US States.csv");
    builder = VisualizationBuilder.make();
  }

  @Test
  public void testConcurrency() throws Exception {
    ExecutorService exec = Executors.newFixedThreadPool(5);

    List<Future<String>> items = new ArrayList<>();
    for (int i = 1; i <= 500; i++) {
      final int size = 100 + i * 100;
      items.add(exec.submit(new Callable<String>() {
        public String call() {
          VisualizationBuilder builder = VisualizationBuilder.make();
          VisItem vis = Action.parse("x(winter) y(summer) size(winter) color(summer)").apply(data);
          builder.build(vis, size, size);
          return builder.getVisualization();
        }
      }));
    }

    assertEquals(500, items.size());
    for (Future<String> item : items) {
      assertTrue(item.get().contains("build"));
    }

  }

  @Test
  public void testBasicBuild() throws Exception {
    Action action = Action.parse("x(winter) y(summer)");
    VisItem vis = action.apply(data);
    builder.build(vis, 500, 500);
    assertTrue(builder.getVisualization().length() > 100);
    assertEquals("", builder.getStyleOverrides());
  }

  @Test
  public void testSingleAt() throws Exception {
    Action action = Action.parse("x(winter) y(summer) at(40,30,80,50)");
    VisItem vis = action.apply(data);
    builder.build(vis, 500, 500);
    String javascript = builder.getVisualization();
    // 500 width @ 40% and 80% results in left and right insets of 200, 100
    // 500 height @ 30% and 50% results in top and bottom insets of 150, 250
    // Order of insets is  TOP(150) LEFT(200) BOTTOM(250) RIGHT(100)
    assertTrue(javascript.contains("BrunelD3.geometry(parentNode || vis.node(), 0.3, 0.4, 0.5, 0.8"));
  }

  @Test
  public void testSmallAxes() throws Exception {
    Action action = Action.parse("x(winter) y(summer)");
    VisItem vis = action.apply(data);
    builder.build(vis, 200, 140);
    String javascript = builder.getVisualization();
    // Should limit the number of ticks
    assertTrue(javascript.contains(".ticks(3)"));
  }

  @Test
  public void testStyles() throws Exception {
    Action action = Action.parse("x(winter) y(summer) style('fill:red')");
    VisItem vis = action.apply(data);
    builder.build(vis, 500, 500);
    assertTrue(builder.getVisualization().length() > 100);
    assertEquals("#visualization.brunel .chart1 .element1 .element {\n\tfill: red;\n}", builder.getStyleOverrides());
  }

}
