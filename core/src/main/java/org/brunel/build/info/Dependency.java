package org.brunel.build.info;

/**
 * Thsi defines a dependency between two elements, such as between a node and and edge element.
 */
public class Dependency {
	public final ElementStructure base;            // This is what defines the positions
	public final ElementStructure dependent;    // This is what uses the defined positions

	public Dependency(ElementStructure base, ElementStructure dependent) {
		this.base = base;
		this.dependent = dependent;
	}

	// Attaches the dependency to its source and target
	public void attach() {
		base.dependencies.add(this);
		dependent.dependencies.add(this);
	}
}
