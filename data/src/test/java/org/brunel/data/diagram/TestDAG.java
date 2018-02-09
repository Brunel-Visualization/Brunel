package org.brunel.data.diagram;

import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.data.diagram.dag.BreakCycles;
import org.brunel.data.diagram.dag.NodeInfo;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Tests the dag layout class
 */
public class TestDAG {

  @Test
  public void testSimpleCycleRemoval() {
    Field nodes = Fields.makeColumnField("nodeID", "nodeID", new Object[]{0, 1, 2});
    Field a = Fields.makeColumnField("a", "fr", new Object[]{0, 1, 2});
    Field b = Fields.makeColumnField("b", "to", new Object[]{1, 2, 2});

    Graph g = new Graph(nodes, a, b);
    NodeInfo.addInfo(g);

    NodeInfo last = (NodeInfo) g.nodes[2].content;
    assertEquals(2, last.incoming.length);
    assertEquals(1, last.outgoing.length);

    new BreakCycles(g).removeCycleEdges();

    assertEquals(1, last.incoming.length);
    assertEquals(0, last.outgoing.length);

  }

  @Test
  public void testMoreComplexCycleRemoval() {
    Field nodes = Fields.makeColumnField("nodeID", "nodeID", new Object[]{0, 1, 2, 3});
    Field a = Fields.makeColumnField("a", "fr", new Object[]{0, 1, 2, 1, 3});
    Field b = Fields.makeColumnField("b", "to", new Object[]{1, 2, 0, 3, 1});

    Graph g = new Graph(nodes, a, b);
    NodeInfo.addInfo(g);

    new BreakCycles(g).removeCycleEdges();

    // Should remove the inlink 2-> 0
    NodeInfo n0 = (NodeInfo) g.nodes[0].content;
    assertEquals(0, n0.incoming.length);
    assertEquals(1, n0.outgoing.length);

    // Should remove the inlink 3-> 1
    NodeInfo n1 = (NodeInfo) g.nodes[1].content;
    assertEquals(1, n1.incoming.length);
    assertEquals(2, n1.outgoing.length);

    // Should remove the outlink 2-> 0
    NodeInfo n2 = (NodeInfo) g.nodes[2].content;
    assertEquals(1, n2.incoming.length);
    assertEquals(0, n2.outgoing.length);

    // Should remove the outlink 3->1
    NodeInfo n3 = (NodeInfo) g.nodes[2].content;
    assertEquals(1, n3.incoming.length);
    assertEquals(0, n3.outgoing.length);

  }


  /* Nodes links

      6 ---> 2 ---> 1 ---> 0 ---> 5
             2 ---> 3 ---> 4 ---> 5
                    1 ---> 4
                    3 ---> 0
      6  ----------------> 0
             2 -----------------> 5
      6 ----------> 7
             2 ---> 7

   */

  @Test
  public void testBasic() {
    Field nodes = Fields.makeColumnField("nodeID", "nodeID", new Object[]{0, 1, 2, 3, 4, 5, 6, 7});
    Field a = Fields.makeColumnField("a", "fr", new Object[]{6, 2, 1, 0, 2, 3, 4, 1, 3, 6, 2, 6, 2});
    Field b = Fields.makeColumnField("b", "to", new Object[]{2, 1, 0, 5, 3, 4, 5, 4, 0, 0, 5, 7, 7});
    Graph g = new Graph(nodes, a, b);

    new DAG(g)
      .size(400, 400)
      .pad(0)
      .layout();

    Node[] loc = g.nodes;

//    // Check the 'y' locations are the correct layers
//    assertEquals(0, coordOf(loc[6], 1));
//    assertEquals(100, coordOf(loc[2], 1));
//    assertEquals(200, coordOf(loc[1], 1));
//    assertEquals(200, coordOf(loc[3], 1));
//    assertEquals(200, coordOf(loc[7], 1));
//    assertEquals(300, coordOf(loc[0], 1));
//    assertEquals(300, coordOf(loc[4], 1));
//    assertEquals(400, coordOf(loc[5], 1));
//
//    // Check the 'x' locations
//    assertEquals(200, coordOf(loc[6], 0));
//    assertEquals(200, coordOf(loc[2], 0));
//    assertEquals(0, coordOf(loc[7], 0));
//    assertEquals(200, coordOf(loc[1], 0));
//    assertEquals(400, coordOf(loc[3], 0));
//    assertEquals(100, coordOf(loc[0], 0));
//    assertEquals(300, coordOf(loc[4], 0));
//    assertEquals(200, coordOf(loc[5], 0));

  }

  private int coordOf(Node node, int p) {
    return (int) Math.round(((double[]) node.content)[p]);
  }

}
