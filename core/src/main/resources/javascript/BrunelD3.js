/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// A closure for the utilities needed to build Brunel items for D3
var BrunelD3 = (function () {

    //Ensure d3/topojson  loaded under AMD.
    if (typeof topojson === 'undefined' && typeof require === 'function') topojson = require('topojson');
    if (typeof d3 === 'undefined' && typeof require === 'function') d3 = require('d3');

    // Detect if this is NOT a chromium browser we are in
    // Chrome browsers add in the symbol offset to the symbols bounding box; other browsers do not
    var notChrome = window.navigator.userAgent.toLowerCase().indexOf("chrome") === -1;

    var tooltip, lastTime, lastTimeDescr;
    // Return geometries for the given target given the desired margins
    function geometries(target, chart_top, chart_left, chart_bottom, chart_right,
                        inner_top, inner_left, inner_bottom, inner_right) {

        var b = target.getBoundingClientRect(),
            x = b.left, y = b.top, w = b.width, h = b.height;
        var owner = target.ownerSVGElement;
        if (owner) {
            var c = owner.getBoundingClientRect();
            x -= c.left;
            y -= c.top;
        } else {
            x = 0;
            y = 0;
        }

        if (!w) w = target.getAttribute("width");
        if (!h) h = target.getAttribute("height");

        var g = {
            chart_top: y + h * chart_top, chart_bottom: y + h * chart_bottom,
            chart_left: x + w * chart_left, chart_right: x + w * chart_right,
            outer_width: w, outer_height: h,
            inner_top: inner_top, inner_bottom: inner_bottom,
            inner_left: inner_left, inner_right: inner_right,

            // Allow the inner coords to be transposed
            'transpose': function () {
                var t = this.inner_width;
                this.inner_width = this.inner_height;
                this.inner_height = t;
            },

            'makeSquare': function () {
                var d = this.inner_width - this.inner_height;           // excess width
                if (d > 0) {
                    // Reduce the width
                    this.inner_width -= d;
                    this.inner_right -= d;
                    this.chart_right -= d;
                    this.inner_rawWidth -= d;
                } else {
                    // Reduce the height
                    this.inner_height += d;
                    this.inner_bottom += d;
                    this.chart_bottom += d;
                    this.inner_rawHeight += d;
                }
            }
        };

        g.inner_width = w - (g.chart_left + g.inner_left) - (w - g.chart_right + g.inner_right);
        g.inner_height = h - (g.chart_top + g.inner_top) - (h - g.chart_bottom + g.inner_bottom);
        g.inner_rawWidth = g.inner_width;
        g.inner_rawHeight = g.inner_height;
        g.inner_radius = Math.min(g.inner_width, g.inner_height) / 2;
        g.default_point_size = Math.max(6, g.inner_radius * 0.035);


        return g;
    }


    // Create a dataset from rows. Each object has three parts - names, types, rows
    // The types are 'string', 'date',  'numeric' or "synthetic" (one of #row, #selection, #count)
    function makeDataset(data) {
        var d = BrunelData.Dataset.makeTyped(data.names, data.options, data.rows);
        d.set("summarized", data.summarized);        // Preserve the status in the info
        return d;
    }


    /**
     * Returns the bounding box for an item, or null if the bounding box is invalid.
     * An invalid rect is one which has no size, or when the svg ite is not displayed and
     * so the call to getBBox() throws a failure. Different browsers handle things differently,
     * and this routine is intended to abstract that out.
     * @param item the svg item to get the box for
     * @returns rect (x,y, width, height), or null
     */
    function getBBox(item) {
        try {
            var box = item.getBBox();
        } catch (e) {
            return null;
        }

        if (notChrome && item.tagName === 'use') {
            // Chrome adds the parent coordinate system into the mix already
            // for every other browser, we have to manually add it
            box.x += +item.getAttribute("x");
            box.y += +item.getAttribute("y");
        }

        // Empty boxes are either useless or an indicator the element is not visible
        return box.width || box.height ? box : null;
    }

    /**
     * Transform a rectangle (usually calculated from the bounding box)
     * @param box box to transform (null is allowed and will return null)
     * @param item the svg item to get the transform from
     * @returns transformed box -- will always be valid if input box was valid
     */
    function transformBox(box, item) {
        var m = item.getCTM();
        if (m && box) {
            box = {
                x: m.a * box.x + m.c * box.y + m.e,
                y: m.b * box.x + m.d * box.y + m.f,
                width: m.a * box.width + m.c * box.height,
                height: m.b * box.width + m.d * box.height
            };
        }
        return box;
    }

    /**
     * Define a legend.
     * The definition parameter contains fields:
     *      - title (text string)
     *      - ticks (array of items to make into swatches)
     *      - dateFormat (for formatting date ticks, may be null)
     *      - color (the scale for showing color)
     *      - symbol (the scale for showing symbol)
     * @param target    the group to add the legend into
     * @param def       structure containing legend definition
     */
    function makeLegend(target, def) {

        // Define the title for the legend
        target.attr('class', 'legend').selectAll('text.title').data(['all']).enter()
            .append('text').attr('x', 0).attr('y', 0)
            .style('text-anchor', 'end').attr('dy', '0.85em').text(def.title).attr('class', 'title');

        // Create the legend entries
        var entries = target.selectAll('g.swatch').data(def.ticks).enter().append('g').attr('class', 'swatch')
            .attr('transform', function (d, i) {
                return 'translate(-20,' + (20 + i * 20) + ')';
            });

        // Create the swatch and style it
        var swatch = entries.append('use').attr('x', 2).attr('width', 18).attr('height', 18);
        if (def.color) swatch.style('fill', def.color);
        swatch.attr('xlink:href', def.symbol ? function (d) {
            return '#' + def.symbol(d)
        } : "#_sym_square");

        // Create an appropriate text function nicely to format the ticks
        var ticks = def.ticks, textf;
        if (def.dateFormat)
            textf = function (d) {
                return def.dateFormat.format(d)
            };
        else if (typeof(ticks[0]) == 'number') {
            var range = Math.abs(ticks[ticks.length - 1] - ticks[0]),
                decimalPlaces = Math.max(0, 4 - Math.log(range) / Math.log(10)),
                inMillions = range > 2e6;

            if (inMillions)
                textf = function (d) {
                    return BrunelData.Data.formatNumeric(d / 1e6, 0, true) + "M"
                };
            else
                textf = function (d) {
                    return BrunelData.Data.formatNumeric(d, decimalPlaces, true)
                };
        } else
            textf = function (d) {
                return BrunelData.Data.format(d, true)
            };

        // Add the text to the legend
        entries.append('text').attr('y', 8).attr('dy', '.35em').style('text-anchor', 'end').text(textf)
            .attr('class', 'legend').append('text').attr('x', 0).attr('y', 0);
    }

    // Create Split data structure for use in lines, paths and areas
    function split(data, path, xFunction) {
        // Create an array for the unique split categories
        var splits = data._rows.map(data._split).filter(function (v, i, a) {
            return a.indexOf(v) === i
        });

        // Creates a list of points on the path
        function makePoints(path, d) {
            var x = path.x(), y = path.y(),                     // Path functions for x and y
                i, pts = [];
            for (i = 0; i < d.length; i++)                      // For each point ...
                pts.push({x: x(d[i]), y: y(d[i]), d: d[i]});    // ... add the x,y, and data index
            return pts;
        }

        // Define a function for each category that extracts the rows
        // It returns a structure for use in the element containing sample row, key and path
        function f(category) {
            var d = data._rows.filter(function (d) {
                return data._split(d) == category;
            });
            if (xFunction) d.sort(function (a, b) {
                // If tied for x, respect row order
                return xFunction(a) - xFunction(b) || a.row - b.row;
            });
            return {'path': path(d), 'key': d[0].key, 'row': d[0].row, 'points': makePoints(path, d)}
        }

        // Convert array of categories into array of above structures
        return splits.map(f)
    }

    // Create rows and keys for the data
    function makeRowsWithKeys(keyFunction, N) {
        var result = [];
        for (var i = 0; i < N; i++) result.push({'row': i, 'key': keyFunction(i)});
        return result;
    }

    /**
     * Centers the given arc within the wedge passed in
     * @param text the text node, which must contain a textpath referencing the target wedge
     * @param arcWidth the width fo the arc
     */
    function centerInArc(text, arcWidth) {
        var textPath = text.select('textPath').node(),                      // The text path for this
            target = d3.select(textPath.getAttribute('href')),              // Target path we're attached to
            arcLen = target.node().getTotalLength() / 2 - arcWidth,         // Length of arc to fit into
            textLen = textPath.getComputedTextLength();                     // Length of the text

        // If we need to reduce the size, delete it if that is not possible
        if (textLen > arcLen - 4 && !addEllipses(textPath, textPath.textContent, arcLen - 4))
            textPath.setAttribute('visibility', 'hidden');
        else
            textPath.setAttribute('startOffset', (arcLen - textPath.getComputedTextLength()) / 2);
    }

    function shrink(rect, sx, sy) {
        if (!rect) return null;
        sx = sx || 1 / 3;
        sy = sy || 1 / 3;
        var w = rect.width * sx;
        var h = rect.height * sy;
        return {x: rect.x + (rect.width - w) / 2, y: rect.y + (rect.height - h) / 2, width: w, height: h}
    }

    function wedgeLoc(path, datum) {
        // Want to be a little further out than just the center (33% more)
        var c = path.centroid(datum);
        var a = Math.atan2(c[1], c[0]);
        var d = 1.3333 * Math.sqrt(c[0] * c[0] + c[1] * c[1]);
        var x = Math.cos(a) * d;
        var y = Math.sin(a) * d;

        // Calculate the wedge area, and put a square inside it (roughly!) for the text
        var r2 = path.outerRadius()(datum);
        var r1 = path.innerRadius()(datum);
        var area = Math.abs(path.endAngle()(datum) - path.startAngle()(datum)) * (r2 * r2 - r1 * r1) / 2;
        var r = Math.sqrt(area * 0.75) / 2;
        return {x: x, y: y, box: {x: x - r, y: y - r, width: 2 * r, height: 2 * r}}
    }

    /**
     * Create a location (x,y for target position and the bounding box) using the centroid algorithm
     * @param path D3 path to use
     * @param d datum for the path
     * @param svgItem item to get the box for
     * @returns {{x: *, y: *, box: ({x, y, width, height}|*)}}
     */
    function centroidLoc(path, d, svgItem) {
        var c = path.centroid(d);
        return {x: c[0], y: c[1], box: shrink(getBBox(svgItem))}
    }

    /**
     * Create a location (x,y for target position and the bounding box) along path
     * @param svgItem item to get the box for
     * @param fraction - how far along path to determine
     * @returns {{x: *, y: *, box: ({x, y, width, height}|*)}}
     */
    function pathLoc(svgItem, fraction) {
        // Find a point half way along
        var c = svgItem.getPointAtLength(svgItem.getTotalLength() * fraction);
        return {x: c.x, y: c.y, box: shrink(getBBox(svgItem), 1, 1 / 3)}
    }

    function polyLoc(svgItem) {
        // Average positions 25% and 75% of the way around the shape
        var len = svgItem.getTotalLength();
        var a = svgItem.getPointAtLength(len / 4);
        var b = svgItem.getPointAtLength(3 * len / 4);
        return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, box: shrink(getBBox(svgItem))}
    }

    function areaLoc(svgItem, margin) {
        var box = getBBox(svgItem);
        if (!box) return null;
        var len = svgItem.getTotalLength();

        var dx = Math.max(6, box.width / 40),               // We discretize to steps of 'dx' amounts
            M = Math.max(len / dx, 200),                    // We examine this many points along the path
            N = Math.round(box.width / dx),                 // Size of the vertical raster array
            i, p, x, y, t;

        // We create arrays for the upper and lower values at discrete locations along the box
        // At each discrete 'x' we store upper and lower y values -- a raster stripe
        var upper = [], lower = [];
        for (i = 0; i < M; i++) {
            p = svgItem.getPointAtLength(len * (i + 0.5) / M);
            x = Math.round((p.x - box.x) / dx);
            if (!upper[x] || upper[x] < p.y) upper[x] = p.y;
            if (!lower[x] || lower[x] > p.y) lower[x] = p.y;
        }

        // Find the raster that has the has the largest y difference
        // We use a smooth on the upper and lower differences
        // Note that we can ignore the first and last values (i==0, i==N) as we don't want to be at an edge
        // We fid the minimum distance to an upper / lower value OR to the nearest left/right edge
        var index = N / 2, heightAtX = 0, edgeD;
        for (i in lower) {
            t = upper[i] - lower[i];
            if (lower[i + 1] && lower[i - 1]) t = (2 * t + (upper[i + 1] - lower[i + 1] + upper[i - 1] - lower[i - 1]) / 2) / 3;
            edgeD = Math.min(i, N - i) * dx;
            if (edgeD < margin) t *= 0.01;          // Only add here if we really have to
            else if (edgeD < 3 * margin) t *= 0.8;   // Prefer further in
            if (t > heightAtX) {
                index = i;
                heightAtX = t;
            }
        }

        var cx = box.x + index * dx;
        var cy = (upper[index] + lower[index]) / 2;
        if (heightAtX < box.height / 5 && heightAtX < 20) heightAtX = 20;  // Allow thin, but varying areas to overflow

        // Return the results
        return {
            x: cx, y: cy,
            box: {x: cx - box.width / 3, y: cy - heightAtX / 2, width: box.width / 3, height: heightAtX}
        }
    }

    function transformLoc(loc, item) {
        if (!loc) return null;
        var a = transformBox({x: loc.x, y: loc.y, width: 1, height: 1}, item),
            b = transformBox(loc.box, item);
        return a && b ? {x: a.x, y: a.y, box: b} : null;
    }

    // Returns an object with 'x', 'y' and 'box' that "surrounds" the text
    // hPos and vPos are optional
    function makeLoc(target, labeling, s) {
        var datum = target.__data__, pad = labeling.pad || 2,
            box, loc, method = labeling.method, pos = labeling.location;
        if (labeling.where) {
            box = getBBox(target);
            if (!box) return null;
            var p = labeling.where(box, s, datum);
            loc = {x: p.x, y: p.y, box: box};
        } else if (method == 'wedge')
            loc = wedgeLoc(labeling.path, datum);
        else if (method == 'poly')
            loc = polyLoc(target);
        else if (method == 'area')
            loc = areaLoc(target, s.length * 3.5);       // Guess at text length
        else if (method == 'path') {
            if (labeling.path.centroid)
                loc = centroidLoc(labeling.path, datum, target);
            else {
                var fraction = 0.5;
                if (labeling.align == 'start') fraction = 0;
                else if (labeling.align == 'end') fraction = 1;
                loc = pathLoc(target, fraction);
            }
        } else {

            box = getBBox(target);
            if (!box) return null;

            // Adjust for target transform
            box = transformBox(box, target);

            var hPad = labeling.align == 'start' ? pad : -pad;
            var vPad = labeling.inside ? -pad : pad;

            // Add padding
            var dx = pos[0] == 'left' ? hPad : (pos[0] == 'right' ? box.width + hPad : box.width / 2);
            var dy = pos[1] == 'top' ? -vPad : (pos[1] == 'bottom' ? box.height + vPad : box.height / 2);
            return {x: box.x + dx, y: box.y + dy, box: box}
        }

        // If we failed to get a location
        if (!loc || !loc.box) return null;

        // Modify for the transform
        return transformLoc(loc, target);
    }

    // Orientation of the three points (p, q, r).
    // The function returns following values
    // 0 --> colinear;     -1 --> Clockwise;         1 --> Counterclockwise
    function orientation(p, q, r) {
        var val = (q.y - p.y) * (r.x - q.x) -
            (q.x - p.x) * (r.y - q.y);

        if (val === 0.0) return 0;  // colinear
        return (val > 0) ? -1 : 1;   // which side
    }

    // Given three colinear points p, q, r, the function checks if point q lies on line segment (p,r)
    function onSegment(p, q, r) {
        return q.x <= Math.max(p.x, r.x) && q.x >= Math.min(p.x, r.x) &&
            q.y <= Math.max(p.y, r.y) && q.y >= Math.min(p.y, r.y);
    }

    /**
     * Determine if two line segments intersect
     * @param p1 start of segment one
     * @param q1 end of segment one
     * @param p2 start of segment two
     * @param q2 end of segment two
     * @returns true if they intersect
     */
    function intersect(p1, q1, p2, q2) {
        // If they share the same exact object, they do not count as intersecting
        if (p1 === q1 || p1 === q2 || p2 === q1 || p2 === q2) return 0;

        var o1 = orientation(p1, q1, p2),
            o2 = orientation(p1, q1, q2),
            o3 = orientation(p2, q2, p1),
            o4 = orientation(p2, q2, q1);

        // If the end-points of each segment are on opposite sides of the other segment, they must intersect
        if (o1 !== o2 && o3 !== o4) return true;

        // When the lines are colinear, check if the other point is exactly on the segment
        // If none match, they do not intersect
        return (o1 === 0 && onSegment(p1, p2, q1))
            || (o2 === 0 && onSegment(p1, q2, q1))
            || (o3 === 0 && onSegment(p2, p1, q2))
            || (o4 === 0 && onSegment(p2, q1, q2));
    }


    function makeTextSpan(loc, parent, content) {
        var span = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
        span.setAttribute('class', 'label');
        span.setAttribute('x', loc.x);
        span.setAttribute('y', loc.y);
        span.textContent = content;
        parent.appendChild(span);
        return span;
    }

    function trim(text, t) {
        if (t < 0) return "";
        if (t == 0) return text.substring(0, 1);
        return text.substring(0, t) + "\u2026";
    }

    function addEllipses(span, text, maxWidth) {
        // Binary search to fit text
        var t, min = -1, max = text.length - 3;
        while (max - min > 1) {
            t = Math.floor((max + min) / 2);
            span.textContent = trim(text, t);
            if (span.getComputedTextLength() <= maxWidth)
                min = t;
            else
                max = t;
        }

        // min will work, but we do not want trailing punctuation
        while (min > 0 && " ,.;-".indexOf(text.charAt(min - 1)) > -1) min--;
        span.textContent = trim(text, min);
        return min >= 0; // True if we have valid text
    }

    function wrapInBox(textItem, text, loc, offset) {
        // Remove existing spans
        while (textItem.firstChild) textItem.removeChild(textItem.firstChild);

        // words -- the array of words we try to add, one at a time
        // content -- the array of words we are trying to add to the current span
        // height -- the height of a single wrapped line
        // W -- the width of the box into which the text must fit
        var words = text.split(/\s+/), content = [], height, tspan, W = loc.box.width - 2,
            word, i;

        for (i = 0; i < words.length; i++) {
            word = words[i];
            content.push(word);
            if (!tspan) {
                tspan = makeTextSpan(loc, textItem, word);
                height = tspan.getBoundingClientRect().height
            } else {
                tspan.textContent = content.join(" ");
            }

            // Too many items; we cannot add the last item
            if (textItem.childNodes.length * height > loc.box.height) {
                textItem.removeChild(tspan);
                break;
            }

            // If it doesn't fit, remove the content and try to add ellipses
            if (tspan.getComputedTextLength() > W) {
                content.pop();
                if (!content.length) {
                    if (!addEllipses(tspan, word, W - 4)) {
                        textItem.removeChild(tspan);
                        break;
                    }
                } else {
                    tspan.textContent = content.join(" ");
                    i--;
                }
                tspan = null;
                content = [];
            }
        }

        var spans = textItem.childNodes;
        if (!spans) return;

        var D = 0.5 + offset;

        // Move everything up to center them
        for (i = 0; i < spans.length; i++)
            spans[i].setAttribute('dy', ((i - spans.length / 2.0) * 1.1 + D ) + "em");
    }

    // Calls the function when the target is ready
    function callWhenReady(func, target) {
        if (target && target.__transition__)
            setTimeout(function () {
                callWhenReady(func, target)
            }, 100);
        else
            func.call();
    }

    // Select the indicated rows of the data.
    // This will wait until any transition is completed, and will then call the desired rebuild function
    // item -- the data for the item being selected (contains 'row' and 'points' for a path)
    // target -- the SVG target
    // element -- the brunel element this selection is operating on
    // func -- callback function to rebuild everything when selection is done
    function select(item, target, element, func) {
        var e = d3.event || event,                                      // jQuery can sometimes kill the d3.event
            data = element.data(),                                      // the processed data
            sel = data.field("#selection"),                             // Selection field
            method = e.altKey ? "tog" : "sel";                          // how to select the data ...
        if (e.shiftKey) method = e.altKey ? "sub" : "add";              // ... add, subtract, toggle, select

        // Get the row from the data (if necessary from embedded data in item.data)
        var row = item ? (item.data && item.data.row != null ? item.data.row : item.row) : null;

        // The selection is in terms of the processed data, but we need to propagate back to the original data set
        element.original().modifySelection(method, row, data, element.fields.key);

        if (func) callWhenReady(func, target);                          // Request a redraw after transition done
    }

    function collapseNode(item, state) {
        item.children = undefined;                          // remove the children
        item.collapsed = true;                              // Mark the item
        state[item.data.key] = true;                        // Add to permanent list
    }

    /**
     * Fixes up tree heights, and also may reduce sizes
     * @param tree
     * @param reduceSizes
     */
    function fixTreeHeights(tree, reduceSizes) {
        var j;
        tree.eachAfter(function (v) {                               // set new values of height
            var children = v.children;
            if (children) {
                if (reduceSizes) v.value = 0;
                for (j = 0; j < children.length; j++) {
                    v.height = Math.min(v.height, children[j].height + 1);
                    if (reduceSizes) v.value += children[j].value;
                }
            }
            else
                v.height = 0;
        });
    }

    /**
     * Take a d3 tree structure and prune it to the given size
     * @param tree the root of the tree, a d3 Node
     * @param userStates a map from node keys to T/F overriding the collapse behavior
     * @param reduceSizes if true, adjust values when we prune
     * @param N desired maximum node count
     */
    function pruneTreeToSize(tree, userStates, reduceSizes, N) {

        // Remove the ones the user marked as to be collapsed
        tree.each(function (v) {
            if (userStates[v.data.key]) collapseNode(v, userStates);
        });
        fixTreeHeights(tree, reduceSizes);

        var n = tree.descendants().length;          // current length

        while (n > N && N > 2) {                    // No more trimming if an invalid lenght (or we are done!)
            var i, items = [];                                          // Collect nodes with height = 1
            tree.each(function (v) {
                // Mark for deletion if
                // (i) not the root (zero depth) and is one above a leaf (unit height)
                // (ii) it has been already marked for collapse
                if (v.depth && v.height == 1 && userStates[v.data.key] == null) items.push(v);
            });

            if (items.length == 0) break;                               // Quit if nothing left to do

            items.sort(function (a, b) {                                // Smallest weights first
                return a.value - b.value
            });

            for (i = 0; i < items.length && n > N; i++) {              // Remove each
                var below = items[i].descendants().length - 1;
                collapseNode(items[i], userStates);
                n -= below;
            }
            fixTreeHeights(tree, reduceSizes);
        }

        if (reduceSizes) {
            var nCollapsed = [];                                        // Counts for collapsed
            tree.each(function (v) {
                if (v.collapsed)
                    nCollapsed[v.depth] = (nCollapsed[v.depth] || 0) + 1;
            });
            tree.eachAfter(function (v) {
                if (v.children) {
                    v.value = 0;
                    for (i = 0; i < v.children.length; i++) v.value += v.children[i].value;
                }
                if (v.collapsed) {
                    v.value = Math.min(v.value, tree.value / nCollapsed[v.depth]) / 10;
                }
            });
        }

    }


    /**
     * Find a good grid size for the layout
     * @param n number of items
     * @param c number of columns (may be falsey)
     * @param aspect size ratio for rows :: columns
     */
    function chooseRows(n, c, aspect) {
        if (c) return Math.ceil(n / c);                         // We know the columns, calculate the rows
        else return Math.round(Math.sqrt(n * aspect));          // Make them fit appropriately
    }

    /**
     * Return tue if they are both non-null and different
     * @param a
     * @param b
     */
    function differ(a, b) {
        return a && b && a != b;
    }

    /**
     * Layout data in a grid
     * @param tree hierarchical layout from d3
     * @param extent display size
     * @param rows desired rows (may be falsey)
     * @param columns desired columns (may be falsey)
     * @param aspect the desired aspect ratio of each cell (y:x)
     */
    function gridLayout(tree, extent, rows, columns, aspect) {
        var i, j, r, ri, ci,
            leaves = tree.leaves();                     // we only use the tree leaves

        // Calculate the best number of rows using the relative aspect
        rows = rows || chooseRows(leaves.length, columns, extent[1] / extent[0] / aspect);


        ri = -1;
        ci = 0;
        for (i = 0; i < leaves.length; i++) {
            if (++ri >= rows) {
                ri = 0;
                ci++;
            }
            leaves[i].x = ci;
            leaves[i].y = ri;
        }

        var cols = ci + 1,
            radius = Math.min(extent[0] / cols, extent[1] / rows) / 2;

        // Rescale space and set the radius
        for (i = 0; i < leaves.length; i++) {
            leaves[i].x = (leaves[i].x + 0.5) * extent[0] / cols;
            leaves[i].y = (leaves[i].y + 0.5) * extent[1] / rows;
            leaves[i].r = radius;
        }
    }


    // Cloud layout -- pass in the dataset, the extent as [width, height]
    function cloud(data, ext, rectangular) {
        // Delta is the distance between locations as we step along the spiral to place items
        // It is also the distance between curves of the spiral.
        // dx and dy are delta, but spread out to fit the space better for non-square results
        // When we start searching out from the center in the spiral, we look for anything larger than us
        // and start outside that. 'precision' reduces the concept of 'larger' so we search less space

        var delta = 1;
        var precision = Math.pow(0.8, Math.sqrt(data.rowCount() / 200));
        var dx = delta * ext[0] / Math.max(ext[0], ext[1]),
            dy = delta * ext[1] / Math.max(ext[0], ext[1]);

        var items = [];             // Items we will build up
        var totalArea = 0;          // Area covered by the text


        function intersects(a, b) {
            // Height first as that is less likely to overlap for long text
            return a.y + a.height / 2 > b.y - b.height / 2
                && b.y + b.height / 2 > a.y - a.height / 2
                && a.x + a.width / 2 > b.x - b.width / 2
                && b.x + b.width / 2 > a.x - a.width / 2;
        }

        function ascender(txt) {
            // 1 == ascender, 2 == descender, 3 == both
            var i, c, type = 0;
            for (i = 0; i < txt.length; i++) {
                c = txt.charAt(i);
                if ("bdfhiklt1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(c) >= 0) type |= 1;
                else if ("gjpqy".indexOf(c) >= 0) type |= 2;
                else if (" \nacemnorsuvwxz.,-".indexOf(c) < 0) return 3;
            }
            return type;
        }


        // Prepare the item, setting necessary information
        function prepItem(x) {
            var d3this = d3.select(x);

            if (x._originalSize) {
                // Restore the original size
                d3this.style('font-size', x._originalSize);
            } else {
                // Set baseline style
                d3this.style('alignment-baseline', 'auto');

                // Ensure we have a size >= 1px and store it so that if we are called again
                // we use the original size for calculations
                var fontSize = getComputedStyle(x).fontSize;
                if (fontSize.substring(0,2) == "0.") {
                    fontSize = "1.0";
                    d3this.style('font-size', fontSize);
                }
                x._originalSize = d3this.style('font-size');             // Store the original font size
                x._ascDesc = ascender(x.textContent);                 // Store info on ascender / descender
            }
        }

        function buildItem(x) {
            var r = getBBox(x);
            if (r) {
                var asc = r.height + r.y, desc = asc * 0.9,
                    ht = r.height, wd = r.width + 2, oy = 0;

                if (x._ascDesc == 0) {
                    // Neither ascenders nor descenders
                    ht -= (asc + desc);
                } else if (x._ascDesc == 1) {
                    // Only ascenders
                    ht -= desc;
                    oy += desc / 2;
                } else if (x._ascDesc == 2) {
                    // Only descenders
                    ht -= asc;
                    oy -= asc / 2;
                }

                var item = {width: wd, height: ht, ox: 0, oy: oy},                // Our trial item (with slight x padding)
                    index = items.length;
                item._txt = x;
                items[index] = item;

                totalArea += item.height * item.width;
            }
        }

        function spinItems() {
            var i, t, o, ordered = items.slice();
            ordered.sort(function (a, b) {
                return a.width / a.height - b.width / b.height
            });
            for (i = 0; i < ordered.length / 3; i++) {
                o = ordered[i];
                t = o.height;
                o.height = o.width;
                o.width = t;
                o._rotated = true;
            }


        }


        function prepare() {
            prepItem(this);
            buildItem(this);
        }

        function build() {

            var k, item, scaling = Math.sqrt(ext[0] * ext[1] / totalArea / 2), ii = items;
            parent = items[0]._txt.parentNode;

            // remove old scaling
            parent.removeAttribute('transform');

            // Resize everything, rebuilding items;
            items = [];
            for (k = 0; k < ii.length; k++) {
                var sel = d3.select(ii[k]._txt),
                    size = sel.style('font-size'),
                    num = size.substring(0, size.length - 2),
                    post = size.substring(size.length - 2),
                    value = Math.round(num * scaling) + post;
                sel.style('font-size', value);
                buildItem(ii[k]._txt);
            }

            spinItems();

            for (k = 0; k < items.length; k++) {
                item = items[k];
                var i, hit = true, theta = 0;                                      // Start at center and ensure we loop

                item.title = this.textContent;
                // Find any items at least this large and where we put them (only consider items with same orientation)
                for (i = k - 1; i >= 0; i--) {
                    if (item.width >= precision * items[i].width && item.height > precision * items[i].height) {
                        theta = Math.max(theta, items[i].theta);
                    }
                }


                while (hit) {
                    // Set trial center location
                    var cos = Math.cos(theta);
                    var sin = Math.sin(theta);

                    // Oval layout
                    item.x = cos * theta * dx;
                    item.y = sin * theta * dy;


                    if (rectangular) {
                        if (Math.abs(cos) > Math.abs(sin)) {
                            // On the left or right sides of the box
                            item.x = cos > 0 ? theta * dx : -theta * dx;
                            item.y *= Math.SQRT2;
                        } else {
                            // On the top or bottom
                            item.y = sin > 0 ? theta * dy : -theta * dy;
                            item.x *= Math.SQRT2;
                        }
                    }

                    item.theta = theta;
                    hit = false;
                    for (i = k - 1; i >= 0; i--)
                        if (intersects(item, items[i])) {
                            hit = true;
                            break;
                        }

                    // Outward on the spiral -- this is approximately the same as the arc sine and faster
                    theta += delta / Math.max(delta, Math.sqrt(item.x * item.x + item.y * item.y));
                }

                // // Debugging text box surrounding the text
                // d3.select(item._txt.parentNode).append('rect')
                //    .attr('x', item.x - item.width / 2).attr('y', item.y - item.height / 2).attr('width', item.width).attr('height', item.height)
                //    .style('fill', 'red').style('fill-opacity', '0.2').style('stroke', 'red').style('stroke-width', '0.5px');


                var s = "translate(" + (item.x - item.ox) + "," + (item.y + item.oy) + ")";
                if (item._rotated) s += "rotate(90, 0, 0) ";
                item._txt.setAttribute('transform', s);
                d3.select(item._txt).style('alignment-baseline', 'middle');

            }

            transformToFill(parent);

            // Resets information used read for next build
            items = [];
            totalArea = 0.0;

        }


        function transformToFill(svg) {
            // Add transform to the item's parent to make it fit
            var sx = items.reduce(function (v, item) {
                return Math.max(v, Math.abs(item.x) + item.width / 2)
            }, 0);
            var sy = items.reduce(function (v, item) {
                return Math.max(v, Math.abs(item.y) + item.height / 2)
            }, 0);
            var s = Math.min(ext[0] / sx, ext[1] / sy) / 2;

            svg.setAttribute('transform', 'scale(' + s + ')');
        }

        // Return the functions that make X and Y locations
        return {
            'prepare': prepare,
            'build': build
        }
    }

    // Parameters are: a D3 selection target, labeling info struct
    function makeTooltip(d3Target, labeling) {
        var node = d3Target.node();
        if (!node) return;

        var svg = node.ownerSVGElement;                                         // Owning SVG
        if (!svg) return;                                                       // Do not add tooltip if not shown

        var w = +svg.attributes.width.value, h = +svg.attributes.height.value,  // width and height
            pt = svg.createSVGPoint();                                          // For matrix calculations


        var showTooltipForItem = function (d) {
            if (!d) return;
            if (d.data && d.data.row != undefined) d = d.data;       // Complex structures embed the data a level

            // Offsets for scrolling
            var ox = document.documentElement.scrollLeft || document.body.scrollLeft,
                oy = document.documentElement.scrollTop || document.body.scrollTop,
                p;

            function getScreenTipPosition(item, d) {
                var labelPos = makeLoc(item, labeling, '', d), ctm = svg.getScreenCTM();
                pt.x = labelPos.x + ctm.e + ox;
                pt.y = labelPos.y + ctm.f + oy;
                return pt;
            }


            if (d.points) {
                // This is a path and we need to find the best point on it
                var pp = d3.mouse(this), off = this.getScreenCTM();

                // Find closest point on the path
                var best = closestPathPoint(d.points, pp[0], pp[1], true, true);
                d = best.d;                                         // replace with the closest 'd' in array
                p = {                                               // replace p by closest point on path
                    x: best.x + off.e + ox,
                    y: best.y + off.f + oy
                }
            } else {
                p = getScreenTipPosition(this, d);                          // get absolute location of the target
            }

            var content = labeling.content(d);                              // To set html content
            if (!content) return;                                           // No tooltips if no data

            if (!tooltip) {
                tooltip = document.createElement('div');                    // The tooltip div
                document.body.appendChild(tooltip);                         // add to document
            }
            tooltip.setAttribute('class', 'brunel tooltip above');          // Base style
            tooltip.style.visibility = 'visible';                           // make visible
            tooltip.style.width = null;   								    // Allow free width (for now)
            tooltip.innerHTML = content;                                    // set html content

            var max_width = w / 3;                                          // One third width at most
            if (tooltip.offsetWidth > max_width)                            // If too wide, set a width for the div
                tooltip.style.width = max_width + "px";

            var top = p.y - 10 - tooltip.offsetHeight;                      // top location
            if (top < 2 && p.y < h / 2) {                                   // We are in top half up AND overflow top
                var old = labeling.method;                                  // save the original method
                labeling.method = "bottom";                                 // switch to finding lower position
                p = getScreenTipPosition(this, d);                          // get modified location of the target
                labeling.method = old;                                      // restore old method
                top = p.y + 10;
                tooltip.setAttribute('class', 'brunel tooltip below');      // Show style for BELOW the target
            }

            tooltip.style.left = (p.x - tooltip.offsetWidth / 2) + 'px';    // Set to be centered on target
            tooltip.style.top = top + 'px';   // locate relative to the div center notch
        };

        var hideTooltip = function () {
            if (tooltip) tooltip.style.visibility = 'hidden';
        };

        function installStandardTooltip() {
            d3Target.on('mousemove', showTooltipForItem);
            d3Target.on('mouseout', hideTooltip);   // hide it
        }

        /**
         * Install a tooltip that snaps to the given distance
         * @param chart the D3 selection for the chart that contains this
         * @param distance the distance to snap to
         */
        function installSnap(chart, distance) {
            d3Target.on('mousemove', showTooltipForItem);               // Show tooltip when directly over item

            // Add handlers to the overlay for when the mouse is not directly over an item
            chart.select('rect.overlay')
                .on('mousemove.tooltip_snap', function () {
                    var c = closestItem(d3Target, 'xy', distance);
                    if (c.item)
                        showTooltipForItem.call(c.target, c.item);
                    else
                        hideTooltip();
                })
                .on('mouseout.tooltip_snap', function () {
                    hideTooltip();
                });
        }

        return {
            install: installStandardTooltip,
            installSnap: installSnap,
            showFor: showTooltipForItem,
            hide: hideTooltip
        }

    }


    /**
     * Creates a transition for a selection, if it is needed
     * @param selection the selection to base this on
     * @param time time to animate (if <=0, no animation will be done)
     * @param endFunction a function to call on the selection after everything has been done
     * @returns the transition or simple selection
     */
    function transition(selection, time, endFunction) {
        if (time > 0) {
            var t = selection.transition().duration(time);
            if (endFunction) t.on('end', endFunction);
            return t;
        } else {
            if (endFunction) selection.each(endFunction);
            return selection
        }
    }


    // Check if it hits an existing space
    function hitsExisting(box, hits) {
        if (!hits || !hits.D) return false;                // Not needed if no hits or no granularity requested
        if (hits.x == null) {
            // Define the offset. We use this to ensure that when we pan, there are no changes to the logic
            // Otherwise we get flickering due to different rounding of the panned coordinates
            hits.x = box.x;
            hits.y = box.y;
        }

        var i, j, D = hits.D, x = box.x - hits.x, y = box.y - hits.y,
            xmin = D * Math.round(x / D), xmax = x + box.width,
            ymin = D * Math.round(y / D), ymax = y + box.height;

        // Does it hit an existing location
        for (i = xmin; i <= xmax; i += D) for (j = ymin; j <= ymax; j += D)
            if (hits[i * 10000 + j]) return true;

        // No! so we must update those locations before returning the fact it misses
        for (i = xmin; i <= xmax; i += D) for (j = ymin; j <= ymax; j += D)
            hits[i * 10000 + j] = true;

        return false;
    }

    // Moves a label (text) inside the space defined by geom so long as part of it would overlap
    function nudgeInside(text, labeling, geom) {
        var o, b = getBBox(text), D = 2;                                      // D is the pad distance
        if (!b) return b;
        if (!labeling.fit) {
            var L = geom.inner_left, R = L + geom.inner_rawWidth,              // left and right of the space
                l = b.x, r = l + b.width;                                   // box left and right
            if (l < L + D && r > L - D) {
                // Offset to the right
                o = L + D;
                if (labeling.align == 'end') o += (r - l);
                else if (labeling.align == 'middle') o += (r - l) / 2;
                text.setAttribute("x", o);
            } else if (r > R - D && l < R + D) {
                // Offset to the right
                o = R - D;
                if (labeling.align == 'start') o -= (r - l);
                else if (labeling.align == 'middle') o -= (r - l) / 2;
                text.setAttribute("x", o);
            }
        }
        return b;
    }


    // Add a label for the selection 'item' to the group 'labelGroup', using the information in 'labeling'
    // 'hits' keeps track of text hit boxes to prevent heavy overlapping
    // 'geom' give the space into which the labels should fit
    /**
     * Add a label for the selection
     * @param item          the SVG item for the element we want to label (a rect, path, etc)
     * @param labelGroup    the group we want to add the label into
     * @param labeling      Brunel structure defining how we want to label
     * @param hits          A map of grid positions to use in overlap prevention (with 'D' being the gird granularity)
     * @param geom          Overall geometric layout of the chart
     */
    function labelItem(item, labelGroup, labeling, hits, geom) {
        var d = item.__data__;
        if (d.data && d.data.row != undefined) d = d.data;  // For hierarchies and structures where the data is a layer down
        var content = labeling.content(d);
        if (!content || !item.parentNode) return;                               // If there is no content, we are done
        // If no parent item then we are likely in a transition state
        // which can cause failures in FF for bbox calculations.

        if (!labeling.align) labeling.align = "middle";

        // Ensure the label exists and store a reference to it
        var attached = item.__labels__;                     // The collection of attached labels
        if (!attached) item.__labels__ = attached = {};     // Ensure it exists
        var txt = attached[labeling.index];                 // Get the item for this label
        if (!txt) {
            txt = labelGroup.append('text');
            attached[labeling.index] = txt;
            txt.__labeling__ = labeling;
        }

        if (labeling.cssClass) txt.attr('class', labeling.cssClass(d));
        else txt.classed('label', true);

        txt.classed("selected", item.classList && item.classList.contains("selected"));           // Copy selection status to label

        var textNode = txt.node(),                          // SVG node
            style = getComputedStyle(textNode),             // SVG style
            posV = style.verticalAlign,                     // positioning
            loc = makeLoc(item, labeling, content);         // Get center point (x,y) and surrounding box (box)

        // Idf we cannot find a location, no need to do anyhtign else
        if (!loc || !loc.box) return;

        if (posV.match("px$") == "px")
            loc.y += Number(posV.substring(0, posV.length - 2));

        txt.style('text-anchor', labeling.align).attr('dy', (labeling.dy || "0.25") + "em");

        // Do not wrap if the text has been explicitly placed
        if (labeling.fit && !labeling.where) {
            wrapInBox(textNode, content, loc, labeling.dy);
        } else {
            txt.attr('x', loc.x).attr('y', loc.y).text(content);            // Place at the required location
            var kill, b = nudgeInside(textNode, labeling, geom);            // Nudge inside (and get the box for it)
            // If it doesn't fit, kill the text
            if (labeling.fit) {
                // Too tall to fit a single line, or too wide and could not add ellipses
                kill = (b.height > loc.box.height ||
                b.width > loc.box.width && !addEllipses(textNode, content, loc.box.width));
                if (kill) {
                    textNode.parentNode.removeChild(textNode);          // remove from parent
                    attached[labeling.index] = null;                    // dissociate from item
                }

            } else {
                txt.classed("overlap", hitsExisting(b, hits));          // Set the style for overlapping text
            }
        }
    }


    function removeLabels(item) {
        var i, attached = item.__labels__;
        if (attached)
            for (i in attached) if (attached[i]) attached[i].remove();
    }


    /**
     * Apply labels to shapes
     * @param element the d3 selection for the element to be labeled
     * @param group the group to place labels in
     * @param time transition time
     * @param geom overall geometry
     * @param labelingArray array of labeling definitions to use
     */
    function applyLabeling(element, group, time, geom, labelingArray) {

        function makeHits() {                        // Keeps track of hit items
            return {D: labelingArray[0].granularity}
        }

        var hits = makeHits();                          // Hit items for one pass

        function doLabeling(item) {
            labelingArray.forEach(function (labeling) {
                labelItem(item, group, labeling, hits, geom);       // label each item
            });
        }


        if (time > 0)
            return element.transition("labels").duration(time).tween('func', function (d, i) {
                var item = this;
                return function () {
                    if (!i) hits = makeHits();                              // Every time we start a pass
                    doLabeling(item);
                }
            });
        else
            return element.each(
                function () {
                    doLabeling(this);
                }
            );
    }

    // If we have a positive timing, start tweening using the defined function
    // Otherwise just call the function for each data item
    function transitionTween(element, time, func) {
        if (time)
            return element.transition("element").duration(time).tween('func', func);
        return element.each(function (d, i) {
            return func.call(this, d, i)()
        })
    }

    // Log timing (for debugging)
    function time(description) {
        if (!console) return;               // Embedded implementations do not have one
        var t = new Date().getTime();
        if (lastTime) console.log("[Time] " + lastTimeDescr + " -> " + description + ":\t" + (t - lastTime));
        lastTime = t;
        lastTimeDescr = description;
    }

    function sizedPath() {

        var ZERO = -1e-6, ONE = 1 + 1e-6;                // Tolerances added for numeric round-off

        // The accessor functions
        var x = function (d) {
                return d.x;
            },
            y = function (d) {
                return d.y;
            },
            r = function (d) {
                return d.r;
            };


        // Create the segment from the sized endpoints p and q, offsetting by the size in the indicated direction
        function makeSegment(p, q, a) {
            var cos = Math.cos(a), sin = Math.sin(a);
            return {
                a: {x: p.x + p.r * cos, y: p.y + p.r * sin, r: p.r},
                b: {x: q.x + q.r * cos, y: q.y + q.r * sin, r: q.r}
            };
        }

        // Returns null if parallel lines, otherwise an object with hit = true/false and p = point of intersection
        function intersect(p, q) {
            var r = subtract(p.b, p.a), s = subtract(q.b, q.a), pq = subtract(q.a, p.a);  // Working vectors
            var denominator = cross(r, s);                                          // Cross product of segment vectors
            if (Math.abs(denominator) < 0.01)  return null;                         // Lines are parallel

            // The parametric distances to the intersection point for both lines
            var t = cross(pq, s) / denominator,
                u = cross(pq, r) / denominator;

            var hit = (t >= ZERO) && (t <= ONE) && (u >= ZERO) && (u <= ONE);       // Hit point on both segments?
            var pt = {x: p.a.x + (p.b.x - p.a.x) * t, y: p.a.y + (p.b.y - p.a.y) * t};    // Point of intersection
            return {hit: hit, p: pt};
        }

        function cross(a, b) {
            return a.x * b.y - a.y * b.x;
        }

        function subtract(a, b) {
            return {x: a.x - b.x, y: a.y - b.y};
        }

        function vertex(p, command) {
            return (command ? command : "") + p.x.toFixed(1) + " " + p.y.toFixed(1);
        }

        function addHalfPath(segments) {
            var i, p = segments[0], q, d = vertex(p.a);
            for (i = 1; i < segments.length; i++) {
                // We consider the line segments p (the previous one) and q (the current one)
                // If they intersect, we use that point as the join, otherwise we add a circular arc
                // Parallel segments, if they ever occur, must have almost touching end points
                q = segments[i];
                var sect = intersect(p, q);
                if (!sect) {
                    // The segments are parallel -- just jump to the start of the target segment
                    d += vertex(q.a, "L");
                } else if (sect.hit) {
                    // Join to the point of intersection
                    d += vertex(sect.p, "L");
                } else {
                    // Add a circular arc between the end of the first and start of the second segments
                    var r = q.a.r.toFixed(1);
                    d += vertex(p.b, "L") + "A" + r + " " + r + " 0 0 1 " + vertex(q.a);
                }
                p = q;
            }
            return d + vertex(p.b, "L");
        }

        function polylineToPath(polyline) {
            if (polyline.length < 2) return "";		                    // Not long enough to be useful
            var i, a, seg, p = polyline[0], q, upper = [], lower = [];  // upper and lower are the edges on the path

            for (i = 1; i < polyline.length; i++) {
                q = polyline[i];
                // Get a point perpendicular to the direction of travel at the second point
                a = Math.atan2(p.y - q.y, p.x - q.x) + Math.PI / 2;
                upper.push(makeSegment(p, q, a));
                seg = makeSegment(p, q, a + Math.PI);
                lower.push({a: seg.b, b: seg.a});
                p = q;
            }
            lower.reverse();

            // Make the half-paths and join them together
            return "M " + addHalfPath(upper) + " L " + addHalfPath(lower) + "Z";
        }

        // The path we create
        function makePath(d) {
            var i, px, py, pr, polyline = [], last = [], path = "";
            for (i = 0; i < d.length; i++) {
                px = x.call(this, d[i], i);
                py = y.call(this, d[i], i);
                pr = r.call(this, d[i], i);
                if (px && py && pr) {
                    // Ignoring duplicated locations, this is the new 'last' item, and add it to the array
                    if (px != last.x || py != last.y)
                        polyline.push(last = {x: px, y: py, r: pr / 2});
                } else {
                    // Missing values signals the end of a segment
                    path += polylineToPath(polyline);
                    polyline = [];
                }
            }
            return path + polylineToPath(polyline);
        }


        // Modify or return the accessors
        makePath.x = function (v) {
            if (!arguments.length) return x;
            x = v;
            return makePath
        };
        makePath.y = function (v) {
            if (!arguments.length) return y;
            y = v;
            return makePath
        };
        makePath.r = function (v) {
            if (!arguments.length) return r;
            r = v;
            return makePath
        };

        return makePath;
    }

    // Define a mapping from IDs of a data source to X, Y locations
    var idToPoint = function (idField, xField, yField, n) {
        // Build the map
        var i, id, map = {};
        for (i = 0; i < n; i++) {
            id = idField.value(i);
            if (id != null) map[id] = [xField.value(i), yField.value(i)]
        }
        // Return mapping function (pair of nulls if no entry)
        return function (x) {
            var v = map[x];
            return v || [null, null];
        }
    };


    /*
     Reads feature data from a geojson file and adds to the data's rows
     data:      Brunel's data structure
     locations: Maps from source file -> map of data names to their geo file indices
     property:  For custom maps, the topojson property name of the feature that matches the data.
     idFunc:    Function to return the element ID (may be null for background maps)
     element:   The element we are loading data into
     millis:    Time to use for transitioning in the next build
     returns true if we have started loading, but not added the data in yet
     */
    function makeMap(data, locations, property, idFunc, element, millis) {

        // locations looks like { "http://../world.json":{'FR':23, 'GE':123, ...} , ... }
        var use_property = property ? true : false;	//for custom maps, use the supplied property name for the feature ids

        function read() {
            element._features = {};             // Maps names in data to GeoJSON features
            element._featureExtras = [];        // Other, unused, features

            var fileIndex = 1, src,
                remaining = Object.keys(locations).length;          // Number of files to download

            // Read in the features and attach to the data, defining each in a closure
            for (src in locations) {
                new function (url, mapping, idx) {

                    /*

                     x.objects.all.geometries;
                     for (i = 0; i < all.length; i++) {
                     d3Data.push({row: i, geo: topojson.feature(x, all[i])});

                     */

                    d3.json(url, function (error, x) {
                        var i, id, rev = {};                        // reverse mapping
                        var d, all = x.objects.all.geometries;       // All features in the topojson
                        if (!all.bbox) all.bbox = 					//Ensure bbox from a custom map is as expected
                            [x.bbox[0], x.bbox[2], x.bbox[1], x.bbox[4]];
                        if (use_property)
                            if (idFunc) {							//Custom topojson, build the mapping object assuming perfect
                                var arr = data._rows.map(idFunc);   //matches between the data and the topojson features
                                for (var i in arr) mapping[arr[i]] = arr[i];
                            }
                            else mapping = {};						//Custom reference map
                        else
                            property = "a";						    //Standard brunel topojson has geo identifier in property 'a'
                        for (i in mapping) rev[mapping[i]] = i;     // 'rev' maps from feature ID to data name
                        all.forEach(function (v, i) {
                            d = topojson.feature(x, v);             // convert using topojson call
                            id = rev[d.properties[property]];       // The data name for this
                            if (id != null)
                                element._features[id] = d;          // Remember it by data name
                            else {
                                element._featureExtras.push(d);     // Store as an unused element
                                d.key = '#' + idx + "-" + i;        // With a unique key

                            }
                        });
                        if (!--remaining) element.chart().build(millis);    // When data ready, build again
                    });
                }(src, locations[src], fileIndex++);
            }
        }

        function build() {
            // Add feature geom to each row
            var i, rows = [];
            for (i = 0; i < data._rows.length; i++) {
                var row = data._rows[i],                                        // The data row
                    fname = idFunc ? idFunc(row) : null,                        // The ID for that row
                    feature = element._features[fname];                         // The feature for that name
                if (feature) {
                    row.geometry = feature.geometry;
                    row.type = feature.type;
                    row.geo_properties = feature.properties;
                    rows.push(row);
                }
            }
            data._rows = element._featureExtras.concat(rows);                   // non data before (underneath) data
        }

        if (!element._features) {
            read();
            return true
        } else {
            build();
            return false
        }
    }


    /**
     * This method returns the SVG string form for a path
     * @param p the structure returned from insetEdge defining edge endpoints
     * @param curved if true, a curve is drawn, otherwise a straight line is
     * @returns
     */
    function makeEdgeShape(p, curved) {
        return "M" + p.x1 + "," + p.y1 +
            (curved ? "A" + 2 * p.d + "," + 2 * p.d + " 0 0,1 " : "L")
            + p.x2 + "," + p.y2;
    }

    /**
     * Create a node object for the svgItem
     * @param svgItem
     * @return the node item
     */
    function makeNode(svgItem) {
        var b = getBBox(svgItem);                   // bounds of the item
        if (!b) return null;                        // Not displayed -- ignore this
        var r = Math.max(b.width, b.height) / 2;    // good for circles, symbols, rects

        if (svgItem.tagName == 'path' || svgItem.tagName == 'poly')
            r = Math.min(20, r / 3);                 // Arbitrary shape, so inset a lot

        return {
            x: b.x + b.width / 2,                         // The shape center
            y: b.y + b.height / 2,                        // The shape center
            radius: r                                     // calculated radius
        }
    }

    /**
     * Create an edge between two nodes
     * @param d -- data item with string key in format "key1|key2" and row
     * @param nodes -- map from keys to nodes
     * @return the edge joining the two nodes, or null on any failure
     */
    function makeEdge(d, nodes) {
        var keys = d.key.split("|");
        if (keys.length != 2) return null;
        var a = nodes[keys[0]], b = nodes[keys[1]];
        return a && b ? {source: a, target: b, key: d.key, row: d.row} : null;
    }

    /**
     * Start a network layout for the node and edge elements
     * The graph should already have been built within the nodeElement

     * @param graph the graph structure (data)
     * @param nodes selection for the nodes
     * @param edges selection for the links
     * @param zoomNode defines the zoom factors
     * @param geom space to lay out in
     * @param curved true for curved (arc) edges
     * @param density a positive value stating how packed the resulting graph should be (default == 1)
     */
    function makeNetworkLayout(graph, nodes, edges, zoomNode, geom, curved, density) {


        var N = graph.nodes.length, E = graph.links.length,
            W = geom.inner_width, H = geom.inner_height;

        var meanRadius = 0;
        nodes.selection().each(function (d) {
            var box = getBBox(this);
            if (!box) return;

            // Set the known radius on the data
            d.radius = +this.getAttribute("r") || box.getWidth();
            meanRadius += d.radius;
        });

        meanRadius /= graph.nodes.length;

        density = density || 1;
        var pad = geom.default_point_size,
            left = pad, top = pad,
            right = geom.inner_width - pad, bottom = geom.inner_height - pad,
            D = density * Math.min(W, H) / Math.sqrt(N) + meanRadius,
            R = D * Math.max(1, D - 3) / 5 / Math.max(1, E / N);
        R = Math.min(R, D * 6);

        var a, i, nd;
        for (i = 0; i < N; i++) {
            // If the positions have already been defined, do not redefine them
            a = Math.PI * 6 * i / N;
            nd = graph.nodes[i];
            nd.x = nd.x || W * (0.5 + Math.cos(a) * Math.sqrt(i / N) / 2);
            nd.y = nd.y || H * (0.5 + Math.sin(a) * Math.sqrt(i / N) / 2)
        }


        var mergedNodes = nodes.selection(), mergedEdges = edges.selection();
        var isSymbol = mergedNodes.node().tagName == "use";

        function edgeConnectsNode(edge, node) {
            return edge.source === node || edge.target === node;
        }

        // Returns an array of all edges connected to either node
        function connectedEdges(n1, n2) {
            var i, e, result = [];
            for (i = 0; i < E; i++) {
                e = graph.links[i];
                if (edgeConnectsNode(e, n1) || edgeConnectsNode(e, n2))
                    result.push(e);
            }
            return result;
        }

        // Count intersections between edges and all other edges
        function countIntersections(edges) {
            var i, j, e1, e2, c = 0;
            for (i = 0; i < edges.length; i++) {
                e1 = edges[i];
                for (j = 0; j < E; j++) {
                    e2 = graph.links[j];
                    if (intersect(e1.source, e1.target, e2.source, e2.target)) c++;
                }
            }
            return c;
        }

        // Swap node locations
        function swapLocations(n1, n2) {
            var tx = n1.x, ty = n1.y;
            n1.x = n2.x;
            n2.x = tx;
            n1.y = n2.y;
            n2.y = ty;
        }

        var off = 0;                        // Compare nodes with ones this distance away
        function quickSwaps() {
            off++;                           // each time, increment offset
            if (off > N) return;             // Only do it for a while; not forever
            var i, j, n1, n2;
            for (i = 0; i < N; i++) {
                j = (i + off) % N;
                n1 = graph.nodes[i];
                n2 = graph.nodes[j];
                var nodeEdges = connectedEdges(n1, n2);                     // The edges that connect them
                var intersectCurrent = countIntersections(nodeEdges);       // Intersections at existing locations
                swapLocations(n1, n2);
                var intersectSwapped = countIntersections(nodeEdges);       // Intersections at swapped locations
                if (intersectSwapped >= intersectCurrent) {
                    // Bad swap, need to undo
                    swapLocations(n1, n2);
                }
            }
        }


        function ticked() {


            if (N < 100) quickSwaps();

            var t = d3.zoomTransform(zoomNode);

            function scaleX(v) {
                return t.x + t.k * v
            }

            function scaleY(v) {
                return t.y + t.k * v
            }

            if (isSymbol) {
                // symbols need bounds like a rect
                mergedNodes
                    .attr('x', function (d) {
                        return scaleX(d.x) - this.getAttribute("r");
                    })
                    .attr('y', function (d) {
                        return scaleY(d.y) - this.getAttribute("r");
                    })
                    .attr('width', function (d) {
                        return 2 * this.getAttribute("r");
                    })
                    .attr('height', function (d) {
                        return 2 * this.getAttribute("r");
                    })
            } else {
                mergedNodes
                    .attr('cx', function (d) {
                        return scaleX(d.x);
                    })
                    .attr('cy', function (d) {
                        return scaleY(d.y);
                    })
            }

            mergedNodes.each(function (d) {
                    // Adjust placement of labels
                    var i, txt, L = this.__labels__;
                    if (L)for (i in L) {
                        txt = L[i];
                        if (txt.__off__) {
                            // We have calculated the position, just need to move it
                            txt.attr('x', scaleX(txt.__off__.dx + d.x));
                            txt.attr('y', scaleY(txt.__off__.dy + d.y));
                        } else {
                            // First time placement without hit detection
                            labelItem(this, null, txt.__labeling__, null, geom);
                            // record unscaled relative location
                            txt.__off__ = {
                                dx: +txt.attr('x') / t.k + t.x - d.x,
                                dy: +txt.attr('y') / t.k + t.y - d.y
                            }
                        }
                    }
                }
            );

            mergedEdges.attr('d', function (d) {
                // First we inset the edges for the radii of the sources
                var p = insetEdge(scaleX(d.source.x), scaleY(d.source.y), d.source,
                    scaleX(d.target.x), scaleY(d.target.y), d.target);

                // Then we return the path description for a curve or a straight line
                // if we do not have a valid inset edge, return nothing
                return p ? makeEdgeShape(p, curved) : null;
            });

            // If the parent element has facet children, build them
            nodes.buildFacet && nodes.buildFacet();
        }


        var force = d3.forceSimulation()
            .force("link", d3.forceLink(graph.links).distance(D))
            .force("center", d3.forceCenter(W / 2, H / 2))
            .force("charge", d3.forceManyBody().distanceMax(geom.inner_radius / 2).strength(-R))
            .force("inside", function () {
                var i, r, n = graph.nodes.length, node;
                for (i = 0; i < n; i++) {
                    node = graph.nodes[i];
                    r = node.radius;
                    if (node.x - r < left) node.vx += left + r - node.x;
                    if (node.x + r > right) node.vx += right - r - node.x;
                    if (node.y - r < top) node.vy += top + r - node.y;
                    if (node.y + r > bottom) node.vy += bottom - r - node.y;
                }
            });


        mergedNodes.call(d3.drag().on('drag', function (d) {
                d.fx = d3.event.x;
                d.fy = d3.event.y;
                if (force.alpha() < 0.25) force.alpha(0.25).restart();
            }).on('end', function (d) {
                d.fx = d.fy = null;
            })
        );


        return force.nodes(graph.nodes).on("tick", ticked);
    }

    /**
     * Insets a line segment for different sized nodes at ends
     * @param xa start x coord
     * @param ya start y coord
     * @param radiusA start object we look in for radius (in it, then in .data, then default to 5)
     * @param xb end x coord
     * @param yb end y coord
     * @param radiusB end object we look in for radius (in it, then in .data, then default to 5)
     * @returns {x1, y1, x2, y2, d} -- the points and original distance between them
     */
    function insetEdge(xa, ya, radiusA, xb, yb, radiusB) {
        var dx = (xa - xb), dy = (ya - yb),
            d = Math.sqrt(dx * dx + dy * dy),       // distance between nodes
            ra = radiusA.radius || (radiusA.data ? radiusA.data.radius : 5) || 5,
            rb = radiusB.radius || (radiusB.data ? radiusB.data.radius : 5) || 5;

        if (d < ra + rb + 1) return null;            // line is not long enough to inset

        return {
            x1: xa - dx * ra / d,                    // indented start point (x1,y1)
            y1: ya - dy * ra / d,
            x2: xb + dx * rb / d,                    // indented end point (x2, y2)
            y2: yb + dy * rb / d,
            d: d                                    // original distance between the points
        };
    }

    // Ensures a D3 item has no cumulative matrix transform
    function undoTransform(labels, element) {
        var ctm = element.node().getCTM();
        if (ctm) {
            var node = labels.node(),                                                   // SVG node
                t = node.ownerSVGElement.createSVGTransformFromMatrix(ctm.inverse());   // Convert to a transform
            node.transform.baseVal.initialize(t);                // Apply to create an overall identity transform
        }
        return labels;
    }

    function facet(chart, parentElement, time) {
        parentElement.selection().each(function (d) {
            var row = d.row >= 0 ? d.row : (d.data ? d.data.row : null);
            if (row == null) return;
            var value = parentElement.data().field("#row").value(row);
            var items = value.items ? value.items : [value];          // If just a single row, make it into an array
            var c = chart(this, items.map(function (v) {
                return v - 1
            }));          // Convert 1-based items to 0-based rows
            c.build(time);
        });

    }

    // v is in the range -1/2 to 1/2
    function interpolate(a, b, v) {
        return (a + b) / 2 + v * (b - a);
    }

    // find the closest point on a path
    // array is an array of objects with (x,y,d) values
    // (x,y) is the target point to find the nearest array point to
    // distanceMethod is one of "x", "y", or "xy"
    function closestPathPoint(array, x, y, distanceMethod) {
        result = {distance: 1e99};
        array.forEach(
            function (t) {
                v = 0;
                if (distanceMethod != "y") v += (t.x - x) * (t.x - x);
                if (distanceMethod != "x") v += (t.y - y) * (t.y - y);
                if (v < result.distance) {
                    result.distance = v;
                    result.d = t.d;
                    result.x = t.x;
                    result.y = t.y;
                }
            });
        return result;
    }

    // An animated start to a visualization
    // vis is the base Brunel object
    // data is the raw data table (CSV-like)
    // time is the time we take to do the effect
    function animateBuild(vis, data, time) {

        if (vis.charts.length != 1) return vis.build(data); // Only animate when one chart

        var i,
            targets = ["size", "y", "color"],               // We prefer to animate over size first, then y, then color
            chart = vis.charts[0],                          // The target chart
            scales = chart.scales,                          // Target chart scales
            originalPost = vis.dataPostProcess(),           // The currently defined data post-processing item
            names = [], fields,                             // The fields we will animate (names and as Brunel fields)
            role;                                           // The role we will choose to animate over


        // Use the data structure options to check it a field is numeric (don't animate a category field)
        function isNumeric(name) {
            if (name.charAt(0) == '#' || !data.options) return true;
            for (var i = 0; i < data.names.length; i++)
                if (data.names[i] == name)
                    return data.options[i] != "string";
            return true;
        }

        // Find suitable fields (by name) that have the given role (size, y, color)
        function suitableFields(type) {
            var i, y, f, seen = {}, result = [];              // collect all element's y fields
            chart.elements.forEach(function (e) {
                y = e.fields[type];
                if (y) for (i = 0; i < y.length; i++) {
                    f = y[i];
                    if (seen[f] || f[0] == "'") continue;   // No duplicates and no constant values
                    if (isNumeric(f))   			        // If numeric add values (upper / lower for stacking)
                        result.push(f, f + "$lower", f + "$upper");
                    seen[f] = true;
                }
            });
            return result;
        }

        // Get a suitable starting point for the animation of a given type on a given field
        function start(field) {
            if (role == 'size') return 1e-6;         // Should always be good
            if (scales && scales[role]) {
                // If the domain starts at zero, use that, otherwise use the scale midpoint
                var domain = scales[role].domain();
                return domain[0] == 0 ? 0 : (domain[0] + domain[domain.length - 1]) / 2;
            } else
                return field.min();
        }

        // Replace the data on a field (and return that field)
        // "this" is the data set being evaluated
        function replaceData(name) {
            var field = this.field(name);
            if (!field) return null;                                // Does not exist
            if (field.oProvider) return field;                      // Already done
            field.oProvider = field.provider;                       // swap provider with a constant value
            field.provider = new BrunelData.values_ConstantProvider(
                start(field, role), field.rowCount());
            return field;
        }


        // Look through the roles and pick the first that has usable fields
        for (i = 0; i < targets.length && !names.length; i++)
            names = suitableFields(role = targets[i]);


        if (!names.length) return vis.build(data);          // If no fields, stop trying

        vis.dataPostProcess(function (d) {                  // replace the post-processing definition with:
            fields = names.map(replaceData, d);             // Modify the data (side effect -- setting fields)
            return originalPost(d);                         // call original function on modified data
        });

        vis.build(data);                                    // build using the new data
        vis.dataPostProcess(originalPost);                  // restore the original post-processing definition
        if (fields) fields.forEach(function (f) {           // restore all fields
            if (f) f.provider = f.oProvider;
        });
        vis.rebuild(time);                                  // rebuild with the correct data, animated
    }


    // Find the closest item in the selection to the mouse location
    // selection is the items to search for; maxDistance is the furthest distance out to accept
    // distanceMethod is one of "x", "y", or "xy"
    function closestItem(selection, distanceMethod, maxDistance) {
        var pp, v, x, y, t, xx, yy, that, result = {distance: 9e99};
        selection.each(function (d) {
            that = this;
            pp = pp || d3.mouse(this);                        // Define the mouse location relative to the selection
            xx = pp[0];
            yy = pp[1];
            if (d.points) {
                d.points.forEach(
                    function (t) {
                        v = 0;
                        if (distanceMethod != "y") v += (t.x - xx) * (t.x - xx);
                        if (distanceMethod != "x") v += (t.y - yy) * (t.y - yy);
                        if (v < result.distance) {
                            result.distance = v;
                            result.item = d;
                            result.target = that;
                            result.x = t.x;
                            result.y = t.y;
                        }
                    });
            } else {
                t = getBBox(this);
                if (!t) return;
                var xP = xx < t.x ? -1 : (xx > t.x + t.width ? 1 : 0);          // point is left, inside, right
                var yP = yy < t.y ? -1 : (yy > t.y + t.height ? 1 : 0);         // point is top, inside, bottom

                if (xP == 0 && yP == 0) {
                    // The point is inside the rectangle
                    v = 0;
                } else {
                    x = xP < 0 ? t.x : (xP > 0 ? t.x + t.width : xx);           // closest corner (or point on side)
                    y = yP < 0 ? t.y : (yP > 0 ? t.y + t.height : yy);          // closest corner (or point on side)
                    v = 0;
                    if (distanceMethod != "y") v += (x - xx) * (x - xx);
                    if (distanceMethod != "x") v += (y - yy) * (y - yy);
                }
                if (v < result.distance) {
                    result.distance = v;
                    result.item = d;
                    result.target = that;
                    result.x = x;
                    result.y = y;
                }
            }
        });

        // If we are not close enough, set the result item to null
        result.distance = Math.sqrt(result.distance);
        if (result.distance > maxDistance) result.item = null;
        return result;
    }

    function showCrossHairs(item, target, element, distanceMethod) {
        var i, R = 10,
            chart = element.chart(),
            scales = chart.scales,
            group = element.group();

        if (!group || !scales || !scales.x || !scales.y) return;

        var g = group.selectAll("g.crosshairs");

        // Create the group and shapes for the cross hairs
        if (g.empty()) {
            g = group.append("g").attr("class", "crosshairs");

            // 4 lines, a central circle, and text tags
            for (i = 0; i < 4; i++)
                g.append("line").attr("class", "dim" + Math.floor(i / 2) + " part" + i);
            g.append("circle");
            g.append("text").attr("class", "dim0").attr("dy", "-0.3em");
            g.append("text").attr("class", "dim1").attr("dy", "-0.3em");
        }

        if (!item) {
            g.style("visibility", "hidden");
            return;
        }


        var px, py;
        if (item.points) {
            // Item is a path with a set of points -- find the closest to the mouse
            var pp = d3.mouse(target);
            p = closestPathPoint(item.points, pp[0], pp[1], distanceMethod);
            px = p.x;
            py = p.y;
        } else {
            var box = getBBox(target);
            if (!box) return;
            px = box.x + box.width / 2;
            py = box.y + box.height / 2;
        }

        //Add an inverse to categorical scales
        function addInvertFunction(scale) {
            scale.invert = function (v) {
                // Binary search for closest point
                var values = scale.domain(), low = 0, high = values.length - 1, t;
                while (high - low > 1) {
                    t = Math.floor((low + high) / 2);
                    if (scale(values[t]) > v)
                        high = t;
                    else
                        low = t;
                }
                // return whichever is the nearest of low and high (which are on either side of the value)
                return v - scale(values[low]) < scale(values[high]) - v ? values[low] : values[high];
            }
        }

        if (!scales.x.invert) addInvertFunction(scales.x);
        if (!scales.y.invert) addInvertFunction(scales.y);

        var x = scales.x.invert(px),
            y = scales.y.invert(py),
            x1 = scales.x.range()[0],
            x2 = scales.x.range()[1],
            y1 = scales.y.range()[0],
            y2 = scales.y.range()[1],
            xText = element.data().field(element.fields.x[0]).format(x),
            yText = element.data().field(element.fields.y[0]).format(y);


        // Place the parts
        g.selectAll("line.dim0").attr("x1", px).attr("x2", px);
        g.selectAll("line.dim1").attr("y1", py).attr("y2", py);
        g.select("line.part0").attr("y1", y1).attr("y2", py + R);
        g.select("line.part1").attr("y1", py - R).attr("y2", y2);
        g.select("line.part2").attr("x1", x1).attr("x2", px - R);
        g.select("line.part3").attr("x1", px + R).attr("x2", x2);
        g.select("circle").attr("cx", px).attr("cy", py).attr("r", R);
        g.select("text.dim0").attr("x", px).attr("y", y1).text("\u00a0" + xText);
        g.select("text.dim1").attr("y", py).attr("x", x1).text("\u00a0" + yText);
        g.style("visibility", "visible");
    }


    // Makes a gridline.
    // interior --  the svg group to add to
    // scale -- the scale to use
    // size -- the width or height of the line to draw
    // x -- if true, for the x axis (vertical lines)
    function makeGrid(interior, scale, size, x) {
        var f, data;
        if (scale.ticks) {
            // Transform the ticks
            data = scale.ticks();
            f = function (d) {
                return scale(d)
            };
        } else {
            // Use raw range
            data = scale.range();
            f = function (d) {
                return d
            };
        }

        var selection = interior.selectAll("line.grid." + (x ? "x" : "y")).data(data),
            grid = selection.enter().append("line")
                .attr("class", "grid " + (x ? "x" : "y"))
                .merge(selection);

        if (x)
            grid.attr("y1", 0).attr("y2", size).attr("x1", f).attr("x2", f);
        else
            grid.attr("x1", 0).attr("x2", size).attr("y1", f).attr("y2", f);

        selection.exit().remove();

    }

    function filterTicks(scale) {
        // For small domains, just use that domain
        var domain = scale.domain();
        if (domain.length < 3) return domain;
        var range = scale.range(),
            delta = Math.abs(range[1] - range[0]),
            skip = Math.ceil(16 * domain.length / delta);
        return skip < 2 ? domain : domain.filter(function (d, i) {
            return !(i % skip);
        })
    }

    /**
     * Set zooming parameters. Missing parameter values are left unchanged
     * @param params - if not null, the values inside are used to zoom
     * @param zoom - d3.zoom to manipulate
     * @returns {{dx, dy, s}}
     */
    function panzoom(params, zoom) {
        var dx = zoom.translate()[0], dy = zoom.translate()[1], s = zoom.scale();
        if (params) {
            dx = params.dx || dx;
            dy = params.dy || dy;
            s = params.s || s;
            zoom.translate([dx, dy]).scale(s);
        }
        return {dx: dx, dy: dy, s: s}
    }

    /**1
     * Restricts the parameters of the pan zoom so as to disallow meaningless transformations
     * @param t -- the translation
     * @param geom -- the space we have available
     * @param target -- the target of the zoom
     * @return an adjusted zoom
     */
    function restrictZoom(t, geom, target) {
        var D = 0.5,                                                    // Minimum fraction of screen screen
            dx = t.x, dy = t.y, s = t.k,                                // transform offsets and scale
            W = geom.inner_width, H = geom.inner_height,                // chart bounds
            minW = D * W, minH = D * H;                                 // minimum number of pixels to show

        if (dx + s * W < minW) dx = minW - s * W;                       // Don't allow scrolling off the left
        if (dx > W - minW) dx = W - minW;                               // Don't allow scrolling off the right
        if (dy + s * H < minH) dy = minH - s * H;                       // Don't allow scrolling off the top
        if (dy > H - minH) dy = H - minH;                               // Don't allow scrolling off the bottom


        var result = d3.zoomIdentity.translate(dx, dy).scale(s);
        target.__zoom = result;                                         // Modify the zoom
        return result;                                                  // Restricted result
    }


    //Sets the aspect ratio of the data domain values
    function setAspect(scale_x, scale_y, aspect) {

        //Is it safe to do?
        if (!scale_x.domain() || scale_x.domain().length != 2 || !scale_y.domain() || scale_y.domain().length != 2)
            return;

        //Find the non-zero value for the range (this handles transpose case)
        var xRange = scale_x.range()[1] != 0 ? scale_x.range()[1] : scale_x.range()[0];
        var yRange = scale_y.range()[0] != 0 ? scale_y.range()[0] : scale_y.range()[1];

        //Were the domains Dates?
        var xDIsDate = scale_x.domain()[0].getTime;
        var yDIsDate = scale_y.domain()[0].getTime;

        //Use numerics for calculations
        var xD1 = BrunelData.Data.asNumeric(scale_x.domain()[1]);
        var xD0 = BrunelData.Data.asNumeric(scale_x.domain()[0]);
        var yD1 = BrunelData.Data.asNumeric(scale_y.domain()[1]);
        var yD0 = BrunelData.Data.asNumeric(scale_y.domain()[0]);

        //Adjusts max values if scales were reversed
        var xSign = xD0 > xD1 ? -1.0 : 1.0;
        var ySign = yD0 > yD1 ? -1.0 : 1.0;

        //Domain widths
        var xDomain = Math.abs(xD1 - xD0);
        var yDomain = Math.abs(yD1 - yD0);

        //Largest domain : range
        var xRatio = xDomain / xRange;
        var yRatio = yDomain / yRange;
        var r = Math.max(xRatio, yRatio);

        //Scale the domain values to the desired aspect ratio
        var minX = xD0;
        var maxX = minX + aspect * r * xRange * xSign;
        var minY = yD0;
        var maxY = minY + r * yRange * ySign;

        //Convert back to dates if needed
        if (xDIsDate) {
            minX = BrunelData.Data.asDate(minX);
            maxX = BrunelData.Data.asDate(maxX);
        }
        if (yDIsDate) {
            minY = BrunelData.Data.asDate(minY);
            maxY = BrunelData.Data.asDate(maxY);
        }

        scale_x.domain([minX, maxX]);
        scale_y.domain([minY, maxY]);
    }


    /**
     * Creates a point stream for efficent maps in D3
     * @param projection the projeytion to base on
     */
    function geoStream(projection) {
        var p, lastX, lastP;                            // Stores last positions (x value, and result point)
        return d3.geoTransform({

            lineStart: function () {
                lastX = lastP = null;               // Reset last values
                this.stream.lineStart();
            },

            lineEnd: function () {
                lastX = lastP = null;               // Reset last values
                this.stream.lineEnd();
            },

            point: function (x, y) {
                if (y > -85) {
                    // Away from the south pole ensure we do not wrap around the world
                    if (lastX > 150 && x < -150) x = Math.min(180, x + 360);
                    if (lastX < -150 && x > 150) x = Math.max(-180, x - 360);
                }
                lastX = x;

                p = projection([x, y]);             // use the defined projection
                if (!p) return;                     // if an invalid value, ignore it
                // If within a half pixel of the last point, ignore it
                if (lastP && (lastP[0] - p[0]) * (lastP[0] - p[0]) + (lastP[1] - p[1]) * (lastP[1] - p[1]) < 0.7) return;
                lastP = p;
                this.stream.point(p);
            }
        });
    }

    // The winkel tripel projection; suitable for dispalying the whole world
    function winkel3() {
        function w(x, y) {
            var a = Math.acos(Math.cos(y) * Math.cos(x / 2)), sinca = Math.abs(a) < 1e-6 ? 1 : Math.sin(a) / a;
            return [Math.cos(y) * Math.sin(x / 2) / sinca + x / Math.PI, (Math.sin(y) * sinca + y) / 2];
        }

        return d3.geoProjection(w);
    }

    /**
     * Returns a string representation of a zoom ratio suitable for use as a class label
     * @param v a value to use
     */
    function zoomLabel(v) {
        var pre = (v > 1) ? "zoomIn zoomIn" : "zoomOut zoomOut";           // zoom direction
        v = Math.round(v > 1 ? v : 1 / v);          // Ensure v >=1
        if (v == 1) return "zoomNone";              // Flat
        if (v <= 5) return pre + v;                 // In1, In2, In3, In4, In5 and similar for out
        if (v <= 10) return pre + "High";           // zoom levels 6-10
        return pre + "Extreme";                     // extreme zoom levels
    }


    /**
     * Copies information from the d3 layout to the brunel nodes
     * @param d3tree d3 tree, a subset of the Brunel tree -- ach node contains a Brunel node as 'data'
     * @param brunelTree the full brunel tree
     * @param pad amount ot pad nodes by
     */
    function copyTreeLayoutInfo(d3tree, brunelTree, pad) {

        // Pad first
        d3tree.forEach(function (d) {
            d.data.x = d.x += pad;
            d.data.y = d.y += pad
        });

        function copy(node, pos) {
            if (!node.x) {
                node.x = pos.x;
                node.y = pos.y;
            }
            if (node.children) {
                node.children.forEach(function (c) {
                    copy(c, node);
                });
            }
        }

        // Children with no position (collapsed) are located at parents
        copy(brunelTree.root, {x: undefined, y: undefined});

        // remove links we cannot place anywhere (root links when we have expanded a subtree so top is invisible)
        if (brunelTree.links)
            brunelTree.links = brunelTree.links.filter(function (d) {
                return !isNaN(d.source.x + d.target.x)
            });
    }


    // filters to data items only
    function hasData(d) {
        return d && (d.row != null || hasData(d.data))
    }

    // returns a filter function identifying selected items of the data
    function isSelected(data) {
        return function (d) {
            return data.$selection(d) == '\u2713'
        }
    }

    // Add definitions tag to the visualization with standard symbols
    function addStandardDefinitions(vis) {
        vis.selectAll('defs').data(['X']).enter().append('defs').html(
            '<g><symbol id="_sym_circle" viewBox="0 0 24 24"><path d="M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z"/></symbol><symbol id="_sym_square" viewBox="0 0 24 24"><path d="M3,3V21H21V3Z"/></symbol><symbol id="_sym_triangle" viewBox="0 0 24 24"><path d="M1,21H23L12,2Z"/></symbol><symbol id="_sym_diamond" viewBox="0 0 24 24"><path d="M21.5,10.8L13.2,2.5C12.5,1.8 11.5,1.8 10.8,2.5L2.5,10.8C1.8,11.5 1.8,12.5 2.5,13.2L10.8,21.5C11.5,22.2 12.5,22.2 13.2,21.5L21.5,13.2C22.1,12.5 22.1,11.5 21.5,10.8Z"/></symbol><symbol id="_sym_cross" viewBox="0 0 24 24"><path d="M23,16H16V23H8V16H1V8H8V1H16V8H23V16Z"/></symbol><symbol id="_sym_pentagon" viewBox="0 0 24 24"><path d="M12,2.5L2,9.8L5.8,21.5H18.2L22,9.8L12,2.5Z"/></symbol><symbol id="_sym_star" viewBox="0 0 24 24"><path d="M12,17.27L18.18,21L16.54,13.97L22,9.24L14.81,8.62L12,2L9.19,8.62L2,9.24L7.45,13.97L5.82,21L12,17.27Z"/></symbol><symbol id="_sym_hexagon" viewBox="0 0 24 24"><path d="M21,16.5C21,16.88 20.79,17.21 20.47,17.38L12.57,21.82C12.41,21.94 12.21,22 12,22C11.79,22 11.59,21.94 11.43,21.82L3.53,17.38C3.21,17.21 3,16.88 3,16.5V7.5C3,7.12 3.21,6.79 3.53,6.62L11.43,2.18C11.59,2.06 11.79,2 12,2C12.21,2 12.41,2.06 12.57,2.18L20.47,6.62C20.79,6.79 21,7.12 21,7.5V16.5Z"/></symbol></g>'
            + '<g><linearGradient id="_gradient_linear_blue" x1="0%" y1="0%" x2="0%" y2="100%"><stop offset="0%" style="stop-color:rgb(11, 155, 219)"/><stop offset="100%" style="stop-color:rgba(11, 155, 219, 0);stop-opacity:0" /></linearGradient><linearGradient id="_gradient_linear_red" x1="0%" y1="0%" x2="0%" y2="100%"><stop offset="0%" style="stop-color:rgb(242, 87, 76)"/><stop offset="100%" style="stop-color:rgba(242, 87, 76, 0);stop-opacity:0" /></linearGradient><radialGradient id="_gradient_radial_blue" cx="50%" cy="50%" r="50%" y2="100%"><stop offset="0%" style="stop-color:rgb(11, 155, 219)"/><stop offset="100%" style="stop-color:rgba(11, 155, 219, 0);stop-opacity:0" /></radialGradient><radialGradient id="_gradient_radial_red" cx="50%" cy="50%" r="50%" y2="100%"><stop offset="0%" style="stop-color:rgb(242, 87, 76)"/><stop offset="100%" style="stop-color:rgba(242, 87, 76, 0);stop-opacity:0" /></radialGradient></g>'
        );
    }

    return {
        'makeData': makeDataset,
        'geometry': geometries,
        'addTooltip': makeTooltip,
        'makePathSplits': split,
        'locate': idToPoint,
        'addLegend': makeLegend,
        'centerInWedge': centerInArc,
        'makeRowsWithKeys': makeRowsWithKeys,
        'label': applyLabeling,
        'removeLabels': removeLabels,
        'undoTransform': undoTransform,
        'cloudLayout': cloud,
        'gridLayout': gridLayout,
        'prune': pruneTreeToSize,
        'select': select,
        'transition': transition,
        'sizedPath': sizedPath,
        'tween': transitionTween,
        'addFeatures': makeMap,
        'edge': makeEdgeShape,
        'insetEdge': insetEdge,
        'network': makeNetworkLayout,
        'makeNode': makeNode,
        'makeEdge': makeEdge,
        'facet': facet,
        'time': time,
        'interpolate': interpolate,
        'animateBuild': animateBuild,
        'makeGrid': makeGrid,
        'crosshairs': showCrossHairs,
        'filterTicks': filterTicks,
        'closestOnPath': closestPathPoint,
        'closest': closestItem,
        'panzoom': panzoom,
        'restrictZoom': restrictZoom,
        'zoomLabel': zoomLabel,
        'geoStream': geoStream,
        'winkel3': winkel3,
        'setAspect': setAspect,
        'hasData': hasData,
        'isSelected': isSelected,
        'copyTreeLayoutInfo': copyTreeLayoutInfo,
        'addDefinitions': addStandardDefinitions
    }

})
();
