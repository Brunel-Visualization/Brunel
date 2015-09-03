# Brunel For Jupyter (IPython)

This project contains code for installing Brunel into Jupyter as well as a `python` package to use Brunel (currently `python 3`).  Currently Brunel execution happens via a web service to produce the resulting visualization in the notebook using Jupyter's Javascript integration features.

## Dependencies

Brunel for the `python` language requires the [pandas](http://pandas.pydata.org/) library.  For execution, start the Brunel web service using:
  
  ```gradle cargoRunLocal```
  
Be sure the service location is set in the BRUNEL_SERVER environment variable.  For example:

```
export BRUNEL_SERVER="http://localhost:8080/brunel"
```

Note:  after installing Brunel into Jupyter, the server location may also be set programmatically in python via:

 ```
 brunel.set_brunel_service_url('http://localhost:9080/brunel')
```

## Setup For Usage

### PIP Install:

Navigate to `/brunel/python` and then:

```
pip install .
```

### Manual Install:
* Run the Gradle task:  `copyWebFilesToNbextensions`.
* Ensure your local environment variable `PYTHONPATH` includes the `brunel` folder

Note:  You will need to restart a running kernel after installation.   If the visualizations/widgets do not appear at first, try
reloading the page, restarting the kernel, and re-executing the cells.


## Samples

Two sample notebooks are included in `/examples` along with the data they use.  Below is an example of Brunel code as used within IPython:

```
 brunel x(mpg) y(horsepower) bin(mpg) mean(horsepower) color(origin) tooltip(name) bar   :: width=800, height=600, output=d3
```
