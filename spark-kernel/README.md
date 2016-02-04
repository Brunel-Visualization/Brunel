# Brunel For [Apache Toree](https://github.com/apache/incubator-toree):  Scala Notebooks for Spark
(Formerly known as "spark-kernel")

This project contains code for integrating Brunel into Apache Toree allowing scala notebooks to use Brunel with Apache Spark in Jupyter.

## Dependencies

* The Apache Toree kernel must be installed into Jupyter.  

## Setup For Usage

### Install:

Issue the following magic

```
%AddJar -magic http://www.brunelvis.org/jar/spark-kernel-brunel-all-1.0.jar 
```


## Samples

Sample notebooks are included along with the required data in `/examples`.  Below is an example of Brunel code as used within Toree:

```
 %%brunel data('df') map x(State) color(Winter) tooltip(#all)
```

...where "df" is a variable assigned to a Spark DataFrame containing the data to visualize.