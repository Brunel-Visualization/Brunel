package org.brunel.build.data;

/**
 * Parameters used to transform a data set
 */
final class TransformParameters {
	/**
	 * Command to add constant fields to the data
	 */
	String constantsCommand;

	/**
	 * Command to filter the data
	 */
	String filterCommand;

	/**
	 * Command to extract multiple values from a field's text
	 */
	String eachCommand;

	/**
	 * Command to transform the data without aggregation (bin, rank, inner, outer)
	 */
	String transformCommand;

	/**
	 * Command to aggregate the data
	 */
	String summaryCommand;

	/**
	 * Command to stack the data
	 */
	String stackCommand;

	/**
	 * Command to sort data and data categories
	 */
	String sortCommand;

	/**
	 * Command to sort data only
	 */
	String sortRowsCommand;

	/**
	 * Command to transform to a series
	 */
	String seriesCommand;

	/**
	 * Command to set the number of rows to a fixed number
	 */
	String rowCountCommand;

	/**
	 * Command to retain only used fields
	 */
	String usedCommand;
}
