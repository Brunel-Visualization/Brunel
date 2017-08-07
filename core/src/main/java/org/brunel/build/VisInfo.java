package org.brunel.build;

import org.brunel.build.controls.Controls;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.model.style.StyleSheet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Details of the overall visualization
 */
class VisInfo {
	final int width, height;                            // The expected visualization size
	final Set<ElementStructure> allElements;          // Collection of all elements used
	final Map<Integer, Integer> nesting;        // Which charts are nested within which other ones
	Controls controls;                          // Contains the controls for the current chart
	StyleSheet visStyles;                                // Custom styles for this vis

	VisInfo(int width, int height, BuilderOptions options) {
		this.width = width;
		this.height = height;
		allElements = new LinkedHashSet<>();
		nesting = new LinkedHashMap<>();
		controls = new Controls(options);
		visStyles = new StyleSheet();
	}

	public String getLanguage() {
		for (ElementStructure e : allElements) {
			String locale = e.vis.fLocale;
			if (locale != null) return locale;
		};
		return Locale.getDefault().getLanguage();
	}
}
