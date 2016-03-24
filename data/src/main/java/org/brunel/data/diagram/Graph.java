/*
 * Copyright (c) 2015 IBM Corporation and others.
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

package org.brunel.data.diagram;

import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A graph layout coordinates graphs and links
 * This class takes the data in standard format and converts it into a form that D3 can use
 */
public class Graph {

    public final Node[] nodes;
    public final Edge[] links;

    public static Graph make(Dataset nodeData, String nodeID, Dataset edgeData, String fromField, String toField) {
        Field nodes = nodeData.field(nodeID);
        Field a = edgeData.field(fromField);
        Field b = edgeData.field(toField);
        return new Graph(nodes, a, b);
    }

    public Graph(Field nd, Field a, Field b) {

        // Create the nodes
        Map<Object, Node> nodeByID = new HashMap<>();
        List<Node> nds = new ArrayList<>();
        for (int i = 0; i < nd.rowCount(); i++) {
            Object o = nd.value(i);
            if (o != null) {
                Node n = new Node(i, 1, o.toString(), null);
                n.key = o;
                nds.add(n);
                nodeByID.put(o, n);
            }
        }

        // Create the edges only when validly defined
        List<Edge> lks = new ArrayList<>();
        for (int i = 0; i < a.rowCount(); i++) {
            Node s = nodeByID.get(a.value(i));
            Node t = nodeByID.get(b.value(i));
            if (s != null && t != null) lks.add(new Edge(s, t, i));
        }

        // Convert to arrays
        nodes = nds.toArray(new Node[nds.size()]);
        links = lks.toArray(new Edge[lks.size()]);
    }

}


