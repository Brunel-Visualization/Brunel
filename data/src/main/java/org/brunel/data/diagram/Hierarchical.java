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

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An hierarchical diagram shows a tree-like display of nodes (and possibly links)
 * This class takes the data in standard format and converts it into a form that D3 can use
 */
public class Hierarchical {

    public static Hierarchical makeByNestingFields(Dataset data, String sizeField, String... fields) {
        return new Hierarchical(data, sizeField, fields);
    }

    public static int compare(Node a, Node b) {
        int d = a.row - b.row;
        return d != 0 ? d : Data.compare(a.key, b.key);
    }

    public final Node root;

    private Hierarchical(Dataset data, String sizeFieldName, String[] fieldNames) {
        Field size = sizeFieldName == null ? null : data.field(sizeFieldName);
        Field[] fields = toFields(data, fieldNames);
        root = makeInternalNode("");
        makeNodesUsingCollections(data, size, fields);
        replaceCollections(data.field("#row"), root, null);
    }

    private Node makeInternalNode(String label) {
        Node node = new Node(null, 0, label, new ArrayList<Node>());
        node.temp = new HashMap<String, Object>();
        return node;
    }

    @SuppressWarnings("unchecked")
    private void makeNodesUsingCollections(Dataset data, Field size, Field[] fields) {
        for (int row = 0; row < data.rowCount(); row++) {
            // Only use this if size is not NaN and is greater than zero
            Double d = size == null ? 1 : Data.asNumeric(size.value(row));
            if (!(d > 0)) continue;

            Node current = root;                            // Start at root
            for (Field field : fields) {
                Map<Object, Node> map = (Map<Object, Node>) current.temp;
                List<Node> children = (List<Node>) current.children;
                Object v = field.value(row);
                current = map.get(v);
                if (current == null) {
                    current = makeInternalNode(field.valueFormatted(row));
                    children.add(current);        // add to ordered list
                    map.put(v, current);                                 // add to map
                }
            }
            ((List<Node>) current.children).add(new Node(row, d, null, null));
        }
    }

    @SuppressWarnings("unchecked")
    private void replaceCollections(Field dataRowField, Node current, Object parentKey) {
        List<Node> array = ((List<Node>) current.children);
        if (array == null) {
            // Leaf node
            current.key = dataRowField == null ? current.row : dataRowField.value(current.row);
        } else {
            // Internal node
            current.children = array.toArray(new Node[array.size()]);
            current.temp = null;
            current.key = parentKey == null ? current.innerNodeName : parentKey + "-" + current.innerNodeName;
            for (Node child : array) replaceCollections(dataRowField, child, current.key);
        }
    }

    private Field[] toFields(Dataset data, String[] fieldNames) {
        Field[] fields = new Field[fieldNames.length];
        for (int i = 0; i < fields.length; i++) fields[i] = data.field(fieldNames[i]);
        return fields;
    }
}


