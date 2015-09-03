# Brunel

This project contains the minimal code to use Brunel. It depends only on the Gson library.

It currently contains data manipulation code, and this needs to be converted to code that uses the Data project.
The D3 and RAVE builder classes also need a fair amount of work.

## Brunel Syntax

A composite action is a string of actions, separated by white space:

    action action ... action

Each action is either a simple action, or a parameterized action:

A simple action is just an action keyword:

    simple:         actionName

A parameterized command has parentheses and arguments inside:

    parameterized:  actionName ( argument, argument, ... , argument )

Each argument is either a field, an option, or a literal

### Literal

A literal is either a string or numeric literal. A numeric literal follows the standard Java/JavaScript rules
that defines the number -- if an argument starts with a numeric digit or a minus symbol, we will assume it is
a numeric literal. String literals must be enclosed by either single or double quotes, and within that we respect the
following escape sequences:

    \\          a literal single backslash
    \n          newline
    \t          tab

### Option

An option is a simple string that identifies a choice. For example, the axes command has the following options

    all | none | x | y auto

No options will ever conflict with literals -- they all must start with an alphabetic character

### Field

A field, at its base, is the name of a field. A field name consists of an alphabetic character, followed by one or more
alphabetic characters, numeric digits, dollar signs, percent signs or underscores. However, note that
the character '#' identifies the start of a special field. Supported special fields are:
    `#row, #count, #series, #values, #all`

In addition, we may add modifiers to a field to modify the way the action works for that field. Modifiers are arguments, separated by a ':'. For example:

    sort(size:decreasing)

    bin(cyclinders:numeric:12)
    
## Using Brunel with D3 / RAVE2

### Include files

When using Brunel in a web environment, the following files need importing / linking:

````
<link rel="stylesheet" type="text/css" href="../out/BrunelBaseStyles.css">
<link rel="stylesheet" type="text/css" href="http://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css">
<script src="http://d3js.org/d3.v3.min.js" charset="utf-8"></script>
<script src="http://labratrevenge.com/d3-tip/javascripts/d3.tip.v0.6.3.js"></script>
<script src="http://code.jquery.com/jquery-1.10.2.js"></script>
<script src="http://code.jquery.com/ui/1.11.4/jquery-ui.js"></script>
<script src="../out/BrunelData.js"></script>
<script src="../out/BrunelD3.js"></script>
<script src="../out/BrunelEventHandlers.js"></script>
<script src="../out/BrunelJQueryControlFactory.js"></script>
<style>
````

In addition, Brunel uses extended characters, and so the page *must* define the use of the UTF-8 character set.  

Note that the local files are common to all visualizations, and so can be placed in a common location on your server.

### Adding Brunel build artifacts

When `brunel.build(...)` is called it returns an array of two Strings. The first is a set of styles specific to this
visualization. These CSS styles are targeted using the ID passed into the build routine, and so are safe to be added
directly to the page without if affecting other page elements. The second is the Javascript definition of the class
that creates and manages the defined visualization. This name of the class is given in the D3Builder constructor, but
defaults to `BrunelVis`.

Thus a skeleton page to include Brunel will look like this

````
<html>
<head>
<meta charset="UTF-8">
<link rel="stylesheet" type="text/css" href="../out/BrunelBaseStyles.css">
<link rel="stylesheet" type="text/css" href="http://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css">
<script src="http://d3js.org/d3.v3.min.js" charset="utf-8"></script>
<script src="http://labratrevenge.com/d3-tip/javascripts/d3.tip.v0.6.3.js"></script>
<script src="http://code.jquery.com/jquery-1.10.2.js"></script>
<script src="http://code.jquery.com/ui/1.11.4/jquery-ui.js"></script>
<script src="../out/BrunelData.js"></script>
<script src="../out/BrunelD3.js"></script>
<script src="../out/BrunelEventHandlers.js"></script>
<script src="../out/BrunelJQueryControlFactory.js"></script>
<style>
                        // INSERT GENERATED CSS FOR THIS VISUALIZATION HERE
</style>
</head>
<body>
<svg id="visualization" width="1000" height="600"></svg>        // WHERE TO PUT THE VISUALIZATION
</body>
<script>

                        // INSERT GENERATED JAVASCRIPT FOR THIS VISUALIZATION HERE
                        // BY DEFAULT THIS DEFINES A CLASS: BrunelVis


var table = [ ... ];    // SOME PROVIDED DATA (MAY BE WRITTEN BY BUILDER)

                        // INSERT CODE HERE TO USE BRUNEL

</script>
</html>
````

### Using Brunel

The simplest use of brunel is to statically display some data, and is achieved by the following:

    new BrunelVis().build(table);
    
Each time build is called, it will transition to the new data passed in. This allows the use of data manipulation on
the raw data table to change the visualization, like the following:

````javascript
var v = new BrunelVis('visualization');
v.transitionTime = 500;
var intervalID;
var p = 0;	
intervalID = setInterval(function() {
	var start,end;
	start = p * table.length / 10;
	end = (p+1) * table.length / 10;
	var s = table.slice( Math.floor(start), Math.floor(end));
	s[0] = table[0];
	v.build(s);
	p = (p+1) % 10;
}, 500);
````

Alternatively, the data may be manipulated using Brunel Data methods, by changing the `dataPreProcess`
and `dataPostProcess` functions:

````javascript
var v = new BrunelVis('visualization');
v.setData(table);
var xField = v.getData().original.field('PutOutRate');
v.transitionTime = 500;
var intervalID;
var p = 0;	
intervalID = setInterval(function() {
	var min = xField.min() + (xField.max() - xField.min()) * p / 10;
	var max = xField.min() + (xField.max() - xField.min()) * (p+1) / 10;
	var command = 'PutOutRate in ' + min + ',' + max;
	console.log(command);
	v.dataPreProcess = function(data) { return data.filter(command)};
	v.build();
	p = (p+1) % 10;
}, 500);
````

### Interactivity

The Brunel engine can generate Javascript that can be used to supply interactivity for Brunel actions such as `filter()`.  Use 
`ControlWriter.java` to generate the code and then place it after the standard generated
Brunel Javascript.  This is done separately because it is expected that different Brunel integrations will
manage Javascript differently to supply Brunel interactivity.

Integrators can retrieve the state needed for interactive controls as JSON in Java using `Builder.getControls()`.  
`BrunelJQueryControlFactory.js` can then produce standard Brunel controls using this state, but any Javascript with a matching public
API can be used instead.  `BrunelEventHandlers.js` contains code to manage events between the controls and the 
visualization and is expected to be used in any integration.  `BrunelBaseStyles.css` contains CSS used by the UI controls.

### Exposed members of the Brunel Visualization class

#### `build(data)`

This command builds the visualization, or rebuilds transitioning to the new data. The data parameter may be null, in
which case the last data passed in is used. When `data` is specified, this is simply a convenience call to `setData`
followed by `build()`

#### `setData(data)`

Sets row-based data to be used by the visualization. The first row MUST be names for the columns. Brunel will
automatically process this data, guessing types and building the Dataset

#### `getData()` 

returns an object with two fields, `original` equal to the Brunel Dataset that was created by the last call to
`setData`, and `processed` equal to the Brunel Dataset that was created by the last call to `build`. The values
are undefined if these calls have not been made.

#### `dataPreProcess`, `dataPostProcess` 

These are functions to be re-defined by the calling code. Each of them takes a Brunel Dataset and returns a Brunel
Dataset. The default definitions of these are  `function(data) { return data; }`. When the `build` method is called,
data is processed in the following transformational steps:

 * The data is set to the `original` data set (the one created by the last call to `setData`)
 * It is transformed via `dataPreProcess`
 * It is transformed via calls generated by the builder that define the data
 * It is transformed via `dataPostProcess`

The user can modify the data build pipeline by setting the attributes as required.

#### `transitionTime`
 
This is a time in milliseconds specifying how long transitions generated by calling build should take.


