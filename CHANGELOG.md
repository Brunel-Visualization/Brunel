# 2.3 Release Notes

## Data Pipeline Change

To support Brunel's increasing use in large data applications, we have modified the way we handle data sets.
Previously, all rows of data were copied into the Javascript for processing, even if a summary was being performed.
This could be very inefficient for large data sets that were being summarized. In version 2.3 we have modified the
behavior so that if we detect that not all the data are needed, we only send summarized data to the Javascript front
end. Further, the data set is tagged as summarized so that the data processing steps for summarizing it are not
performed a second time.
 
The rules for when we can summarize a data set and pass it down are complex, but in general:
 
 * If there is no summary operation requested (no explicit sum, mean, or other summary operation and no use of #count),
   then summarization is not performed.
 * If a data set is used for two elements, then they must have the same way of summarizing the data 
  (same summary, transforms, each statements), otherwise summarization is not performed
 * filter statements (since they dynamically change the data in the client) stop summarization of a data set 
 * Using the Javascript API to pre-modify the data (by defining the `pre` command) is not recognized because it is
   a client-side operation. If you wish to use `pre` it is strong recommended you change the build option for data to
   be `columns` to stop automatic summarization (see below)
   
### Builder Options

You can specify the field `includeData` in the `BuilderOptions` structure to modify the data pipeline. The default
value is `minimal`:

 * `none` - no data set information will be written. It is the responsibility of the user to provide data sets
 * `full` - all data passed to Brunel will be sent to the Javascript, even columns not needed for the chart
 * `columns` - only the columns needed for the visualization will be written (this was the version 2.2 default)
 * `minimal` - only columns needed will be passed down, and summarization will be performed if possible

## Multiple labels

It is now possible to have multiple labels on a shape. This is done by specifying an optional location as part of
each label element. All labels at the same location are concatenated into a single string. 

The location can be specified as a list composing location parts, or as a string linking them with "-".
The allowable elements are: `top`, `bottom`, `middle`  for the vertical location; `left`, `right`, `center`
for the horizontal, and `inside`, `outside` for whether to show the label inside or outside the shape.

Thus the following are all legal label comments:

       label(name:top, ':':top, #count:top)
       label(name:'top-outside', #count:'bottom-right')
       label(name:'outside-top', #count:[bottom,right])


## Symbol Aesthetic

This aesthetic is used only for point elements, and replaces the default circle with a glyph, drawn as a path.
Brunel defines two sets of symbols, a *basic* and an *extended* set. Basic symbols are:

    circle, square, triangle, diamond, 
    cross, pentagon, star, hexagon

and extended symbols include the basic symbols and

    plus, alert, arrowup, arrowdown, chart, circleOutline, database, 
    person, question, happy, neutral, sad, thumbup, thumbdown, 
    lightbulb, flag, female, male, heart, pencil, info.

The basic set are the ones used if no parameters are specified in the aesthetic syntax. The extended ones are
used only if specifically requested. The icons have been taken from the open source collection "Google Material
Designs". Symbol names can also be used in the style syntax to apply across a whole element.

The full syntax for `symbol` is: `symbol(field:[name1, name2, ...]:uri)`
When a set of names are given, then only the named symbols are used in the symbol mapping, with the given order.
If the URI is given, then the given URI should contain an SVG file containing a list of valid SVG `symbol`
definitions, each with a unique ID. If the IDs all have an identical prefix or suffix, those are stripped. Here are
some examples of symbols; also included is the URL of a set of test symbols

    point x(state) y(density) size(population:300%) symbol(density)
    bubble x(region) symbol(presidential_choice:[heart, flag]) label(abbr)
    x(region) y(girls_name) size(#count) style('symbol:female') + x(region) y(boys_name) size(#count) style('symbol:male')
    x(summer) y(winter) symbol(region:'http://brunelvis.org/test/testsym.svg')
    
    
### Legends for symbols

Previously, only `color` would display a legend. Now, a legend will be shown for color and symbol mappings. 
If only one of `color` or `symbol` is specified, that will be shown in a legend (subject to the options in the 
legend statement, if any exists). If both are specified, and they refer to the same field with the same
transformations and summaries a combined legend will be shown with both aesthetics applied. If both are specified,
but they are not showing compatible information, the color legend only will be shown. 

## Limited gradient support

Brunel defines four gradients which can be used in style statements. A typical use might be:
 
    style('fill:url(#_gradient_radial_red);stroke:none')
    
The four gradients are `_gradient_linear_blue`, `_gradient_linear_red`, `_gradient_radial_blue`, `_gradient_radial_red`.
As you might guess form the names, two are linear and two are radial. The colors have been chosen to match the 
first two default brunel element colors.

Note that use of gradient styles overrides any color or opacity aesthetic on the element.


## Map quality option 'full'

Added the map quality option `full` to denote a map with the full natural earth data, no quantization
except to regularize the maps. These map files are about twice as large as the `high` quality files,
but for most purposes are very similar in appearance.

## css aesthetic now also applies to labels

The CSS aesthetic now also applies to labels, so you can specify styles for labels by mapping to
a field, like so:

`map css(region:'':names) key(state) label(state) style('.label.Midwest {font-size:16px;font-weight:bold}')`

This will show labels for the midwest states in the designated style.

## Edge styling

We now support curves and arrows on edges in network, tree and other charts using them.
They can be set with a style `symbol` command to request that style. The valid symbols are given below:

 * `style("symbol:straight")`   -- simple straight lines
 * `style("symbol:arrow")`      -- straight lines with an arrow
 * `style("symbol:curved")`     -- curved lines with no arrowheads
 * `style("symbol:curvedArrow")`-- curved lines with arrowheads

The arrowhead will always be a drawn in a neutral grey color. This is a limitation
of current SVG technology, due to be fixed in SVG 2.0.

## Custom Maps

You can now use custom maps.  Instructions for using GeoJSON files are here:  https://github.com/Brunel-Visualization/Brunel/wiki/Custom-Maps.
The `map` action takes a location to a topojson file and the name of the property used for matching to the data values separated by `#`

`data('boroughs') map('http://localhost:8889/tree/notebooks/data/boroughs.json#BoroName') x(Borough) color(value) label(Borough)`

Note that a field must be present in the data containing values that exactly match the attribute values for the chosen property name.


# 2.2 Release Notes

## Trees and Hierarchies

We have added a number of linked features for trees and, more generally for hierarchies.
Previous support was limited and considered 'experimental'. The new support should be considered
standard.

### Networks

A new optional numeric parameter to `network` controls the balance between attractive and repulsive
forces in the layout; when the value is higher than unity, nodes are forced further apart; lower
than unity and they are more clustered.

Networks have two interactions defined by default, panning and zooming and the ability to drag nodes
around. When nodes are dragged the graph will modify the layout to adapt to the new configuration.


### Trees
-----
A tree assumes the data has a hierarchical structure (much like a bubble chart or a treemap) and so
can be used whenever the data support that structure. Thus we can take a hierarchical display such
as a treemap and simply change the diagram from `treemap` to `tree` to get the desired tree. 

Alternatively, a tree can be defined with two data sets for nodes and links, exactly like a network.
If the data is not actually a tree, extra nodes are dropped to make it so. Thus the example for a
network above can be directly changed to be a tree, although the resulting display makes little
sense -- trees should be reserved for hierarchical data.

For trees, the default interactivity is pan and zoom as usual, but we also add the ability to
double-click on a node to hide or unhide the subtree coming out of it.


### interactivity

To support interactivity for trees, two new interactivity options have been made available:

`interaction(collapse)` -- when double-clicking on a node, the subtree under that node (all its descendants)
will be hidden; you can double-click again to show it. Note that this works also for other hierarchical views
like treemaps and bubble charts, although it is less useful

`interaction(expand)` -- when double-clicking on a node, that node is moved to the root of the hierarchy; 
showing ONLY descendants of that node. Double-click on it again to reverse the operation.
Note that this works also for other hierarchical views like treemaps and bubble charts, and is quite useful!


## Symbols

If the "symbol" style is set for an element and that element is shown as a point, the requested
symbol will be drawn instead of a circle. Valid symbols are all d3 symbols 
( `circle`, `cross`, `diamond`, `square`, `Wye`, `triangle`) 
together with the following brunel extensions:

`star-N` request an 'N' pointed star (N is a number > = 3). `star` defaults to a 5-pointed star.

`poly-N` request an 'N' sided polygon (N is a number > = 3). `poly` defaults to a pentagram.

`person` request an outline of a person as a symbol


## Statistics
`stderr` was added to support visualizations such as error bars.  An optional parameter can be used as a multiplier as in:

`bar x(country) yrange(price) stderr(price:2)`

## CSS aesthetic

This aesthetic tags a generated SVG element with a CSS class based on the field passed in. This
field should be a categorical field, or a binned numeric one, but will work with a numeric field if
provided. Without any parameters, elements will be tagged as having classes "brunel_class_1",
"brunel_class_2" etc. depending on values of the field passed in, and so this can be used, in
combination with setting css styles for these tags, to generate specific looks.

The full syntax for css is: `css(field:prefix:[names|numbers])`. If "prefix" is set, it is used
instead of "brunel_class_" in the generated name "brunel_class_N". If "names" is specified instead
of the default "numbers" the actual value of the field is used in the class name, not just the index
of it. Take care with this option that the field names ae valid identifiers!

## Zoom

zoom levels are now added to the "interior" group for each chart, allowing it to be used for CSS
selection purposes. The classes added are (for various degrees of zoom):

 * `zoomOut zoomOutExtreme`       (more than a factor of 10)
 * `zoomOut zoomOutHigh`          (more than a factor of 5)
 * `zoomOut zoomOut5`
 * `zoomOut zoomOut4`
 * `zoomOut zoomOut3`
 * `zoomOut zoomOut2`
 * `zoomNone`
 * `zoomIn zoomIn2`
 * `zoomIn zoomIn3`
 * `zoomIn zoomIn4`
 * `zoomIn zoomIn5`
 * `zoomIn zoomInHigh`            (more than a factor of 5)
 * `zoomIn zoomInExtreme`         (more than a factor of 10)
 
## Notebooks

Support for Brunel in Toree notebooks using Scala now require Spark 2.0 & Scala 2.11.

# 2.0 Release Notes

## Accessibility

Brunel is now accessible. By specifying the _accessibility_ flag in `BuilderOptions`
(also by using -accessibility as n option for the command-line tools), then Brunel generates
SVG with Aria roles and labels so as to allow aria compliant screen readers to read the
content of items. The content is currently in English only. Veuillez nous excuser.

Brunel adds region roles to major areas, such as elements (in a multi-element chart),
charts (in a multi-chart visualization), axes and legends. This should allow the user to use
a compliant navigation system to navigate through the major blocks and arrive at the one you
desire rapidly.

The system has been tested with Apple's Voice Over technology, but we are actively looking
for feedback on this feature, particularly how we can improve it to make it more useful for
all people, rather than merely compliant.

High-contrast views can be mostly achieved by use of a custom-designed style sheet. This is
not an area we have addressed in this release.

## X and Y coordinates reverse

You can reverse the X and Y dimension scales by adding a `reverse` option to the definition:

    x(summer) y(winter:reverse)

## Axes

### Gridlines
Gridlines have been brought back in Brunel, and additional syntax added for them.
Previously gridlines were generated by default, but were styled to be invisible.
Also, they didn't work well ...

The new way to request gridlines is to use a `grid` modifier on an `axes()` command
to request them, for example:

    x(summer) y(winter) axes(x:grid, y:grid)
    x(summer) y(winter) axes(x:grid:50, y:grid)
    x(Summer) y(Population:log) axes(x, y:grid)

Standard CSS styling applies to the grid lines; you can set it in the style sheet you use,
or define it either for both sets of gridlines, or individually:

    x(Summer) y(Population) axes(x:grid, y:grid) style('.grid {stroke:green}')
    x(Summer) y(Population) axes(x:grid, y:grid) style('.grid{opacity:1}
       .grid.y {stroke-dasharray:5,5} .grid.x {stroke-width:40px; opacity:0.2}')

### Alignment

`label-location` is now supported on styles for axis title locations, and can be used to place the
axes titles relative to the axis. We have also improved support for large title fonts.
Below is an example of this in operation:

    x(Region) y(dem_rep) transpose tooltip(#all)
    style('.axis .title { fill:red; label-location:right; font-size:60px }')

### Padding and mark sizes

We now support padding in the CSS for text elements associated with axes and legends.
padding, padding-left, padding-right, padding-top and padding-bottom are all supported,
with standard units EXCEPT that we do not support percentage padding.

Here is an example of the use with axes:

    bar x(Winter) y(#count) sort(#count) tooltip(#all) bin(Winter)
    style('.axis .title {fill:red;label-location:left; padding-left:1in}
        .tick{fill:blue;padding:10;padding-right:50px}')

We also now use the css size for the tick mark (`.tick line`) to determmine its size.

The following Brunel is long and ugly, but shows all the styles in action:

    x(winter) y(summer) style('.axis.y .tick text{fill:red;padding-right:10px}')
    title('Ugly Style test')
    style('.axis.x .tick text{fill:blue;font-size:1cm; padding:2mm} .axis .tick line{size:-5mm} ')
    style('.axis .title {label-location:left;font-size:20px}')
    style('.axis .title {label-location:left;font-size:20px;font-style:italic;padding-left:1in}')
    style('.header {fill:red;font-size:40px;padding-bottom:50px;label-location:right}')


## Styles

Elements that have been selected now have the css class 'selection' defined for them.
This allows you to use style definitions for custom display of selected elements, as
in the below:

    point x(Longitude) y(Latitude) color(region) size(population:1000%)
    style('.selected {opacity:0.5; stroke-opacity:1; stroke-width:2; stroke-dasharray:2 2}
    .element {opacity:0.2}') interaction(select:mouseover)

Labels for elements that are selected also have the `selected` class defined, so you
can modify selected labels' appearances using styles. In this version, we only
support one position modifier -- `vertical-align`. If this value is set to a pixel value
(such as `20px` or `-30px`) it will move the text the indicated amount AFTER placement

Here is a long sample with a lot of styling going on for text:

    data('sample:whiskey.csv') bar x(category) y(#count) transpose
    size(#selection:[20%, 80%]) sort(#count:ascending) label(category) axes(y)
    style('.label.selected {fill:yellow; text-shadow:0px 0px 4px black; vertical-align:18px; text-transform:uppercase}')
    style('label-location:outside-top-right; text-align:end; padding:1px')
    interaction(select:mouseover)

Another modification was done to how we hand overlapping data labels; previously they were
removed from the display, but now they are given the class `overlap` which our default
style sheet hides, but you can modify to treat any way you want. For example:

    data('sample:whiskey.csv') point x(Age) y(Price) label(category:5)
    style('.overlap {visibility:visible; text-shadow:none; opacity:0.2}')

## Axis ranges

The initial range of a numeric field can be set by defining a range for the `x` or `y`
command, much like a transform. Examples are:

    point x(Longitude:[-100, -80]) y(Latitude:[35, 45])

## Guides

We have added a new feature to allow an element to define a guide. This is described more fully in
the complete documentation, but here is an example showing its use:

    x(winter) y(summer) + guide(y:40+x, y:70, y:'70+10*sin(x)')
    style('.guide1{stroke:red} .guide3 {stroke-dasharray:none}')

## Interactions
A new `animate` command has been added that provides an interactive control to animate a visualization over
the values of a continuous field.  As part of this, labels on continuous filters have improved (particularly for date fields).

The `interaction(select)` command and also the new callback event command `interaction(call:func)`
can now take an event name parameter `snap` that allows interactivity to be fired when the
mouse is near a data item on screen

`interaction(call:func)` has been added; the ability to call bak to a javascript function to handle
events in special ways. A new page in the documentation describes the API.

`interaction(panzoom)` can now take options `x`, `y`, `xy`, `auto` as well as `none` which allow
 detailed control of how panning and zooming operate

 Panning and zooming now work for diagrams as well as charts based on scales; the zooming keeps
 fonts and point sizes fixed (not a simple graphical zoom) so zooming in will show better labels
 and make features clearer when applicable.


## Notebooks

R Notebooks (IRkernel) no longer require use of a web service.  Simply install Brunel Visualization directly into R and enjoy.

## Minor fixes

 * Wrapped text in Firefox browsers has been improved to compensate for the difference in
   how FF calculates text height.

 * Choice of date format to use in legends and other cases has been improved

 * Better choice of precision to show numeric values in labels and tooltips

 * Expanded the use of "M" millions scaling

 * Zooming and panning has been restricted so you cannot scroll or pan too far from the data space.
   This feature can be disabled by holding down the ALT or OPTION key while zooming to allow
   unrestricted pan and zoom.

 * Chord charts clip text that does not fit into the labels rather than simply dropping it







# 1.2 Release Notes

## Maps

Maps has undergone a major set of improvements, mostly internal, but the following
changes should be noted:

 * Instead of GeoJSON files, we are now using the more efficient topoJSON format.
   (https://github.com/mbostock/topojson/wiki). This makes maps download speed much
   faster

 * The `map` command now accepts an option parameter for the level of detail of the
   map polygons. The possible values are `high`, `medium`, and `low` with medium being
   the default. In general, `medium` assumes a 800x800 pixel display space with about
   10x zooming still looking good. `low` assumes a smaller space with very little
   zooming (but the zooming is way faster!) and `high` is as much detail as we have
   available.

 * We have improved the name look up routines so they now accept a much wider
   choice of names for a country, including non-english names.

 * Axes for maps are supported and generate a graticule for the map. The number of
   divisions is dynamically calculated

 * Look has improved

## Labels

 * Labels are now automatically thinned so they are not shown all on top of each other.   This is dynamic, so as you zoom in, more labels will be shown if now possible

## Notebooks

Some of the changes in version 1.2 may cause graphs in existing notebooks to not appear immediately. If this happens try executing all cells, then reloading the browser page.  There may be other issues when upgrading.  More information and work-arounds are in [this git issue](https://github.com/Brunel-Visualization/Brunel/issues/98)

* Python:  Added support for pandas DataFrame index.  If the index has a label, it can be referenced in the Brunel as a field.
* Toree:  Update to magic now requires Toree dev8 or later

## Miscellaneous

* `filter()` may now  contain default values for each field.  See "Interactivity" section in online documentation for examples.
*  Added new action to add graph titles and footnotes:  `title("A Title")`.  See "Titles, Guides and Style" section in online docs.
*  Animated entrances in new `effect()` command.  See "Interactivity" section of online docs.

## Integrators

* Note that a new JS file is required:  `//cdnjs.cloudflare.com/ajax/libs/topojson/1.6.20/topojson.min.js`







# 1.1 Release Notes

# Java version

Although informally true before, the official version of Java supported is Java 7.
Java 8 should also work fine, but primary testing will done on 1.7


# Minor Improvements
Axes on Smaller charts show fewer ticks, reducing overlapping.

Summmarization has been permitted for the x axis, so we can write `x(income) color(region) mean(income)` for example.

Fit and smooth operations now work for categorical data, both on the X and Y dimensions.
Categories are treated as ordered, and those category orders are used in the calculations.

# Support for lists of items

If a field has string values separated by commas, these values will be autoconverted
into an internal list object. This will not change functionality significantly,
but it allows the use of a new summary operator `each(x)` which splits up the list
into multiple rows. As an example:

Raw data

        A   B
        0   a,b,c
        0   a,c
        1   a,c,b

Transformed by `each(B)`

        A   B
        0   a
        0   b
        0   c
        0   a
        0   c
        1   a
        1   c
        1   b

Transformed and summarized using the Brunel `x(B) y(#count) each(B)`

        B   #count
        a   3
        b   2
        c   3

# Notebooks

Added support for Python 2 Notebooks.






# 1.0 Release Notes

## Maps

Brunel now supports maps, using an intelligent matching feature and an online atlas of world regions.
maps can be specified by name by requesting a given map in the `map` command, or Brunel will find a suitable
set of maps for a given data column.

By specifying a `key` field, that field is matched against Brunel's database of known regions and is used
to generate maps. Key features of mapping are:

 * Maps can have multiple layers, each specified as an element using the `+` notation
 * Key names can be used to create maps, or explicit latitude/longitude can be defined
 * By default, Brunel chooses which set of features will make a suitable map based on the key data
 * Brunel chooses a suitable projection based on the regions to be displayed
 * Maps elements default to polygonal features, but specifying `point` or `text` converts the element to
   one of that type, with all the usual features
 * By adding the additional element `map(labels)` maps are automatically labeled with suitable labels
   taking into account the scaling of the map.

The region and name data that back the map feature are courtesy of the public domain data sets found in the
Natural Earth repository (Free vector and raster map data @ naturalearthdata.com).

## Networks

The `edge` element is now more fully supported, as is the `network` diagram. This allows the creation of node
and link diagrams, by default using D3's force layout method. These diagrams:

 * Animate as they are laid out
 * Allow node dragging using force-directed updates
 * Use point elements for the nodes, allowing color, size and labeling as usual for such an element
 * Use edge elements for the links, allowing color and size to be used

Two dataset are usually required, one for the nodes and one for the edges. However, a single data set can be used
for the edges if there is little no extra data for the nodes. The nodes can be generated form the edges by combining
them as you would a series, for example: `edge key(a,b) + y(a,b) label(#values)` will generate a labeled graph from
a single dataset consisting only of edges from column _a_ to column _b_.

## Spark/Scala Notebooks

This release adds support for Scala/Spark Jupyter Notebooks using the Apachee Toree kernel.  See the notes in the [spark-kernel](https://github.com/Brunel-Visualization/Brunel/tree/master/spark-kernel) project for details.

## Other New Features

 * Improved axis options. `axis(...)` now takes options `x`, `y`, `none` or both `x` and `y` as options.
   Options `x` and `y` can take optional parameters that are numeric (to hint at the number of ticks
   desired for a numeric axis) or string (to give a title to an axis, with the empty string '' suppressing titles)
 * Improved the algorithm for shortening labels (using `label(field:N)` where _N_ is the desired text length)
 * Clustering is now allowed; specifying two _x_ dimensions where the first is categorical will place the
   second dimensions within the first as a clustered X axis

## Fixes

 * Improved labeling for diagrams ensures labels are not "lost" when diagram structure animates
 * Labels in transposed charts work as expected
 * Some defects with tooltip locations resolved

## Future

 The use of `:: data` in notebooks is deprecated; it continues to be supported in 1.0, but will go away
 in the next version. The language command `data(...)` should be used instead.
