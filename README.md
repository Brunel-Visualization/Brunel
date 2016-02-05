# Brunel Visualization [![Build Status](https://travis-ci.org/Brunel-Visualization/Brunel.svg?branch=master)](https://travis-ci.org/Brunel-Visualization/Brunel)
![Brunel Visualization](https://raw.github.com/Brunel-Visualization/Brunel/master/brunel.png)

## What is Brunel?

Brunel defines a highly succinct and novel language that defines interactive data visualizations
based on tabular data.  The language is well suited for both data scientists and more aggressive business users.
The system interprets the language and produces visualizations using the user's choice of existing lower-level
visualization technologies typically used by application engineers such as RAVE or D3.
It can operate stand-alone and integrated into Jupyter (iPython) notebooks with further integrations as well as other low-level
rendering support depending on the desires of the community.

Articles and notes on Brunel can be found in the [brunelvis.org blog](http://www.brunelvis.org).

## Zero to Visualization in Sixty Seconds

Users:
* [Try it out online](http://brunel.mybluemix.net/gallery_app/renderer?title=Bubble+chart+of+2000+games&brunel_src=data%28%27http%3A%2F%2Fwillsfamily.org%2Ffiles%2Fvis%2Fdata%2FBGG+Top+2000+Games.csv%27%29+bubble+color%28rating%29+size%28voters%29+sort%28rating%29+label%28title%29+tooltip%28title%2C+%23all%29+legends%28none%29+style%28%27*+%7Bfont-size%3A+7pt%7D%27%29&description=A+simple+bubble+chart+showing+the+top+ranked+games.+The+color+shows+the+BGG+rating+and+the+size+of+each+bubble+represents+the+number+of+voters+for+that+game.+The+data+is+already+sorted+by+rank%2C+so+no+sort+was+needed.+Data+is+from+March+2015) (and add your own data)
* Try it out in Jupyter notebooks: [Python](https://pypi.python.org/pypi/brunel), [R](https://github.com/Brunel-Visualization/Brunel/tree/master/R), or [Spark](https://github.com/Brunel-Visualization/Brunel/tree/master/spark-kernel)
* View the [gallery](https://github.com/Brunel-Visualization/Brunel/wiki)
* Check out the [Brunel Visualization Cookbook](https://github.com/Brunel-Visualization/Brunel/wiki/Brunel-Visualization-Cookbook)
* Use the interactive [language tutorial](http://brunel.mybluemix.net/docs)

Developers:
* Download the [latest build](https://github.com/Brunel-Visualization/Brunel/releases)
* Sample Java ["Hello World"](https://github.com/Brunel-Visualization/Brunel/blob/master/etc/src/main/java/org/brunel/app/SampleApp.java) App
* Sample Javascript ["Hello World"](https://github.com/Brunel-Visualization/Brunel/blob/master/etc/src/main/resources/html/SampleJSApp.html) App
* Read how to [Build and deploy the code](https://github.com/Brunel-Visualization/Brunel/wiki/Project-Structure-and-Builds)

## Core Features of Brunel

* Supports standard charts, diagrams, maps and network graphs
* Automatically chooses good transforms, mappings, and formatting for your data
* Allows multiple combinations of visualization elements overlay bars, lines, paths, areas and text freely and in a coordinated space.
* Handles building structures for D3 diagram like hierarchies, treemaps and chords
* Handles data ranges, binning and stacking automatically
* Automatically wraps and fits text, even when animating
* Intelligently works out a good layout for the chart aspects, taking into account the data (so you don't have to guess axis sizes, for example)
* Provides flexible interactivity including tooltips, pan/zoom and interactive brushing
* Coordinates multiple visualizations in the same space, including interactive brushing
* Adds features such as Word clouds and paths with smoothly varying size
* Data engine is in the Javascript code, so high-speed interactivity works with binning, aggregation and filtering

## How to use Brunel

### Online; no coding required
The simplest way to get started to go to the [online builder application](http://brunel.mybluemix.net/gallery_app/renderer?title=Bubble+chart+of+2000+games&brunel_src=data%28%27http%3A%2F%2Fwillsfamily.org%2Ffiles%2Fvis%2Fdata%2FBGG+Top+2000+Games.csv%27%29+bubble+color%28rating%29+size%28voters%29+sort%28rating%29+label%28title%29+tooltip%28title%2C+%23all%29+legends%28none%29+style%28%27*+%7Bfont-size%3A+7pt%7D%27%29&description=A+simple+bubble+chart+showing+the+top+ranked+games.+The+color+shows+the+BGG+rating+and+the+size+of+each+bubble+represents+the+number+of+voters+for+that+game.+The+data+is+already+sorted+by+rank%2C+so+no+sort+was+needed.+Data+is+from+March+2015),
drop in your own data or reference your data via a URL and build a custom visualization. Once you have a chart you want to share, you can:
* Just take a screenshot of the page or download the SVG to get a cool picture
* Copy the Javascript directly into wherever you want to be completely independent of the Brunel service -- this is a
 great way to rapidly generate D3 Javascript ready for you to modify and deploy.
* Take the HTML iframe snippet and embed it in a HTML page to have a live call to the Brunel Service

If you are writing an application, you can use the web service to generate Javascript for you on the fly, just as the
iframe snippet does -- copy that format and use the call to the Brunel service from your application.

### Using the code
If you want to use Brunel from Java, download the [latest build](https://github.com/Brunel-Visualization/Brunel/releases)
and get started! You can also set up your own web server, or add Brunel to an existing one. More details can be found
[here](https://github.com/Brunel-Visualization/Brunel/wiki/Project-Structure-and-Builds).

Of course, the purpose of github is for you to grab and modify the code easily -- do that anyway you want!

If you do something cool and fun, consider contributing it back!

## License

Brunel is licensed under the Apache License, Version 2.0 (the "License")
You may obtain a copy of the License at
[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

