// An animated start to a visualization
function animateBuild(vis, data, time) {

    function getFieldNames(vis) {
        if (vis.charts.length != 1) return [];          // Only works for single charts
        var y, result = [], chart = vis.charts[0];      // collect all element's y fields
        chart.elements.forEach(function (e) {
            y = e.fields.y;
            if (y) result.push.apply(result, y);
        });
        return result;
    }

    var names = getFieldNames(vis), fields,             // names and the corresponding build fields
        originalFunc = vis.dataPostProcess();           // the currently installed post-processing function

    if (names.length == 0) {                            // Nothing to animate
        vis.build(data);
        return;
    }

    var scales = vis.charts[0].scales,                  // scales defined for this chart
        start = scales ? scales.y.domain()[0] : null;   // If none, each field will define its own min

    vis.dataPostProcess(function (d) {                  // replace the post-processing definition
        fields = names.map(function (name) {            // collect the built fields
            var value, field = d.field(name);
            if (!field) return null;
            value = start == null ? field.min() : start;        // get a defined start location
            field.oProvider = field.provider;                   // swap provider with a constant value
            field.provider = new BrunelData.values_ConstantProvider(value, field.rowCount());
            return field;
        });
        return originalFunc(d);
    });

    vis.build(data);                                    // build using the new data
    vis.dataPostProcess(originalFunc);                  // restore the original post-processing definition
    fields.forEach(function (f) {                       // restore all fields
        if (f) f.provider = f.oProvider;
    });
    vis.rebuild(time);                                  // rebuild with the correct data, animated
}
