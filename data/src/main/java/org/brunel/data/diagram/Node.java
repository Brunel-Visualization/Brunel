/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.data.diagram;

/**
 * A hierarchical node with values named such that they can easily be used by D3
 */
public class Node {
    public final Integer row;                       // Row this came from
    public final double value;                      // "size" measure
    public Object children;                         // Initially a List, then an array
    public Object temp;                             // Temporary storage for building
    public final String innerNodeName;              // Name for a non-leaf node
    public Object key;                              // Key name for transitions

    public Node(Integer row, double value, String innerNodeName, Object children) {
        this.row = row;
        this.value = value;
        this.innerNodeName = innerNodeName;
        this.children = children;
    }
}
