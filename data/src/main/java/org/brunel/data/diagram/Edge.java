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

/**
 * A hierarchical edges with values named such that they can easily be used by D3
 */
public class Edge {
    public final Node source, target;               // End points of the edge
    public final int row;                           // Row this came from
    public final Object key;                        // Key name for transitions

    public Edge(Node a, Node b, int row) {
        this.row = row;
        source = a;
        target = b;
        key = a.key + "--" + b.key;
    }
}
