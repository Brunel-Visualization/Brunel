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

import org.brunel.action.Param;
import org.brunel.build.data.TransformedData;
import org.brunel.build.diagrams.D3Diagram;
import org.brunel.build.element.ElementDetails;
import org.brunel.build.util.BuildUtil;
import org.brunel.build.util.ModelUtil;
import org.brunel.data.Field;
import org.brunel.maps.GeoMapping;
import org.brunel.model.VisElement;
import org.brunel.model.VisTypes;
import org.brunel.model.VisTypes.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines how to display an element
 */
public class ElementStructure {
	public final ChartStructure chart;                    // The owning chart
	public final int index;                                // index of this element within the parent chart
	public final VisElement vis;                            // definition of the element
	public final TransformedData data;                    // data set to use for this element (transformation has been applied)
	public final GeoMapping geo;                        // geographical mapping info for this element
	public final String styleSymbol;                    // This is the symbol defined in the style for the element
	public final D3Diagram diagram;                    // This is the diagram we will use for the element (may be null)
	public final ElementDetails details;                // Details on element appearance

	public List<Dependency> dependencies;

	public ElementStructure(ChartStructure chartStructure, int elementIndex, VisElement vis, TransformedData data, GeoMapping geo) {
		this.chart = chartStructure;
		this.index = elementIndex;
		this.vis = vis;
		this.data = data;
		this.geo = geo;
		this.styleSymbol = ModelUtil.getSymbolFromStyle(vis);
		this.dependencies = new ArrayList<>();
		this.diagram = D3Diagram.make(this);
		this.details = diagram == null ? ElementDetails.makeForCoordinates(this) : diagram.makeDetails();
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

	public boolean isDependentEdge() {
		return vis.tElement == Element.edge && isDependent();
	}

	public boolean isSourceForDependent() {
		for (Dependency dependency : dependencies)
			if (dependency.base == this) return true;
		return false;
	}

	public String[] makeReferences(Field[] keys) {
		String idToPointName = "elements[" + getDependencyBase().index + "].internal()._idToPoint(";
		String[] references = new String[keys.length];
		for (int i = 0; i < references.length; i++)
			references[i] = idToPointName + BuildUtil.writeCall(keys[i]) + ")";
		return references;
	}

	public boolean needsLabels() {
		// Clouds do not need labels
		return vis.tDiagram != VisTypes.Diagram.cloud && !vis.itemsLabel.isEmpty();
	}

	public boolean needsTooltips() {
		return !vis.itemsTooltip.isEmpty();
	}

	/**
	 * Return the distance at which we want snap for tooltips to operate.
	 * @return a distance in pixels, with &lt;= 0 meaning no snap is required.
	 */
	public int tooltipSnapDistance() {
		for (Param param : vis.itemsTooltip) {
			if ("snap".equals(param.firstTextModifier())) {
				Double d = param.firstNumericModifier();
				return d == null ? 20 : d.intValue();
			}
		}
		return 0;
	}

	// Returns the element we depend on
	private ElementStructure getDependencyBase() {
		for (Dependency dependency : dependencies) {
			if (dependency.dependent == this) return dependency.base;
		}
		return null;
	}
}
