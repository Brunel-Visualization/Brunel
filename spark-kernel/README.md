# Brunel For [Apache Toree](https://github.com/apache/incubator-toree):  Scala Notebooks for Spark
(Formerly known as "spark-kernel")

This project contains code for integrating Brunel into Apache Toree allowing scala notebooks to use Brunel with Apache Spark 2.0 in Jupyter.  Apache Spark 2.0 is required beginning with Brunel version 2.2.

## Dependencies

* The Apache Toree kernel must be installed into Jupyter.

## Setup For Usage

### Install:

Issue the following magic

```
%AddJar -magic https://brunelvis.org/jar/spark-kernel-brunel-all-2.2.jar
```

Notes:
* Brunel version 2.0+ requires Toree "dev8" or higher.  
* If you are upgrading to a new version of Brunel and the graphs do not appear, try executing the cell, then save the notebook, then reload the page in the browser.


## Samples

Sample notebooks are included along with the required data in `/examples`.  Below is an example of Brunel code as used within Toree:

```
 %%brunel data('df') map x(State) color(Winter) tooltip(#all)
```

...where "df" is a variable assigned to a Spark DataFrame containing the data to visualize.
