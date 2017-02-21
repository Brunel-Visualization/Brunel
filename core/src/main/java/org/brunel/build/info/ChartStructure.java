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

import org.brunel.build.InteractionDetails;
import org.brunel.build.SymbolHandler;
import org.brunel.build.data.TransformedData;
import org.brunel.data.Dataset;
import org.brunel.maps.GeoInformation;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Diagram;
import org.brunel.model.VisTypes.Element;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Manages dependency between elements
 */
public class ChartStructure {

	public final int chartIndex;                            // 0-based chart index
	public final ChartStructure outer;                      // If non-null, the enclosing element for a nested chart
	public final Integer innerChartIndex;                   // If non-null, the index of the chart we enclose

	public final ChartCoordinates coordinates;                // Coordinate system for this chart
	public final GeoInformation geo;                        // Geo information
	public final Diagram diagram;                            // Diagram for this chart

	public final SymbolHandler symbols;                        // Symbol handler for the chart
	public final InteractionDetails interaction;                    // Interactivity handler for the chart

	public final String visIdentifier;                      // Identifier for the overall vis (the SVG ID)
	public boolean accessible;                              // If true, generate accessible content

	public final VisSingle[] elements;
	public final ElementStructure[] elementStructure;
	public final Dataset[] baseDataSets;
	public int chartHeight, chartWidth;                     // Pixel expanse of chart (set during building)

	public ChartStructure(int chartIndex, VisSingle[] elements, TransformedData[] transformedData, Dataset[] dataSets,
						  ChartStructure outer, Integer innerChartIndex, String visIdentifier) {
		this.baseDataSets = dataSets;
		this.chartIndex = chartIndex;
		this.elements = elements;
		this.outer = outer;
		this.innerChartIndex = innerChartIndex;
		this.visIdentifier = visIdentifier;
		this.elementStructure = new ElementStructure[elements.length];
		this.diagram = findDiagram();
		this.coordinates = new ChartCoordinates(elements, transformedData, diagram);
		this.geo = makeGeo(elements, transformedData);

		// Define any interactivity needed
		this.interaction = new InteractionDetails(diagram, coordinates, elements);

		// Define the elements
		for (int i = 0; i < elements.length; i++) {
			GeoMapping geoMapping = geo == null ? null : geo.getGeo(elements[i]);
			elementStructure[i] = new ElementStructure(this, i, elements[i], transformedData[i], geoMapping);
		}

		// Define any dependencies between the elements
		int sourceIndex = findSourceElement(elements);                      // A source for dependencies

		if (sourceIndex >= 0) {
			for (ElementStructure structure : this.elementStructure) {
				VisSingle vis = structure.vis;
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

	public void setExtent(int chartWidth, int chartHeight) {
		this.chartWidth = chartWidth;
		this.chartHeight = chartHeight;
	}

	public static String makeChartID(int index) {
		return "" + (index + 1);
	}

	public boolean nested() {
		return outer != null;
	}

	private Diagram findDiagram() {
		// Any diagram make the chart all diagram. Mixing diagrams and non-diagrams will
		// likely be useless at best, but we will not throw an error for it
		// Note that we don't count a dependent edge as a diagram for the overall chart
		for (VisSingle e : elements)
			if (e.tDiagram != null && e.tDiagram != Diagram.dependentEdge) return e.tDiagram;
		return null;
	}

	private int findSourceElement(VisSingle[] elements) {
		int candidate = -1;
		for (int i = 0; i < elements.length; i++) {
			VisSingle vis = elements[i];
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

	private GeoInformation makeGeo(VisSingle[] elements, TransformedData[] data) {
		// If any element specifies a map, we make the map information for all to share
		for (VisSingle vis : elements)
			if (vis.tDiagram == Diagram.map)
				return new GeoInformation(elements, data, coordinates);
		return null;
	}

	public Integer[] elementBuildOrder() {
		// Start with the default order
		Integer[] order = new Integer[elements.length];
		for (int i = 0; i < order.length; i++) order[i] = i;

		Arrays.sort(order, new Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				VisSingle aa = elements[a], bb = elements[b];

				// Diagrams go first
				if (aa.tDiagram != null && bb.tDiagram == null) return -1;
				if (aa.tDiagram == null && bb.tDiagram != null) return 1;

				// Edges go last
				if (aa.tElement == Element.edge && bb.tElement != Element.edge) return 1;
				if (aa.tElement != Element.edge && bb.tElement == Element.edge) return -1;

				// Otherwise the more keys you have, the later you are built
				return aa.fKeys.size() - bb.fKeys.size();
			}
		});

		return order;
	}

	public int getBaseDatasetIndex(Dataset dataset) {
		for (int i = 0; i < baseDataSets.length; i++)
			if (dataset == baseDataSets[i]) return i;
		throw new IllegalStateException("Could not find data set in array of datasets");
	}

	public String chartID() {
		return makeChartID(chartIndex);
	}

}
