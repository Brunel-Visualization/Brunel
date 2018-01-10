package org.brunel.build;

import org.brunel.build.controls.Controls;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.BuilderOptions;
import org.brunel.model.VisElement;
import org.brunel.model.style.StyleSheet;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Details of the overall visualization
 */
class VisInfo {
	final int width, height;                            // The expected visualization size
	final Set<ElementStructure> allElements;          // Collection of all elements used
	Controls controls;                          // Contains the controls for the current chart
	StyleSheet visStyles;                                // Custom styles for this vis

	VisInfo(int width, int height, BuilderOptions options) {
		this.width = width;
		this.height = height;
		allElements = new LinkedHashSet<>();
		controls = new Controls(options);
		visStyles = new StyleSheet();
	}

  public ElementStructure findElement(VisElement target) {
    for (ElementStructure e : allElements) {
      if (e.vis == target) return e;
    }
    throw new IllegalStateException("Unfound target: " + target);
  }

  public String getLanguage() {
		for (ElementStructure e : allElements) {
			String locale = e.vis.fLocale;
			if (locale != null) return locale;
		};
		return Locale.getDefault().getLanguage();
	}
}
