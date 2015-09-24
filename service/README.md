# Service

A basic JAX-RS web service implementation for Brunel that produces Javascript that renders a Brunel visualization given Brunel syntax and the data to visualize.

#### To start the Brunel server

Gradle can be used to deploy Brunel to TomEE and start the web server.  From `/brunel`:

```gradle cargoRunLocal```

To confirm it is working, navigate to:

    http://localhost:8080/brunel-service/brunel/interpret/d3?brunel_src=data('http%3A%2F%2Fbrunel.mybluemix.net%2Fsample_data%2FBGG%2520Top%25202000%2520Games.csv')%20chord%20x(categories)%20y(playerage)%20color(playerage)%20size(%23count)%20tooltip(%23all)&width=575&height=575
  
#### REST methods   
The REST methods are defined in `BrunelService.java`.  The main REST calls are:

````POST /brunel/interpret/d3````  
Post a CSV on the payload (as a String)

Params:

    src:  The brunel syntax
    width: The visualization width to use
    height: The visualization height to use
    visid:  A unique identifier for the HTML tag containing the visualization

This returns the Javascript that will render the visualization.

````GET /brunel/interpret/d3````  
Gets full HTML that can be placed into an `<iframe>`.

Params:

    brunel_src:  The brunel syntax
    brunel_url:  Optionally, an URL pointing to a file containg the brunel.
    width:  The visualization width to use
    height:  The visualization height to use
    data:  An URL reference to a CSV

This returns HTML that can be placed into an IFrame.  It does not return the `<iframe>` HTML tag--only the contents.

