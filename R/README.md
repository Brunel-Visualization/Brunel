# Brunel For Jupyter (R - IRkernel)

This project contains code for integrating Brunel into Jupyter using the [IRkernel](https://github.com/IRkernel/IRkernel) (for use with the R language).  

## Dependencies

* The [IRkernel](https://github.com/IRkernel/IRkernel) must be installed into Jupyter.  This code will only work via a Jupyter notebook.  It will not work in R desktop.
* Follow set up instructions in `/python` since this includes the installation of Brunel for Jupyter

## Setup For Usage

### Install:

Installing Brunel for R requires the [devtools](https://cran.r-project.org/web/packages/devtools/README.html) library. 
First install `devtools`, then set the working directory to the location of the Brunel R code and then install the brunel package.  For example:

```
install.packages("devtools")
library("devtools")
setwd('/brunel/R')
install('brunel')
```


## Samples

A sample notebook is included along with the required data in `/examples`.  Below is an example of Brunel code as used within R:

```
 library(brunel)
 brunel ("x(mpg) y(horsepower)  mean(horsepower) color(origin) tooltip(name) ", data=cars)
```