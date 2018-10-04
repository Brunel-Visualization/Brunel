package org.brunel.build;

/**
 * Defines the space needed for the chart
 */
public class ChartLocation {
	public final double[] insets;                                // insets as fractions

	public int left, right, top, bottom, width, height;   // Chart extents, relative to parent vis

	private int legendWidth;                                    // Width of legend (on right)
	public int axisTop, axisLeft, axisBottom, axisRight;       // space needed for axes
	private int titleTop, titleBottom;                            // space needed for titles

	/**
	 * Define a chart layout relative to the parent (the vis, usually)
	 *
	 * @param parentWidth  parent width
	 * @param parentHeight parent height
	 * @param locPercent   percentage locations, defined as numbers 0-100 , in order TLBR
	 */
	public ChartLocation(int parentWidth, int parentHeight, double[] locPercent) {

		this.insets = new double[]{locPercent[0] / 100.0, locPercent[1] / 100.0,
				locPercent[2] / 100.0, locPercent[3] / 100.0};

		this.left = (int) Math.round(parentWidth * insets[1]);
		this.right = (int) Math.round(parentWidth * insets[3]);
		this.top = (int) Math.round(parentHeight * insets[0]);
		this.bottom = (int) Math.round(parentHeight * insets[2]);

		this.width = this.right - this.left;
		this.height = this.bottom - this.top;
	}

	public int getAvailableWidth() {
		int[] inner = innerMargins();
		return width - inner[1] - inner[3];
	}

	public int getAvailableHeight() {
		int[] inner = innerMargins();
		return height - inner[0] - inner[2];
	}

	/**
	 * Return the margins to inset to for the inner location, in pixels
	 *
	 * @return rectangle in T L B R form
	 */
	public int[] innerMargins() {
		return new int[]{
				Math.max(axisTop, titleTop),                    // A little overlap here is OK
				axisLeft,                                        // Only the axis is here
				Math.max(axisTop, axisBottom + titleBottom),    // Ensure a little space for the V axis to overflow
				Math.max(axisRight, legendWidth)                // Space for the axis or the legend
		};
	}

	/**
	 * Return the currently defined inner rectangle (inside the margins)
	 *
	 * @return rectangle in T L B R form
	 */
	public int[] innerRectangle() {
		int[] result = innerMargins();
		result[0] += top;
		result[1] += left;
		result[2] = bottom - result[2];
		result[3] = right - result[3];
		return result;
	}

	public void setAxisMargins(int axisTop, int axisLeft, int axisBottom, int axisRight) {
		this.axisTop = axisTop;
		this.axisLeft = axisLeft;
		this.axisBottom = axisBottom;
		this.axisRight = axisRight;
	}

	public void setLegendWidth(int legendWidth) {
		this.legendWidth = legendWidth;
	}

	public void setTitleMargins(int titleTop, int titleBottom) {
		this.titleTop = titleTop;
		this.titleBottom = titleBottom;
	}
}
