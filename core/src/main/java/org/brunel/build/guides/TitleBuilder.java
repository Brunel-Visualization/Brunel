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

package org.brunel.build.guides;

import org.brunel.build.util.ModelUtil;
import org.brunel.build.util.Padding;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.model.VisElement;
import org.brunel.model.style.StyleTarget;

/**
 * An abstract class for a builder to construct text content for titles.
 * Handles singles lines of text only at present
 */
public abstract class TitleBuilder {
	protected final VisElement vis;                  // Owning vis
	protected final StyleTarget styleTarget;        // Style target
	protected final Padding padding;                // Space around element
	protected final int fontSize;                   // font height
	private final String alignment;                 // left, middle, right
	private String content;                         // The text to show

	/**
	 * An abstract class for a builder to construct text content for titles
	 * Used for char titles, axis titles and legend titles
	 *
	 * @param vis         the element that defines the item
	 * @param styleTarget the css style target
	 */
	public TitleBuilder(VisElement vis, StyleTarget styleTarget) {
		this.vis = vis;
		this.styleTarget = styleTarget;
		alignment = ModelUtil.getTitlePosition(vis, styleTarget);
		padding = ModelUtil.getPadding(vis, styleTarget, 4);
		fontSize = (int) ModelUtil.getFontSize(vis, styleTarget, 16);
	}

	public String content() {
		if (content == null) {
			String s = makeText();
			content = (s == null || s.isEmpty()) ? null : Data.quote(s);
		}
		return content;
	}

	public int verticalSpace() {
		return content() == null ? 0 : Math.round(fontSize + padding.vertical());
	}

	/**
	 * Write the title content
	 *
	 * @param group the owning group (D3 selection) into which to place the text
	 * @param out   writer
	 */
	public void writeContent(String group, ScriptWriter out) {
		if (content() == null) return;

		out.add(group + ".append('text').attr('class', '" + Data.join(styleTarget.classes, " ") + "')")
				.add(".text(" + content() + ")");
		defineHorizontalLocation(out);
		defineVerticalLocation(out);
		out.endStatement();
	}

	protected abstract void defineVerticalLocation(ScriptWriter out);

	protected abstract String makeText();

	protected String[] getXOffsets() {
		// The defaults are simply in terms of the enclosing object
		return new String[]{"'0%'", "'50%'", "'100%'"};
	}

	private void defineHorizontalLocation(ScriptWriter out) {
		String[] xOffsets = getXOffsets();

		String xLoc, anchor;
		int dx;
		switch (alignment) {
			case "left":
				xLoc = xOffsets[0];
				anchor = "start";
				dx = padding.left;
				break;
			case "right":
				xLoc = xOffsets[2];
				anchor = "end";
				dx = -padding.right;
				break;
			default:
				xLoc = xOffsets[1];
				anchor = "middle";
				dx = (padding.left - padding.right) / 2;
				break;
		}
		out.add(".style('text-anchor', '" + anchor + "')")
				.addChained("attr('x'," + xLoc + ")");
		if (dx != 0) out.addChained("attr('dx',", dx, ")");
	}
}
