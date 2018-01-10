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

package org.brunel.build.info;

import org.brunel.build.ChartLocation;
import org.brunel.build.InteractionDetails;
import org.brunel.build.SymbolHandler;
import org.brunel.build.data.TransformedData;
import org.brunel.maps.GeoInformation;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisElement;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Manages dependency between elements
 */
public class ChartStructure {

	public static String makeChartID(int index) {
		return "" + (index + 1);
	}

	public final int chartIndex;                            // 0-based chart index
	public final ChartLocation location;                    // chart location information
	public final boolean isNested;                      		// True if this is nested within another element
	public final ChartCoordinates coordinates;                // Coordinate system for this chart
	public final GeoInformation geo;                        // Geo information
	public final Diagram diagram;                            // Diagram for this chart
	public final SymbolHandler symbols;                        // Symbol handler for the chart
	public final InteractionDetails interaction;                    // Interactivity handler for the chart
	public final String visIdentifier;                      // Identifier for the overall vis (the SVG ID)
	public final VisElement[] elements;
	public final ElementStructure[] elementStructure;
	public boolean accessible;                              // If true, generate accessible content

	public ChartStructure(int chartIndex, VisElement[] elements, ChartLocation location, TransformedData[] data,
												boolean isNested, String visIdentifier) {
		this.chartIndex = chartIndex;
		this.elements = elements;
		this.location = location;
		this.isNested = isNested;
		this.visIdentifier = visIdentifier;
		this.elementStructure = new ElementStructure[elements.length];
		this.diagram = findDiagram();
		this.coordinates = new ChartCoordinates(elements, data, diagram);
		this.geo = makeGeo(elements, data);

		// Define any interactivity needed
		this.interaction = new InteractionDetails(diagram, coordinates, elements);

		// Define the elements
		for (int i = 0; i < elements.length; i++) {
			GeoMapping geoMapping = geo == null ? null : geo.getGeo(elements[i]);
			elementStructure[i] = new ElementStructure(this, i, elements[i], data[i], geoMapping);
		}

		// Define any dependencies between the elements
		int sourceIndex = findSourceElement(elements);                      // A source for dependencies

		if (sourceIndex >= 0) {
			for (ElementStructure structure : this.elementStructure) {
				VisElement vis = structure.vis;
				boolean isOtherDependent = vis.positionFields().length == 0 && vis.tDiagram == null && !vis.fKeys.isEmpty();
				if (vis.tDiagram == Diagram.dependentEdge || isOtherDependent) {
					// No position or diagram, and we do have keys to link us to the source
					// Check we do not depend on ourselves!
					if (structure != elementStructure[sourceIndex]) {
						Dependency dependency = new Dependency(elementStructure[sourceIndex], structure);
						dependency.attach();
					}
				}

			}
		}

		// This must be called when the elements have been defined. It will look for custom symbol URIs
		// and manage naming conventions for them
		this.symbols = new SymbolHandler(this);

	}

	public String chartID() {
		return makeChartID(chartIndex);
	}

	public double defaultPointSize() {
		return Math.max(6, Math.min(location.width, location.height) / 2 * 0.035);
	}

	/**
	 * Returns true if the diagram defines a graph layout.
	 * tree layouts and network diagrams define graphs
	 *
	 * @return true or false
	 */
	public boolean diagramDefinesGraph() {
		return diagram == Diagram.tree || diagram == Diagram.network;
	}

	public Integer[] elementBuildOrder() {
		// Start with the default order
		Integer[] order = new Integer[elements.length];
		for (int i = 0; i < order.length; i++) order[i] = i;

		Arrays.sort(order, new Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				VisElement aa = elements[a], bb = elements[b];

				// Edges go last
				if (aa.tElement == Element.edge && bb.tElement != Element.edge) return 1;
				if (aa.tElement != Element.edge && bb.tElement == Element.edge) return -1;

				// Diagrams go first
				if (aa.tDiagram != null && bb.tDiagram == null) return -1;
				if (aa.tDiagram == null && bb.tDiagram != null) return 1;

				// Otherwise the more keys you have, the later you are built
				return aa.fKeys.size() - bb.fKeys.size();
			}
		});

		return order;
	}

	public boolean nested() {
		return isNested;
	}

	private Diagram findDiagram() {
		// Any diagram make the chart all diagram. Mixing diagrams and non-diagrams will
		// likely be useless at best, but we will not throw an error for it
		// Note that we don't count a dependent edge as a diagram for the overall chart
		for (VisElement e : elements)
			if (e.tDiagram != null && e.tDiagram != Diagram.dependentEdge) return e.tDiagram;
		return null;
	}

	private int findSourceElement(VisElement[] elements) {
		int candidate = -1;
		for (int i = 0; i < elements.length; i++) {
			VisElement vis = elements[i];
			if (vis.fKeys.size() == 1) {
				// A source must have one key only
				if (candidate < 0) {
					candidate = i;
				} else {
					// If there are multiple elements with one key, we need to pick the better one
					// Diagrams are always better sources, otherwise the one that defines positions is better.
					if (vis.tDiagram != null) return i;
					if (vis.positionFields().length > elements[candidate].positionFields().length) {
						candidate = i;
					}
				}
			}
		}
		return candidate;
	}

	private GeoInformation makeGeo(VisElement[] elements, TransformedData[] data) {
		// If any element specifies a map, we make the map information for all to share
		for (VisElement vis : elements)
			if (vis.tDiagram == Diagram.map)
				return new GeoInformation(elements, data, coordinates);
		return null;
	}

}
