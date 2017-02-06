package org.brunel.data.diagram;

/**
 * Class capturing information needed to create a ribbon for a chord chart
 */
public class ChordGroup {
	public final int index;
	public final Object name;
	public double value;
	public double startAngle;
	public double endAngle;

	public ChordGroup(int index, Object name) {
		this.index = index;
		this.name = name;
	}
}
