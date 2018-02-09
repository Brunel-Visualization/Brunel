package org.brunel.data.diagram.dag;

import org.brunel.data.diagram.Node;
import org.brunel.translator.JSTranslation;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by graham on 2/8/18.
 */
public class WithinLayerOrdering {
  private static final double EPSILON = 1e-6;
  private final Node[][] layers;
  private final int maxMaxLayerCount;

  public WithinLayerOrdering(Node[][] layers) {
    this.layers = layers;
    int maxLayer = 0;
    for (Node[] layer : layers) {
      maxLayer = Math.max(maxLayer, layer.length);
    }
    this.maxMaxLayerCount = maxLayer;
  }

  public void layout() {
    makeInitialPlacement();           // Place nodes within layers using the degree

    for (int i = 0; i < 3; i++) {     // Three times works pretty well. No real reason
      alignChildren();                // Place children relative to parents
      alignParents();                 // Place parents relative to daughters
    }
    alignChildren();                  // Final sweep should be in this direction
  }

  private void alignChildren() {
    // For each layer except the top one
    for (int i = 1; i < layers.length; i++) {
      Node[] layer = layers[i];
      for (Node node : layer) {
        // Set position to the average of the in-nodes positions, biasing towards current position (breaks ties)
        NodeInfo info = info(node);
        info.pos = (averagePos(info.incoming) + info.pos * EPSILON) / (1 + EPSILON);
      }
      distributeLayer(layer);
    }
  }

  private void alignParents() {
    // Reverse through the layers, starting at the second from bottom
    for (int i = layers.length - 2; i >= 0; i--) {
      Node[] layer = layers[i];
      for (Node node : layer) {
        // Set position to the average of the out-nodes positions, biasing towards current position (breaks ties)
        NodeInfo info = info(node);
        info.pos = (averagePos(info.outgoing) + info.pos * EPSILON) / (1 + EPSILON);
      }
      distributeLayer(layer);
    }
  }

  private double averagePos(Node[] nodes) {
    if (nodes.length == 0) {
      return 0.0;
    }
    double sum = 0.0;
    for (Node node : nodes) {
      sum += info(node).pos;
    }
    return sum / nodes.length;
  }

  private void makeInitialPlacement() {
    for (Node[] layer : layers) {
      for (int j = 0; j < layer.length; j++) {
        Node node = layer[j];
        NodeInfo ni = info(node);
        double degrees = ni.degree() + 1e-6 * j;
        for (Node o : ni.incoming) {
          degrees += 0.1 * (info(o)).degree();
        }
        for (Node o : ni.outgoing) {
          degrees += 0.1 * (info(o)).degree();
        }
        ni.pos = degrees;
      }
      distributeLayer(layer);
    }
  }

  // Get the order of the nodes based on pos, and evenly spread out, centering on zero
  private void distributeLayer(Node[] layer) {
    int n = layer.length;
    if (n == 1) {
      info(layer[0]).pos = 0;
      return;
    }
    sortLayers(layer);
    for (int i = 0; i < n; i++) {
      NodeInfo info = info(layer[i]);
      double v = i - (n - 1) / 2.0;
      double w = maxMaxLayerCount * v / (n - 1);

      info.pos = (v + w) / 2;
    }
  }

  @JSTranslation(js = {"layer.sort(function(s,t) { return s.content.pos < t.content.pos ? -1 : 1});"})
  private void sortLayers(Node[] layer) {
    Arrays.sort(layer, new Comparator<Node>() {
      public int compare(Node a, Node b) {
        return Double.compare(info(a).pos, (info(b)).pos);
      }
    });
  }

  private NodeInfo info(Node a) {
    return (NodeInfo) a.content;
  }

}
