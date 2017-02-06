package org.brunel.data.diagram;

/**
 * Class capturing information needed to create a ribbon for a chord chart
 */
public class ChordRibbon {
	public final RibbonNode source, target;
	public final int row;
	public final double size;

	public ChordRibbon(int sourceIndex, int targetIndex, int row, double size) {
		source = new RibbonNode(sourceIndex);
		target = new RibbonNode(targetIndex);
		this.row = row;
		this.size = size;
	}

	public final static class RibbonNode {
		public double startAngle, endAngle;
		public final int index;

		public RibbonNode(int index) {
			this.index = index;
		}
	}
}
