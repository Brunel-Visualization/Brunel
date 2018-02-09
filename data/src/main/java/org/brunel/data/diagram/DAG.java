package org.brunel.data.diagram;

import org.brunel.data.diagram.dag.BreakCycles;
import org.brunel.data.diagram.dag.NodeInfo;
import org.brunel.data.diagram.dag.WithinLayerOrdering;
import org.brunel.translator.JSTranslation;

import java.util.ArrayList;
import java.util.List;

/**
 * This class takes a graph, finds the maximal directed acyclic graph inside it, and then lays it
 * out.
 */
public class DAG {

  private final Graph graph;
  private double width = 1;
  private double height = 1;
  private double padding = 0;

  public DAG(Graph graph) {
    this.graph = graph;
    NodeInfo.addInfo(graph);
    new BreakCycles(graph).removeCycleEdges();

    // TODO: What to do with disconnected components
  }

  public DAG layout() {
    int maxLayer = assignLayers();

    Node[][] layers = makeNodeLayers(maxLayer + 1);
    new WithinLayerOrdering(layers).layout();

    double maxPos = 0.0;
    for (Node node : graph.nodes) {
      maxPos = Math.max(maxPos, Math.abs(info(node).pos));
    }

    // Set the locations into the content, replacing the node info
    for (Node nd : graph.nodes) {
      double x = padding + (width - 2 * padding) * (info(nd).pos / 2 / maxPos + 0.5);
      double y = padding + (height - 2 * padding) * info(nd).layer / maxLayer;
      setNodeLocations(nd, x, y);
    }
    return this;
  }

  // For Javascript, additionally write directly into the node x and y slots
  @JSTranslation(js = {"nd.x = x; nd.y = y; nd.content = [x, y];"})
  private void setNodeLocations(Node nd, double x, double y) {
    nd.content = new double[]{x, y};
  }

  @SuppressWarnings("unchecked")
  private Node[][] makeNodeLayers(int layerCount) {
    List<Node>[] layer = new List[layerCount];
    for (int i = 0; i < layerCount; i++) {
      layer[i] = new ArrayList<>();
    }
    for (Node node : graph.nodes) {
      layer[info(node).layer].add(node);
    }
    Node[][] array = new Node[layerCount][];
    for (int i = 0; i < layerCount; i++) {
      array[i] = layer[i].toArray(new Node[layer[i].size()  ]);
    }
    return array;
  }

  public DAG pad(double padding) {
    this.padding = padding;
    return this;
  }

  public DAG size(double width, double height) {
    this.width = width;
    this.height = height;
    return this;
  }

  private int assignLayers() {
    // Layer so layer #0 is the leaf
    int maxLayer = 0;
    for (Node node : graph.nodes) {
      int dLeaf = distanceToLeaf(node);
      info(node).layer = dLeaf;
      maxLayer = Math.max(maxLayer, dLeaf);
    }

    // recode so layer #0 is the root
    for (Node node : graph.nodes) {
      info(node).layer = maxLayer - info(node).layer;
    }

    // Shuffle up children nodes until they are just under all their parents.
    for (int i = 1; i <= maxLayer; i++) {
      for (Node node : graph.nodes) {
        NodeInfo info = info(node);
        if (info.layer == i) {
          if (info.incoming.length > 0) {
            int parentLayer = 0;
            for (Node parent : info.incoming) {
              parentLayer = Math.max(parentLayer, info(parent).layer);
            }
            info.layer = parentLayer + 1;
          } else{
            // It is top level; move to layer zero
            info.layer = 0;
          }
        }
      }
    }
    return maxLayer;
  }

  private int distanceToLeaf(Node node) {
    NodeInfo info = info(node);
    if (info.distanceToLeaf < 0) {
      int d = 0;
      for (Node other : info(node).outgoing) {
        d = Math.max(d, distanceToLeaf(other) + 1);
      }
      info.distanceToLeaf = d;
    }
    return info.distanceToLeaf;
  }

  private NodeInfo info(Node node) {
    return (NodeInfo) node.content;
  }
}
