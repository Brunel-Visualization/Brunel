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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An hierarchical diagram shows a tree-like display of nodes (and possibly links)
 * This class takes the data in standard format and converts it into a form that D3 can use
 */
public class Hierarchical {

	public static Hierarchical makeByNestingFields(Dataset data, String sizeField, String... fieldNames) {
		Hierarchical result = new Hierarchical();
		Field size = sizeField == null ? null : data.field(sizeField);
		Field[] fields = toFields(data, fieldNames);
		result.makeNodesUsingFields(data, size, fields);
		result.fixChildren();
		return result;
	}

	public static Hierarchical makeByEdges(Dataset data, String nodeField, Dataset edges, String fromField, String toField) {
		Hierarchical result = new Hierarchical();
		Field id = data.field(nodeField);
		Field from = edges.field(fromField);
		Field to = edges.field(toField);
		result.makeNodesUsingEdges(id, from, to);
		result.fixChildren();
		return result;
	}

	public Node root;
	public Edge[] links;

	// Used by javascript
	public static int compare(Node a, Node b) {
		int d = Data.compare(a.meanRow(), b.meanRow());
		return d != 0 ? d : Data.compare(a.key, b.key);
	}

	// Used by javascript
	public static int compareReverse(Node a, Node b) {
		return compare(b, a);
	}

	public Node find(Object name) {
		return findNamedNode(root, name);
	}

	private Node findNamedNode(Node node, Object name) {
		if (name.equals(node.key)) return node;
		if (node.children == null) return null;
		for (Node n : (Node[]) node.children) {
			Node hit = findNamedNode(n, name);
			if (hit != null) return hit;
		}
		return null;
	}

	private void fixChildren() {
		replaceCollections(root, null, new HashSet<Node>());
	}

	private static Node makeInternalNode(String label) {
		Node node = new Node(null, 0, label, new ArrayList<Node>());
		node.temp = new HashMap<String, Object>();
		return node;
	}

	@SuppressWarnings("unchecked")
	private void makeNodesUsingFields(Dataset data, Field size, Field[] fields) {
		this.root = makeInternalNode("");            // General root node

		for (int row = 0; row < data.rowCount(); row++) {
			// Only use this if size is not NaN and is greater than zero
			Double d = size == null ? 1 : Data.asNumeric(size.value(row));
			if (!(d > 0)) continue;

			Node current = root;                            // Start at root

			for (Field field : fields) {
				Map<Object, Node> map = (Map<Object, Node>) current.temp;
				List<Node> children = (List<Node>) current.children;
				Object v = field.value(row);
				if (v != null) current = map.get(v);
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
	private void makeNodesUsingEdges(Field id, Field from, Field to) {
		// Build the nodes
		int N = id.rowCount();
		Set<Node> unparented = new HashSet<>();
		List<Node> all = new ArrayList<>();
		Map<Object, Node> byID = new HashMap<>();                        // Map from ID to node
		for (int i = 0; i < N; i++) {
			Object key = id.value(i);
			Node node = new Node(i, 0, null, new ArrayList<>());
			node.key = key;
			unparented.add(node);
			all.add(node);
			byID.put(key, node);
		}

		// Connect up the edges
		List<Edge> validEdges = new ArrayList<>();
		int M = from.rowCount();
		for (int i = 0; i < M; i++) {
			Node a = byID.get(from.value(i));
			Node b = byID.get(to.value(i));
			// Very important we do not add if it already has a parent -- cycles are a disaster
			if (a != null && b != null && unparented.contains(b)) {
				((List<Node>) a.children).add(b);
				unparented.remove(b);
				validEdges.add(new Edge(a, b, i));
			}
		}
		this.links = validEdges.toArray(new Edge[validEdges.size()]);

		// Return the single root node if it exists
		if (unparented.size() == 1) {
			this.root = new ArrayList<>(unparented).get(0);
		} else {
			// Otherwise build a fake node to contain all the roots
			this.root = makeInternalNode("");
			for (Node n : all)
				if (unparented.contains(n))
					((List<Node>) (root.children)).add(n);
		}
	}

	@SuppressWarnings("unchecked")
	private void replaceCollections(Node current, Object parentKey, Set<Node> processed) {
		if (!processed.add(current))
			return;            // Do not do this twice, in case the data was not actually a tree

		current.temp = null;	// Free temp memory


		List<Node> array = ((List<Node>) current.children);
		if (array != null) {
			current.children = array.isEmpty() ? null : array.toArray(new Node[array.size()]);
			if (current.key == null)
				current.key = parentKey == null ? current.innerNodeName : parentKey + "|" + current.innerNodeName;
			for (Node child : array) replaceCollections(child, current.key, processed);
		}
	}

	private static Field[] toFields(Dataset data, String[] fieldNames) {
		Field[] fields = new Field[fieldNames.length];
		for (int i = 0; i < fields.length; i++) fields[i] = data.field(fieldNames[i]);
		return fields;
	}
}


