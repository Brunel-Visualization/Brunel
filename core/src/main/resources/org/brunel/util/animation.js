// An animated start to a visualization
function animateBuild(vis, data, time) {

    var targets = ["size", "y", "color"];

    function isNumeric(name) {
        if (!data.options) return true;
        for (var i = 0; i < data.names.length; i++)
            if (data.names[i] == name)
                return data.options[i] != "string";
        return true;
    }

    function getFieldNames(vis, type) {
        if (vis.charts.length != 1) return [];          // Only works for single charts
        var i, y, result = [], chart = vis.charts[0];   // collect all element's y fields
        chart.elements.forEach(function (e) {
            y = e.fields[type];
            if (y) for (i = 0; i < y.length; i++)       // only add numeric values
                if (isNumeric(y[i])) result.push(y[i]);
        });
        return result;
    }

    // Find the names of the fields to animate over

    var i, what, names;                                 // 'what' is the type of field to target; names are the fields
    for (i = 0; i < targets.length; i++) {                  // Find the first that has defined fields
        what = targets[i];
        names = getFieldNames(vis, what);
        if (names.length > 0) break;
    }

    var fields,                                         // the corresponding build fields
        originalFunc = vis.dataPostProcess();           // the currently installed post-processing function

    if (names.length == 0) {                            // Nothing to animate
        vis.build(data);
        return;
    }

    var scales = vis.charts[0].scales, start;           // scales defined for this chart
    if (scales && scales[what]) {
        var domain = scales[what].domain();
        start = domain[0];
        if (start != 0) start = (start + domain[domain.length-1])/2;
    }

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
