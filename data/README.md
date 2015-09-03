# Data

This project contains lightweight code that enables a set of data manipulations to be performed on tables.
The goal here is NOT to create Yet Another DataFrame, but to enable only those features that are needed for
interactive manipulation of visualizations.

Specifically, this is intended to support:

 * Binning
 * Aggregation
 * Sorting

This project is fully translatable using the "Translation" project, and so can be used within a browser to
provide client-side interactive graphics. The project therefore requires the Translation project, but is otherwise
dependency free.

## Field, Dataset, Data

These are the three core classes:
 * `Field` defines a column of data, together with meta-data on it
 * `DataSet` is a lightly enhanced array of fields (a "data table")
 * `Data` contains static methods that manipulate data

Properties in `Field` are used to store numeric and date statistics, information on hwo the field has been manipulated,
defined as a date or binned, and so on. `Data` allows conversion to numeric or dates, the creation of `Field`s and,
binning, and some utility methods.


## Sort, Summarize

Fields are binned individually, but an entire `Dataset` is summarized or sorted, and these two classes each do that.
They have a single entry point, and take a specification object that dictates how they should perform their tasks.
They do not modify the existing data set, but instead create a new one based on it.

## Auto

This class builds scales automatically for a Field. It knows how dates and log scales work, and the results can be
used to set up an actual scale. They are also used in binning to create automatic bins at nice ranges

## CSV

This is a simple, fragile, translatable CSV reader, intended mainly for testing. A real-world application should use a
more robust data reader to create the necessary fields.
