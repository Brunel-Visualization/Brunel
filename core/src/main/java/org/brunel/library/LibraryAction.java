package org.brunel.library;

import org.brunel.action.Action;

/**
 * A library action is a normal action together with some extra information
 */
public class LibraryAction extends Action {

	private final String name;
	private final String description;
	private final double score;

	LibraryAction(Action base, String name, String description, double score) {
		super(base.steps);
		this.name = name;
		this.description = description;
		this.score = score;
	}

	/**
	 * Comparison puts highest scores at the start of a sorted list
	 *
	 * @param o item to compare to
	 * @return standard comparison result
	 */
	public int compareTo(Action o) {
		// if we are asked to compare ourself to a non-action item, throw an exception
		if (!(o instanceof LibraryAction))
			throw new IllegalStateException("Do not attempt to compare Library and non-library actions");

		int compare = Double.compare(((LibraryAction) o).score, this.score);
		return compare != 0 ? compare : super.compareTo(o);
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public double getScore() {
		return score;
	}
}
