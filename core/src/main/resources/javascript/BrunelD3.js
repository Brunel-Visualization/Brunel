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

    //Ensure topojson is loaded under AMD.  Unclear why this is needed for topojson, but not d3.
    if (typeof topojson === 'undefined' && typeof require === 'function') topojson = require('topojson');

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

        var g = {
            chart_top: y + h * chart_top, chart_bottom: y + h * chart_bottom,
            chart_left: x + w * chart_left, chart_right: x + w * chart_right,
            outer_width: w, outer_height: h,
            inner_top: inner_top, inner_bottom: inner_bottom,
            inner_left: inner_left, inner_right: inner_right,

            // Allow the inner coords to be transposed
            'transpose': function () {
                var t = this['inner_width'];
                this['inner_width'] = this['inner_height'];
                this['inner_height'] = t;
            }
        };
        g.margin_top = g.chart_top + g.inner_top;
        g.margin_left = g.chart_left + g.inner_left;
        g.margin_bottom = h - g.chart_bottom + g.inner_bottom;
        g.margin_right = w - g.chart_right + g.inner_right;
        g.inner_width = g.outer_width - g.margin_left - g.margin_right;
        g.inner_height = g.outer_height - g.margin_top - g.margin_bottom;
        g.inner_radius = Math.min(g.inner_width, g.inner_height) / 2;
        g.default_point_size = Math.max(6, g.inner_radius * 0.035);
        return g;
    }


    // Create a dataset from rows. Each object has three parts - names, types, rows
    // The types are 'string', 'date' or 'numeric'
    function makeDataset(data) {
        var col, field, i, opt, fields = [];
        for (i = 0; i < data.names.length; i++) {
            col = data.rows.map(function (x) {
                return x[i]
            });                               // Extract i'th item
            field = new BrunelData.Field(data.names[i], null, new BrunelData.values_ColumnProvider(col));
            opt = data.options ? data.options[i] : "string";                                // Apply type options
            if (opt == 'numeric') field = BrunelData.Data.toNumeric(field);
            if (opt == 'date') field = BrunelData.Data.toDate(field);
            if (opt == 'list') field = BrunelData.Data.toList(field);
            fields.push(field);
        }
        return BrunelData.Dataset.make(fields, false);
    }

    // Add a color legend
    function colorLegend(target, title, scale, ticks, labels) {
        target.attr('class', 'legend').append('text').attr('x', 0).attr('y', 0)
            .style('text-anchor', 'end').attr('dy', '0.85em').text(title).attr('class', 'title');
        var legend = target.selectAll('legend').data(ticks).enter().append('g').attr('class', 'swatch')
            .attr('transform', function (d, i) {
                return 'translate(-20,' + (20 + i * 20) + ')';
            });
        legend.append('rect').attr('x', 6).attr('width', 14).attr('height', 14).style('fill', scale);
        legend.append('text').attr('y', 7).attr('dy', '.35em').style('text-anchor', 'end').text(function (d, i) {
            return labels ? labels[i] : BrunelData.Data.format(d, true);
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
            return {'path': pathFunction(d), 'key': d[0].key, 'row': d[0].row}
        };

        // Convert array of categories into array of above structures
        return splits.map(f)
    }

    // Create rows and keys for the data
    function makeRowsWithKeys(keyFunction, N) {
        var result = [];
        for (var i = 0; i < N; i++) result.push({'row': i, 'key': keyFunction(i)});
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
        return {x: c[0], y: c[1], box: shrink(b)}
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
            if (lower[i + 1] && lower[i - 1])  t = (2 * t + (upper[i + 1] - lower[i + 1] + upper[i - 1] - lower[i - 1]) / 2) / 3;
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

    function apply(m, x, y) {
        return [m.a * x + m.c * y + m.e, m.b * x + m.d * y + m.f];
    }

    function transformBox(box, matrix) {
        var p = apply(matrix, box.x, box.y),
            w = matrix.a * box.width + matrix.c * box.height,
            h = matrix.b * box.width + matrix.d * box.height;
        return {x: p[0], y: p[1], width: w, height: h};
    }

    function transformLoc(loc, matrix) {
        var c = apply(matrix, loc.x, loc.y),
            b = transformBox(loc.box, matrix);
        return {x: c[0], y: c[1], box: b};
    }

    // Returns an object with 'x', 'y' and 'box' that "surrounds" the text
    function makeLoc(target, labeling, s) {
        var datum = target.__data__,                            // Associated data value
            box, loc, method = labeling.method;
        if (labeling.where) {
            box = target.getBBox();
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
            else
                loc = pathLoc(target);
        } else {
            box = transformBox(target.getBBox(), target.getCTM());

            // Add 3 pixels padding
            var dx = method == 'left' ? -3 : (method == 'right' ? box.width + 3 : box.width / 2);
            var dy = method == 'top' ? -3 : (method == 'bottom' ? box.height + 3 : box.height / 2);
            return {x: box.x + dx, y: box.y + dy, box: box}
        }
        // Modify for the transform
        return transformLoc(loc, target.getCTM());
    }


    function makeTextSpan(loc, parent) {
        var span = document.createElementNS('http://www.w3.org/2000/svg', 'tspan');
        span.setAttribute('class', 'label');
        span.setAttribute('x', loc.x);
        span.setAttribute('y', loc.y);
        parent.appendChild(span);
        return span;
    }

    function addEllipses(span, text, maxWidth) {
        // Binary search to fit text
        var t, min = 0, max = text.length - 3;
        while (max - min > 1) {
            t = Math.floor((max + min) / 2);
            span.textContent = text.substring(0, t) + "\u2026";
            if (span.getComputedTextLength() <= maxWidth)
                min = t;
            else
                max = t;
        }

        // min will work, but we do not want trailing punctuation
        while (min > 0 && " ,.;-".indexOf(text.charAt(min - 1)) > -1) min--;
        return min > 0; // True if we have valid text
    }

    function wrapInBox(textItem, text, loc) {
        while (textItem.firstChild) textItem.removeChild(textItem.firstChild);
        var words = text.split(/\s+/), word, content = [], height, tspan, i;

        for (i = 0; i < words.length; i++) {
            tspan = tspan || makeTextSpan(loc, textItem);
            word = words[i];
            content.push(word);
            tspan.textContent = content.join(" ");
            height = height || textItem.getBBox().height;
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
        if (!len) return "";
        if (len == 1) return text.charAt(0);

        var result = "", i, n, parts = text.split(/[ \t\n]+/);
        if (parts.length == len) {
            // One letter from each word
            for (i = 0; i < parts.length; i++) result += parts[i].charAt(0);
        } else if (parts.length == 1) {
            // Remove vowels first, then cut off if it still is too long
            var t = parts[0];
            n = t.length - 2;
            while (n > 0 && t.length > len) {
                if ("aeiou".indexOf(t.charAt(n)) >= 0) t = t.substring(0, n) + t.substring(n + 1);
                n--;
            }
            return t.length <= len ? t : t.substring(0, len);
        } else if (parts.length == 2) {
            // Abbreviate first word to just a letter
            result = parts[0].charAt(0) + " " + shorten(parts[1], len - 2);
        } else {
            // Divide up space evenly between words
            n = Math.floor((len - (parts.length - 1)) / parts.length);     // Account for spaces between parts
            if (n < 1) return text.substr(0, len);
            for (i = 0; i < parts.length - 1; i++) result = result + parts[i].substr(0, n) + " ";
            result += parts[parts.length - 1].substring(0, len - result.length);
        }
        return result;
    }

    // Calls the function when the target is ready
    function callWhenReady(func, target) {
        if (target.__transition__)
            setTimeout(function () {
                callWhenReady(func, target)
            }, 100);
        else
            func.call();
    }

    // Select the indicated rows of the data.
    // This will wait until any transition is completed, and will then call the desired rebuild function
    // The row selected refers into the rowData dataset, but the selection to be modified is in the data dataset
    function select(row, rowData, data, target, func) {
        var sel = data.field("#selection"),                     // Selection field
            method = d3.event.altKey ? "tog" : "sel";           // how to select the data (add, subtract, toggle, select)
        if (d3.event.shiftKey) method = d3.event.altKey ? "sub" : "add";
        data.modifySelection(method, row, rowData);             // Modify the selection
        callWhenReady(func, target);                            // Request a redraw after current transition done
    }


    // Cloud layout -- pass in the dataset, the extent as [width, height]
    function cloud(data, ext) {
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
                else if ("gjpqy,".indexOf(c) >= 0) type |= 2;
                else if ("acemnorsuvwxz.-".indexOf(c) < 0) return 3;
            }
            return type;
        }

        function prepare(d, index) {
            d3.select(this).style('alignment-baseline', 'auto');
            var r = this.getBBox(), asc = r.height + r.y, desc = asc * 0.9,
                ht = r.height, wd = r.width + 2, oy = 0,
                ascDesc = ascender(this.textContent);

            if (ascDesc == 0) {
                // Neither ascenders nor descenders
                ht -= (asc + desc);
            } else if (ascDesc == 1) {
                // Only ascenders
                ht -= desc;
                oy += desc / 2;
            } else if (ascDesc == 2) {
                // Only descenders
                ht -= asc;
                oy -= asc / 2;
            }

            var item = {width: wd, height: ht, ox: 0, oy: oy};                // Our trial item (with slight x padding)
            var rotated = (index % 5) % 2 == 1;
            if (rotated) item = {height: item.width, width: item.height, oy: 0, ox: oy, _rotated: true};
            item._txt = this;
            items[index] = item;
            totalArea += item.height * item.width;
        }

        function build() {

            var k, item, scaling = Math.sqrt(ext[0] * ext[1] / totalArea / 2);

            // Resize everything
            for (k = 0; k < items.length; k++) {
                item = items[k];
                var sel = d3.select(item._txt);
                var size = sel.style('font-size');
                var num = size.substring(0, size.length - 2);
                var post = size.substring(size.length - 2);
                var value = Math.round(num * scaling) + post;
                sel.style('font-size', value);
                prepare.call(item._txt, null, k);
            }

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
                    item.x = Math.cos(theta) * theta * dx;
                    item.y = Math.sin(theta) * theta * dy;
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

                //// Debugging text box surrounding the text
                //d3.select(item._txt.parentNode).append('rect')
                //    .attr('x', item.x - item.width / 2).attr('y', item.y - item.height / 2).attr('width', item.width).attr('height', item.height)
                //    .style('fill', 'red').style('fill-opacity', '0.2').style('stroke', 'red').style('stroke-width', '0.5px');


                var s = "translate(" + (item.x - item.ox) + "," + (item.y + item.oy) + ")";
                if (item._rotated) s += "rotate(90, 0, 0) ";
                item._txt.setAttribute('transform', s);
                d3.select(item._txt).style('alignment-baseline', 'middle');

            }

            transformToFill(items[0]._txt.parentNode);

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

        var svg = node.ownerSVGElement,                          // Owning SVG
            w = +svg.attributes.width.value, h = +svg.attributes.height.value,  // width and height
            pt = svg.createSVGPoint();                                      // For matrix calculations

        d3Target.on('mouseover', function (d) {
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

            var p = getScreenTipPosition(this, d);                          // get absolute location of the target
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
        });

        d3Target.on('mouseout', function () {
            if (tooltip) tooltip.style.visibility = 'hidden';
        });   // hide it

        function getScreenTipPosition(item, d) {
            var labelPos = makeLoc(item, labeling, '', d), ctm = svg.getScreenCTM();
            pt.x = labelPos.x + ctm.e + (document.documentElement.scrollLeft || document.body.scrollLeft);
            pt.y = labelPos.y + ctm.f + (document.documentElement.scrollTop || document.body.scrollTop);
            return pt;
        }

    }


    // If we have a positive timing, return a transition on the element.
    // Otherwise just return the element
    function transition(element, time) {
        if (time > 0) {
            return element.transition().duration(time);
        } else {
            return element
        }
    }


    // for each position, gives the text-anchor and y offset
    var LABEL_DEF = {
        'center': ['middle', '0.3em'],
        'left': ['end', '0.3em'],
        'inner-left': ['start', '0.85em'],
        'right': ['start', '0.3em'],
        'top': ['middle', '-0.3em'],
        'bottom': ['middle', '0.7em']
    };

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
        for (i = xmin; i <= xmax; i++) for (j = ymin; j <= ymax; j++)
            if (hits[i * 10000 + j]) return true;

        // No! so we must update those locations before returning the fact it misses
        for (i = xmin; i <= xmax; i++) for (j = ymin; j <= ymax; j++)
            hits[i * 10000 + j] = true;

        return false;
    }


    // Add a label for the selection 'item' to the group 'labelGroup', using the information in 'labeling'
    // 'hits' keeps track of text hit boxes to prevent heavy overlapping
    function labelItem(item, labelGroup, labeling, hits) {
        var content = labeling.content(item.__data__);
        if (!content) return;                               // If there is no content, we are done

        var loc = makeLoc(item, labeling, content);         // Get center point (x,y) and surrounding box (box)

        // Ensure the label exists and cross-reference both to each other
        var txt = item.__label__;
        if (!txt) {
            txt = labelGroup.append('text');
            item.__label__ = txt;
            txt.__target__ = item;
            txt.__labeling__ = labeling;
        }

        var textNode = txt.node();

        if (labeling.cssClass) txt.classed(labeling.cssClass(item.__data__), true);
        else txt.classed('label', true);

        var attrs = LABEL_DEF[labeling.method] || LABEL_DEF['center'];          // Default to center
        txt.style('text-anchor', attrs[0]).attr('dy', attrs[1]);

        if (labeling.fit && !labeling.where) {
            // Do not wrap if the text has been explicitly placed
            wrapInBox(textNode, content, loc);
        } else {

            // Place at the required location
            txt.attr('x', loc.x).attr('y', loc.y).text(content);

            var kill, b = textNode.getBBox();
            // If it doesn't fit, kill the text
            if (labeling.fit) {
                // Too tall to fit a single line, or too wide and could not add ellipses
                kill = (b.height > loc.box.height ||
                b.width > loc.box.width && !addEllipses(textNode, content, loc.box.width));
            } else {
                kill = hitsExisting(b, hits);
            }

            if (kill) {
                textNode.parentNode.removeChild(textNode);          // remove from parent
                item.__label__ = null;                              // dissociate from item
            }
        }
    }


    // Apply labeling
    function applyLabeling(element, group, labeling, time) {
        var hits = {D: labeling.granularity};                      // Keep track of hit items; not the pixel granularity
        if (time > 0)
            return element.transition("labels").duration(time).tween('func', function () {
                var item = this;
                return function () {
                    labelItem(item, group, labeling, hits);
                }
            });
        else
            return element.each(
                function () {
                    labelItem(this, group, labeling, hits)
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
     idFunc:    Function to return the element ID (may be null for background maps)
     element:   The element we are loading data into
     millis:    Time to use for transitioning in the next build
     returns true if we have started loading, but not added the data in yet
     */
    function makeMap(data, locations, idFunc, element, millis) {

        // locations looks like { "http://../world.json":{'FR':23, 'GE':123, ...} , ... }

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
                        for (i in mapping) rev[mapping[i]] = i;     // 'rev' maps from feature ID to data name
                        all.forEach(function (v, i) {
                            d = topojson.feature(x, v);             // convert using topojson call
                            id = rev[d.properties.a];               // The data name for this
                            if (id)
                                element._features[id] = d;          // Remember it by data name
                            else {
                                element._featureExtras.push(d);     // Store as an unused element
                                d.key = '#' + idx + "-" + i;        // With a unique key

                            }
                        });
                        if (!--remaining) element.build(millis);    // When data ready, build again
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

    // A star of radius r with n points
    function star(radius, n) {
        var i, a, r, p = "M";
        for (i = 0; i < 2 * n; i++) {
            a = (i / n - 0.5) * Math.PI;
            if (i > 0) p += "L";
            r = radius * (i % 2 ? 0.4 : 1);
            p += r * Math.cos(a) + ',' + r * Math.sin(a);
        }
        return p + "Z";
    }

    function makeSymbol(type, radius) {
        radius = radius || 4;
        if (type == 'star') return star(radius * 1.5, 5);
        return d3.svg.symbol().type(type).size(radius * radius * 4)();
    }

    // Start a network layout for the node and edge elements
    // The graph should already have been built within the nodeElement
    // density is a 0-1 value stating hwo packed the resulting graph should be
    function makeNetworkLayout(layout, graph, nodes, edges, geom, density) {
        // "D" is the desired distance we would like to have between nodes
        var N = graph.nodes.length, E = graph.links.length,
            pad = geom.default_point_size,
            W = geom.inner_width / 2 - pad, H = geom.inner_height / 2 - pad,
            D = (density || 1) * 1.57 * Math.min(W, H) / Math.sqrt(N),
            R = D * Math.max(1, D - 3) / 5 / Math.max(1, E / N);

        layout.nodes(graph.nodes).links(graph.links)
            .size([W, H])
            .linkDistance(D).charge(-R)
            .start();

        layout.on("tick", function () {
            var minx, maxx, miny, maxy;
            var r, cx, cy, hits = {};
            nodes.selection()
                .each(function (d, i) {
                    if (i) {
                        minx = Math.min(minx, d.x);
                        maxx = Math.max(maxx, d.x);
                        miny = Math.min(miny, d.y);
                        maxy = Math.max(maxy, d.y);
                    } else {
                        minx = maxx = d.x;
                        miny = maxy = d.y;
                    }
                })
                .call(function () {
                    cx = (minx + maxx) / 2;
                    cy = (miny + maxy) / 2;
                    r = 2 * Math.min(W / (maxx - minx), H / (maxy - miny));
                    if (r > 1.05) r = 1.05;
                    if (r < 0.95) r = 0.95;
                })
                .attr('cx', function (d) {
                    return d.x = (d.x - cx) * r + geom.inner_width / 2;
                })
                .attr('cy', function (d) {
                    return d.y = (d.y - cy) * r + geom.inner_height / 2;
                })
                .each(function (d) {
                        var txt = this.__label__;
                        if (!txt) return;
                        if (txt.__off__) {
                            // We have calculated the position, just need to move it
                            txt.attr('x', txt.__off__.dx + d.px);
                            txt.attr('y', txt.__off__.dy + d.py);
                        } else {
                            // First time placement, and then record the offset relative to the node (note no hit collision handling)
                            labelItem(this, null, txt.__labeling__, null);
                            txt.__off__ = {
                                dx: +txt.attr('x') - d.x,
                                dy: +txt.attr('y') - d.y
                            }
                        }
                    }
                );

            edges.selection()
                .attr('x1', function (d) {
                    return d.source.x
                })
                .attr('y1', function (d) {
                    return d.source.y
                })
                .attr('x2', function (d) {
                    return d.target.x
                })
                .attr('y2', function (d) {
                    return d.target.y
                });
        });
        nodes.selection().call(layout.drag)
    }

    // Ensures a D3 item has no cumulative matrix transform
    function undoTransform(labels, element) {
        var node = labels.node(),                                       // SVG node
            m = element.node().getCTM().inverse(),                      // Invert its matrix
            t = node.ownerSVGElement.createSVGTransformFromMatrix(m);   // Convert to a transform
        node.transform.baseVal.initialize(t);                           // Apply to create an overall identity transform
        return labels;
    }

    function facet(chart, parentElement, time) {
        parentElement.selection().each(function (d, i) {
            if (d.row == null) return;
            var value = parentElement.data().field("#row").value(d.row);
            var items = value.items ? value.items : [value];          // If just a single row, make it into an array
            var c = chart(this, items.map(function (v) {
                return v - 1
            }));          // Convert 1-based items to 0-based rows
            c.build(time);
        });
    }

    // v is in the range -1/2 to 1/2
    function interpolate(a, b, v) {
        return (a + b) / 2 + v * (a - b);
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
            var i, y, result = [];   // collect all element's y fields
            chart.elements.forEach(function (e) {
                y = e.fields[type];
                if (y) for (i = 0; i < y.length; i++)
                    if (isNumeric(y[i]))    // If numeric add values (add upper / lower ranges for stacking)
                        result.push(y[i], y[i] + "$lower", y[i] + "$upper");
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
            if (!field) return null;
            field.oProvider = field.provider;                   // swap provider with a constant value
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


    // Expose these methods
    return {
        'makeData': makeDataset,
        'geometry': geometries,
        'addTooltip': makeTooltip,
        'makePathSplits': split,
        'locate': idToPoint,
        'addLegend': colorLegend,
        'centerInWedge': centerInArc,
        'makeRowsWithKeys': makeRowsWithKeys,
        'label': applyLabeling,
        'undoTransform': undoTransform,
        'cloudLayout': cloud,
        'select': select,
        'shorten': shorten,
        'trans': transition,
        'sizedPath': sizedPath,
        'tween': transitionTween,
        'addFeatures': makeMap,
        'symbol': makeSymbol,
        'network': makeNetworkLayout,
        'facet': facet,
        'time': time,
        'interpolate': interpolate,
        'animateBuild': animateBuild
    }

})
();
