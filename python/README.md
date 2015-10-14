# Brunel For Jupyter (IPython)

This project contains code for installing Brunel into Jupyter as well as a `python` package to use Brunel (currently `python 3`).  Currently Brunel Java execution happens via `jpype-py3` to produce the resulting visualization in the notebook using Jupyter's Javascript integration features.

## Dependencies

* Brunel for the `python` language requires the [pandas](http://pandas.pydata.org/) library and [jpype-py3](https://pypi.python.org/pypi/JPype1-py3).
* Java 1.7+ must be installed
* It is likely that environment variable `JAVA_HOME` also needs to be properly set to the location of the Java installation.

## Setup For Usage

### PIP Install:

We are working on a PyPI deployment.  For now, after building Brunel using Gradle, navigate to `/brunel/python` and then:

```
pip install .
```

### Manual Install:
* Do a full Gradle build
* Run the Gradle task:  `copyWebFilesToNbextensions`.
* Ensure your local environment variable `PYTHONPATH` includes the `brunel` folder

Note:  You will need to restart a running kernel after installation.   If the visualizations/widgets do not appear at first, try
reloading the page, restarting the kernel, and re-executing the cells.


## Samples

Two sample notebooks are included in `/examples` along with the data they use.  Below is an example of Brunel code as used within IPython:

```
 brunel x(mpg) y(horsepower) bin(mpg) mean(horsepower) color(origin) tooltip(name) bar   :: width=800, height=600, output=d3
```
