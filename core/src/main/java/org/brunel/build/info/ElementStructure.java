/*
 * Copyright (c) 2016 IBM Corporation and others.
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

import org.brunel.build.d3.D3Util;
import org.brunel.build.d3.diagrams.D3Diagram;
import org.brunel.build.d3.element.ElementDetails;
import org.brunel.build.util.ModelUtil;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines how to display an element
 */
public class ElementStructure {
	public final ChartStructure chart;
	public final int index;
	public final VisSingle vis;
	public final Dataset original;
	public final Dataset data;
	public final GeoMapping geo;
	public final String styleSymbol;                // This is the symbol defined in the style for the element
	public final D3Diagram diagram;					// This is the diagram we will use for the element (may be null)

	public ElementDetails details;
	public List<Dependency> dependencies;

	public ElementStructure(ChartStructure chartStructure, int elementIndex, VisSingle vis, Dataset data, GeoMapping geo) {
		this.chart = chartStructure;
		this.index = elementIndex;
		this.vis = vis;
		this.data = data;
		this.geo = geo;
		this.styleSymbol = ModelUtil.getSymbolFromStyle(vis);
		this.original = vis.getDataset();
		this.dependencies = new ArrayList<>();
		this.diagram = D3Diagram.make(this);
	}

	public int getBaseDatasetIndex() {
		return chart.getBaseDatasetIndex(original);
	}

	public String elementID() {
		return "" + (index + 1);
	}

	public boolean hasHierarchicalData() {
		return chart.diagram != null && chart.diagram.isHierarchical;
	}

	public boolean isClustered() {
		// We are clustered when we are a coordinate chart with 2+ X dimensions
		return chart.diagram == null && vis.fX.size() > 1;
	}

	// Returns true of we depend on another element
	public boolean isDependent() {
		return getDependencyBase() != null;
	}

	// Returns the element we depend on
	private ElementStructure getDependencyBase() {
		for (Dependency dependency : dependencies) {
			if (dependency.dependent == this) return dependency.base;
		}
		return null;
	}

	public boolean isDependentEdge() {
		return vis.tElement == Element.edge && isDependent();
	}

	public String[] makeReferences(Field[] keys) {
		String idToPointName = "elements[" + getDependencyBase().index + "].internal()._idToPoint(";
		String[] references = new String[keys.length];
		for (int i = 0; i < references.length; i++)
			references[i] = idToPointName + D3Util.writeCall(keys[i]) + ")";
		return references;
	}
}
