/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

// A closure for the utilities needed to build Brunel items for D3
var BrunelD3 = (function () {

    var tooltip, lastTime, lastTimeDescr;        // Div for the tooltip

    // Return geometries for the given target given the desired margins
    function geometries(target, chart_top, chart_left, chart_bottom, chart_right,
                        inner_top, inner_left, inner_bottom, inner_right) {
        var attrs = target.attributes;
        var g = {
            outer_width: attrs.width.value,
            outer_height: attrs.height.value,
            margin_top: chart_top + inner_top,
            margin_left: chart_left + inner_left,
            margin_bottom: chart_bottom + inner_bottom,
            margin_right: chart_right + inner_right,
            chart_top: chart_top,
            chart_left: chart_left,
            chart_bottom: chart_bottom,
            chart_right: chart_right,
            inner_top: inner_top,
            inner_left: inner_left,
            inner_bottom: inner_bottom,
            inner_right: inner_right,

            // Allow the inner coords to be transposed
            transpose: function () {
                var t = this.inner_width;
                this.inner_width = this.inner_height;
                this.inner_height = t;
            }
        };
        g.inner_width = g.outer_width - g.margin_left - g.margin_right;
        g.inner_height = g.outer_height - g.margin_top - g.margin_bottom;
        g.inner_radius = Math.min(g.inner_width, g.inner_height) / 2;
        g.default_point_size = Math.max(6, g.inner_radius * 0.035);
        return g;
    }


    // Add a color legend
    function colorLegend(target, title, scale, ticks) {
        target.attr('class', 'legend').append('text').attr('x', 0).attr('y', 16).style('text-anchor', 'end')
            .text(title).attr('class', 'title');
        var legend = target.selectAll('legend').data(ticks).enter().append('g').attr('class', 'swatch')
            .attr('transform', function (d, i) {
                return 'translate(-20,' + (25 + i * 20) + ')';
            });
        legend.append('rect').attr('x', 6).attr('width', 14).attr('height', 14).style('fill', scale);
        legend.append('text').attr('y', 7).attr('dy', '.35em').style('text-anchor', 'end').text(function (d) {
            return BrunelData.Data.format(d, true);
        })
    }


    // Create Split data structure for use in lines, paths and areas
    function split(data, pathFunction, xFunction) {
        // Create an array for the unique split categories
        var splits = data._rows.map(data._split).filter(function (v, i, a) {
            return a.indexOf(v) === i
        });

        // Define a function for each category that extracts the rows and sorts them into x order
        // It returns a structure for use in the element containing sample row, key and path
        var f = function (category) {
            var d = data._rows.filter(function (d) {
                return data._split(d) == category;
            });
            if (xFunction) d.sort(function (a, b) {
                return xFunction(a) - xFunction(b)
            });
            return {path: pathFunction(d), key: d[0].key, row: d[0].row}
        };

        // Convert array of categories into array of above structures
        return splits.map(f)
    }

    // Create rows and keys for the data
    function makeRowsWithKeys(keyFunction, N) {
        var result = [];
        for (var i = 0; i < N; i++) result.push({row: i, key: keyFunction(i)});
        return result;
    }

    // Offset in pixels to center svg text in an svg arc, returns a large number if it does not fit
    function centerInArc(text, arc, arcWidth) {
        // The length around the middle of the arc
        var arcLen = arc.getTotalLength() / 2 - arcWidth;
        var d = (arcLen - text.getComputedTextLength()) / 2;
        text.firstChild.setAttribute('startOffset', d > 0 ? d : 0);
        text.firstChild.setAttribute('visibility', d < 1 ? "hidden" : "visible");
    }

    function shrink(rect, sx, sy) {
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

    function centroidLoc(path, d, svgItem) {
        // Find the centroid
        var c = path.centroid(d);
        var b = svgItem.getBBox();
        return {x: c.x, y: c.y, box: shrink(b)}
    }

    function pathLoc(svgItem) {
        // Find a point half way along
        var c = svgItem.getPointAtLength(svgItem.getTotalLength() / 2);
        var b = svgItem.getBBox();
        return {x: c.x, y: c.y, box: shrink(b, 1, 1 / 3)}
    }

    function polyLoc(svgItem) {
        // Average positions 25% and 75% of the way around the shape
        var len = svgItem.getTotalLength();
        var a = svgItem.getPointAtLength(len / 4);
        var b = svgItem.getPointAtLength(3 * len / 4);
        var box = svgItem.getBBox();
        return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, box: shrink(box)}
    }


    function areaLoc(svgItem, margin) {
        var len = svgItem.getTotalLength();
        var box = svgItem.getBBox();

        var dx = Math.max(6, box.width / 40),               // We discretize to steps of 'dx' amounts
            M = Math.max(len / dx, 200),                      // We examine this many points along the path
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
        var index = N/2, heightAtX = 0, edgeD;
        for (i in lower) {
            t = upper[i] - lower[i];
            if (lower[i + 1] && lower[i - 1])  t = (2 * t + (upper[i + 1] - lower[i + 1] + upper[i - 1] - lower[i - 1]) / 2) / 3;
            edgeD = Math.min(i, N - i) * dx;
            if (edgeD < margin) t *= 0.01;          // Only add here if we really have to
            else if (edgeD < 2* margin) t *= 0.9;   // Prefer further in
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

    function boxLoc(svgItem, method) {
        // Add 3 pixels padding
        var b = svgItem.getBBox();
        var x = method == 'left' ? b.x - 3 : (method == 'right' ? b.x + b.width + 3 : b.x + b.width / 2);
        var y = method == 'top' ? b.y - 3 : (method == 'bottom' ? b.y + b.height + 3 : b.y + b.height / 2);
        return {x: x, y: y, box: b}
    }

    // Returns an object with 'x', 'y' and 'box' that "surrounds" the text
    function makeLoc(target, labeling, s, datum) {
        if (labeling.where) {
            var box = target.getBBox();
            var p = labeling.where(box, s);
            return {x: p.x, y: p.y, box: box};
        } else if (labeling.method == 'wedge')
            return wedgeLoc(labeling.path, datum);
        else if (labeling.method == 'poly')
            return polyLoc(target);
        else if (labeling.method == 'area')
            return areaLoc(target, s.length * 3.5);                   // Guess at text length
        else if (labeling.method == 'path') {
            if (labeling.path.centroid)
                return centroidLoc(labeling.path, datum, target);
            else
                return pathLoc(target);
        } else
            return boxLoc(target, labeling.method);
    }

    function makeLabel(datum, text, target, labeling, needsRow) {
        return function () {
            if (!target || !text || needsRow && datum.row == null) return;  // Some labeling only work for rows
            var s = labeling.content(datum);                        // If there is no content, we are done
            if (!s) return;

            var loc = makeLoc(target, labeling, s, datum);          // Get center point (x,y) and surrounding box (box)

            if (labeling.fit && !labeling.where) {
                wrapInBox(text, s, loc);
            } else {
                // Place at the required location
                text.setAttribute('x', loc.x);
                text.setAttribute('y', loc.y);
                text.textContent = s;

                // f it doesn't fit, kill the text
                if (labeling.fit) {
                    var b = text.getBBox();
                    if (b.height > loc.box.height) {
                        // Too tall to fit a single line
                        text.parentNode.removeChild(text);
                    } else if (b.width > loc.box.width) {
                        // If adding ellipses doesn't work, kill it
                        if (!addEllipses(text, s, loc.box.width))
                            text.parentNode.removeChild(text);
                    }
                }
            }

        };
    }

    function makeTextSpan(loc, parent) {
        var tspan = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
        tspan.setAttribute('class', 'label');
        tspan.setAttribute('x', loc.x);
        tspan.setAttribute('y', loc.y);
        parent.appendChild(tspan);
        return tspan;
    }

    function addEllipses(span, text, maxWidth) {
        var i = text.length - 3;
        while (i >= 0) {
            span.textContent = text[text.length - 1] == '.' ? text : text.substring(0, i) + "\u2026";
            if (span.getComputedTextLength() <= maxWidth) return true;
            i--;
        }
        return false;
    }

    function wrapInBox(textItem, text, loc) {
        while (textItem.firstChild) textItem.removeChild(textItem.firstChild);
        var words = text.split(/\s+/), word, content = [], height;

        var tspan;
        for (var i = 0; i < words.length; i++) {
            tspan = tspan || makeTextSpan(loc, textItem);
            word = words[i];
            content.push(word);
            tspan.textContent = content.join(" ");
            height = height || tspan.getBBox().height;
            if (textItem.childNodes.length * height > loc.box.height) {
                textItem.removeChild(tspan);
                break;
            }

            if (tspan.getComputedTextLength() > loc.box.width - 4) {
                content.pop();
                if (content.length == 0) {
                    if (!addEllipses(tspan, word, loc.box.width)) {
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

        // Move everything up to center them
        for (i = 0; i < spans.length; i++)
            spans[i].setAttribute('dy', ((i - spans.length / 2.0) * 1.1 + 0.85) + "em");
    }


    function shorten(text, len) {
        if (!text || text.length <= len) return text;
        var parts = text.split(/[ \t\n]+/);
        var result = "";
        if (parts.length == len) {
            for (var i = 0; i < parts.length; i++) result += parts[i].substr(0, 1);
        } else {
            var n = Math.floor((len - (parts.length - 1)) / parts.length);     // Account for spaces between parts
            if (n < 1) return text.substr(0, len);
            for (var i = 0; i < parts.length - 1; i++) result = result + parts[i].substr(0, n) + " ";
            result += parts[parts.length - 1].substring(0, len - result.length);
        }
        return result;
    }

    // Select the indicated rows
    function select(row, data) {
        var i, j,
            rows = row.items ? row.items : [row],         // 'row' can be a single integer, or a collection of them
            sel = data.field("#selection"),                 // Selection field
            method = "sel";                                 // how to select the data (add, subtract, toggle, select)

        if (d3.event.altKey) method = d3.event.altKey ? "tog" : "sub";
        else if (d3.event.shiftKey) method = "add";

        // For simple selection (no modifiers) everything is initially cleared
        if (method == "sel") for (i = 0; i < sel.rowCount(); i++) sel.setValue('\u2717', i);

        for (i in rows) {
            // rows are 1-based, so need to subtract off one when setting selection using them
            j = rows[i] - 1;
            if (method == "sel" || method == "add") sel.setValue('\u2713', j);
            else if (method == "sub") sel.setValue('\u2717', j);
            else sel.setValue(sel.value(j) == '\u2717' ? '\u2713' : '\u2717', j);
        }
    }


    // Cloud layout -- pass in the dataset, the extent as [width, height]
    function cloud(data, ext) {
        // Delta is the distance between locations as we step along the spiral to place items
        // It is also the distance between curves of the spiral.
        // dx and dy are delta, but spread out to fit the space better for non-square results
        // When we start searching out from the center in the spiral, we look for anything larger than us
        // and start outside that. 'precision' reduces the concept of 'larger' so we search less space
        var delta = Math.max(1, Math.pow(data.rowCount() / 300, 2));
        var precision = Math.pow(0.9, data.rowCount() / 100);
        var dx = delta * ext[0] / Math.max(ext[0], ext[1]),
            dy = delta * ext[1] / Math.max(ext[0], ext[1]);
        var placed = [];           // Placed items

        function intersects(a, b) {
            // Height first as that is less likely to overlap for long text
            return a.y + a.height / 2 >= b.y - b.height / 2
                && b.y + b.height / 2 >= a.y - a.height / 2
                && a.x + a.width / 2 >= b.x - b.width / 2
                && b.x + b.width / 2 >= a.x - a.width / 2;
        }

        function place(svg, index) {
            if (placed.length > index) return placed[index];                // Placed already, just return
            var r = svg.getBBox();
            var item = {width: r.width + 4, height: r.height};                // Our trial item (with slight x padding)
            var rotated = index % 3 == 1;
            if (rotated) item = {height: r.width + 4, width: r.height};
            var i, hit = true, theta = 0;                                      // Start at center and ensure we loop

            item.title = svg.textContent;
            // Find any items at least this large and where we put them (only consider items with same orientation)
            for (i = placed.length - 1; i >= 0; i--) {
                if (item.width >= precision * placed[i].width && item.height > precision * placed[i].height) {
                    theta = Math.max(theta, placed[i].theta);
                }
            }


            while (hit) {
                // Set trial center location
                item.x = Math.cos(theta) * theta * dx;
                item.y = Math.sin(theta) * theta * dy;
                item.theta = theta;
                hit = false;
                for (i = placed.length - 1; i >= 0; i--)
                    if (intersects(item, placed[i])) {
                        hit = true;
                        break;
                    }

                // Outward on the spiral -- this is approximately the same as the arc sine and faster
                theta += delta / Math.max(delta, Math.sqrt(item.x * item.x + item.y * item.y));
            }
            placed.push(item);       // Keep track of the placed items

            if (index == data.rowCount() - 1) transformToFill(svg);


            var s = "translate(" + item.x + "," + item.y + ")";
            if (rotated) s += "rotate(90, 0, 0) ";
            return s;
        }

        function transformToFill(svg) {
            // Add transform to the item's parent to make it fit
            var sx = placed.reduce(function (v, item) {
                return Math.max(v, Math.abs(item.x) + item.width / 2)
            }, 0);
            var sy = placed.reduce(function (v, item) {
                return Math.max(v, Math.abs(item.y) + item.height / 2)
            }, 0);
            var s = Math.min(ext[0] / sx, ext[1] / sy) / 2;
            svg.parentNode.setAttribute('transform', 'scale(' + s + ')');
        }

        // Return the functions that make X and Y locations
        return {
            transform: function (d, i) {
                return place(this, i)
            }
        }
    }


    function makeTooltip(d3Target, labeling) {
        var svg = d3Target.node().ownerSVGElement;                          // Owning SVG
        var pt = svg.createSVGPoint();                                      // For matrix calculations

        d3Target.on('mouseover', function (d) {
            var content = labeling.content(d);                              // To set html content
            if (!content) return;                                           // No tooltips if no data
            var p = getScreenTipPosition(this);                             // get absolute location of the target
            if (!tooltip) {
                tooltip = document.createElement('div');                    // The tooltip div
                tooltip.setAttribute('class', 'brunel tooltip');            // Get the correct styles
                document.body.appendChild(tooltip);                         // add to document
            }
            tooltip.innerHTML = content;                                    // set html content
            tooltip.style.visibility = 'visible';                           // make visible
            tooltip.style.top = (p.y - 10 - tooltip.offsetHeight) + 'px';   // locate relative to the div center notch
            tooltip.style.left = (p.x - tooltip.offsetWidth / 2) + 'px';
        });

        d3Target.on('mouseout', function () {
            if (tooltip) tooltip.style.visibility = 'hidden';
        });   // hide it

        function getScreenTipPosition(item) {
            var labelPos = makeLoc(item, labeling);
            pt.x = labelPos.x + (document.documentElement.scrollLeft || document.body.scrollLeft);
            pt.y = labelPos.y + (document.documentElement.scrollTop || document.body.scrollTop);
            return pt.matrixTransform(item.getScreenCTM());
        }

    }


    // If we have a positive timing, return a transition on the element.
    // Otherwise just return the element
    function transition(element, time) {
        return time && time > 0 ? element.transition().duration(time) : element
    }

    // If we have a positive timing, start tweening using the defiend function
    // Otherwise just call the function for each data item
    function transitionTween(element, time, func) {
        if (time && time > 0)
            return element.transition().duration(time).tween('func', func);
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

    // Expose these methods
    return {
        geometry: geometries,
        addTooltip: makeTooltip,
        makePathSplits: split,
        addLegend: colorLegend,
        centerInWedge: centerInArc,
        makeRowsWithKeys: makeRowsWithKeys,
        makeLabeling: makeLabel,
        cloudLayout: cloud,
        select: select,
        shorten: shorten,
        trans: transition,
        tween: transitionTween,
        time: time
    }

})();
