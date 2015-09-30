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
var BrunelData = (function () {

////////////////////// GLOBAL AND INITIALIZATION ///////////////////////////////////////////////////////////////////////

// Utilities for internal use
var $ = {
    // Naive and simple extension mechanism
    extend: function (childClass, parentClass) {
        var inner = function () {};
        childClass.prototype = new inner();
        childClass.prototype.constructor = childClass;
        childClass.$superConstructor = parentClass;
        childClass.$super = parentClass.prototype;
        for (var v in parentClass.prototype)
            childClass.prototype[v] = parentClass.prototype[v];
    },

    extendEnum: function (cls) {
        var values = [];
        var idx = 0;
        for (var i in cls) {
            if (!cls.hasOwnProperty(i)) continue;
            var c = cls[i];
            if (c.constructor == cls && i != 'prototype') {
                c._ordinal = idx++;
                c._name = i;
                c.toString = function () {
                    return this._name;
                };
                c.ordinal = function () {
                    return this._ordinal;
                };
                values.push(c);
            }
        }
        cls.values = function () {
            return values;
        }
    },

    iter: function (a) {
        return new $.Iterator(a);
    },

    equals: function (a, b) {
        if (a === b) return true;
        if (a === null || b === null) return false;
        if (typeof (a) != typeof (b)) return false;
        if (typeof (b.equals) == "function") return a.equals(b);
        if (typeof (a.getTime) == 'function') return a.getTime() == b.getTime();
        return a == b;
    },

    hash: function (a) {
        if (!a) return "";
        if (typeof (a.hashCode) == "function") return a.hashCode();
        return String(a);
    },

    toArray: function (a) {
        if (a.constructor === Array) return a;
        if (typeof (a.toArray) == "function") return a.toArray();
        return [a];
    },

    Array: function (length, val) {
        // initialize to val; may be multiple length dimensions
        var arr = new Array(length), i = length;
        if (arguments.length > 2) {
            var dims = Array.prototype.slice.call(arguments, 1);
            while (i--)
                arr[i] = $.Array.apply(this, dims);
        } else {
            for (i = 0; i < length; i++) arr[i] = val;
        }
        return arr;
    },

    compare: function (a, b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        var ca = typeof (a), cb = typeof (b);
        if (ca < cb) return -1;
        if (ca > cb) return 1;
        if (typeof (a.compareTo) == 'function') return a.compareTo(b);
        if (typeof (a.getTime) == 'function') {
            a = a.getTime();
            b = b.getTime();
        }
        return a < b ? -1 : (a > b ? 1 : 0);
    },

    addAll: function (collection, items) {
        for (var i = 0; i < items.length; i++) collection.add(items[i]);
    },

    sort: function (a, b) {
        // If 'b' is an object with a compare method, we need to ensure it is "this" when called
        if (typeof (b) == "function")
            a.sort(b);
        else if (b)
            a.sort( function(i,j) { return b.compare.call(b, i,j) } );
        else
            a.sort($.compare);
    },

    len: function (a) {
        var t = a.length;
        if (typeof (t) == "function") return t();
        return t;
    },

    startsWith: function (a, b) {
        return a.indexOf(b) == 0;
    },

    endsWith: function (a, b) {
        return a.lastIndexOf(b) == a.length - b.length;
    },

    equalsIgnoreCase: function (a, b) {
        if (a === b) return true;
        if (a == null || b == null) return false;
        return a.toLowerCase() == b.toLowerCase();
    },

    isEmpty: function (a) {
        if (a.isEmpty) return a.isEmpty();
        return $.len(a) == 0;
    },

    formatInt: function (d, useGrouping) {
        var s = '' + Math.round(d);
        if (useGrouping) {
            // Add in commas
            var firstDigit = d < 0 ? 1 : 0;
            if (Math.abs(d) >= 10000) for (var p = s.length - 3; p > firstDigit; p -= 3)
                s = s.substring(0, p) + ',' + s.substring(p);
        }
        return s;
    },

    formatScientific: function (d) {
        var s = d.toExponential(3);
        return s.replace(/[0]?0e/, 'e').replace('+', '');
    },

    formatFixed: function (d, useGrouping) {
        var s = d.toPrecision(8);
        var p = s.length;
        // Strip trailing zeros
        while (s.charAt(p - 1) == '0')
            p--;
        s = s.substring(0, p);
        if (useGrouping) {
            // Add in commas
            var firstDigit = d < 0 ? 1 : 0;
            for (p = s.indexOf('.') - 3; p > firstDigit; p -= 3)
                s = s.substring(0, p) + ',' + s.substring(p);
        }
        return s;
    },

    parseDate: function (str) {
        var colon = str.indexOf(':');
        var datePart = str;
        var parts, h = 0, m = 0, s = 0;
        if (colon > 0) {
            var p = colon - 1;
            while (p >= 0 && "0123456789".indexOf(str[p]) != -1)
                p--;
            datePart = p < 0 ? "" : str.substring(0, p).trim();
            var timePart = str.substring(p + 1).trim();
            parts = timePart.split(':');
            h = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10);
            if (parts.length > 2) s = parseInt(parts[2], 10);
        }

        if (datePart.length == 0) {
            // Just the time
            return new Date(Date.UTC(1970, 0, 1, h, m, s));
        }
        if (datePart.indexOf("-") != -1) {
            // ISO format, so can use the UTC call
            parts = datePart.split('-');
            var year = parseInt(parts[0], 10);
            var month = parseInt(parts[1], 10) - 1;
            var day = parseInt(parts[2], 10);
            return new Date(Date.UTC(year, month, day, h, m, s));
        }

        // Check for a simple number -- this is NOT a date
        if (!isNaN(datePart)) return null;

        // Parse in the "general format" and modify by the offset to put the time in UTC
        var d = new Date(datePart);
        if (isNaN(d.getTime())) return null;
        return new Date(d.getTime() + ((h * 60 + m - d.getTimezoneOffset()) * 60 + s) * 1000);
    },

    assertEquals: function (a, b, tol) {
        if (tol != null && !(Math.abs(a - b) < tol) || tol == null && !$.equals(a, b))
            throw "Expected " + a + ", but was " + b;
    },

    assertNotEquals: function (a, b) {
        if (a == b) throw "Expected inequality, but both were " + a
    },

    assertTrue: function (a) {
        if (!a) throw "Was not true"
    },


    Exception: function (message) {
        this.message = message;
    },

    List: function (base) {
        this.items = [];
        if (base) {
            var other = $.toArray(base);
            for (var i = 0; i < other.length; i++)
                this.items[i] = other[i];
        }
    },

    Map: function () {
        this.clear();
    },

    Set: function () {
        this.clear();
    },

    Iterator: function (a) {
        if (a instanceof $.List) {
            this.target = a;
            this.useGet = true;
            this.N = a.size();
        } else {
            this.target = $.toArray(a);
            this.useGet = false;
            this.N = this.target.length;
        }
        this.i = -1;
        this.current = this.next();
    }
};

$.Iterator.prototype = {
    hasNext: function () {
        return this.current !== undefined;
    },
    next: function () {
        if (++this.i >= this.N)
            this.current = undefined;
        else
            this.current = this.useGet ? this.target.get(this.i) : this.target[this.i];
        return this.current;
    }

};

$.Exception.prototype = {
    getMessage: function () {
        return this.message;
    },
    toString: function () {
        return this.message;
    }
};

$.List.prototype = {
    add: function (o) {
        this.items.push(o === undefined ? null : o);      // change 'undefined' to  'null'
    },
    addAll: function (o) {
        this.items = this.items.concat($.toArray(o));
    },
    get: function (i) {
        return this.items[i];
    },
    set: function (i, o) {
        this.items[i] = o;
    },
    sort: function (f) {
        $.sort(this.items, f);
    },
    clear: function () {
        this.items = [];
    },
    size: function () {
        return this.items.length;
    },
    toArray: function () {
        return [].concat(this.items);
    },
    isEmpty: function () {
        return this.items.length == 0
    },
    remove: function (i) {
        this.items.splice(i, 1);
    },
    contains: function (o) {
        for (var i = 0; i < this.items.length; i++)
            if (this.items[i] == o) return true;
        return false;
    },
    indexOf: function (o) {
        for (var i = 0; i < this.items.length; i++)
            if (this.items[i] == o) return i;
        return -1;
    },
    toString: function () {
        return "[" + this.items.join(", ") + "]";
    }
};

$.Map.prototype = {
    size: function () {
        return this.count;
    },
    clear: function () {
        this.items = {};
        this.count = 0;
    },
    isEmpty: function () {
        return this.count == 0
    },
    put: function (key, value) {
        var h = $.hash(key);
        var list = this.items[h];
        if (!list) {
            list = [[key, value]];
            this.items[h] = list;
            this.count++;
            return null;
        }
        var idx = this._findInArray(key, list);
        if (idx < 0) {
            list.push([key, value]);
            this.count++;
            return null;
        }
        var prev = list[idx][1];
        list[idx][1] = value;
        return prev;
    },
    remove: function (key) {
        var list = this.items[$.hash(key)];
        var idx = this._findInArray(key, list);
        if (idx < 0) return null;
        this.count--;
        return list.splice(idx, 1)[0][1];
    },
    putAll: function (map) {
        for (var key in map.items) {
            var list = map.items[key];
            for (var j = 0; j < list.length; j++)
                this.put(list[j][0], list[j][1]);
        }
    },
    get: function (key) {
        var list = this.items[$.hash(key)];
        var idx = this._findInArray(key, list);
        return idx >= 0 ? list[idx][1] : null;
    },
    keySet: function () {
        var keys = new $.Set();
        for (var key in this.items) {
            var list = this.items[key];
            for (var j = 0; j < list.length; j++)
                keys.add(list[j][0]);
        }
        return keys;
    },
    containsKey: function (key) {
        var list = this.items[$.hash(key)];
        return this._findInArray(key, list) >= 0;
    },
    toArray: function () {
        var a = [];
        for (var key in this.items) {
            var list = this.items[key];
            for (var j = 0; j < list.length; j++)
                a.push(list[j][0]);
        }
        return a;
    },
    toString: function () {
        var base = this;
        return '{' + this.toArray().map(function (x) {
                return x + "=" + base.get(x)
            }).join(", ") + '}';
    },
    _findInArray: function (key, arr) {
        if (arr) for (var i = 0; i < arr.length; i++)
            if ($.equals(key, arr[i][0])) return i;
        return -1;
    }
};

// A set is-a map with some renamed methods
$.extend($.Set, $.Map);
$.Set.prototype.contains = $.Set.prototype.containsKey;
$.Set.prototype.add = function (x) {
    return !this.put(x, x);
};
$.Set.prototype.addAll = $.Set.prototype.putAll;
$.Set.prototype.toString = function () {
    return '[' + this.toArray().join(", ") + ']';
};

// Polyfill for String trim
if (!String.prototype.trim) {
    (function() {
        var rtrim = /^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g;
        String.prototype.trim = function() {
            return this.replace(rtrim, '');
        };
    })();
}

// The object we augment and make publicly available
var V = {
    version: "0.0.3"
};
////////////////////// Auto ////////////////////////////////////////////////////////////////////////////////////////////
//
//   Contains a number of static methods for automatic processing.
//   This includes determining suitable ticking structure and domains for axes
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.auto_Auto = function() {};

V.auto_Auto.FRACTION_TO_CONVERT = 0.5;
V.auto_Auto.SKIPS = [7, 47, 283, 2053, 10007, 100000, 10000000];

V.auto_Auto.makeNumericScale = function(f, nice, padFraction, includeZeroTolerance, desiredTickCount, forBinning) {
    var p, scaling;
    V.auto_Auto.setTransform(f);
    if (desiredTickCount < 1)
        desiredTickCount = Math.min(V.auto_Auto.optimalBinCount(f), 20) + 1;
    if (f.isDate())
        return V.auto_NumericScale.makeDateScale(f, nice, padFraction, desiredTickCount);
    p = f.stringProperty("transform");
    if (p == "log")
        return V.auto_NumericScale.makeLogScale(f, nice, padFraction, includeZeroTolerance);
    if (p == "root") {
        if (f.min() > 0) {
            scaling = (f.min() / f.max()) / (Math.sqrt(f.min()) / Math.sqrt(f.max()));
            includeZeroTolerance *= scaling;
            padFraction[0] *= scaling;
        }
    }
    return V.auto_NumericScale.makeLinearScale(f, nice, includeZeroTolerance,
        padFraction, desiredTickCount, forBinning);
};

V.auto_Auto.setTransform = function(f) {
    var skew;
    if (f.property("transform") != null) return;
    skew = f.numericProperty("skew");
    if (skew == null) {
        f.set("transform", "linear");
        return;
    }
    if (skew > 2 && f.min() > 0 && f.max() > 75 * f.min())
        f.set("transform", "log");
    else if (skew > 1.0 && f.min() >= 0)
        f.set("transform", "root");
    else
        f.set("transform", "linear");
};

V.auto_Auto.optimalBinCount = function(f) {
    var h1 = 2 * (f.numericProperty("q3") - f.numericProperty("q1")) / Math.pow(f.valid(), 0.33333);
    var h2 = 3.5 * f.numericProperty("stddev") / Math.pow(f.valid(), 0.33333);
    var h = Math.max(h1, h2);
    if (h == 0)
        return 1;
    else
        return Math.round((f.max() - f.min()) / h + 0.499);
};

V.auto_Auto.convert = function(base) {
    var N, asNumeric, i, j, n, nDate, nNumeric, o, order, t;
    if (base.isSynthetic() || base.isDate()) return base;
    N = base.valid();
    order = $.Array(base.rowCount(), 0);
    for (i = 0; i < order.length; i++)
        order[i] = i;
    for (i = 0; i < order.length; i++){
        j = Math.floor(Math.random() * (order.length - i));
        t = order[i];
        order[i] = order[j];
        order[j] = t;
    }
    if (base.isNumeric()) {
        asNumeric = base;
    } else {
        n = 0;
        i = 0;
        nNumeric = 0;
        while (n < N && n < 50) {
            o = base.value(order[i++]);
            if (o == null) continue;
            n++;
            if (!(o instanceof Date) && V.Data.asNumeric(o) != null) nNumeric++;
        }
        asNumeric = nNumeric > V.auto_Auto.FRACTION_TO_CONVERT * n ? V.Data.toNumeric(base) : null;
    }
    if (asNumeric != null) {
        if (V.auto_Auto.isYearly(asNumeric))
            return V.Data.toDate(asNumeric, "year");
        return asNumeric;
    }
    n = 0;
    i = 0;
    nDate = 0;
    while (n < N && n < 50) {
        o = base.value(order[i++]);
        if (o == null) continue;
        n++;
        if (V.Data.asDate(o) != null) nDate++;
    }
    if (nDate > V.auto_Auto.FRACTION_TO_CONVERT * n)
        return V.Data.toDate(base);
    else
        return base;
};

V.auto_Auto.isYearly = function(asNumeric) {
    var d;
    if (asNumeric.min() < 1800) return false;
    if (asNumeric.max() > 2100) return false;
    d = asNumeric.numericProperty("granularity");
    return d != null && d - Math.floor(d) < 1e-6;
};

////////////////////// NumericScale ////////////////////////////////////////////////////////////////////////////////////
//
//   This class contains methods to define a numeric scale, called via static methods
//   They are each initialized using a field, a boolean 'nice' to indicate whether to expand the range to nice numbers,
//   a fractional amount to pad the field's domain by (e.g. 0.02) and a desired number of ticks,
//   as well as other parameters for specific scales.
//   When called, the effect is to return a NumericScale that has all properties set to useful values
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.auto_NumericScale = function(type, min, max, divs, granular) {
    this.type = type;
    this.min = min;
    this.max = max;
    this.divisions = divs;
    this.granular = granular;
};

V.auto_NumericScale.makeDateScale = function(f, nice, padFraction, desiredTickCount) {
    var d, data, desiredDaysGap, multiple, unit, v, x;
    var a = f.min();
    var b = f.max();
    if (a == b) {
        unit = f.property("dateUnit");
        a = V.Data.asNumeric(V.util_DateUnit.increment(V.Data.asDate(a), unit, -1));
        b = V.Data.asNumeric(V.util_DateUnit.increment(V.Data.asDate(b), unit, 1));
    } else {
        a -= padFraction[0] * (b - a);
        b += padFraction[1] * (b - a);
    }
    desiredDaysGap = (b - a) / (desiredTickCount - 1);
    unit = V.stats_DateStats.getUnit(desiredDaysGap * 4);
    multiple = V.auto_NumericScale.bestDateMultiple(unit, desiredDaysGap);
    x = V.util_DateUnit.floor(V.Data.asDate(a), unit, multiple);
    if (nice) a = V.Data.asNumeric(x);
    d = new $.List();
    while (true) {
        v = V.Data.asNumeric(x);
        if (v >= b) {
            if (nice || v == b) {
                b = v;
                d.add(v);
            }
            break;
        }
        if (v >= a) d.add(v);
        x = V.util_DateUnit.increment(x, unit, multiple);
    }
    if (nice) b = V.Data.asNumeric(x);
    data = d.toArray();
    return new V.auto_NumericScale("date", a, b, data, false);
};

V.auto_NumericScale.bestDateMultiple = function(unit, desiredDaysGap) {
    var i;
    var target = desiredDaysGap / unit.approxDaysPerUnit;
    var multiple = 1;
    for (i = 2; i <= unit.base / 2; i++){
        if (unit.base % i != 0) continue;
        if (i == 4) continue;
        if (i == 6 && unit.base == 60) continue;
        if (Math.abs(target - i) <= Math.abs(target - multiple)) multiple = i;
    }
    return multiple;
};

V.auto_NumericScale.makeLinearScale = function(f, nice,
    includeZeroTolerance, padFraction, desiredTickCount, forBinning) {
    var _i, a, a0, b, b0, bestDiff, choices, d, dCount, data, delta, deltaLog10, desiredDivCount, diff, granularDivs,
        granularity, high, low, padA, padB, rawDelta, transform, x;
    if (f.valid() == 0)
        return new V.auto_NumericScale("linear", 0, 1, [0.0, 1.0], false);
    a0 = f.min();
    b0 = f.max();
    padA = padFraction[0] * (b0 - a0);
    padB = padFraction[1] * (b0 - a0);
    a = a0 - padA;
    b = b0 + padB;
    if (a > 0 && a / b <= includeZeroTolerance) a = 0;
    if (b < 0 && b / a <= includeZeroTolerance) b = 0;
    if (a == 0) {
        if (b0 <= 1 + 1e-4 && b > 1) b = 1;
        if (b0 < 100 + 1e-3 && b > 100) b = 100;
    }
    if (a + 1e-6 > b) {
        b = Math.max(0, 2 * a);
        a = Math.min(0, 2 * a);
    }
    desiredDivCount = Math.max(desiredTickCount - 1, 1);
    transform = f.stringProperty("transform");
    granularity = f.numericProperty("granularity");
    granularDivs = (b - a) / granularity;
    if ((forBinning || f.preferCategorical()) && granularDivs >
        desiredDivCount / 2 && granularDivs < desiredDivCount * 2) {
        data = V.auto_NumericScale.makeGranularDivisions(a, b, granularity, nice);
        return new V.auto_NumericScale(transform, a, b, data, true);
    }
    rawDelta = (b - a) / desiredDivCount;
    deltaLog10 = Math.floor(Math.log(rawDelta) / Math.log(10));
    delta = Math.pow(10, deltaLog10);
    bestDiff = 1e9;
    choices = [delta, delta * 10, delta / 10, delta * 5, delta / 2, delta * 2, delta / 5];
    for(_i=$.iter(choices), d=_i.current; _i.hasNext(); d=_i.next()) {
        low = d * Math.ceil(a / d);
        high = d * Math.floor(b / d);
        dCount = Math.round((high - low) / d) + 1;
        if (nice && a < low) dCount++;
        if (nice && b > high) dCount++;
        diff = Math.abs(dCount - desiredTickCount);
        if (dCount > desiredTickCount) diff -= 0.001;
        if (diff < bestDiff) {
            bestDiff = diff;
            delta = d;
        }
    }
    x = delta * Math.floor(a / delta);
    if (nice) {
        a = x;
        b = delta * Math.ceil(b / delta);
    }
    if (x < a - 1e-6) x += delta;
    d = new $.List();
    while (x < b + 1e-6) {
        d.add(x);
        x += delta;
    }
    data = d.toArray();
    return new V.auto_NumericScale(transform, a, b, data, false);
};

V.auto_NumericScale.makeGranularDivisions = function(min, max, granularity, nice) {
    var at;
    var div = new $.List();
    if (!nice) {
        min += granularity;
        max -= granularity;
    }
    at = min - granularity / 2;
    while (at < max + granularity) {
        div.add(at);
        at += granularity;
    }
    return div.toArray();
};

V.auto_NumericScale.makeLogScale = function(f, nice, padFraction, includeZeroTolerance) {
    var d, data, i;
    var a = Math.log(f.min()) / Math.log(10);
    var b = Math.log(f.max()) / Math.log(10);
    a -= padFraction[0] * (b - a);
    b += padFraction[1] * (b - a);
    if (a > 0 && a / b <= includeZeroTolerance) a = 0;
    if (nice) {
        a = Math.floor(a);
        b = Math.ceil(b);
    }
    d = new $.List();
    for (i = Math.ceil(a); i <= b + 1e-6; i++)
        d.add(Math.pow(10, i));
    data = d.toArray();
    return new V.auto_NumericScale("log", Math.pow(10, a), Math.pow(10, b), data, false);
};

////////////////////// Data ////////////////////////////////////////////////////////////////////////////////////////////

V.Data = function() {};

V.Data.indexOf = function(v, d) {
    var mid;
    var low = -1;
    var high = d.length;
    while (high - low > 1) {
        mid = Math.floor((high + low) / 2.0);
        if (d[mid] <= v)
            low = mid;
        else
            high = mid;
    }
    return low;
};

V.Data.join = function(items, inter) {
    var _i, first, o, s;
    if (inter == null) inter = ", ";
    s = "";
    first = true;
    for(_i=$.iter(items), o=_i.current; _i.hasNext(); o=_i.next()) {
        if (!first) s += inter;
        s += V.Data.format(o, false);
        first = false;
    }
    return s;
};

V.Data.format = function(o, useGrouping) {
    if (o == null) return '?';
    if (typeof(o) == 'number') return V.Data.formatNumeric(o, useGrouping);
    return '' + o;
};

V.Data.formatNumeric = function(d, useGrouping) {
    if (d == 0) return '0';
    if (Math.abs(d) <= 1e-6 || Math.abs(d) >= 1e8) return $.formatScientific(d);
    if (Math.abs((d - Math.round(d)) / d) < 1e-9) return $.formatInt(Math.round(d), useGrouping);
    return $.formatFixed(d, useGrouping);
};

V.Data.makeConstantField = function(name, label, o, len) {
    var field = new V.Field(name, label, new V.values_ConstantProvider(o, len));
    if (V.Data.asNumeric(o) != null) field.set("numeric", true);
    return field;
};

V.Data.asNumeric = function(c) {
    if (c == null) return null;
    if (c && c.asNumeric) return c.asNumeric();
    if (c && c.getTime) return c.getTime() / 86400000;
    var v = Number(c);
    if (!isNaN(v)) return v;
    return null;
};

V.Data.makeIndexingField = function(name, label, len) {
    var field = new V.Field(name, label, new V.values_RowProvider(len));
    field.set("numeric", true);
    return field;
};

V.Data.sort = function(data) {
    $.sort(data, V.Data.compare)
};

V.Data.compare = function(a, b) {
    return $.compare(a,b);
};

V.Data.toDate = function(f, method) {
    var changed, data, i, o, result, v;
    if (f.isDate()) return f;
    data = $.Array(f.rowCount(), null);
    changed = false;
    for (i = 0; i < data.length; i++){
        o = f.value(i);
        if ("year" == method) {
            v = V.Data.asNumeric(o);
            if (v != null)
                data[i] = V.Data.asDate(V.Data.format(v, false) + "-01-01");
        } else if ("excel" == method) {
            v = V.Data.asNumeric(o);
            if (v != null) data[i] = V.Data.asDate(v - 24107);
        } else
            data[i] = V.Data.asDate(o);
        if (!changed)
            changed = V.Data.compare(o, data[i]) != 0;
    }
    result = V.Data.makeColumnField(f.name, f.label, data);
    result.set("date", true);
    result.set("numeric", true);
    return result;
};

V.Data.asDate = function(c) {
    if (c==null) return null;
    if (c.getTime) return c;
    if (typeof c == 'string') {d = $.parseDate(c); return d == null || isNaN(d.getTime()) ? null : d };
    if (!isNaN(c)) return new Date(c*86400000);
    return null;
};

V.Data.makeColumnField = function(name, label, data) {
    return new V.Field(name, label, new V.values_ColumnProvider(data));
};

V.Data.makeIndexedColumnField = function(name, label, items, indices) {
    return new V.Field(name, label, new V.values_ReorderedProvider(new V.values_ColumnProvider(items), indices));
};

V.Data.toNumeric = function(f) {
    var changed, data, i, o, result;
    if (f.isNumeric()) return f;
    changed = false;
    data = $.Array(f.rowCount(), 0);
    for (i = 0; i < data.length; i++){
        o = f.value(i);
        data[i] = V.Data.asNumeric(o);
        if (!changed)
            changed = V.Data.compare(o, data[i]) != 0;
    }
    result = changed ? V.Data.makeColumnField(f.name, f.label, data) : f;
    result.set("numeric", true);
    return result;
};

V.Data.permute = function(field, order, onlyOrderChanged) {
    var f;
    if (onlyOrderChanged)
        return new V.Field(field.name, field.label, new V.values_ReorderedProvider(field.provider, order), field);
    f = new V.Field(field.name, field.label, new V.values_ReorderedProvider(field.provider, order));
    V.Data.copyBaseProperties(f, field);
    return f;
};

V.Data.copyBaseProperties = function(target, source) {
    target.set("numeric", source.property("numeric"));
    target.set("binned", source.property("binned"));
    target.set("summary", source.property("summary"));
    if (source.isDate()) {
        target.set("date", true);
        target.set("dateUnit", source.property("dateUnit"));
        target.set("dateFormat", source.property("dateFormat"));
    }
};

V.Data.isQuoted = function(txt) {
    var c;
    if (txt == null || $.len(txt) < 2) return false;
    c = txt.charAt(0);
    return c == '"' || c == '\'' && txt.charAt($.len(txt) - 1) == c;
};

V.Data.deQuote = function(s) {
    var c, i;
    var text = "";
    var n = $.len(s) - 1;
    for (i = 1; i < n; i++){
        c = s.charAt(i);
        if (c == '\\') {
            c = s.charAt(++i);
            if (c == 'n')
                text += "\n";
            else if (c == 't')
                text += "\t";
            else
                text += c;
        } else {
            text += c;
        }
    }
    return text;
};

V.Data.quote = function(s) {
    var c, i, n, text;
    var quoteChar = '\'';
    if (s.indexOf(quoteChar) >= 0) quoteChar = '"';
    text = "";
    n = $.len(s);
    for (i = 0; i < n; i++){
        c = s.charAt(i);
        if (c == '\n')
            text += "\\n";
        else if (c == '\t')
            text += "\\t";
        else if (c == '\\')
            text += "\\\\";
        else if (c == quoteChar)
            text += "\\" + c;
        else
            text += c;
    }
    return quoteChar + text + quoteChar;
};

V.Data.order = function(c, ascending) {
    var v = [];
    for (var i=0; i<c.length; i++) v.push(i);
    v.sort(function(s,t) { var r= $.compare(c[s], c[t]); return ascending ? r : -r});
    return v;
};

V.Data.toPrimitive = function(items) {
    return items;
};

////////////////////// Informative /////////////////////////////////////////////////////////////////////////////////////

V.util_Informative = function() {
    this.info = new $.Map();
};

V.util_Informative.prototype.copyPropertiesFrom = function(other) {
    this.info.putAll(other.info);
};

V.util_Informative.prototype.numericProperty = function(key) {
    return V.Data.asNumeric(this.property(key));
};

V.util_Informative.prototype.property = function(key) {
    return this.info.get(key);
};

V.util_Informative.prototype.stringProperty = function(key) {
    var v = this.property(key);
    return v == null ? null : v.toString();
};

V.util_Informative.prototype.set = function(key, value) {
    if (value == null)
        this.info.remove(key);
    else
        this.info.put(key, value);
};

////////////////////// Dataset /////////////////////////////////////////////////////////////////////////////////////////


V.Dataset = function(fields) {
    var _i, f;
    V.Dataset.$superConstructor.call(this);
    this.fields = null;
    this.fieldByName = null;this.fields = V.Dataset.ensureUniqueNames(fields);
    this.fieldByName = new $.Map();
    for(_i=$.iter(fields), f=_i.current; _i.hasNext(); f=_i.next())
        this.fieldByName.put(f.name.toLowerCase(), f);
    for(_i=$.iter(fields), f=_i.current; _i.hasNext(); f=_i.next())
        this.fieldByName.put(f.name, f);
};

$.extend(V.Dataset, V.util_Informative);

V.Dataset.makeFromRows = function(rows) {
    var column, i, j;
    var fields = $.Array(rows[0].length, null);
    for (j = 0; j < fields.length; j++){
        column = $.Array(rows.length - 1, null);
        for (i = 1; i < rows.length; i++)
            column[i - 1] = rows[i][j];
        fields[j] = new V.Field(rows[0][j].toString(), null, new V.values_ColumnProvider(column));
    }
    return V.Dataset.make(fields);
};

V.Dataset.make = function(fields, autoConvert) {
    var augmented, i, len, selection;
    fields = V.Dataset.ensureUniqueNames(fields);
    augmented = $.Array(fields.length + 3, null);
    for (i = 0; i < fields.length; i++)
        augmented[i] = false == autoConvert ? fields[i] : V.auto_Auto.convert(fields[i]);
    len = fields[0].rowCount();
    augmented[fields.length] = V.Data.makeConstantField("#count", "Count", 1.0, len);
    augmented[fields.length + 1] = V.Data.makeIndexingField("#row", "Row", len);
    selection = V.Data.makeConstantField("#selection", "Selection", "\u2717", len);
    augmented[fields.length + 2] = selection;
    return new V.Dataset(augmented);
};

V.Dataset.ensureUniqueNames = function(fields) {
    var i, k, name, result;
    var cannotUse = new $.Set();
    cannotUse.add("");
    result = $.Array(fields.length, null);
    for (i = 0; i < fields.length; i++){
        name = fields[i].name;
        if (name == null) name = "";
        if (!cannotUse.contains(name))
            result[i] = fields[i];
        else
            for (k = 1; k < fields.length + 1; k++)
                if (!cannotUse.contains(name + "_" + k)) {
                    result[i] = fields[i].rename(name + "_" + k, name);
                    break;
                }
        cannotUse.add(result[i].name);
    }
    return result;
};

V.Dataset.prototype.bin = function(command) {
    return V.modify_Transform.transform(this, command);
};

V.Dataset.prototype.removeSpecialFields = function() {
    var _i, f, fields1;
    var removed = new $.List();
    for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next())
        if (!$.startsWith(f.name, "#")) removed.add(f);
    fields1 = removed.toArray();
    return this.replaceFields(fields1);
};

V.Dataset.prototype.addConstants = function(command) {
    return V.modify_AddConstantFields.transform(this, command);
};

V.Dataset.prototype.expectedSize = function() {
    var _i, f;
    var total = this.fields.length * 56 + 56;
    for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next())
        total += f.expectedSize();
    return total;
};

V.Dataset.prototype.field = function(name, lax) {
    var field = this.fieldByName.get(name);
    return (field != null || !lax) ? field : this.fieldByName.get(name.toLowerCase());
};

V.Dataset.prototype.filter = function(command) {
    return V.modify_Filter.transform(this, command);
};

V.Dataset.prototype.replaceFields = function(fields) {
    var result = new V.Dataset(fields);
    result.copyPropertiesFrom(this);
    return result;
};

V.Dataset.prototype.rowCount = function() {
    return this.fields.length == 0 ? 0 : this.fields[0].rowCount();
};

V.Dataset.prototype.name = function() {
    return this.stringProperty("name");
};

V.Dataset.prototype.reduce = function(command) {
    var _i, f, ff;
    var names = new $.Set();
    $.addAll(names, V.modify_DataOperation.parts(command));
    ff = new $.List();
    for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next()) {
        if ($.startsWith(f.name, "#") || names.contains(f.name)) ff.add(f);
    }
    return this.replaceFields(ff.toArray());
};

V.Dataset.prototype.series = function(command) {
    return V.modify_ConvertSeries.transform(this, command);
};

V.Dataset.prototype.sort = function(command) {
    return V.modify_Sort.transform(this, command);
};

V.Dataset.prototype.stack = function(command) {
    return V.modify_Stack.transform(this, command);
};

V.Dataset.prototype.summarize = function(command) {
    var dataset = V.modify_Summarize.transform(this, command);
    dataset.set("reduced", true);
    return dataset;
};

////////////////////// Chord ///////////////////////////////////////////////////////////////////////////////////////////
//
//   A chord diagram shows sized links between two categorical fields.
//   This class takes the data in standard format and converts it into a form that D3 can use
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Chord = function(data, fieldA, fieldB, fieldSize) {
    var N, _i, i, i1, i2, o, size;
    var a = data.field(fieldA);
    var b = data.field(fieldB);
    var s = data.field(fieldSize);
    var indices = new $.Map();
    for(_i=$.iter(a.categories()), o=_i.current; _i.hasNext(); o=_i.next())
        if (indices.get(o) == null)
            indices.put(o, indices.size());
    for(_i=$.iter(b.categories()), o=_i.current; _i.hasNext(); o=_i.next())
        if (indices.get(o) == null)
            indices.put(o, indices.size());
    N = indices.size();
    this.names = $.Array(N, null);
    for(_i=$.iter(indices.keySet()), o=_i.current; _i.hasNext(); o=_i.next())
        this.names[indices.get(o)] = o;
    this.mtx = $.Array(N, N, 0);
    this.idx = $.Array(N, N, 0);
    for (i = 0; i < a.rowCount(); i++){
        size = s == null ? 1 : V.Data.asNumeric(s.value(i));
        if (size > 0) {
            i1 = indices.get(a.value(i));
            i2 = indices.get(b.value(i));
            this.mtx[i1][i2] += size;
            this.mtx[i2][i1] += size;
            this.idx[i1][i2] = i;
            this.idx[i2][i1] = i;
        }
    }
};

V.diagram_Chord.make = function(data, fieldA, fieldB, fieldSize) {
    return new V.diagram_Chord(data, fieldA, fieldB, fieldSize);
};

V.diagram_Chord.prototype.group = function(i) {
    return this.names[i];
};

V.diagram_Chord.prototype.index = function(from, to) {
    return this.idx[from][to];
};

V.diagram_Chord.prototype.matrix = function() {
    return this.mtx;
};

////////////////////// Hierarchical ////////////////////////////////////////////////////////////////////////////////////
//
//   An hierarchical diagram shows a tree-like display of nodes (and possibly links)
//   This class takes the data in standard format and converts it into a form that D3 can use
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Hierarchical = function(data, sizeFieldName, fieldNames) {
    var size = sizeFieldName == null ? null : data.field(sizeFieldName);
    var fields = this.toFields(data, fieldNames);
    this.root = this.makeInternalNode("");
    this.makeNodesUsingCollections(data, size, fields);
    this.replaceCollections(data.field("#row"), this.root);
};

V.diagram_Hierarchical.makeByNestingFields = function(data, sizeField) {
    var fields = Array.prototype.slice.call(arguments, 2);
    return new V.diagram_Hierarchical(data, sizeField, fields);
};

V.diagram_Hierarchical.compare = function(a, b) {
    return V.Data.compare(a.key, b.key);
};

V.diagram_Hierarchical.prototype.makeInternalNode = function(label) {
    var node = new V.diagram_Node(null, 0, label, new $.List());
    node.temp = new $.Map();
    return node;
};

V.diagram_Hierarchical.prototype.makeNodesUsingCollections = function(data, size, fields) {
    var _i, children, current, d, field, map, row, v;
    for (row = 0; row < data.rowCount(); row++){
        d = size == null ? 1 : V.Data.asNumeric(size.value(row));
        if (!(d > 0)) continue;
        current = this.root;
        for(_i=$.iter(fields), field=_i.current; _i.hasNext(); field=_i.next()) {
            map = current.temp;
            children = current.children;
            v = field.value(row);
            current = map.get(v);
            if (current == null) {
                current = this.makeInternalNode(field.valueFormatted(row));
                children.add(current);
                map.put(v, current);
            }
        }
        current.children.add(new V.diagram_Node(row, d, null, null));
    }
};

V.diagram_Hierarchical.prototype.replaceCollections = function(dataRowField, current, parentKey) {
    var _i, child;
    var array = current.children;
    if (array == null) {
        current.key = dataRowField.value(current.row);
    } else {
        current.children = array.toArray();
        current.temp = null;
        current.key = parentKey == null ? current.innerNodeName : parentKey + "-" + current.innerNodeName;
        for(_i=$.iter(array), child=_i.current; _i.hasNext(); child=_i.next())
            this.replaceCollections(dataRowField, child, current.key);
    }
};

V.diagram_Hierarchical.prototype.toFields = function(data, fieldNames) {
    var i;
    var fields = $.Array(fieldNames.length, null);
    for (i = 0; i < fields.length; i++)
        fields[i] = data.field(fieldNames[i]);
    return fields;
};

////////////////////// Node ////////////////////////////////////////////////////////////////////////////////////////////
//
//   A hierarchical node with values named such that they can easily be used by D3
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Node = function(row, value, innerNodeName, children) {
    this.children = null;
    this.temp = null;
    this.key = null;this.row = row;
    this.value = value;
    this.innerNodeName = innerNodeName;
    this.children = children;
};

////////////////////// Field ///////////////////////////////////////////////////////////////////////////////////////////


V.Field = function(name, label, provider, base) {
    V.Field.$superConstructor.call(this);
    this.provider = null;
    this.calculatedNominal = null;
    this.calculatedNumeric = null;
    this.calculatedDate = null;
    this.categoryOrder = null;this.name = name;
    this.label = label == null ? name : label;
    this.provider = provider;
    if (base != null) {
        if (provider == null) {
            base.makeNominalStats();
            base.makeNumericStats();
            base.makeDateStats();
        }
        this.copyPropertiesFrom(base);
    }
};

V.Field.VAL_SELECTED = "\u2713";
V.Field.VAL_UNSELECTED = "\u2717";

$.extend(V.Field, V.util_Informative);

V.Field.prototype.setValue = function(o, index) {
    this.provider = this.provider.setValue(o, index);
};

V.Field.prototype.compareRows = function(a, b) {
    var cats, i;
    if (this.categoryOrder == null) {
        this.categoryOrder = new $.Map();
        if (this.preferCategorical()) {
            cats = this.categories();
            for (i = 0; i < cats.length; i++)
                this.categoryOrder.put(cats[i], i);
        }
    }
    return this.provider.compareRows(a, b, this.categoryOrder);
};

V.Field.prototype.expectedSize = function() {
    return ($.len(this.label) + $.len(this.name)) * 2 + 84 + 24 + this.provider.expectedSize();
};

V.Field.prototype.property = function(key) {
    var o = V.Field.$super.property.call(this, key);
    if (o == null) {
        if (!this.calculatedNominal && V.stats_NominalStats.creates(key)) {
            this.makeNominalStats();
            o = V.Field.$super.property.call(this, key);
        }
        if (!this.calculatedNumeric && V.stats_NumericStats.creates(key)) {
            if (!this.calculatedNominal) this.makeNominalStats();
            this.makeNumericStats();
            o = V.Field.$super.property.call(this, key);
        }
        if (!this.calculatedDate && V.stats_DateStats.creates(key)) {
            if (!this.calculatedNominal) this.makeNominalStats();
            if (!this.calculatedNumeric) this.makeNumericStats();
            this.makeDateStats();
            o = V.Field.$super.property.call(this, key);
        }
    }
    return o;
};

V.Field.prototype.setCategories = function(cats) {
    this.makeNominalStats();
    this.set("categories", cats);
};

V.Field.prototype.makeDateStats = function() {
    if (this.isNumeric()) V.stats_DateStats.populate(this);
    this.calculatedDate = true;
};

V.Field.prototype.isNumeric = function() {
    return true == this.property("numeric");
};

V.Field.prototype.isDate = function() {
    return true == this.property("date");
};

V.Field.prototype.isBinned = function() {
    return true == this.property("binned");
};

V.Field.prototype.makeNumericStats = function() {
    if (this.provider != null) V.stats_NumericStats.populate(this);
    this.calculatedNumeric = true;
};

V.Field.prototype.makeNominalStats = function() {
    if (this.provider != null) V.stats_NominalStats.populate(this);
    this.calculatedNominal = true;
};

V.Field.prototype.categories = function() {
    return this.property("categories");
};

V.Field.prototype.compareTo = function(o) {
    var p = V.Data.compare(this.name, o.name);
    if ($.startsWith(this.name, "#"))
        return $.startsWith(o.name, "#") ? p : 1;
    return $.startsWith(o.name, "#") ? -1 : p;
};

V.Field.prototype.dropData = function() {
    return new V.Field(this.name, this.label, null, this);
};

V.Field.prototype.hasProvider = function() {
    return this.provider != null;
};

V.Field.prototype.max = function() {
    return this.property("max");
};

V.Field.prototype.min = function() {
    return this.property("min");
};

V.Field.prototype.isSynthetic = function() {
    return $.startsWith(this.name, "#");
};

V.Field.prototype.preferCategorical = function() {
    return !this.isNumeric() || (true == this.property("binned"));
};

V.Field.prototype.ordered = function() {
    return this.isNumeric() || (this.name == "#selection");
};

V.Field.prototype.rename = function(name, label) {
    var field = new V.Field(name, label, this.provider);
    field.copyPropertiesFrom(this);
    return field;
};

V.Field.prototype.rowCount = function() {
    return this.provider != null ? this.provider.count() : this.numericProperty("n");
};

V.Field.prototype.toString = function() {
    return this.name;
};

V.Field.prototype.uniqueValuesCount = function() {
    return Math.round(this.numericProperty("unique"));
};

V.Field.prototype.valid = function() {
    return this.property("valid");
};

V.Field.prototype.value = function(index) {
    return this.provider.value(index);
};

V.Field.prototype.valueFormatted = function(index) {
    return this.format(this.provider.value(index));
};

V.Field.prototype.format = function(v) {
    var d;
    if (v == null) return "?";
    if (v instanceof V.util_Range) return v.toString();
    if (this.isDate())
        return this.property("dateFormat").format(V.Data.asDate(v));
    if ("percent" == this.property("summary")) {
        d = V.Data.asNumeric(v);
        if (d == null) return null;
        return V.Data.formatNumeric(Math.round(d * 10) / 10.0, false) + "%";
    }
    if (this.isNumeric()) {
        d = V.Data.asNumeric(v);
        return d == null ? "?" : V.Data.formatNumeric(d, true);
    }
    return v.toString();
};

////////////////////// ByteInput ///////////////////////////////////////////////////////////////////////////////////////
//
//   A class for writing bytes to
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.io_ByteInput = function(data) {
    this.p = null;this.data = data;
    this.p = 0;
};

V.io_ByteInput.prototype.readNumber = function() {
    var d3, d4;
    var a = this.readByte() & 0xff;
    if (a <= 252) {
        return a;
    } else if (a == 253) {
        d3 = this.readByte() & 0xff;
        d4 = this.readByte() & 0xff;
        return d3 + d4 * 256;
    } else if (a == 254) {
        return this.readDouble();
    } else if (a == 255) {
        return null;
    } else {
        throw new $.Exception("Serializing " + a);
    }
};

V.io_ByteInput.prototype.readDate = function() {
    return V.Data.asDate(this.readNumber());
};

V.io_ByteInput.prototype.readDouble = function() {
    var s = this.readString();
    return s == "NaN" ? Number.NaN : Number(s);
};

V.io_ByteInput.prototype.readString = function() {
    var i, len, c, d, char2, char3, out=''
    for(;;) {
      c = this.readByte();
      if (c == 3 && out == '') return null;    // 03 at start encodes a null
      if (c == 0) return out;                  // 00 terminates the string
      d = c >> 4;                              // handle the UTF-8 top nibble encoding
      if (d<8) out += String.fromCharCode(c);  // One byte
      else if (d == 12 || d == 13) {           // Two bytes
        out += String.fromCharCode(((c & 0x1F) << 6) | (this.readByte() & 0x3F));
      } else if (d == 14) {                    // Three bytes
        var c2 = this.readByte(), c3 = this.readByte();
        out += String.fromCharCode( ((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F) );
      }
    }
};

V.io_ByteInput.prototype.readByte = function() {
    return this.data[this.p++];
};

////////////////////// ByteOutput //////////////////////////////////////////////////////////////////////////////////////
//
//   A class for writing bytes to
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.io_ByteOutput = function() {
    this.out=[];
};

V.io_ByteOutput.prototype.addByte = function(b) {
    this.out.push(b); return this
};

V.io_ByteOutput.prototype.addNumber = function(value) {
    var d, e;
    if (value == null) {
        this.addByte(255);
        return this;
    }
    d = value;
    e = Math.floor(d);
    if (e == d && e >= 0 && e < 256 * 256) {
        if (e <= 252) return this.addByte(e);
        this.addByte(253);
        this.addByte(e & 0xff);
        this.addByte((e >> 8) & 0xff);
        return this;
    }
    this.addByte(254);
    this.addDouble(value);
    return this;
};

V.io_ByteOutput.prototype.addDouble = function(value) {
    if (isNaN(value))
        this.addString("NaN");
    else
        this.addString(V.Data.formatNumeric(value, false));
};

V.io_ByteOutput.prototype.addDate = function(date) {
    return this.addNumber(V.Data.asNumeric(date));
};

V.io_ByteOutput.prototype.addString = function(s) {
    if (s==null) return this.addByte(3);    // null encoded is '03'
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c < 128)
        this.addByte(c)
      else if (c < 2048)
        this.addByte((c >> 6) | 192).addByte((c & 63) | 128);
      else
        this.addByte((c >> 12) | 224).addByte(((c >> 6) & 63) | 128).addByte((c & 63) | 128);
    }
    return this.addByte(0);
};

V.io_ByteOutput.prototype.asBytes = function() {
    return this.out;
};

////////////////////// CSV /////////////////////////////////////////////////////////////////////////////////////////////

V.io_CSV = function() {};

V.io_CSV.read = function(base) {
    var data = V.io_CSV.parse(base);
    return V.io_CSV.makeFields(data);
};

V.io_CSV.parse = function(data) {
    var c, i, result, row;
    var common = new $.Map();
    var lines = new $.List();
    var line = new $.List();
    var last = ' ';
    var inQuote = false;
    var currentIndex = 0;
    var separator = V.io_CSV.findSeparator(data);
    var building = null;
    var fieldCount = -1;
    while (currentIndex <= $.len(data)) {
        c = currentIndex == $.len(data) ? '\n' : data.charAt(currentIndex);
        if (inQuote) {
            if (c == '\"') {
                if (currentIndex < $.len(data) - 1 && data.charAt(currentIndex + 1) == '\"') {
                    building += '\"';
                    currentIndex++;
                } else
                    inQuote = false;
            } else
                building += c;
        } else if (c == '\n' || c == '\r') {
            if (last != '\r' || c != '\n') {
                if ($.isEmpty(line) && (building == null)) {
                    break;
                }
                line.add(V.io_CSV.saveMemory(building, common));
                lines.add(line);
                if (fieldCount < 0)
                    fieldCount = line.size();
                else if (fieldCount != line.size())
                    throw new $.Exception("Line " + lines.size() + " had " + line.size()
                        + " entries; expected " + fieldCount);
                line = new $.List();
                building = null;
            }
        } else if (c == '\"') {
            inQuote = true;
            if (building == null) building = "";
        } else {
            if (c == separator) {
                line.add(V.io_CSV.saveMemory(building, common));
                building = null;
            } else {
                if (building == null) building = "";
                building += c;
            }
        }
        last = c;
        currentIndex++;
    }
    result = $.Array(lines.size(), null);
    for (i = 0; i < result.length; i++){
        row = lines.get(i);
        result[i] = row.toArray();
    }
    return result;
};

V.io_CSV.findSeparator = function(data) {
    var count, i, j, line, s, trial, trialScore;
    var potential = [',', '\t', '|', ';'];
    var best = ',';
    var score = -100000;
    var N = Math.min(5000, $.len(data));
    for (i = 0; i < 4; i++){
        trial = potential[i];
        count = [0, 0];
        line = 0;
        for (j = 0; j < N && line < 2; j++){
            s = data.charAt(j);
            if (s == trial) count[line]++;
            if (s == '\n') line++;
        }
        trialScore = count[0];
        if (count[0] != count[1] || count[0] == 0) trialScore -= 10000;
        if (trialScore > score) {
            best = trial;
            score = trialScore;
        }
    }
    return best;
};

V.io_CSV.saveMemory = function(s, common) {
    var t = common.get(s);
    if (t == null) {
        common.put(s, s);
        return s;
    } else {
        return t;
    }
};

V.io_CSV.makeFields = function(data) {
    var column, i, j, name;
    var fields = $.Array(data[0].length, null);
    for (i = 0; i < fields.length; i++){
        column = $.Array(data.length - 1, null);
        for (j = 0; j < column.length; j++)
            column[j] = data[j + 1][i];
        name = data[0][i] == null ? "" : data[0][i].toString();
        fields[i] = V.Data.makeColumnField(V.io_CSV.identifier(name), V.io_CSV.readable(name), column);
    }
    return fields;
};

V.io_CSV.identifier = function(text) {
    var c, d, i;
    var result = "";
    var last = "X";
    for (i = 0; i < $.len(text); i++){
        c = text.substring(i, i + 1);
        if (V.io_CSV.isDigit(c)) {
            if (i == 0) result = "_";
            d = c;
        } else if ((c == "_") || V.io_CSV.isLower(c) || V.io_CSV.isUpper(c) || V.io_CSV.isDigit(c))
            d = c;
        else {
            d = "_";
        }
        if (d == "_") {
            if (!(last == "_")) result += d;
        } else {
            result += d;
        }
        last = d;
    }
    return $.len(result) == 0 ? "_" : result;
};

V.io_CSV.readable = function(text) {
    var _i, at, built, c, part;
    var parts = text.split(" ");
    var result = new $.List();
    for(_i=$.iter(parts), part=_i.current; _i.hasNext(); part=_i.next()) {
        built = "";
        at = 0;
        while (at < $.len(part)) {
            c = part.substring(at, at + 1);
            if (at == 0 && V.io_CSV.isLower(c)) {
                c = c.toUpperCase();
                built += c;
            } else if (at > 0 && !V.io_CSV.isLower(c) && V.io_CSV.isLower(part.substring(at - 1, at))) {
                result.add(built);
                part = part.substring(at);
                built = "" + c;
                at = 0;
            } else
                built += c;
            at++;
        }
        if ($.len(built) > 0) result.add(built);
    }
    return V.Data.join(result, " ");
};

V.io_CSV.isDigit = function(c) {
    return "0123456789".indexOf(c) >= 0;
};

V.io_CSV.isLower = function(c) {
    return "abcdefghijklmnopqrstuvwxyz".indexOf(c) >= 0;
};

V.io_CSV.isUpper = function(c) {
    return "ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(c) >= 0;
};

////////////////////// Serialize ///////////////////////////////////////////////////////////////////////////////////////
//
//   This class serializes data items
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.io_Serialize = function() {};

V.io_Serialize.DATA_SET = 1;
V.io_Serialize.FIELD = 2;
V.io_Serialize.NUMBER = 3;
V.io_Serialize.STRING = 4;
V.io_Serialize.DATE = 5;
V.io_Serialize.VERSION = 6;
V.io_Serialize.DATASET_VERSION_NUMBER = 1;

V.io_Serialize.serializeDataset = function(data) {
    var _i, f, s;
    data = data.removeSpecialFields();
    s = new V.io_ByteOutput();
    s.addByte(V.io_Serialize.VERSION).addNumber(V.io_Serialize.DATASET_VERSION_NUMBER);
    s.addByte(V.io_Serialize.DATA_SET).addNumber(data.fields.length);
    for(_i=$.iter(data.fields), f=_i.current; _i.hasNext(); f=_i.next())
        V.io_Serialize.addFieldToOutput(f, s);
    return s.asBytes();
};

V.io_Serialize.serializeField = function(field) {
    var s = new V.io_ByteOutput();
    V.io_Serialize.addFieldToOutput(field, s);
    return s.asBytes();
};

V.io_Serialize.addFieldToOutput = function(field, s) {
    var _i, i, items, o, uniques, value;
    var N = field.rowCount();
    s.addByte(V.io_Serialize.FIELD).addString(field.name).addString(field.label);
    items = new $.Map();
    uniques = new $.List();
    for (i = 0; i < N; i++){
        value = field.value(i);
        if (!items.containsKey(value)) {
            items.put(value, items.size());
            uniques.add(value);
        }
    }
    s.addNumber(uniques.size());
    if (field.isDate()) {
        s.addByte(V.io_Serialize.DATE);
        for(_i=$.iter(uniques), o=_i.current; _i.hasNext(); o=_i.next())
            s.addDate(o);
    } else if (field.isNumeric()) {
        s.addByte(V.io_Serialize.NUMBER);
        for(_i=$.iter(uniques), o=_i.current; _i.hasNext(); o=_i.next())
            s.addNumber(o);
    } else {
        s.addByte(V.io_Serialize.STRING);
        for(_i=$.iter(uniques), o=_i.current; _i.hasNext(); o=_i.next())
            s.addString(o);
    }
    s.addNumber(N);
    for (i = 0; i < N; i++)
        s.addNumber(items.get(field.value(i)));
};

V.io_Serialize.deserialize = function(data) {
    var d = new V.io_ByteInput(data);
    return V.io_Serialize.readFromByteInput(d);
};

V.io_Serialize.readFromByteInput = function(d) {
    var field, fields, i, indices, items, label, len, name, uniqueCount, versionNum;
    var b = d.readByte();
    if (b == V.io_Serialize.FIELD) {
        name = d.readString();
        label = d.readString();
        uniqueCount = d.readNumber();
        b = d.readByte();
        items = $.Array(uniqueCount, null);
        for (i = 0; i < uniqueCount; i++){
            if (b == V.io_Serialize.NUMBER)
                items[i] = d.readNumber();
            else if (b == V.io_Serialize.STRING)
                items[i] = d.readString();
            else if (b == V.io_Serialize.DATE)
                items[i] = d.readDate();
            else
                throw new $.Exception("Unknown column type " + b);
        }
        len = d.readNumber();
        indices = $.Array(len, 0);
        for (i = 0; i < len; i++)
            indices[i] = d.readNumber();
        field = V.Data.makeIndexedColumnField(name, label, items, indices);
        if (b == V.io_Serialize.NUMBER || b == V.io_Serialize.DATE) field.set("numeric", true);
        if (b == V.io_Serialize.DATE) field.set("date", true);
        return field;
    } else if (b == V.io_Serialize.DATA_SET) {
        len = d.readNumber();
        fields = $.Array(len, null);
        for (i = 0; i < len; i++)
            fields[i] = V.io_Serialize.readFromByteInput(d);
        return V.Dataset.make(fields, false);
    } else if (b == V.io_Serialize.VERSION) {
        versionNum = d.readNumber();
        if (versionNum != V.io_Serialize.DATASET_VERSION_NUMBER) {
            throw new $.Exception("Serialized version differs from current execution version");
        }
        return V.io_Serialize.readFromByteInput(d);
    } else {
        throw new $.Exception("Unknown class: " + b);
    }
};

////////////////////// DataOperation ///////////////////////////////////////////////////////////////////////////////////
//
//   Common code for Dataset transforms
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_DataOperation = function() {};

V.modify_DataOperation.map = function(command, sep) {
    var _i, c, key, result, s, value;
    var parts = V.modify_DataOperation.parts(command);
    if (parts == null) return null;
    result = new $.Map();
    for(_i=$.iter(parts), c=_i.current; _i.hasNext(); c=_i.next()) {
        s = c.split(sep);
        key = s[0].trim();
        value = s.length > 1 ? s[1].trim() : "";
        result.put(key, value);
    }
    return result;
};

V.modify_DataOperation.parts = function(command) {
    var i;
    var parts = command.split(";");
    for (i = 0; i < parts.length; i++)
        parts[i] = parts[i].trim();
    return parts.length == 1 && $.isEmpty(parts[0]) ? null : parts;
};

V.modify_DataOperation.list = function(items) {
    var i;
    var parts = items.split(",");
    for (i = 0; i < parts.length; i++)
        parts[i] = parts[i].trim();
    return parts.length == 1 && $.isEmpty(parts[0]) ? null : parts;
};

////////////////////// AddConstantFields ///////////////////////////////////////////////////////////////////////////////
//
//   This transform takes data and adds constant fields.
//   The fields are given in the command as semi-colon separated items, with quoted values used as strings,
//   The fields are named the same as their constants, so a constant field with value 4.3 is called '4.3'
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_AddConstantFields = function() {
    V.modify_AddConstantFields.$superConstructor.call(this);
};

$.extend(V.modify_AddConstantFields, V.modify_DataOperation);

V.modify_AddConstantFields.transform = function(base, command) {
    var fields, i, name;
    var additional = V.modify_DataOperation.parts(command);
    if (additional == null) return base;
    fields = $.Array(base.fields.length + additional.length, null);
    for (i = 0; i < additional.length; i++){
        name = additional[i];
        if (V.Data.isQuoted(name))
            fields[i] = V.Data.makeConstantField(name, V.Data.deQuote(name), V.Data.deQuote(name), base.rowCount());
        else
            fields[i] = V.Data.makeConstantField(name, name, V.Data.asNumeric(name), base.rowCount());
    }
    for (i = 0; i < base.fields.length; i++)
        fields[i + additional.length] = base.fields[i];
    return base.replaceFields(fields);
};

////////////////////// ConvertSeries ///////////////////////////////////////////////////////////////////////////////////
//
//   This transform converts a set of "y" values into a single "#values" field and adds another
//   field with the "y" field names that is given the special name "#series".
//   It takes a list of y values, comma separated, and a then a list of other fields that need to be in the result.
//   Any field not in either of those will be dropped.
//   Note that it is possible (and useful) to have a field both in the 'y' and 'other' lists
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_ConvertSeries = function() {
    V.modify_ConvertSeries.$superConstructor.call(this);
};

$.extend(V.modify_ConvertSeries, V.modify_DataOperation);

V.modify_ConvertSeries.transform = function(base, commands) {
    var N, _i, f, fieldName, fields, indexing, otherFields, resultFields, sections, series, values, yFields;
    if ($.isEmpty(commands)) return base;
    sections = V.modify_DataOperation.parts(commands);
    yFields = V.modify_DataOperation.list(sections[0]);
    if (yFields == null || yFields.length < 2) return base;
    otherFields = V.modify_ConvertSeries.addRequired(V.modify_DataOperation.list(sections[1]));
    N = base.rowCount();
    series = V.modify_ConvertSeries.makeSeries(yFields, N);
    values = V.modify_ConvertSeries.makeValues(yFields, base, N);
    indexing = V.modify_ConvertSeries.makeIndexing(yFields.length, N);
    resultFields = new $.List();
    resultFields.add(series);
    resultFields.add(values);
    for(_i=$.iter(otherFields), fieldName=_i.current; _i.hasNext(); fieldName=_i.next()) {
        if ((fieldName == "#series") || (fieldName == "#values")) continue;
        f = base.field(fieldName);
        resultFields.add(V.Data.permute(f, indexing, false));
    }
    fields = resultFields.toArray();
    return base.replaceFields(fields);
};

V.modify_ConvertSeries.addRequired = function(list) {
    var result = new $.List();
    $.addAll(result, list);
    if (!result.contains("#row")) result.add("#row");
    if (!result.contains("#count")) result.add("#count");
    return result.toArray();
};

V.modify_ConvertSeries.makeSeries = function(names, reps) {
    var i, j;
    var temp = V.Data.makeColumnField("#series", "Series", names);
    var blocks = $.Array(names.length * reps, 0);
    for (i = 0; i < names.length; i++)
        for (j = 0; j < reps; j++)
            blocks[i * reps + j] = i;
    return V.Data.permute(temp, blocks, false);
};

V.modify_ConvertSeries.makeValues = function(yNames, base, n) {
    var data, field, i, j;
    var y = $.Array(yNames.length, null);
    for (i = 0; i < y.length; i++)
        y[i] = base.field(yNames[i]);
    data = $.Array(y.length * n, null);
    for (i = 0; i < y.length; i++)
        for (j = 0; j < n; j++)
            data[i * n + j] = y[i].value(j);
    field = V.Data.makeColumnField("#values", V.Data.join(yNames), data);
    V.Data.copyBaseProperties(field, y[0]);
    return field;
};

V.modify_ConvertSeries.makeIndexing = function(m, reps) {
    var i, j;
    var blocks = $.Array(m * reps, 0);
    for (i = 0; i < m; i++)
        for (j = 0; j < reps; j++)
            blocks[i * reps + j] = j;
    return blocks;
};

////////////////////// Filter //////////////////////////////////////////////////////////////////////////////////////////
//
//   This transform takes data and removes rows based on filter commands
//   Commands are one of the following:
//   FIELD is a,b ...                -- one of those values
//   FIELD not a,b, ...              -- not one of those values
//   FIELD in a,b                    -- in that range of values (exactly two)
//   FIELD valid                     -- not null
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_Filter = function() {
    V.modify_Filter.$superConstructor.call(this);
};

$.extend(V.modify_Filter, V.modify_DataOperation);

V.modify_Filter.transform = function(base, command) {
    var N, c, commands, field, i, keep, p, par, params, q, results, t, type;
    if (!base.fields[0].hasProvider()) return base;
    commands = V.modify_DataOperation.parts(command);
    if (commands == null) return base;
    N = commands.length;
    field = $.Array(N, null);
    type = $.Array(N, 0);
    params = $.Array(N, null);
    for (i = 0; i < N; i++){
        c = commands[i].trim();
        p = c.indexOf(" ");
        q = c.indexOf(" ", p + 1);
        if (q < 0) q = $.len(c);
        field[i] = base.field(c.substring(0, p).trim());
        t = V.modify_Filter.getType(c.substring(p, q).trim());
        par = V.modify_Filter.getParams(c.substring(q).trim(), field[i].preferCategorical());
        if (t == 4 || t == -4) {
            par = V.modify_Filter.getRankedObjects(field[i], V.Data.asNumeric(par[0]), V.Data.asNumeric(par[1]));
            t = t < 0 ? -3 : 3;
        }
        type[i] = t;
        params[i] = par;
    }
    keep = V.modify_Filter.makeRowsToKeep(field, type, params);
    if (keep == null) return base;
    results = $.Array(base.fields.length, null);
    for (i = 0; i < results.length; i++)
        results[i] = V.Data.permute(base.fields[i], keep, false);
    return base.replaceFields(results);
};

V.modify_Filter.getRankedObjects = function(field, p1, p2) {
    var N, a, b, d, high, i, low, o;
    var data = new $.List();
    var n = field.rowCount();
    for (i = 0; i < n; i++){
        o = field.value(i);
        if (o != null) data.add(o);
    }
    d = data.toArray();
    V.Data.sort(d);
    N = d.length;
    a = Math.min(Math.max(1, Math.floor(p1)), N);
    b = Math.min(Math.max(1, Math.floor(p2)), N);
    high = d[N - a];
    low = d[N - b];
    return [low, high];
};

V.modify_Filter.getType = function(s) {
    if ($.startsWith(s, "!"))
        return -V.modify_Filter.getType(s.substring(1).trim());
    if (s == "valid") return 1;
    if (s == "is") return 2;
    if (s == "in") return 3;
    if (s == "ranked") return 4;
    throw new $.Exception("Cannot use filter command " + s);
};

V.modify_Filter.getParams = function(s, categorical) {
    var i;
    var parts = s.split(",");
    var result = $.Array(parts.length, null);
    for (i = 0; i < result.length; i++){
        if (categorical)
            result[i] = parts[i].trim();
        else
            result[i] = V.Data.asNumeric(parts[i].trim());
    }
    return result;
};

V.modify_Filter.makeRowsToKeep = function(field, type, params) {
    var bad, i, keep, pars, row, t, v;
    var rows = new $.List();
    var n = field[0].rowCount();
    for (row = 0; row < n; row++){
        bad = false;
        for (i = 0; i < field.length; i++){
            v = field[i].value(row);
            if (v == null) {
                bad = true;
                break;
            }
            t = type[i];
            pars = params[i];
            if (t == 2 || t == -2)
                bad = !V.modify_Filter.matchAny(v, pars);
            else if (t == 3 || t == -3)
                bad = V.Data.compare(v, pars[0]) < 0 || V.Data.compare(v, pars[1]) > 0;
            if (t < 0) bad = !bad;
            if (bad) break;
        }
        if (!bad) rows.add(row);
    }
    if (rows.size() == n) return null;
    keep = $.Array(rows.size(), 0);
    for (i = 0; i < keep.length; i++)
        keep[i] = rows.get(i);
    return keep;
};

V.modify_Filter.matchAny = function(v, params) {
    var _i, p;
    for(_i=$.iter(params), p=_i.current; _i.hasNext(); p=_i.next())
        if (V.Data.compare(v, p) == 0) return true;
    return false;
};

////////////////////// Sort ////////////////////////////////////////////////////////////////////////////////////////////
//
//   This transform sorts a dataset into an order determined bya  set of fields.
//   Each field can be defined as "increasing" or "decreasing" and the order of fields is important!
//   This class may sort categories of data if the data type allows it (i.e. it is categorical, not ordinal)
//   It will always sort the rows of the data (so a PATH element will use that order, for example)
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_Sort = function() {
    V.modify_Sort.$superConstructor.call(this);
};

$.extend(V.modify_Sort, V.modify_DataOperation);

V.modify_Sort.transform = function(base, command) {
    var ascending, dimensions, f, field, fields, i, newOrder, rowOrder;
    var sortFields = V.modify_DataOperation.parts(command);
    if (sortFields == null) return base;
    dimensions = V.modify_Sort.getFields(base, sortFields);
    ascending = V.modify_Sort.getAscending(dimensions, sortFields);
    rowOrder = new V.summary_FieldRowComparison(dimensions, ascending, true).makeSortedOrder(base.rowCount());
    for (i = base.fields.length - 1; i >= 0; i--){
        f = base.fields[i];
        if (f.isBinned() && f.preferCategorical())
            rowOrder = V.modify_Sort.moveCatchAllToEnd(rowOrder, f);
    }
    fields = $.Array(base.fields.length, null);
    for (i = 0; i < fields.length; i++){
        newOrder = null;
        field = base.fields[i];
        if (!field.ordered())
            newOrder = V.modify_Sort.makeOrder(field, dimensions, ascending);
        fields[i] = V.Data.permute(field, rowOrder, true);
        if (newOrder != null)
            fields[i].setCategories(newOrder);
    }
    return base.replaceFields(fields);
};

V.modify_Sort.makeOrder = function(field, dimensions, ascending) {
    var categories, category, dimensionData, i, n, order, r, result, summaries, value;
    var categorySums = new $.Map();
    for (i = 0; i < field.rowCount(); i++){
        category = field.value(i);
        if (category == null) continue;
        value = categorySums.get(category);
        if (value == null) {
            value = new $.List();
            categorySums.put(category, value);
        }
        value.add(i);
    }
    categories = field.categories();
    n = categories.length;
    dimensionData = $.Array(dimensions.length, n, null);
    for (r = 0; r < n; r++){
        value = categorySums.get(categories[r]);
        for (i = 0; i < dimensions.length; i++){
            if (dimensions[i].isNumeric())
                dimensionData[i][r] = V.modify_Sort.sum(dimensions[i], value);
            else
                dimensionData[i][r] = V.modify_Sort.mode(dimensions[i], value);
        }
    }
    summaries = $.Array(dimensions.length, null);
    for (i = 0; i < dimensions.length; i++){
        summaries[i] = V.Data.makeColumnField("", null, dimensionData[i]);
        if (dimensions[i].isNumeric())
            summaries[i].set("numeric", true);
    }
    order = new V.summary_FieldRowComparison(summaries, ascending, true).makeSortedOrder(n);
    result = $.Array(n, null);
    for (i = 0; i < n; i++)
        result[i] = categories[order[i]];
    return result;
};

V.modify_Sort.sum = function(field, rows) {
    var _i, i, v;
    var sum = 0;
    for(_i=$.iter(rows), i=_i.current; _i.hasNext(); i=_i.next()) {
        v = V.Data.asNumeric(field.value(i));
        if (v != null) sum += v;
    }
    return sum;
};

V.modify_Sort.mode = function(field, rows) {
    var _i, c, i, v;
    var mode = null;
    var max = 0;
    var count = new $.Map();
    for(_i=$.iter(rows), i=_i.current; _i.hasNext(); i=_i.next()) {
        v = field.value(i);
        c = count.get(v);
        if (c == null) c = 0;
        if (++c > max) {
            max = c;
            mode = v;
        }
        count.put(v, c);
    }
    return mode;
};

V.modify_Sort.makeRowRanking = function(order, comparison) {
    var runEnd, v;
    var ranks = $.Array(order.length, 0);
    var runStart = 0;
    while (runStart < order.length) {
        runEnd = runStart + 1;
        while (runEnd < order.length && comparison.compare(order[runStart], order[runEnd]) == 0) runEnd++;
        v = (runEnd + runStart + 1) / 2.0;
        while (runStart < runEnd) ranks[order[runStart++]] = v;
    }
    return ranks;
};

V.modify_Sort.getFields = function(base, names) {
    var i, name;
    var fields = $.Array(names.length, null);
    for (i = 0; i < fields.length; i++){
        name = names[i].split(":")[0];
        fields[i] = base.field(name.trim());
        if (fields[i] == null) {
            throw new $.Exception("Could not find field: " + name);
        }
    }
    return fields;
};

V.modify_Sort.getAscending = function(dimensions, names) {
    var i, p, parts;
    var ascending = $.Array(dimensions.length, false);
    for (i = 0; i < ascending.length; i++){
        parts = names[i].split(":");
        if (parts.length > 1) {
            p = parts[1].trim();
            if ($.equalsIgnoreCase(p, "ascending"))
                ascending[i] = true;
            else if ($.equalsIgnoreCase(p, "descending"))
                ascending[i] = false;
            else
                throw new $.Exception("Sort options must be 'ascending' or 'descending'");
        } else
            ascending[i] = !dimensions[i].isNumeric();
    }
    return ascending;
};

V.modify_Sort.moveCatchAllToEnd = function(order, f) {
    var _i, i, j;
    var result = $.Array(order.length, 0);
    var atEnd = new $.List();
    var at = 0;
    for(_i=$.iter(order), j=_i.current; _i.hasNext(); j=_i.next()) {
        if ("\u2026" == f.value(j))
            atEnd.add(j);
        else
            result[at++] = j;
    }
    for(_i=$.iter(atEnd), i=_i.current; _i.hasNext(); i=_i.next())
        result[at++] = i;
    return result;
};

V.modify_Sort.categoriesFromRanks = function(field, rowRanking) {
    var _i, cats, i, idx, index, o, which;
    var n = field.rowCount();
    var categories = field.categories();
    var counts = field.property("categoryCounts");
    var means = $.Array(counts.length, 0);
    for (i = 0; i < means.length; i++)
        means[i] = i / 100.0 / means.length;
    index = new $.Map();
    for(_i=$.iter(categories), o=_i.current; _i.hasNext(); o=_i.next())
        index.put(o, index.size());
    for (i = 0; i < n; i++){
        o = field.value(i);
        if (o == null) continue;
        idx = index.get(o);
        means[idx] += rowRanking[i] / counts[idx];
    }
    which = V.Data.order(means, true);
    cats = $.Array(which.length, null);
    for (i = 0; i < which.length; i++)
        cats[i] = categories[which[i]];
    return cats;
};

////////////////////// Stack ///////////////////////////////////////////////////////////////////////////////////////////
//
//   This transform takes a single y field and produces two new fields; a lower and an upper value.
//   It does so by stacking values from zero and placing items that are at the same 'x' values on top of each other.
//   The command passed in has four semi-colon separated parts --
//   [1] The y field to use
//   [2] A comma-separated list of x fields
//   [3] A comma-separated list of the fields used as groups within each X value
//   [4] "true" means we will generate all combinations of x fields and groups, even if not present in the data
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_Stack = function() {
    V.modify_Stack.$superConstructor.call(this);
};

$.extend(V.modify_Stack, V.modify_DataOperation);

V.modify_Stack.transform = function(base, command) {
    var aesthetics, allFields, comboFields, fields, full, keyFields, p, x, yField;
    if ($.isEmpty(command)) return base;
    p = V.modify_DataOperation.parts(command);
    yField = p[0];
    x = V.modify_DataOperation.list(p[1]);
    aesthetics = V.modify_DataOperation.list(p[2]);
    full = $.equalsIgnoreCase(p[3], "true");
    if (x == null) x = $.Array(0, null);
    if (aesthetics == null) aesthetics = $.Array(0, null);
    keyFields = V.modify_Stack.getFields(base.fields, x, aesthetics, [yField]);
    allFields = V.modify_Stack.orderRows(base, keyFields);
    if (full) {
        comboFields = V.modify_Stack.getFields(allFields, x, aesthetics);
        allFields = V.modify_Stack.addAllCombinations(allFields, comboFields);
    }
    fields = V.modify_Stack.makeStackedValues(allFields, V.modify_Stack.getField(allFields,
        yField), V.modify_Stack.getFields(allFields, x), full);
    return base.replaceFields(fields);
};

V.modify_Stack.addAllCombinations = function(baseFields, keys) {
    var currentRow, currentRowIndex, fields, i, index, j, matched, row, rows;
    var keyIndices = $.Array(keys.length, 0);
    for (i = 0; i < keys.length; i++){
        for (j = 0; j < baseFields.length; j++)
            if (baseFields[j] == keys[i]) keyIndices[i] = j;
    }
    rows = new $.List();
    currentRowIndex = 0;
    currentRow = V.modify_Stack.makeRealRow(baseFields, currentRowIndex);
    index = $.Array(keys.length, 0);
    while (index != null) {
        row = V.modify_Stack.makeGeneratedRow(baseFields, keyIndices, index);
        matched = false;
        while (V.modify_Stack.matchKeys(row, currentRow, keyIndices)) {
            rows.add(currentRow);
            currentRow = V.modify_Stack.makeRealRow(baseFields, ++currentRowIndex);
            matched = true;
        }
        if (!matched) rows.add(row);
        index = V.modify_Stack.nextIndex(keys, index);
    }
    fields = $.Array(baseFields.length, null);
    for (i = 0; i < baseFields.length; i++){
        fields[i] = V.Data.makeColumnField(baseFields[i].name, baseFields[i].label,
            V.modify_Stack.extractColumn(rows, i));
        V.Data.copyBaseProperties(fields[i], baseFields[i]);
    }
    return fields;
};

V.modify_Stack.extractColumn = function(rows, index) {
    var i;
    var result = $.Array(rows.size(), null);
    for (i = 0; i < result.length; i++)
        result[i] = rows.get(i)[index];
    return result;
};

V.modify_Stack.getField = function(fields, name) {
    var _i, f;
    for(_i=$.iter(fields), f=_i.current; _i.hasNext(); f=_i.next())
        if ($.equals(f.name, name)) return f;
    throw new $.Exception("Could not find field: " + name);
};

V.modify_Stack.getFields = function(fields) {
    var namesList = Array.prototype.slice.call(arguments, 1);
    var _i, _j, fName, s;
    var result = new $.List();
    for(_i=$.iter(namesList), s=_i.current; _i.hasNext(); s=_i.next())
        for(_j=$.iter(s), fName=_j.current; _j.hasNext(); fName=_j.next()) {
            fName = fName.trim();
            if (!$.isEmpty(fName))
                result.add(V.modify_Stack.getField(fields, fName));
        }
    return result.toArray();
};

V.modify_Stack.makeGeneratedRow = function(fields, keyIndices, index) {
    var i, j;
    var n = fields.length;
    var row = $.Array(n, null);
    for (i = 0; i < keyIndices.length; i++){
        j = keyIndices[i];
        row[j] = fields[j].categories()[index[i]];
    }
    return row;
};

V.modify_Stack.makeRealRow = function(fields, index) {
    var i, n, row;
    if (index >= fields[0].rowCount()) return null;
    n = fields.length;
    row = $.Array(n, null);
    for (i = 0; i < fields.length; i++)
        row[i] = fields[i].value(index);
    return row;
};

V.modify_Stack.makeStackedValues = function(allFields, y, x, full) {
    var fields, i, n, v;
    var N = y.rowCount();
    var bounds = $.Array(2, N, null);
    var lastPositive = 0;
    var lastNegative = 0;
    var rowComparison = new V.summary_FieldRowComparison(x, null, false);
    for (i = 0; i < N; i++){
        v = V.Data.asNumeric(y.value(i));
        if (v == null) {
            if (full)
                v = 0.0;
            else
                continue;
        }
        if (i > 0 && rowComparison.compare(i, i - 1) != 0) {
            lastPositive = 0;
            lastNegative = 0;
        }
        if (v < 0) {
            bounds[0][i] = lastNegative;
            lastNegative += v;
            bounds[1][i] = lastNegative;
        } else {
            bounds[0][i] = lastPositive;
            lastPositive += v;
            bounds[1][i] = lastPositive;
        }
    }
    n = allFields.length;
    fields = $.Array(n + 2, null);
    for (i = 0; i < n; i++)
        fields[i] = allFields[i];
    fields[n] = V.Data.makeColumnField(y.name + "$lower", y.label, bounds[0]);
    fields[n + 1] = V.Data.makeColumnField(y.name + "$upper", y.label, bounds[1]);
    V.Data.copyBaseProperties(fields[n], y);
    V.Data.copyBaseProperties(fields[n + 1], y);
    $.sort(fields);
    return fields;
};

V.modify_Stack.matchKeys = function(a, b, indices) {
    var _i, i;
    if (a == null || b == null) return false;
    for(_i=$.iter(indices), i=_i.current; _i.hasNext(); i=_i.next())
        if (V.Data.compare(a[i], b[i]) != 0) return false;
    return true;
};

V.modify_Stack.nextIndex = function(keys, index) {
    var max, p;
    for (p = index.length - 1; p >= 0; p--){
        max = keys[p].categories().length;
        if (++index[p] < max) return index;
        index[p] = 0;
    }
    return null;
};

V.modify_Stack.orderRows = function(base, keyFields) {
    var _i, f, fields, i, rowOrder, valid;
    var baseFields = base.fields;
    var comparison = new V.summary_FieldRowComparison(keyFields, null, true);
    var items = new $.List();
    var n = base.rowCount();
    for (i = 0; i < n; i++){
        valid = true;
        for(_i=$.iter(keyFields), f=_i.current; _i.hasNext(); f=_i.next())
            if (f.value(i) == null) valid = false;
        if (valid) items.add(i);
    }
    $.sort(items, comparison);
    rowOrder = items.toArray();
    fields = $.Array(baseFields.length, null);
    for (i = 0; i < baseFields.length; i++)
        fields[i] = V.Data.permute(baseFields[i], V.Data.toPrimitive(rowOrder), true);
    return fields;
};

////////////////////// Summarize ///////////////////////////////////////////////////////////////////////////////////////
//
//   Performs aggregation by defining a set of summarization commands:
//   FIELD_NAME              -- a field to be used as a dimension (a factor or group)
//   FIELD_NAME : base       -- a field to be used as a dimension AND a base for percentages
//   FIELD_NAME : transform  -- a measure to use to transform the field (e.g. 'mean', 'count', ...)
//   'transform' is a statistical summary; one of sum, count, mode, median, mean, q1, q3, range, variance,
//   stddev, list (concatenates names together), iqr(interquartile range), range.
//   Note that an empty field is legal for the count transform
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.modify_Summarize = function(measures, dimensions, percentBase, rowCount) {
    var _i, m, percentNeeded;
    V.modify_Summarize.$superConstructor.call(this);
    this.measures = measures;
    this.dimensions = dimensions;
    this.percentBase = percentBase;
    this.rowCount = rowCount;
    percentNeeded = false;
    for(_i=$.iter(measures), m=_i.current; _i.hasNext(); m=_i.next())
        if (m.isPercent()) percentNeeded = true;
    this.percentNeeded = percentNeeded;
};

$.extend(V.modify_Summarize, V.modify_DataOperation);

V.modify_Summarize.transform = function(base, command) {
    var _i, baseField, dimensions, fields, measureField, measures, name, operations, percentBase, s, values;
    if (base.rowCount() == 0) return base;
    operations = V.modify_DataOperation.map(command, "=");
    if (operations == null) return base;
    measures = new $.List();
    dimensions = new $.List();
    percentBase = new $.List();
    for(_i=$.iter(operations.keySet()), name=_i.current; _i.hasNext(); name=_i.next()) {
        values = operations.get(name).split(":");
        baseField = base.field(values[0].trim());
        if (values.length == 1) {
            dimensions.add(new V.summary_DimensionField(baseField, name));
        } else if (values[1].trim() == "base") {
            dimensions.add(new V.summary_DimensionField(baseField, name));
            percentBase.add(baseField);
        } else {
            measureField = new V.summary_MeasureField(baseField, name, values[1].trim());
            if (values.length > 2) {
                measureField.option = values[2].trim();
            }
            measures.add(measureField);
        }
    }
    $.sort(measures);
    $.sort(dimensions);
    if (operations.get("#count") == null) {
        measures.add(new V.summary_MeasureField(base.field("#count"), "#count", "sum"));
    }
    if (operations.get("#row") == null)
        measures.add(new V.summary_MeasureField(base.field("#row"), "#row", "list"));
    s = new V.modify_Summarize(measures, dimensions, percentBase, base.rowCount());
    fields = s.make();
    return base.replaceFields(fields);
};

V.modify_Summarize.prototype.make = function() {
    var dimData, f, fields, g, i, m, measureData, originalRow, percentSums, result, row, v, value, values;
    var dimensionFields = this.getFields(this.dimensions);
    var percentBaseFields = this.percentBase.toArray();
    var measureFields = this.getFields(this.measures);
    var dimComparison = new V.summary_FieldRowComparison(dimensionFields, null, false);
    var percentBaseComparison = new V.summary_FieldRowComparison(percentBaseFields, null, false);
    var group = $.Array(this.rowCount, 0);
    var groupCount = this.makeGroups(group, dimComparison);
    var percentGroup = this.percentNeeded ? $.Array(this.rowCount, 0) : null;
    var percentGroupCount = this.percentNeeded ? this.makeGroups(percentGroup, percentBaseComparison) : 0;
    var summaries = $.Array(groupCount, null);
    for (i = 0; i < summaries.length; i++)
        summaries[i] = new V.summary_SummaryValues(measureFields);
    percentSums = $.Array(percentGroupCount, measureFields.length, 0);
    for (row = 0; row < this.rowCount; row++){
        value = summaries[group[row]];
        if (this.percentNeeded) {
            if (value.percentSums == null)
                value.percentSums = percentSums[percentGroup[row]];
            for (i = 0; i < measureFields.length; i++){
                if (this.measures.get(i).isPercent()) {
                    v = V.Data.asNumeric(measureFields[i].value(row));
                    if (v != null) value.percentSums[i] += v;
                }
            }
        }
        value.rows.add(row);
    }
    dimData = $.Array(this.dimensions.size(), groupCount, null);
    measureData = $.Array(this.measures.size(), groupCount, null);
    for (g = 0; g < groupCount; g++){
        values = summaries[g];
        originalRow = values.firstRow();
        for (i = 0; i < this.dimensions.size(); i++)
            dimData[i][g] = dimensionFields[i].value(originalRow);
        for (i = 0; i < this.measures.size(); i++){
            m = this.measures.get(i);
            measureData[i][g] = values.get(i, m, percentBaseFields);
        }
    }
    fields = $.Array(dimData.length + measureData.length, null);
    for (i = 0; i < dimData.length; i++){
        f = this.dimensions.get(i);
        fields[i] = V.Data.makeColumnField(f.rename, f.label(), dimData[i]);
        this.setProperties(fields[i], f.field);
    }
    for (i = 0; i < measureData.length; i++){
        m = this.measures.get(i);
        result = V.Data.makeColumnField(m.rename, m.label(), measureData[i]);
        this.setProperties(result, m.field, m.measureFunction);
        result.set("summary", m.measureFunction);
        if (m.field != null)
            result.set("originalLabel", m.field.label);
        fields[dimData.length + i] = result;
    }
    return fields;
};

V.modify_Summarize.prototype.getFields = function(list) {
    var i;
    var result = $.Array(list.size(), null);
    for (i = 0; i < result.length; i++)
        result[i] = list.get(i).field;
    return result;
};

V.modify_Summarize.prototype.makeGroups = function(group, dimComparison) {
    var i;
    var order = dimComparison.makeSortedOrder(this.rowCount);
    var currentGroup = 0;
    for (i = 0; i < group.length; i++){
        if (i > 0 && dimComparison.compare(order[i], order[i - 1]) != 0) currentGroup++;
        group[order[i]] = currentGroup;
    }
    return currentGroup + 1;
};

V.modify_Summarize.prototype.setProperties = function(to, from, summary) {
    if (summary == null || (summary == "mode"))
        V.Data.copyBaseProperties(to, from);
    else {
        if (!(summary == "count") && !(summary == "valid") && !(summary == "unique"))
            V.Data.copyBaseProperties(to, from);
        to.set("numeric", !(summary == "list") && !(summary == "shorten"));
    }
};

////////////////////// Transform ///////////////////////////////////////////////////////////////////////////////////////
//
//   This transform takes data and bins/ranks or odes another non-sumamarizing transformation
//   on the fields as given
//   The fields are given in the command as semi-colon separated items. Each item has the form a=b
//   which means the field a will have the transform b applied to it. The transforms are
//   bin  -- with an optional parameter for
//   each field determining the desired number of bins. For example "salary=bin; age=bin:10" will ask for standard
//   binning for salary, and about 10 bins for age.
//   rank -- ranks the data, optional parameter "descending" has the largest as #1
//   inner/outer - keeps or removes data that lies within the inter-quartile range. An optional integer parameter gives the
//   percentage of data we would expect to keep or remove if the data were normal (so inner:5% would remove the outer 5%)
//   Note that this transform does not aggregate the data -- it replaces fields with transformed values for each field.
//   Only inner/outer modify the actual data set values, removing rows
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_Transform = function() {
    V.modify_Transform.$superConstructor.call(this);
};

$.extend(V.modify_Transform, V.modify_DataOperation);

V.modify_Transform.transform = function(base, command) {
    var fields, i, operations;
    if (base.rowCount() == 0) return base;
    operations = V.modify_DataOperation.map(command, "=");
    if (operations == null) return base;
    fields = $.Array(base.fields.length, null);
    for (i = 0; i < fields.length; i++)
        fields[i] = V.modify_Transform.modify(base.fields[i], operations.get(base.fields[i].name));
    return base.replaceFields(fields);
};

V.modify_Transform.modify = function(field, operation) {
    var desiredBinCount, name, option, parts;
    if (operation == null) return field;
    parts = operation.split(":");
    name = parts[0].trim();
    option = parts.length > 1 ? parts[1].trim() : null;
    if (name == "bin") {
        desiredBinCount = option == null ? -1 : Number(option);
        return V.modify_Transform.bin(field, desiredBinCount);
    } else if (name == "rank") {
        return V.modify_Transform.rank(field, ("ascending" == option));
    } else {
        return field;
    }
};

V.modify_Transform.rank = function(f, ascending) {
    var i, q, result, rowP;
    var N = f.rowCount();
    var comparison = new V.summary_FieldRowComparison([f], [ascending], true);
    var order = comparison.makeSortedOrder(N);
    var ranks = $.Array(N, null);
    var p = 0;
    while (p < N) {
        rowP = order[p];
        q = p + 1;
        while (q < N && f.compareRows(rowP, order[q]) == 0) q++;
        for (i = p; i < q; i++)
            ranks[order[i]] = (p + q + 1) / 2.0;
        p = q;
    }
    result = V.Data.makeColumnField(f.name, f.label, ranks);
    result.set("numeric", true);
    return result;
};

V.modify_Transform.bin = function(f, desiredBinCount) {
    var field = f.preferCategorical() ? V.modify_Transform.binCategorical(f, desiredBinCount)
        : V.modify_Transform.binNumeric(f, desiredBinCount);
    field.set("binned", true);
    return field;
};

V.modify_Transform.binCategorical = function(f, desiredBinCount) {
    var categories, data, i, newNames, order;
    if (desiredBinCount < 1) desiredBinCount = 7;
    categories = f.categories();
    if (categories.length <= desiredBinCount) return f;
    order = V.Data.order(f.property("categoryCounts"), false);
    newNames = new $.Map();
    for (i = 0; i < order.length; i++)
        newNames.put(categories[order[i]], i < desiredBinCount ? categories[order[i]] : "\u2026");
    data = $.Array(f.rowCount(), null);
    for (i = 0; i < data.length; i++)
        data[i] = newNames.get(f.value(i));
    return V.Data.makeColumnField(f.name, f.label, data);
};

V.modify_Transform.binNumeric = function(f, desiredBinCount) {
    var scale = V.auto_Auto.makeNumericScale(f, true, [0, 0], 0.0, desiredBinCount + 1, true);
    var divisions = scale.divisions;
    var isDate = f.isDate();
    var dateFormat = isDate ? f.property("dateFormat") : null;
    var ranges = V.modify_Transform.makeBinRanges(divisions, dateFormat, scale.granular);
    var data = V.modify_Transform.binData(f, divisions, ranges);
    var result = V.Data.makeColumnField(f.name, f.label, data);
    if (f.isDate()) result.set("date", true);
    result.set("numeric", true);
    result.set("categories", ranges);
    return result;
};

V.modify_Transform.makeBinRanges = function(divisions, dateFormat, nameByCenter) {
    var a, b, i;
    var ranges = $.Array(divisions.length - 1, null);
    for (i = 0; i < ranges.length; i++){
        a = divisions[i];
        b = divisions[i + 1];
        ranges[i] = (dateFormat == null) ? V.util_Range.makeNumeric(a, b, nameByCenter)
            : V.util_Range.makeDate(a, b, nameByCenter, dateFormat);
    }
    return ranges;
};

V.modify_Transform.binData = function(f, divisions, ranges) {
    var d, i, n;
    var data = $.Array(f.rowCount(), null);
    for (i = 0; i < data.length; i++){
        d = V.Data.asNumeric(f.value(i));
        if (d == null) continue;
        n = V.Data.indexOf(d, divisions);
        data[i] = ranges[Math.min(n, ranges.length - 1)];
    }
    return data;
};

////////////////////// DateStats ///////////////////////////////////////////////////////////////////////////////////////

V.stats_DateStats = function() {};

V.stats_DateStats.populate = function(f) {
    var factor, granularity, unit;
    var days = f.max() - f.min();
    if (days == 0) days = f.max();
    unit = V.stats_DateStats.getUnit(days);
    f.set("dateUnit", unit);
    granularity = f.numericProperty("granularity");
    factor = Math.min(1.0, Math.sqrt(f.valid()) / 7);
    f.set("dateFormat", V.stats_DateStats.getFormat(unit, granularity * factor));
};

V.stats_DateStats.getUnit = function(days) {
    var _i, d;
    for(_i=$.iter(V.util_DateUnit.values()), d=_i.current; _i.hasNext(); d=_i.next()) {
        if (days > 3.5 * d.approxDaysPerUnit) return d;
        if (d == V.util_DateUnit.day && days >= 2.5 * d.approxDaysPerUnit) return d;
    }
    return V.util_DateUnit.second;
};

V.stats_DateStats.getFormat = function(unit, granularity) {
    if (granularity > 360) return V.util_DateFormat.Year;
    if (granularity > 27) return V.util_DateFormat.YearMonth;
    if (granularity > 0.9)
        return V.util_DateFormat.YearMonthDay;
    if (unit.ordinal() < V.util_DateUnit.hour.ordinal()) return V.util_DateFormat.DayHour;
    if (granularity > 0.9 / 24 / 60) return V.util_DateFormat.HourMin;
    return V.util_DateFormat.HourMinSec;
};

V.stats_DateStats.creates = function(key) {
    return ("dateUnit" == key) || ("dateFormat" == key);
};

////////////////////// NominalStats ////////////////////////////////////////////////////////////////////////////////////

V.stats_NominalStats = function() {};

V.stats_NominalStats.populate = function(f) {
    var c, cats, counts, i, naturalOrder, o, sortedModes, value;
    var count = new $.Map();
    var modes = new $.Set();
    var N = f.rowCount();
    var maxCount = 0;
    var valid = 0;
    for (i = 0; i < N; i++){
        o = f.value(i);
        if (o == null) continue;
        valid++;
        c = count.get(o);
        value = c == null ? 1 : c + 1;
        count.put(o, value);
        if (value > maxCount) modes.clear();
        if (value >= maxCount) {
            modes.add(o);
            maxCount = value;
        }
    }
    f.set("n", N);
    f.set("unique", count.size());
    f.set("valid", valid);
    if ($.isEmpty(modes)) {
        f.set("mode");
    } else {
        sortedModes = modes.toArray();
        V.Data.sort(sortedModes);
        f.set("mode", sortedModes[Math.floor((sortedModes.length - 1) / 2)]);
    }
    if (f.name == "#selection") {
        if (!count.containsKey(V.Field.VAL_UNSELECTED))
            count.put(V.Field.VAL_UNSELECTED, 0);
        if (!count.containsKey(V.Field.VAL_SELECTED))
            count.put(V.Field.VAL_SELECTED, 0);
        naturalOrder = [V.Field.VAL_UNSELECTED, V.Field.VAL_SELECTED];
    } else {
        cats = count.keySet();
        naturalOrder = cats.toArray();
        V.Data.sort(naturalOrder);
    }
    f.set("categories", naturalOrder);
    counts = $.Array(naturalOrder.length, 0);
    for (i = 0; i < naturalOrder.length; i++)
        counts[i] = count.get(naturalOrder[i]);
    f.set("categoryCounts", counts);
};

V.stats_NominalStats.creates = function(key) {
    return ("n" == key) || ("mode" == key) || ("unique" == key) || ("valid" == key)
        || ("categories" == key) || ("categoryCounts" == key);
};

////////////////////// NumericStats ////////////////////////////////////////////////////////////////////////////////////

V.stats_NumericStats = function() {};

V.stats_NumericStats.populate = function(f) {
    var d, data, high, i, item, low, m1, m2, m3, m4, max, min, minD;
    var n = f.rowCount();
    var valid = new $.List();
    for (i = 0; i < n; i++){
        item = f.value(i);
        if (item != null) {
            if (item instanceof V.util_Range) {
                low = item.low;
                high = item.high;
                valid.add(V.Data.asNumeric(low));
                valid.add(V.Data.asNumeric(high));
            } else {
                d = V.Data.asNumeric(item);
                if (d != null) valid.add(d);
            }
        }
    }
    data = valid.toArray();
    n = data.length;
    f.set("validNumeric", n);
    if (n == 0) return false;
    m1 = V.stats_NumericStats.moment(data, 0, 1, n);
    m2 = V.stats_NumericStats.moment(data, m1, 2, n - 1);
    m3 = V.stats_NumericStats.moment(data, m1, 3, n - 1);
    m4 = V.stats_NumericStats.moment(data, m1, 4, n - 1);
    f.set("mean", m1);
    f.set("stddev", Math.sqrt(m2));
    f.set("variance", m2);
    f.set("skew", m3 / m2 / Math.sqrt(m2));
    f.set("kurtosis", m4 / m2 / m2 - 3.0);
    $.sort(data);
    min = data[0];
    max = data[n - 1];
    f.set("min", min);
    f.set("max", max);
    f.set("median", V.stats_NumericStats.av(data, (n - 1) * 0.5));
    if (n % 2 == 0) {
        f.set("q1", V.stats_NumericStats.av(data, (n / 2 - 1) * 0.5));
        f.set("q3", V.stats_NumericStats.av(data, n / 2 + (n / 2 - 1) * 0.5));
    } else {
        f.set("q1", V.stats_NumericStats.av(data, (n - 1) * 0.25));
        f.set("q3", V.stats_NumericStats.av(data, (n - 1) / 2 + (n - 1) * 0.25));
    }
    minD = max - min;
    if (minD == 0) minD = Math.abs(max);
    for (i = 1; i < data.length; i++){
        d = data[i] - data[i - 1];
        if (d > 0) minD = Math.min(minD, d);
    }
    f.set("granularity", minD);
    return true;
};

V.stats_NumericStats.moment = function(data, c, p, N) {
    var _i, element, sum;
    if (N <= 0) return Number.NaN;
    sum = 0.0;
    for(_i=$.iter(data), element=_i.current; _i.hasNext(); element=_i.next())
        sum += Math.pow(element - c, p);
    return sum / N;
};

V.stats_NumericStats.av = function(v, index) {
    return (v[Math.floor(index)] + v[Math.ceil(index)]) / 2.0;
};

V.stats_NumericStats.creates = function(key) {
    return ("validNumeric" == key) || ("mean" == key) || ("stddev" == key) || ("variance" == key) || ("skew" == key)
        || ("kurtosis" == key) || ("min" == key) || ("max" == key) || ("q1" == key) || ("q3" ==
        key) || ("median" == key) || ("granularity" == key);
};

////////////////////// DimensionField //////////////////////////////////////////////////////////////////////////////////


V.summary_DimensionField = function(field, rename) {
    this.field = field;
    this.rename = rename == null ? field.name : rename;
};

V.summary_DimensionField.prototype.compareTo = function(o) {
    return V.Data.compare(this.rename, o.rename);
};

V.summary_DimensionField.prototype.getDateFormat = function() {
    return this.isDate() ? this.field.property("dateFormat") : null;
};

V.summary_DimensionField.prototype.isDate = function() {
    return this.field != null && this.field.isDate();
};

V.summary_DimensionField.prototype.label = function() {
    return this.field == null ? this.rename : this.field.label;
};

V.summary_DimensionField.prototype.toString = function() {
    return $.equals(this.rename, this.field.name) ? this.rename : this.field.name + "[->" + this.rename + "]";
};

////////////////////// FieldRowComparison //////////////////////////////////////////////////////////////////////////////
//
//   Details on how to compare rows
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.summary_FieldRowComparison = function(fields, ascending, rowsBreakTies) {
    this.fields = fields;
    this.ascending = ascending;
    this.rowsBreakTies = rowsBreakTies;
    this.n = fields.length;
};

V.summary_FieldRowComparison.prototype.compare = function(a, b) {
    var i, n;
    for (i = 0; i < this.n; i++){
        n = this.fields[i].compareRows(a, b);
        if (n != 0)
            return this.ascending != null && !this.ascending[i] ? -n : n;
    }
    return this.rowsBreakTies ? (a - b) : 0;
};

V.summary_FieldRowComparison.prototype.makeSortedOrder = function(len) {
    var i;
    var items = $.Array(len, 0);
    for (i = 0; i < len; i++)
        items[i] = i;
    $.sort(items, this);
    return V.Data.toPrimitive(items);
};

////////////////////// MeasureField ////////////////////////////////////////////////////////////////////////////////////


V.summary_MeasureField = function(field, rename, measureFunction) {
    this.option = null;
    this.fit = null;
    V.summary_MeasureField.$superConstructor.call(this, field, rename ==
        null && field == null ? measureFunction : rename);
    if (field != null && (measureFunction == "mean") && !field.isNumeric())
        this.measureFunction = "mode";
    else
        this.measureFunction = measureFunction;
};

$.extend(V.summary_MeasureField, V.summary_DimensionField);

V.summary_MeasureField.prototype.isPercent = function() {
    return this.measureFunction == "percent";
};

V.summary_MeasureField.prototype.toString = function() {
    if (this.field != null && $.equals(this.field.name, this.rename)) return this.label();
    return this.label() + "[->" + this.rename + "]";
};

V.summary_MeasureField.prototype.label = function() {
    var a, b;
    if ((this.measureFunction == "sum") && (this.field.name == "#count")) return this.field.label;
    if ((this.measureFunction == "percent") && (this.field.name == "#count")) return "Percent";
    a = this.measureFunction.substring(0, 1).toUpperCase();
    b = this.measureFunction.substring(1);
    return a + b + "(" + (this.field == null ? "" : this.field.label) + ")";
};

////////////////////// Regression //////////////////////////////////////////////////////////////////////////////////////
//
//   Calculates a regression function
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.summary_Regression = function(y, x) {
    var i, mx, my, sxx, sxy, xv, yv;
    this.m = null;
    this.b = null;
    var data = V.summary_Regression.asPairs(y, x);
    var n = data[0].length;
    if (n == 0) return;
    my = V.summary_Regression.mean(data[0]);
    mx = V.summary_Regression.mean(data[1]);
    sxy = 0;
    sxx = 0;
    for (i = 0; i < n; i++){
        yv = data[0][i];
        xv = data[1][i];
        sxy += (xv - mx) * (yv - my);
        sxx += (xv - mx) * (xv - mx);
    }
    if (sxx > 0) {
        this.m = sxy / sxx;
        this.b = my - this.m * mx;
    }
};

V.summary_Regression.mean = function(values) {
    var i;
    var s = 0;
    for (i = 0; i < values.length; i++)
        s += values[i];
    return s / values.length;
};

V.summary_Regression.asPairs = function(y, x) {
    var i, order, xv, xx, yv, yy;
    var xList = new $.List();
    var yList = new $.List();
    var n = x.rowCount();
    for (i = 0; i < n; i++){
        xv = V.Data.asNumeric(x.value(i));
        yv = V.Data.asNumeric(y.value(i));
        if (xv != null && yv != null) {
            xList.add(xv);
            yList.add(yv);
        }
    }
    order = V.Data.order(xList.toArray(), true);
    xx = $.Array(order.length, 0);
    yy = $.Array(order.length, 0);
    for (i = 0; i < order.length; i++){
        xx[i] = xList.get(order[i]);
        yy[i] = yList.get(order[i]);
    }
    return [yy, xx];
};

V.summary_Regression.prototype.get = function(value) {
    return this.m == null ? null : this.m * V.Data.asNumeric(value) + this.b;
};

////////////////////// Smooth //////////////////////////////////////////////////////////////////////////////////////////
//
//   Calculates a smooth fit function
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.summary_Smooth = function(y, x, windowPercent) {
    var n;
    if (windowPercent == null) {
        n = V.auto_Auto.optimalBinCount(x);
        this.delta = Math.max(2, Math.round((x.valid() / n)));
    } else {
        this.delta = Math.max(2, Math.floor(x.valid() * windowPercent / 200));
    }
    this.data = V.summary_Regression.asPairs(y, x);
};

V.summary_Smooth.prototype.get = function(value) {
    var d, high, i, idx, low, sw, sy, w, window, x, y;
    var at = V.Data.asNumeric(value);
    if (at == null) return null;
    y = this.data[0];
    x = this.data[1];
    idx = this.search(at, x);
    low = Math.max(0, idx - this.delta);
    high = Math.min(idx + this.delta, x.length - 1);
    window = Math.max(at - x[low], x[high] - at);
    sy = 0;
    sw = 0;
    for (i = low; i <= high; i++){
        d = (x[i] - at) / window;
        w = 0.75 * (1 - d * d);
        sw += w;
        sy += w * y[i];
    }
    return sw > 0 ? sy / sw : null;
};

V.summary_Smooth.prototype.search = function(at, x) {
    var t;
    var p = 0;
    var q = x.length - 1;
    while (q - p > 1) {
        t = p + q >> 1;
        if (x[t] <= at) p = t;
        if (x[t] >= at) q = t;
    }
    while (p > 0 && x[p - 1] == at) p--;
    while (q < x.length - 1 && x[q + 1] == at) q++;
    return (p + q) >> 1;
};

////////////////////// SummaryValues ///////////////////////////////////////////////////////////////////////////////////


V.summary_SummaryValues = function(fields) {
    this.rows = new $.List();
    this.percentSums = null;this.fields = fields;
};

V.summary_SummaryValues.prototype.firstRow = function() {
    return this.rows.get(0);
};

V.summary_SummaryValues.prototype.get = function(fieldIndex, m, xFields) {
    var categories, data, displayCount, f, high, i, low, mean, sum, windowPercent, x;
    var summary = m.measureFunction;
    if (summary == "count") return this.rows.size();
    if (summary == "fit") {
        x = xFields[0];
        if (m.fit == null)
            m.fit = new V.summary_Regression(m.field, x);
        return m.fit.get(x.value(this.rows.get(0)));
    }
    if (summary == "smooth") {
        x = xFields[0];
        windowPercent = null;
        if (m.option != null)
            windowPercent = Number(m.option);
        if (m.fit == null)
            m.fit = new V.summary_Smooth(m.field, x, windowPercent);
        return m.fit.get(x.value(this.rows.get(0)));
    }
    data = $.Array(this.rows.size(), null);
    for (i = 0; i < data.length; i++)
        data[i] = this.fields[fieldIndex].value(this.rows.get(i));
    f = V.Data.makeColumnField("temp", null, data);
    mean = f.numericProperty("mean");
    if (summary == "percent") {
        if (mean == null) return null;
        sum = this.percentSums[fieldIndex];
        return sum > 0 ? 100 * mean * f.numericProperty("valid") / sum : null;
    }
    if (summary == "range") {
        if (mean == null) return null;
        low = f.numericProperty("min");
        high = f.numericProperty("max");
        return low == null ? null : V.util_Range.make(low, high, m.getDateFormat());
    }
    if (summary == "iqr") {
        if (mean == null) return null;
        low = f.numericProperty("q1");
        high = f.numericProperty("q3");
        return low == null ? null : V.util_Range.make(low, high, m.getDateFormat());
    }
    if (summary == "sum") {
        if (mean == null) return null;
        return mean * f.numericProperty("valid");
    }
    if (summary == "list") {
        categories = new V.util_ItemsList(f.property("categories"), m.getDateFormat());
        if (m.option != null) {
            displayCount = Number(m.option);
            categories.setDisplayCount(displayCount);
        }
        return categories;
    }
    return f.property(summary);
};

////////////////////// DateFormat //////////////////////////////////////////////////////////////////////////////////////

V.util_DateFormat = function() {};

V.util_DateFormat.prototype.format = function(date) {
    var p = date.toUTCString().split(' ');
    var t = this.toString();
    if (t == 'HourMinSec') return p[4];
    if (t == 'Year') return p[3];
    if (t == 'YearMonth') return p[2] + ' ' + Number(p[3]);
    if (t == 'YearMonthDay') return p[2] + ' ' + Number(p[1]) + ', ' + Number(p[3]);
    var q = p[4].split(':');
    var hm = q[0] + ':' + q[1];
    if (t == 'HourMin') return hm;
    return p[2] + ' ' + Number(p[1]) + ' ' +  hm;
};

V.util_DateFormat.HourMinSec = new V.util_DateFormat();
V.util_DateFormat.HourMin = new V.util_DateFormat();
V.util_DateFormat.DayHour = new V.util_DateFormat();
V.util_DateFormat.YearMonthDay = new V.util_DateFormat();
V.util_DateFormat.YearMonth = new V.util_DateFormat();
V.util_DateFormat.Year = new V.util_DateFormat();

$.extendEnum(V.util_DateFormat);

////////////////////// DateUnit ////////////////////////////////////////////////////////////////////////////////////////


V.util_DateUnit = function(days, base) {
    this.approxDaysPerUnit = days;
    this.base = base;
};

V.util_DateUnit.floor = function(d, u, multiple) {
    if (u == V.util_DateUnit.day) {
    	return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), V.util_DateUnit.floorNumeric(d.getUTCDate(), multiple, 1)));
    } else if (u == V.util_DateUnit.week) {
    	return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), V.util_DateUnit.floorNumeric(d.getUTCDate(), multiple*7, 1)));
    } else if (u == V.util_DateUnit.month) {
    	return new Date(Date.UTC(d.getUTCFullYear(), V.util_DateUnit.floorNumeric(d.getUTCMonth(), multiple, 0)));
    } else if (u == V.util_DateUnit.quarter) {
    	return new Date(Date.UTC(d.getUTCFullYear(), V.util_DateUnit.floorNumeric(d.getUTCMonth(), multiple*3, 0)));
    } else if (u == V.util_DateUnit.year) {
    	return new Date(Date.UTC(V.util_DateUnit.floorNumeric(d.getUTCFullYear(), multiple, 0), 0));
    } else if (u == V.util_DateUnit.decade) {
    	return new Date(Date.UTC(V.util_DateUnit.floorNumeric(d.getUTCFullYear(), multiple*10, 0), 0));
    } else if (u == V.util_DateUnit.century) {
    	return new Date(Date.UTC(V.util_DateUnit.floorNumeric(d.getUTCFullYear(), multiple*100, 0), 0));
    }
    var c = new Date(d.getTime());
    if (u == V.util_DateUnit.second) {
    	c.setUTCSeconds(V.util_DateUnit.floorNumeric(d.getUTCSeconds(), multiple, 0));
    } else if (u == V.util_DateUnit.minute) {
    	c.setUTCSeconds(0);
    	c.setUTCMinutes(V.util_DateUnit.floorNumeric(d.getUTCMinutes(), multiple, 0));
    } else if (u == V.util_DateUnit.hour) {
    	c.setUTCSeconds(0); c.setUTCMinutes(0);
    	c.setUTCHours(V.util_DateUnit.floorNumeric(d.getUTCHours(), multiple, 0));
    } else if (u == V.util_DateUnit.hour) {
    	c.setUTCSeconds(0); c.setUTCMinutes(0);
    	c.setUTCHours(V.util_DateUnit.floorNumeric(d.getUTCHours(), multiple, 0));
    } else
    	throw 'Invalid date unit ' + u;
    return c;
};

V.util_DateUnit.floorNumeric = function(value, multiple, offset) {
    return multiple * Math.floor((value - offset) / multiple) + offset;
};

V.util_DateUnit.increment = function(d, u, delta) {
    var c = new Date(d.getTime());
    if (u == V.util_DateUnit.second) c.setUTCSeconds(c.getUTCSeconds()+delta);
    else if (u == V.util_DateUnit.minute) c.setUTCMinutes(c.getUTCMinutes()+delta);
    else if (u == V.util_DateUnit.hour) c.setUTCHours(c.getUTCHours()+delta);
    else if (u == V.util_DateUnit.day) c.setUTCDate(c.getUTCDate()+delta);
    else if (u == V.util_DateUnit.week) c.setUTCDate(c.getUTCDate()+7*delta);
    else if (u == V.util_DateUnit.month) c.setUTCMonth(c.getUTCMonth()+delta);
    else if (u == V.util_DateUnit.quarter) c.setUTCMonth(c.getUTCMonth()+3*delta);
    else if (u == V.util_DateUnit.year) c.setUTCFullYear(c.getUTCFullYear()+delta);
    else if (u == V.util_DateUnit.decade) c.setUTCFullYear(c.getUTCFullYear()+10*delta);
    else if (u == V.util_DateUnit.century) c.setUTCFullYear(c.getUTCFullYear()+100*delta);
    return c;
};

V.util_DateUnit.century = new V.util_DateUnit(365 * 100, 10);
V.util_DateUnit.decade = new V.util_DateUnit(365 * 10, 10);
V.util_DateUnit.year = new V.util_DateUnit(365, 10);
V.util_DateUnit.quarter = new V.util_DateUnit(365.0 / 4, 4);
V.util_DateUnit.month = new V.util_DateUnit(30, 4);
V.util_DateUnit.week = new V.util_DateUnit(7, 4);
V.util_DateUnit.day = new V.util_DateUnit(1, 7);
V.util_DateUnit.hour = new V.util_DateUnit(1 / 24.0, 24);
V.util_DateUnit.minute = new V.util_DateUnit(1 / 24.0 / 60.0, 60);
V.util_DateUnit.second = new V.util_DateUnit(1 / 24.0 / 3600.0, 60);

$.extendEnum(V.util_DateUnit);

////////////////////// ItemsList ///////////////////////////////////////////////////////////////////////////////////////


V.util_ItemsList = function(items, df) {
    V.util_ItemsList.$superConstructor.call(this);
    this.displayCount = 12;this.dateFormat = df;
    $.addAll(this, items);
};

$.extend(V.util_ItemsList, $.List);

V.util_ItemsList.prototype.equals = function(obj) {
    return this == obj || obj instanceof V.util_ItemsList && this.compareTo(obj) == 0;
};

V.util_ItemsList.prototype.compareTo = function(o) {
    var d, i;
    var n = Math.min(this.size(), o.size());
    for (i = 0; i < n; i++){
        d = V.Data.compare(this.get(i), o.get(i));
        if (d != 0) return d;
    }
    return this.size() - o.size();
};

V.util_ItemsList.prototype.setDisplayCount = function(displayCount) {
    this.displayCount = displayCount;
};

V.util_ItemsList.prototype.toString = function() {
    var d, i, v;
    var s = "";
    var n = this.size();
    for (i = 0; i < n; i++){
        if (i > 0) s += ", ";
        if (i == this.displayCount - 1 && n > this.displayCount) return s + "\u2026";
        v = this.get(i);
        if (this.dateFormat != null)
            s += this.dateFormat.format(V.Data.asDate(v));
        else {
            d = V.Data.asNumeric(v);
            if (d != null)
                s += V.Data.formatNumeric(d, false);
            else
                s += v.toString();
        }
    }
    return s;
};

////////////////////// Range ///////////////////////////////////////////////////////////////////////////////////////////


V.util_Range = function(low, high, mid, name) {
    this.low = low;
    this.high = high;
    this.mid = mid;
    this.name = name;
};

V.util_Range.make = function(low, high, dateFormat) {
    return dateFormat == null ? V.util_Range.makeNumeric(low, high, false)
        : V.util_Range.makeDate(low, high, false, dateFormat);
};

V.util_Range.makeNumeric = function(low, high, nameAtMid) {
    var mid = (high + low) / 2;
    var name = nameAtMid ? V.Data.formatNumeric(mid, true) : V.Data.formatNumeric(low,
        true) + "\u2026" + V.Data.formatNumeric(high, true);
    return new V.util_Range(low, high, mid, name);
};

V.util_Range.makeDate = function(low, high, nameAtMid, df) {
    var lowDate = V.Data.asDate(low);
    var highDate = V.Data.asDate(high);
    var midDate = V.Data.asDate((high + low) / 2);
    var name = nameAtMid ? df.format(midDate) : df.format(lowDate) + "\u2026" + df.format(highDate);
    return new V.util_Range(lowDate, highDate, midDate, name);
};

V.util_Range.prototype.compareTo = function(o) {
    return V.Data.compare(this.asNumeric(), o.asNumeric());
};

V.util_Range.prototype.asNumeric = function() {
    return (V.Data.asNumeric(this.low) + V.Data.asNumeric(this.high)) / 2.0;
};

V.util_Range.prototype.extent = function() {
    return V.Data.asNumeric(this.high) - V.Data.asNumeric(this.low);
};

V.util_Range.prototype.hashCode = function() {
    return this.name;
};

V.util_Range.prototype.equals = function(obj) {
    if (this == obj) return true;
    return obj instanceof V.util_Range && obj.low == this.low && obj.high == this.high;
};

V.util_Range.prototype.toString = function() {
    return this.name;
};

////////////////////// ColumnProvider //////////////////////////////////////////////////////////////////////////////////


V.values_ColumnProvider = function(column) {
    var i, stored, value;
    var common = new $.Map();
    this.column = $.Array(column.length, null);
    for (i = 0; i < column.length; i++){
        value = column[i];
        stored = common.get(value);
        if (stored == null) {
            common.put(value, value);
            this.column[i] = value;
        } else {
            this.column[i] = stored;
        }
    }
};

V.values_ColumnProvider.copy = function(base) {
    var i;
    var data = $.Array(base.count(), null);
    for (i = 0; i < data.length; i++)
        data[i] = base.value(i);
    return new V.values_ColumnProvider(data);
};

V.values_ColumnProvider.prototype.count = function() {
    return this.column.length;
};

V.values_ColumnProvider.prototype.expectedSize = function() {
    var _i, c;
    var seen = new $.Set();
    var total = 24 + 4 * this.column.length;
    for(_i=$.iter(this.column), c=_i.current; _i.hasNext(); c=_i.next()) {
        if (c == null) continue;
        if (seen.add(c)) {
            if (c instanceof String)
                total += (42 + $.len(c) * 2);
            else
                total += 16;
        }
    }
    return total;
};

V.values_ColumnProvider.prototype.setValue = function(o, index) {
    this.column[index] = o;
    return this;
};

V.values_ColumnProvider.prototype.compareRows = function(a, b, categoryOrder) {
    var p = this.column[a];
    var q = this.column[b];
    if (p == q) return 0;
    if (p == null) return 1;
    if (q == null) return -1;
    if ($.isEmpty(categoryOrder))
        return V.Data.compare(p, q);
    else
        return categoryOrder.get(p) - categoryOrder.get(q);
};

V.values_ColumnProvider.prototype.value = function(index) {
    return this.column[index];
};

////////////////////// ConstantProvider ////////////////////////////////////////////////////////////////////////////////


V.values_ConstantProvider = function(o, len) {
    this.o = o;
    this.len = len;
};

V.values_ConstantProvider.prototype.compareRows = function(a, b, categoryOrder) {
    return 0;
};

V.values_ConstantProvider.prototype.count = function() {
    return this.len;
};

V.values_ConstantProvider.prototype.expectedSize = function() {
    return 24;
};

V.values_ConstantProvider.prototype.setValue = function(o, index) {
    return V.values_ColumnProvider.copy(this).setValue(o, index);
};

V.values_ConstantProvider.prototype.value = function(index) {
    return this.o;
};

////////////////////// ReorderedProvider ///////////////////////////////////////////////////////////////////////////////


V.values_ReorderedProvider = function(base, order) {
    this.base = base;
    this.order = order;
};

V.values_ReorderedProvider.prototype.compareRows = function(a, b, categoryOrder) {
    return this.base.compareRows(this.order[a], this.order[b], categoryOrder);
};

V.values_ReorderedProvider.prototype.count = function() {
    return this.order.length;
};

V.values_ReorderedProvider.prototype.expectedSize = function() {
    return 24 + this.order.length * 4 + this.base.expectedSize();
};

V.values_ReorderedProvider.prototype.setValue = function(o, index) {
    return V.values_ColumnProvider.copy(this).setValue(o, index);
};

V.values_ReorderedProvider.prototype.value = function(index) {
    return this.base.value(this.order[index]);
};

////////////////////// RowProvider /////////////////////////////////////////////////////////////////////////////////////


V.values_RowProvider = function(len) {
    this.len = len;
};

V.values_RowProvider.prototype.count = function() {
    return this.len;
};

V.values_RowProvider.prototype.expectedSize = function() {
    return 24;
};

V.values_RowProvider.prototype.setValue = function(o, index) {
    return V.values_ColumnProvider.copy(this).setValue(o, index);
};

V.values_RowProvider.prototype.value = function(index) {
    return index + 1;
};

V.values_RowProvider.prototype.compareRows = function(a, b, categoryOrder) {
    return a - b;
};

return V;
})();
