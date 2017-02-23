package org.brunel.build.guides;

import org.brunel.action.Param;
import org.brunel.build.info.ChartStructure;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes.Axes;

import java.util.Map;

/**
 * Defines the axis we want to create
 */
public final class AxisRequirement {

	final Axes dimension;                // Which dimension
	final int index;                    // Which axis within that dimension (currently only used for parallel axes)

	final int ticks;                    // Required ticks to show
	final String title;                    // Required title
	final boolean grid;                    // Show grid?

	public AxisRequirement(Axes dimension, int index) {
		this.dimension = dimension;
		this.index = index;
		this.title = null;
		this.grid = false;
		this.ticks = 9999;
	}

	private AxisRequirement(Axes dimension, int index, int ticks, String name, boolean grid) {
		this.dimension = dimension;
		this.index = index;
		this.ticks = ticks;
		this.title = name;
		this.grid = grid;
	}

	private AxisRequirement merge(Param[] params) {
		AxisRequirement result = this;
		for (Param p : params) {
			if (p.type() == Param.Type.number) {
				int newTicks = Math.min((int) p.asDouble(), result.ticks);
				result = new AxisRequirement(dimension, index, newTicks, result.title, result.grid);
			} else if (p.type() == Param.Type.string) {
				String newTitle = p.asString();
				result = new AxisRequirement(dimension, index, result.ticks, newTitle, result.grid);
			} else if (p.type() == Param.Type.option) {
				if ("grid".equals(p.asString()))
					result = new AxisRequirement(dimension, index, result.ticks, result.title, true);
			}
		}
		return result;
	}

	/**
	 * Create a consolidated axis definition.
	 * A chart may have many elements, and we merge all these together to create a single
	 * requirement for the axes we want
	 *
	 * @param structure the chart to build for
	 * @return a pair of axes [x, y]
	 */
	static AxisRequirement makeCombinedAxis(Axes which, ChartStructure structure) {
		if (structure.diagram != null) return null;            // Diagrams mean no axis
		boolean auto = true;                                // If true, the user made no request

		// The default is unbounded ticks, no required title and no grid
		AxisRequirement result = new AxisRequirement(which, -1);

		// Rules:
		// none overrides everything and no axes are used
		// auto or no parameters means that we want default axes for this chart
		// x or y means that we wish to define just that axis

		// The rule here is that we add axes as much as possible, so presence overrides lack of presence
		for (VisSingle e : structure.elements) {
			// None means none -- return nothing
			if (e.fAxes.containsKey(Axes.none)) return null;

			for (Map.Entry<Axes, Param[]> p : e.fAxes.entrySet()) {
				auto = false;                                // Any axis statement means we do not use defaults
				if (p.getKey() == which)                    // Merge current definition with parameters
					result = result.merge(p.getValue());
			}
		}

		if (auto) {
			// There were no axis statements, so we choose based on the coordinate system
			// No axes desired for nested or polar charts
			if (structure.coordinates.isPolar() || structure.nested()) return null;
			return result;
		} else {
			// Honor exactly the user definition
			return result;
		}
	}

}
