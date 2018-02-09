package org.brunel.data.diagram.dag;

import org.brunel.data.diagram.Graph;
import org.brunel.data.diagram.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * Removes edges to break cycles.
 * This is a very lazy technique; we just search until we get a cycle and then remove an edge, and repeat
 * We don't try to find the minimal set or anything like that
 */
public class BreakCycles {
  private final Graph graph;              // The graph to analyze
  private final Set<Node> safe;           // The list of nodes known to be safe

  public BreakCycles(Graph graph) {
    this.graph = graph;
    this.safe = new HashSet<>();
  }

  public void removeCycleEdges() {
    // Search through the nodes looking for cycles containing each node
    for (Node node : graph.nodes) {
      searchForCycles(null, node, new HashSet<Node>());
    }
  }

  private void searchForCycles(Node last, Node node, HashSet<Node> path) {
    if (safe.contains(node)) {
      return;      // Known to be free from cycles
    }
    if (path.contains(node)) {
      removeEdgeInfo(last, node);
      return;
    }

    path.add(node);
    for (Node n : ((NodeInfo) node.content).outgoing) {
      searchForCycles(node, n, path);
    }
    safe.add(node);
    path.remove(node);
  }

  private void removeEdgeInfo(Node from, Node to) {
    NodeInfo fromInfo = (NodeInfo) from.content;
    NodeInfo toInfo = (NodeInfo) to.content;
    fromInfo.outgoing = removeFromArray(fromInfo.outgoing, to);
    toInfo.incoming = removeFromArray(toInfo.incoming, from);
  }

  private Node[] removeFromArray(Node[] nodes, Node nd) {
    Node[] result = new Node[nodes.length - 1];
    int at = 0;
    for (Node node : nodes) {
      if (node != nd) {
        result[at++] = node;
      }
    }
    return result;
  }
}
