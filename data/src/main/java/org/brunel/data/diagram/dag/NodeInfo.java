package org.brunel.data.diagram.dag;

import org.brunel.data.diagram.Edge;
import org.brunel.data.diagram.Graph;
import org.brunel.data.diagram.Node;
import org.brunel.translator.JSTranslation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Information used when building a DAG
 */
public class NodeInfo {

  @SuppressWarnings("unchecked")
  public static void addInfo(Graph graph) {
    // Create outgoing links for each node
    Map<Node, Set<Node>> outgoing = new HashMap<>();
    Map<Node, Set<Node>> incoming = new HashMap<>();
    for (Node node : graph.nodes) {
      outgoing.put(node, new HashSet<Node>());
      incoming.put(node, new HashSet<Node>());
    }
    for (Edge link : graph.links) {
      outgoing.get(link.source).add(link.target);
      incoming.get(link.target).add(link.source);
    }

    // Attach info to each node
    for (Node node : graph.nodes) {
      Set<Node> inNodes = incoming.get(node);
      Set<Node> outNodes = outgoing.get(node);
      Node[] in = inNodes.toArray(new Node[inNodes.size()]);
      Node[] out = outNodes.toArray(new Node[outNodes.size()]);
      node.content = new NodeInfo(in, out);
    }
  }

  public Node[] incoming;             // incoming linked nodes
  public Node[] outgoing;             // outgoing linked nodes
  public int distanceToLeaf = -1;           // Distance to leaf (-1 means not calculated yet)
  public int layer = -1;                    // The layer it has been assigned to
  public double pos;                        // Position within the layer

  public NodeInfo(Node[] in, Node[] out) {
    this.incoming = in;
    this.outgoing = out;
  }

  public int degree() {
    return incoming.length + outgoing.length;
  }

  @JSTranslation(ignore = true)
  public String toString() {
    return "DAGNodeInfo{" + "in=" + Arrays.toString(incoming) +
      ", out=" + Arrays.toString(outgoing) +
      ", distanceToLeaf=" + distanceToLeaf +
      ", layer=" + layer +
      ", pos=" + pos +
      '}';
  }
}
