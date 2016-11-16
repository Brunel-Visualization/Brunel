package org.brunel.build.d3.element;

import org.brunel.build.util.ScriptWriter;

/**
 * Defines how to draw an edge in a graph or tree
 */
public class EdgeBuilder {

	private final ScriptWriter out;
	private final boolean polar;

	public EdgeBuilder(ScriptWriter out, boolean polar) {
		this.out = out;
		this.polar = polar;
	}

	public void defineLocation() {
		writeEdgePlacement("target");
	}

	public void write(String groupName) {
		// Create paths for the added items, and grow the from the source
		out.add("var added = " + "edgeGroup" + ".enter().append('path').attr('class', 'edge')");
		writeEdgePlacement("source");
		out.endStatement();

		// Create paths for all items, and transition them to the final locations
		out.add("BrunelD3.transition(" + groupName + ".merge(added), transitionMillis)");
		writeEdgePlacement("target");
		out.endStatement();
	}

	private void writeEdgePlacement(String target) {

		out.addChained("attr('d', function(d) {")
				.indentMore().indentMore().onNewLine();

		if (polar) {
			out.add("var r1 = d.source.y, a1 = d.source.x, r2 = d." + target + ".y, a2 = d." + target + ".x, r = (r1+r2)/2").endStatement()
					.add("return 'M' + scale_x(r1*Math.cos(a1)) + ',' + scale_y(r1*Math.sin(a1)) ")
					.continueOnNextLine().add(" + 'Q' +  scale_x(r*Math.cos(a2)) + ',' + scale_y(r*Math.sin(a2))")
					.continueOnNextLine().add(" + ' ' +  scale_x(r2*Math.cos(a2)) + ',' + scale_y(r2*Math.sin(a2))")
					.endStatement();
		} else {
			out.add("var x1 =  scale_x(d.source.y), y1 = scale_y(d.source.x), x2 = scale_x(d." + target + ".y), y2 = scale_y(d." + target + ".x)").endStatement()
					.add("return 'M' + x1 + ',' + y1 ")
					.continueOnNextLine().add(" + 'C' + (x1+x2)/2 + ',' + y1")
					.continueOnNextLine().add(" + ' ' + (x1+x2)/2 + ',' + y2")
					.continueOnNextLine().add(" + ' ' + x2 + ',' + y2")
					.endStatement();
		}
		out.indentLess().indentLess().add("})");

	}

}
