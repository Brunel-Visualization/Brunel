Brunel Visualization For Jupyter/IPython Notebooks
===================================================
.. image:: https://raw.github.com/Brunel-Visualization/Brunel/master/brunel.png

Brunel defines a highly succinct and novel language that produces interactive data visualizations using ``pandas DataFrame`` objects. The language is well suited for both data scientists and more aggressive business users. The system interprets the language syntax and produces live visualizations directly within Jupyter notebooks.

* Articles and notes on Brunel can be found in the `brunelvis.org <http://www.brunelvis.org>`_ blog.  Videos are available on `YouTube <https://www.youtube.com/channel/UClXE1IhLQs6NpdMd0X8jALA>`_.
* Details about the language can be found in the `Brunel Language Tutorial <http://brunel.mybluemix.net/docs>`_
* Examples can be found in the `Brunel Visualization Gallery  <https://github.com/Brunel-Visualization/Brunel/wiki>`_ and the `Brunel Visualization Cookbook <https://github.com/Brunel-Visualization/Brunel/wiki/Brunel-Visualization-Cookbook>`_.
* See the `Release Change Log <https://github.com/Brunel-Visualization/Brunel/blob/master/CHANGELOG.md>`_ for new features and changes.


Please report any issues on our `Github  <https://github.com/Brunel-Visualization/Brunel>`_
site.  Q&A available on `Gitter  <https://gitter.im/Brunel-Visualization/Brunel>`_

Dependencies
------------

* Brunel Visualization currently only works in IPython/Jupyter notebooks which must be installed prior to installing Brunel.
* ``Java 1.7+`` must be installed
* It is likely that environment variable ``JAVA_HOME`` also needs to be properly set to the location of the Java installation.

Installation
---------------

``pip install brunel``

Note, if after installing the visualizations are not visible try installing using:

``pip install brunel --user``


Example
----------------
Sample code that reads data from a ``CSV`` file and creates a simple bar chart of averages using the Brunel magic function::

    import pandas as pd
    import brunel

    cars = pd.read_csv("data/Cars.csv")

    %brunel data('cars') x(origin) y(horsepower) mean(horsepower) bar tooltip(#all) :: width=300, height=300


