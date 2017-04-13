# Brunel For Jupyter (R - IRkernel)

This project contains code for integrating Brunel into Jupyter using the [IRkernel](https://github.com/IRkernel/IRkernel) (for use with the R language).  
[rJava](https://cran.r-project.org/web/packages/rJava/index.html) is used for the integration between R and Java for Brunel Visualization.

## Pre-requisites
* The [IRkernel](http://irkernel.github.io/) must be installed into Jupyter.  This code will only work via a Jupyter notebook.  It will not work in R desktop.
* Java 1.7 or greater must be installed
* The `JAVA_HOME` env variable must be set to the location of the `Java` installation
* Install [Brunel Visualization for Jupyter Notebooks](https://pypi.python.org/pypi/brunel):  `pip install brunel`
* On Win64, you may also need to modify your `PATH` to include `jvm.dll`.  Try it first without doing this to see if it works.  See [this StackOverflow issue](http://stackoverflow.com/questions/7019912/using-the-rjava-package-on-win7-64-bit-with-r) for more information.


## Setup For Usage

### Install:

Important:  Install Brunel Visualization into R using R desktop, not from within IRkernel in Jupyter.

Installing Brunel for R requires the [devtools](https://cran.r-project.org/web/packages/devtools/README.html) library. 
First install `devtools` if you have not already and then install the brunel package.  For example:

```
install.packages("devtools")
devtools::install_github("Brunel-Visualization/Brunel", subdir="R", ref="v2.3")
```


## Samples

A sample notebook is included along with the required data in `/examples`.  Below is an example of Brunel code as used within R:

```
 library(brunel)
 brunel ("x(mpg) y(horsepower)  mean(horsepower) color(origin) tooltip(name)", data=cars)
```
or
```
 brunel ("data('cars') x(mpg) y(horsepower)  mean(horsepower) color(origin) tooltip(name)")
```