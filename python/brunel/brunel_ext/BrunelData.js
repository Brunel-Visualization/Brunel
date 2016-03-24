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
        var v, sup = parentClass.prototype, inner = function () {
        };
        childClass.prototype = new inner();
        childClass.prototype.constructor = childClass;
        childClass.$superConstructor = parentClass;
        childClass.$super = sup;
        for (v in sup)
            if (sup.hasOwnProperty(v))
                childClass.prototype[v] = sup[v];
    },

    // Within the child, call this to initialize superclasses
    superconstruct: function (child) {
        child.constructor.$superConstructor.apply(child, Array.prototype.slice.call(arguments, 1));
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

    copy: function (dest, src) {
        for (var i in src) dest[i] = src[i];
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
        if (!a) return "#";
        if (typeof (a.hashCode) == "function") return a.hashCode();
        return "#" + String(a);
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
            a.sort(function (i, j) {
                return b.compare.call(b, i, j)
            });
        else
            a.sort($.compare);
    },

    toString: function (v) {
        if (!v) return null;
        if (Array.isArray(v)) {
            var i, s;
            for (i in v) if (v.hasOwnProperty(i)) {
                if (!s) s = "[" + $.toString(v[i]);
                else s += ", " + $.toString(v[i]);
            }
            return s + "]";
        }
        if (typeof (v.toString) == 'function') return v.toString();
        return "" + v;
    },

    copyOf: function (array, n) {
        var i, c = $.Array(n, 0), m = Math.min(n, $.len(array));
        for (i = 0; i < m; i++) c[i] = array[i];
        return c;
    },

    fill: function (a, v) {
        for (i in a) if (a.hasOwnProperty(i)) a[i] = v;
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
        var key, list, j, items = map.items;
        for (key in items) if (items.hasOwnProperty(key)) {
            list = items[key];
            for (j = 0; j < list.length; j++)
                this.put(list[j][0], list[j][1]);
        }
    },
    values: function () {
        var key, list, j, keys = new $.List(), items = this.items;
        for (key in items) if (items.hasOwnProperty(key)) {
            list = items[key];
            for (j = 0; j < list.length; j++)
                keys.add(list[j][1]);
        }
        return keys;
    },
    get: function (key) {
        var list = this.items[$.hash(key)];
        var idx = this._findInArray(key, list);
        return idx >= 0 ? list[idx][1] : null;
    },
    keySet: function () {
        var key, list, j, keys = new $.Set(), items = this.items;
        for (key in items) if (items.hasOwnProperty(key)) {
            list = items[key];
            for (j = 0; j < list.length; j++)
                keys.add(list[j][0]);
        }
        return keys;
    },
    containsKey: function (key) {
        var list = this.items[$.hash(key)];
        return this._findInArray(key, list) >= 0;
    },
    toArray: function () {
        var key, list, j, a = [], items = this.items;
        for (key in items) if (items.hasOwnProperty(key)) {
            list = items[key];
            for (j = 0; j < list.length; j++)
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
    (function () {
        var rtrim = /^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g;
        String.prototype.trim = function () {
            return this.replace(rtrim, '');
        };
    })();
}

// Polyfill for Array.isArray
if (!Array.isArray) {
    Array.isArray = function (arg) {
        return Object.prototype.toString.call(arg) === '[object Array]';
    };
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

$.copy(V.auto_Auto, {

    makeNumericScale: function(f, nice, padFraction, includeZeroTolerance, desiredTickCount, forBinning) {
        var p, scaling;
        V.auto_Auto.setTransform(f);
        if (desiredTickCount < 1)
            desiredTickCount = Math.min(V.auto_Auto.optimalBinCount(f), 20) + 1;
        if (f.isDate())
            return V.auto_NumericScale.makeDateScale(f, nice, padFraction, desiredTickCount);
        p = f.strProperty("transform");
        if (p == "log")
            return V.auto_NumericScale.makeLogScale(f, nice, padFraction, includeZeroTolerance, desiredTickCount);
        if (p == "root") {
            if (f.min() > 0) {
                scaling = (f.min() / f.max()) / (Math.sqrt(f.min()) / Math.sqrt(f.max()));
                includeZeroTolerance *= scaling;
                padFraction[0] *= scaling;
            }
        }
        return V.auto_NumericScale.makeLinearScale(f, nice, includeZeroTolerance,
            padFraction, desiredTickCount, forBinning);
    },

    setTransform: function(f) {
        var skew;
        if (f.property("transform") != null) return;
        skew = f.numProperty("skew");
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
    },

    optimalBinCount: function(f) {
        var h1 = 2 * (f.numProperty("q3") - f.numProperty("q1")) / Math.pow(f.valid(), 0.33333);
        var h2 = 3.5 * f.numProperty("stddev") / Math.pow(f.valid(), 0.33333);
        var h = Math.max(h1, h2);
        if (h == 0)
            return 1;
        else
            return Math.round((f.max() - f.min()) / h + 0.499);
    },

    convert: function(base) {
        var N, asList, asNumeric, i, j, n, nDate, nNumeric, o, order, t;
        if (base.isSynthetic() || base.isDate()) return base;
        if (base.isProperty("list")) return base;
        asList = V.Data.toList(base);
        if (V.auto_Auto.goodLists(asList)) return asList;
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
        if (nDate > V.auto_Auto.FRACTION_TO_CONVERT * n) return V.Data.toDate(base);
        return base;
    },

    goodLists: function(f) {
        var i, n, nList, o;
        var nValid = f.valid();
        if (nValid < 3) return false;
        n = -1;
        for (i = 1; i < f.rowCount(); i++){
            o = f.value(i);
            if (o == null) continue;
            if (n < 0)
                n = o.size();
            else if (o.size() != n) {
                if (nValid < 20) return true;
                nList = f.property("listCategories").length;
                return (nList * nList < nValid * 2);
            }
        }
        return false;
    },

    isYearly: function(asNumeric) {
        var d;
        if (asNumeric.min() < 1600) return false;
        if (asNumeric.max() > 2100) return false;
        d = asNumeric.numProperty("granularity");
        return d != null && d - Math.floor(d) < 1e-6;
    }

});

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
}
V.auto_NumericScale.HALF_LOG = 3;

$.copy(V.auto_NumericScale, {

    makeDateScale: function(f, nice, padFraction, desiredTickCount) {
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
    },

    bestDateMultiple: function(unit, desiredDaysGap) {
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
    },

    makeLinearScale: function(f, nice, includeZeroTolerance, padFraction, desiredTickCount, forBinning) {
        var _i, a, a0, b, b0, bestDiff, choices, d, dCount, data, delta, deltaLog10, desiredDivCount, diff,
            granularDivs, granularity, high, low, padA, padB, rawDelta, transform, x;
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
        transform = f.strProperty("transform");
        granularity = f.numProperty("granularity");
        granularDivs = (b - a) / granularity;
        if ((forBinning || f.preferCategorical()) && granularDivs > desiredDivCount /
            2 && granularDivs < desiredDivCount * 2) {
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
    },

    makeGranularDivisions: function(min, max, granularity, nice) {
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
    },

    makeLogScale: function(f, nice, padFraction, includeZeroTolerance, desiredTickCount) {
        var add5, d, data, factor, high, low, n, tolerantHigh, x;
        var a = Math.log(f.min()) / Math.log(10);
        var b = Math.log(f.max()) / Math.log(10);
        var pad = Math.max(padFraction[0], padFraction[1]);
        a -= pad * (b - a);
        b += pad * (b - a);
        if (includeZeroTolerance > 0.5 && a == 0) a = -0.5;
        if (a > 0 && a / b <= includeZeroTolerance) a = 0;
        if (nice) {
            a = Math.floor(a);
            b = Math.ceil(b);
        }
        n = b - a + 1;
        add5 = n < desiredTickCount * 0.666;
        factor = n > desiredTickCount * 1.66 ? 100 : 10;
        d = new $.List();
        low = Math.pow(10, a);
        high = Math.pow(10, b);
        if (add5 && high / 2 > f.max()) high /= 2;
        x = Math.pow(10, Math.ceil(a));
        tolerantHigh = high * 1.001;
        while (x < tolerantHigh) {
            d.add(x);
            if (add5 && x * V.auto_NumericScale.HALF_LOG < tolerantHigh) d.add(x * V.auto_NumericScale.HALF_LOG);
            x *= factor;
        }
        data = d.toArray();
        return new V.auto_NumericScale("log", low, high, data, false);
    }

});

////////////////////// Data ////////////////////////////////////////////////////////////////////////////////////////////

V.Data = function() {};

$.copy(V.Data, {

    indexOf: function(v, d) {
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
    },

    join: function(items, inter) {
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
    },

    format: function(o, useGrouping) {
        if (o == null) return '?';
        if (typeof(o) == 'number') return V.Data.formatNumeric(o, useGrouping);
        return '' + o;
    },

    formatNumeric: function(d, useGrouping) {
        if (d == 0) return '0';
        if (Math.abs(d) <= 1e-6 || Math.abs(d) >= 1e8) return $.formatScientific(d);
        if (Math.abs((d - Math.round(d)) / d) < 1e-9) return $.formatInt(Math.round(d), useGrouping);
        return $.formatFixed(d, useGrouping);
    },

    asNumeric: function(c) {
        if (c == null) return null;
        if (c && c.asNumeric) return c.asNumeric();
        if (c && c.getTime) return c.getTime() / 86400000;
        var v = Number(c);
        if (!isNaN(v)) return v;
        return null;
    },

    sort: function(data) {
        $.sort(data, V.Data.compare)
    },

    compare: function(a, b) {
        return $.compare(a,b);
    },

    toDate: function(f, method) {
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
        result = V.Fields.makeColumnField(f.name, f.label, data);
        result.set("date", true);
        result.setNumeric();
        return result;
    },

    asDate: function(c) {
        if (c==null) return null;
        if (c.getTime) return c;
        if (typeof c == 'string') {d = $.parseDate(c); return d == null || isNaN(d.getTime()) ? null : d };
        if (!isNaN(c)) return new Date(c*86400000);
        return null;
    },

    toNumeric: function(f) {
        var data, i, o, result;
        if (f.isNumeric()) return f;
        data = $.Array(f.rowCount(), 0);
        for (i = 0; i < data.length; i++){
            o = f.value(i);
            data[i] = V.Data.asNumeric(o);
        }
        result = V.Fields.makeColumnField(f.name, f.label, data);
        result.setNumeric();
        return result;
    },

    toList: function(base) {
        var _i, _j, c, common, commonParts, f, i, items, n, o, parts, s, valid;
        var sep = ',';
        var nSep = -1;
        for(_i=$.iter([',', ';', '|']), s=_i.current; _i.hasNext(); s=_i.next()) {
            c = 0;
            for(_j=$.iter(base.categories()), o=_j.current; _j.hasNext(); o=_j.next())
                if (o.toString().indexOf(c) >= 0) c++;
            if (c > nSep) {
                sep = s;
                nSep = c;
            }
        }
        n = base.rowCount();
        commonParts = new $.Map();
        items = $.Array(n, null);
        for (i = 0; i < n; i++){
            o = base.value(i);
            if (o == null) continue;
            valid = new $.List();
            for(_i=$.iter(V.Data.split(o.toString(), sep)), s=_i.current; _i.hasNext(); s=_i.next()) {
                s = s.trim();
                if ($.len(s) > 0) {
                    common = commonParts.get(s);
                    if (common == null) {
                        common = s;
                        commonParts.put(common, s);
                    }
                    valid.add(common);
                }
            }
            items[i] = new V.util_ItemsList(valid.toArray());
        }
        f = V.Fields.makeColumnField(base.name, base.label, items);
        parts = commonParts.values();
        common = parts.toArray();
        $.sort(common);
        f.set("list", true);
        f.set("listCategories", common);
        return f;
    },

    split: function(text, sep) {
        return text.split(sep);
    },

    isQuoted: function(txt) {
        var c;
        if (txt == null || $.len(txt) < 2) return false;
        c = txt.charAt(0);
        return c == '"' || c == '\'' && txt.charAt($.len(txt) - 1) == c;
    },

    deQuote: function(s) {
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
    },

    quote: function(s) {
        var c, i, n, quoteChar, text;
        if (s == null) return "null";
        quoteChar = '\'';
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
    },

    order: function(c, ascending) {
        var v = [];
        for (var i=0; i<c.length; i++) v.push(i);
        v.sort(function(s,t) { var r= $.compare(c[s], c[t]); return ascending ? r : -r});
        return v;
    },

    toPrimitive: function(items) {
        return items;
    }

});

////////////////////// Informative /////////////////////////////////////////////////////////////////////////////////////

V.util_Informative = function() {
    this.info = new $.Map();
};

$.copy(V.util_Informative.prototype, {

    copyProperties: function(source) {
        var items = Array.prototype.slice.call(arguments, 1);
        var _i, s;
        for(_i=$.iter(items), s=_i.current; _i.hasNext(); s=_i.next())
            this.set(s, source.property(s));
    },

    copyAllProperties: function(other) {
        this.info.putAll(other.info);
    },

    numProperty: function(key) {
        return V.Data.asNumeric(this.property(key));
    },

    property: function(key) {
        return this.info.get(key);
    },

    strProperty: function(key) {
        var v = this.property(key);
        return v == null ? null : v.toString();
    },

    isProperty: function(key) {
        var v = this.property(key);
        return v != null && v;
    },

    set: function(key, value) {
        if (value == null)
            this.info.remove(key);
        else
            this.info.put(key, value);
    }

});

////////////////////// Dataset /////////////////////////////////////////////////////////////////////////////////////////


V.Dataset = function(fields) {
    var _i, f;
    $.superconstruct(this);
    this.fields = null;
    this.fieldByName = null;this.fields = V.Dataset.ensureUniqueNames(fields);
    this.fieldByName = new $.Map();
    for(_i=$.iter(fields), f=_i.current; _i.hasNext(); f=_i.next())
        this.fieldByName.put(f.name.toLowerCase(), f);
    for(_i=$.iter(fields), f=_i.current; _i.hasNext(); f=_i.next())
        this.fieldByName.put(f.name, f);
}
$.extend(V.Dataset, V.util_Informative);

$.copy(V.Dataset, {

    make: function(fields, autoConvert) {
        var augmented, i, len, selection;
        fields = V.Dataset.ensureUniqueNames(fields);
        augmented = $.Array(fields.length + 3, null);
        for (i = 0; i < fields.length; i++)
            augmented[i] = false == autoConvert ? fields[i] : V.auto_Auto.convert(fields[i]);
        len = fields.length == 0 ? 0 : fields[0].rowCount();
        augmented[fields.length] = V.Fields.makeConstantField("#count", "Count", 1.0, len);
        augmented[fields.length + 1] = V.Fields.makeIndexingField("#row", "Row", len);
        selection = V.Fields.makeConstantField("#selection", "Selection", "\u2717", len);
        augmented[fields.length + 2] = selection;
        return new V.Dataset(augmented);
    },

    ensureUniqueNames: function(fields) {
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
    }

});

$.copy(V.Dataset.prototype, {

    retainRows: function(keep) {
        var i;
        var results = $.Array(this.fields.length, null);
        for (i = 0; i < results.length; i++)
            results[i] = V.Fields.permute(this.fields[i], keep, false);
        return this.replaceFields(results);
    },

    transform: function(command) {
        return V.modify_Transform.transform(this, command);
    },

    removeSpecialFields: function() {
        var _i, f, fields1;
        var removed = new $.List();
        for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next())
            if (!$.startsWith(f.name, "#")) removed.add(f);
        fields1 = removed.toArray();
        return this.replaceFields(fields1);
    },

    addConstants: function(command) {
        return V.modify_AddConstantFields.transform(this, command);
    },

    expectedSize: function() {
        var _i, f;
        var total = this.fields.length * 56 + 56;
        for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next())
            total += f.expectedSize();
        return total;
    },

    field: function(name, lax) {
        var field = this.fieldByName.get(name);
        return (field != null || !lax) ? field : this.fieldByName.get(name.toLowerCase());
    },

    fieldArray: function() {
        var names = Array.prototype.slice.call(arguments, 0);
        var i;
        var ff = $.Array(names.length, null);
        for (i = 0; i < ff.length; i++)
            ff[i] = this.field(names[i], false);
        return ff;
    },

    filter: function(command) {
        return V.modify_Filter.transform(this, command);
    },

    each: function(command) {
        return V.modify_Each.transform(this, command);
    },

    replaceFields: function(fields) {
        var result = new V.Dataset(fields);
        result.copyAllProperties(this);
        return result;
    },

    rowCount: function() {
        return this.fields.length == 0 ? 0 : this.fields[0].rowCount();
    },

    name: function() {
        return this.strProperty("name");
    },

    reduce: function(command) {
        var _i, f, ff;
        var names = new $.Set();
        $.addAll(names, V.modify_DataOperation.strings(command, ';'));
        ff = new $.List();
        for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next()) {
            if ($.startsWith(f.name, "#") || names.contains(f.name)) ff.add(f);
        }
        return this.replaceFields(ff.toArray());
    },

    series: function(command) {
        return V.modify_ConvertSeries.transform(this, command);
    },

    sort: function(command) {
        return V.modify_Sort.transform(this, command, true);
    },

    sortRows: function(command) {
        return V.modify_Sort.transform(this, command, false);
    },

    stack: function(command) {
        return V.modify_Stack.transform(this, command);
    },

    summarize: function(command) {
        var dataset = V.modify_Summarize.transform(this, command);
        dataset.set("reduced", true);
        return dataset;
    },

    modifySelection: function(method, row, source) {
        var _i, expanded, i;
        var off = V.Field.VAL_UNSELECTED;
        var on = V.Field.VAL_SELECTED;
        var sel = this.field("#selection");
        var n = this.rowCount();
        for (i = 0; i < n; i++)
            sel.setValue(off, i);
        expanded = source.expandedOriginalRows(row);
        for(_i=$.iter(expanded), i=_i.current; _i.hasNext(); i=_i.next()) {
            if ((method == "sel") || (method == "add"))
                sel.setValue(on, i);
            else if (method == "sub")
                sel.setValue(off, i);
            else
                sel.setValue(sel.value(i) == on ? off : on, i);
        }
    },

    expandedOriginalRows: function(row) {
        var _i, compare, expanded, f, i, j, list, o, rowField, targetFields;
        var n = this.rowCount();
        var important = new $.Set();
        for(_i=$.iter(this.fields), f=_i.current; _i.hasNext(); f=_i.next())
            if (!f.isSynthetic() && f.property("summary") == null) important.add(f);
        targetFields = important.toArray();
        compare = new V.summary_FieldRowComparison(targetFields, null, false);
        rowField = this.field("#row");
        expanded = new $.Set();
        for (i = 0; i < n; i++)
            if (compare.compare(i, row) == 0) {
                o = rowField.value(i);
                if (o instanceof V.util_ItemsList) {
                    list = o;
                    for (j = 0; j < list.size(); j++)
                        expanded.add(list.get(j) - 1);
                } else if (o != null)
                    expanded.add(o - 1);
            }
        return expanded;
    }

});

////////////////////// Chord ///////////////////////////////////////////////////////////////////////////////////////////
//
//   A chord diagram shows sized links between two categorical fields.
//   This class takes the data in standard format and converts it into a form that D3 can use
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Chord = function(data, fieldA, fieldB, fieldSize) {
    var i, i1, i2, size;
    var a = data.field(fieldA);
    var b = data.field(fieldB);
    var s = data.field(fieldSize);
    var indices = new V.util_MapInt().index(a.categories()).index(b.categories());
    var N = indices.size();
    this.names = indices.getIndexedKeys();
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
}

$.copy(V.diagram_Chord, {

    make: function(data, fieldA, fieldB, fieldSize) {
        return new V.diagram_Chord(data, fieldA, fieldB, fieldSize);
    }

});

$.copy(V.diagram_Chord.prototype, {

    group: function(i) {
        return this.names[i];
    },

    index: function(from, to) {
        return this.idx[from][to];
    },

    matrix: function() {
        return this.mtx;
    }

});

////////////////////// Edge ////////////////////////////////////////////////////////////////////////////////////////////
//
//   A hierarchical edges with values named such that they can easily be used by D3
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Edge = function(a, b, row) {
    this.row = row;
    this.source = a;
    this.target = b;
    this.key = a.key + "--" + b.key;
}
////////////////////// Graph ///////////////////////////////////////////////////////////////////////////////////////////
//
//   A graph layout coordinates graphs and links
//   This class takes the data in standard format and converts it into a form that D3 can use
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Graph = function(nd, a, b) {
    var i, lks, n, o, s, t;
    var nodeByID = new $.Map();
    var nds = new $.List();
    for (i = 0; i < nd.rowCount(); i++){
        o = nd.value(i);
        if (o != null) {
            n = new V.diagram_Node(i, 1, o.toString(), null);
            n.key = o;
            nds.add(n);
            nodeByID.put(o, n);
        }
    }
    lks = new $.List();
    for (i = 0; i < a.rowCount(); i++){
        s = nodeByID.get(a.value(i));
        t = nodeByID.get(b.value(i));
        if (s != null && t != null) lks.add(new V.diagram_Edge(s, t, i));
    }
    this.nodes = nds.toArray();
    this.links = lks.toArray();
}

$.copy(V.diagram_Graph, {

    make: function(nodeData, nodeID, edgeData, fromField, toField) {
        var nodes = nodeData.field(nodeID);
        var a = edgeData.field(fromField);
        var b = edgeData.field(toField);
        return new V.diagram_Graph(nodes, a, b);
    }

});

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
    this.replaceCollections(this.root);
}

$.copy(V.diagram_Hierarchical, {

    makeByNestingFields: function(data, sizeField) {
        var fields = Array.prototype.slice.call(arguments, 2);
        return new V.diagram_Hierarchical(data, sizeField, fields);
    }

});

$.copy(V.diagram_Hierarchical.prototype, {

    makeInternalNode: function(label) {
        var node = new V.diagram_Node(null, 0, label, new $.List());
        node.temp = new $.Map();
        return node;
    },

    makeNodesUsingCollections: function(data, size, fields) {
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
    },

    replaceCollections: function(current, parentKey) {
        var _i, child;
        var array = current.children;
        if (array != null) {
            current.children = array.toArray();
            current.temp = null;
            current.key = parentKey == null ? current.innerNodeName : parentKey + "-" + current.innerNodeName;
            for(_i=$.iter(array), child=_i.current; _i.hasNext(); child=_i.next())
                this.replaceCollections(child, current.key);
        }
    },

    toFields: function(data, fieldNames) {
        var i;
        var fields = $.Array(fieldNames.length, null);
        for (i = 0; i < fields.length; i++)
            fields[i] = data.field(fieldNames[i]);
        return fields;
    }

});

////////////////////// Node ////////////////////////////////////////////////////////////////////////////////////////////
//
//   A  node with values named such that they can easily be used by D3 in hierarchies
//   This is also usd in Node/Edge layouts, but most of the fields are unused
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.diagram_Node = function(row, value, innerNodeName, children) {
    this.key = null;
    this.children = null;
    this.temp = null;this.row = row;
    this.value = value;
    this.innerNodeName = innerNodeName;
    this.children = children;
}
////////////////////// Field ///////////////////////////////////////////////////////////////////////////////////////////


V.Field = function(name, label, provider, base) {
    $.superconstruct(this);
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
        this.copyAllProperties(base);
    }
}
V.Field.VAL_SELECTED = "\u2713";
V.Field.VAL_UNSELECTED = "\u2717";

$.extend(V.Field, V.util_Informative);

$.copy(V.Field.prototype, {

    setValue: function(o, index) {
        this.provider = this.provider.setValue(o, index);
    },

    compareRows: function(a, b) {
        if (this.categoryOrder == null) {
            this.categoryOrder = new V.util_MapInt();
            if (this.preferCategorical())
                this.categoryOrder.index(this.categories());
        }
        return this.provider.compareRows(a, b, this.categoryOrder);
    },

    expectedSize: function() {
        return ($.len(this.label) + $.len(this.name)) * 2 + 84 + 24 + this.provider.expectedSize();
    },

    property: function(key) {
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
                if (this.isDate()) {
                    if (!this.calculatedNominal) this.makeNominalStats();
                    if (!this.calculatedNumeric) this.makeNumericStats();
                    this.makeDateStats();
                    o = V.Field.$super.property.call(this, key);
                } else {
                    this.calculatedDate = true;
                }
            }
        }
        return o;
    },

    setCategories: function(cats) {
        this.set("categories", cats);
        this.set("categoriesOrdered", true);
    },

    makeDateStats: function() {
        if (this.isNumeric()) V.stats_DateStats.populate(this);
        this.calculatedDate = true;
    },

    isNumeric: function() {
        return this.isProperty("numeric");
    },

    setNumeric: function() {
        this.set("numeric", true);
    },

    isDate: function() {
        return this.isProperty("date");
    },

    isBinned: function() {
        return this.isProperty("binned");
    },

    makeNumericStats: function() {
        if (this.provider != null) V.stats_NumericStats.populate(this);
        this.calculatedNumeric = true;
    },

    makeNominalStats: function() {
        if (this.provider != null) V.stats_NominalStats.populate(this);
        this.calculatedNominal = true;
    },

    categories: function() {
        return this.property("categories");
    },

    compareTo: function(o) {
        var p = V.Data.compare(this.name, o.name);
        if ($.startsWith(this.name, "#"))
            return $.startsWith(o.name, "#") ? p : 1;
        return $.startsWith(o.name, "#") ? -1 : p;
    },

    dropData: function() {
        return new V.Field(this.name, this.label, null, this);
    },

    max: function() {
        return this.property("max");
    },

    min: function() {
        return this.property("min");
    },

    isSynthetic: function() {
        return $.startsWith(this.name, "#");
    },

    preferCategorical: function() {
        return !this.isNumeric() || this.isBinned();
    },

    ordered: function() {
        return this.isNumeric() || (this.name == "#selection");
    },

    rename: function(name, label) {
        var field = new V.Field(name, label, this.provider);
        field.copyAllProperties(this);
        return field;
    },

    rowCount: function() {
        return this.provider != null ? this.provider.count() : this.numProperty("n");
    },

    toString: function() {
        return this.name;
    },

    uniqueValuesCount: function() {
        return Math.round(this.numProperty("unique"));
    },

    valid: function() {
        return this.property("valid");
    },

    value: function(index) {
        return this.provider.value(index);
    },

    valueFormatted: function(index) {
        return this.format(this.provider.value(index));
    },

    format: function(v) {
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
        if (this.isProperty("list"))
            return v.toString(this.property("dateFormat"));
        return v.toString();
    }

});

////////////////////// Fields //////////////////////////////////////////////////////////////////////////////////////////
//
//   Utilities for manipulating fields
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.Fields = function() {};

$.copy(V.Fields, {

    makeConstantField: function(name, label, o, len) {
        var field = new V.Field(name, label, new V.values_ConstantProvider(o, len));
        if (V.Data.asNumeric(o) != null) field.setNumeric();
        return field;
    },

    makeIndexingField: function(name, label, len) {
        var field = new V.Field(name, label, new V.values_RowProvider(len));
        field.setNumeric();
        return field;
    },

    makeColumnField: function(name, label, data) {
        return new V.Field(name, label, new V.values_ColumnProvider(data));
    },

    permute: function(field, order, onlyOrderChanged) {
        var f;
        if (field.provider instanceof V.values_ConstantProvider) {
            if (onlyOrderChanged)
                return field;
            else
                return V.Fields.makeConstantField(field.name, field.label, field.value(0), field.rowCount());
        }
        if (onlyOrderChanged)
            return new V.Field(field.name, field.label, new V.values_ReorderedProvider(field.provider, order), field);
        f = new V.Field(field.name, field.label, new V.values_ReorderedProvider(field.provider, order));
        V.Fields.copyBaseProperties(field, f);
        return f;
    },

    copyBaseProperties: function(source, target) {
        target.copyProperties(source, "numeric", "binned", "summary", "transform", "list", "listCategories",
            "date", "categoriesOrdered", "dateUnit", "dateFormat");
        if (source.isProperty("categoriesOrdered"))
            target.set("categories", source.property("categories"));
    }

});

////////////////////// ByteInput ///////////////////////////////////////////////////////////////////////////////////////
//
//   A class for writing bytes to
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.io_ByteInput = function(data) {
    this.p = null;this.data = data;
    this.p = 0;
}

$.copy(V.io_ByteInput.prototype, {

    readNumber: function() {
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
    },

    readDate: function() {
        return V.Data.asDate(this.readNumber());
    },

    readDouble: function() {
        var s = this.readString();
        return s == "NaN" ? Number.NaN : Number(s);
    },

    readString: function() {
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
    },

    readByte: function() {
        return this.data[this.p++];
    }

});

////////////////////// ByteOutput //////////////////////////////////////////////////////////////////////////////////////
//
//   A class for writing bytes to
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.io_ByteOutput = function() {
    this.out=[];
}

$.copy(V.io_ByteOutput.prototype, {

    addByte: function(b) {
        this.out.push(b); return this
    },

    addNumber: function(value) {
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
    },

    addDouble: function(value) {
        if (isNaN(value))
            this.addString("NaN");
        else
            this.addString(V.Data.formatNumeric(value, false));
    },

    addDate: function(date) {
        this.addNumber(V.Data.asNumeric(date));
    },

    addString: function(s) {
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
    },

    asBytes: function() {
        return this.out;
    }

});

////////////////////// CSV /////////////////////////////////////////////////////////////////////////////////////////////

V.io_CSV = function() {};

$.copy(V.io_CSV, {

    read: function(base) {
        var data = V.io_CSV.parse(base);
        return V.io_CSV.makeFields(data);
    },

    parse: function(data) {
        var c, i, result, row;
        var common = new $.Map();
        var lines = new $.List();
        var line = new $.List();
        var last = ' ';
        var inQuote = false;
        var wasQuoted = false;
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
                    if ($.isEmpty(line) && (building == null || $.len(building.trim()) == 0)) {
                        break;
                    }
                    line.add(V.io_CSV.saveMemory(building, common, wasQuoted));
                    lines.add(line);
                    if (fieldCount < 0)
                        fieldCount = line.size();
                    else if (fieldCount != line.size())
                        throw new $.Exception("Line " + lines.size() + " had " + line.size()
                            + " entries; expected " + fieldCount);
                    line = new $.List();
                    building = null;
                    wasQuoted = false;
                }
            } else if (c == '\"') {
                inQuote = true;
                wasQuoted = true;
                if (building == null) building = "";
            } else {
                if (c == separator) {
                    line.add(V.io_CSV.saveMemory(building, common, wasQuoted));
                    building = null;
                    wasQuoted = false;
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
    },

    findSeparator: function(data) {
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
    },

    saveMemory: function(s, common, wasQuoted) {
        var t;
        if (s == null) return null;
        if (!wasQuoted) s = s.trim();
        t = common.get(s);
        if (t == null) {
            common.put(s, s);
            return s;
        } else {
            return t;
        }
    },

    makeFields: function(data) {
        var column, i, j, name;
        var fields = $.Array(data[0].length, null);
        for (i = 0; i < fields.length; i++){
            column = $.Array(data.length - 1, null);
            for (j = 0; j < column.length; j++)
                column[j] = data[j + 1][i];
            name = data[0][i] == null ? "" : data[0][i].toString();
            fields[i] = V.Fields.makeColumnField(V.io_CSV.identifier(name), V.io_CSV.readable(name), column);
        }
        return fields;
    },

    identifier: function(text) {
        var c, d, i, last, result;
        var parenthesis = text.indexOf('(');
        if (parenthesis > 0)
            text = text.substring(0, parenthesis).trim();
        result = "";
        last = "X";
        for (i = 0; i < $.len(text); i++){
            c = text.substring(i, i + 1);
            if (V.io_CSV.isDigit(c)) {
                if (i == 0) result = "_";
                d = c;
            } else if ((c == "_") || V.io_CSV.isLower(c) || V.io_CSV.isUpper(c)) {
                if (result == "_") result = "";
                d = c;
            } else {
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
    },

    readable: function(text) {
        var i, lower, s, upper;
        var built = "";
        var last = " ";
        var lastLower = false;
        for (i = 0; i < $.len(text); i++){
            s = text.substring(i, i + 1);
            if (s == "_") s = " ";
            lower = V.io_CSV.isLower(s);
            upper = V.io_CSV.isUpper(s);
            if (s == " ") {
                if (!(last == " ")) built += s;
            } else if (lower) {
                if (last == " ")
                    built += s.toUpperCase();
                else
                    built += s;
            } else {
                if (lastLower && (upper || V.io_CSV.isDigit(s)))
                    built += " " + s;
                else
                    built += s;
            }
            lastLower = lower;
            last = s;
        }
        return built;
    },

    isDigit: function(c) {
        return "0123456789".indexOf(c) >= 0;
    },

    isLower: function(c) {
        return "abcdefghijklmnopqrstuvwxyz".indexOf(c) >= 0;
    },

    isUpper: function(c) {
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(c) >= 0;
    }

});

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

$.copy(V.io_Serialize, {

    serializeDataset: function(data) {
        var _i, f, s;
        data = data.removeSpecialFields();
        s = new V.io_ByteOutput();
        s.addByte(V.io_Serialize.VERSION).addNumber(V.io_Serialize.DATASET_VERSION_NUMBER);
        s.addByte(V.io_Serialize.DATA_SET).addNumber(data.fields.length);
        for(_i=$.iter(data.fields), f=_i.current; _i.hasNext(); f=_i.next())
            V.io_Serialize.addFieldToOutput(f, s);
        return s.asBytes();
    },

    serializeField: function(field) {
        var s = new V.io_ByteOutput();
        V.io_Serialize.addFieldToOutput(field, s);
        return s.asBytes();
    },

    addFieldToOutput: function(field, s) {
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
    },

    deserialize: function(data) {
        var d = new V.io_ByteInput(data);
        return V.io_Serialize.readFromByteInput(d);
    },

    readFromByteInput: function(d) {
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
            field = V.Fields.permute(V.Fields.makeColumnField(name, label, items), indices, false);
            if (b == V.io_Serialize.NUMBER || b == V.io_Serialize.DATE) field.setNumeric();
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
    }

});

////////////////////// DataOperation ///////////////////////////////////////////////////////////////////////////////////
//
//   Common code for Dataset transforms
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_DataOperation = function() {};

$.copy(V.modify_DataOperation, {

    map: function(command) {
        var _i, c;
        var result = new $.List();
        for(_i=$.iter(V.modify_DataOperation.strings(command, ';')), c=_i.current; _i.hasNext(); c=_i.next())
            result.add(V.modify_DataOperation.strings(c, '='));
        return result;
    },

    strings: function(items, sep) {
        var i;
        var parts = V.Data.split(items, sep);
        for (i = 0; i < parts.length; i++)
            parts[i] = parts[i].trim();
        return parts.length == 1 && $.isEmpty(parts[0]) ? $.Array(0, null) : parts;
    }

});

////////////////////// AddConstantFields ///////////////////////////////////////////////////////////////////////////////
//
//   This transform takes data and adds constant fields.
//   The fields are given in the command as semi-colon separated items, with quoted values used as strings,
//   The fields are named the same as their constants, so a constant field with value 4.3 is called '4.3'
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_AddConstantFields = function() {
    $.superconstruct(this);};

$.extend(V.modify_AddConstantFields, V.modify_DataOperation);

$.copy(V.modify_AddConstantFields, {

    transform: function(base, command) {
        var fields, i, name;
        var additional = V.modify_DataOperation.strings(command, ';');
        if (additional.length == 0) return base;
        fields = $.Array(base.fields.length + additional.length, null);
        for (i = 0; i < additional.length; i++){
            name = additional[i];
            if (V.Data.isQuoted(name))
                fields[i] = V.Fields.makeConstantField(name, V.Data.deQuote(name),
                    V.Data.deQuote(name), base.rowCount());
            else
                fields[i] = V.Fields.makeConstantField(name, name, V.Data.asNumeric(name), base.rowCount());
        }
        for (i = 0; i < base.fields.length; i++)
            fields[i + additional.length] = base.fields[i];
        return base.replaceFields(fields);
    }

});

////////////////////// AllCombinations /////////////////////////////////////////////////////////////////////////////////
//
//   Expands out a set of data so it has all combinations of the X and group fields.
//   This ensures that stacking liens and areas works, as they require that each one have
//   the full range of data.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.modify_AllCombinations = function(fields, xCount, groupCount) {
    this.fields = fields;
    this.xCount = xCount;
    this.keyLength = xCount + groupCount;
    this.index = $.Array(this.keyLength, 0);
    this.categories = this.makeFieldCategories();
}

$.copy(V.modify_AllCombinations.prototype, {

    makeFieldCategories: function() {
        var i, j, objects;
        var result = $.Array(this.keyLength, null);
        for (i = 0; i < this.keyLength; i++){
            objects = this.fields[i].categories();
            if (i < this.xCount)
                result[i] = objects;
            else if (i >= this.xCount) {
                result[i] = $.Array(objects.length, null);
                for (j = 0; j < objects.length; j++)
                    result[i][j] = objects[objects.length - 1 - j];
            }
        }
        return result;
    },

    make: function() {
        var built, i, matched, row;
        var rowOrder = V.modify_Stack.makeStackDataOrder(this.fields, this.keyLength, this.xCount);
        var dataIndex = 0;
        var rows = new $.List();
        while (true) {
            row = this.makeKeyRow();
            matched = false;
            while (this.matchesCurrent(row, rowOrder, dataIndex)) {
                matched = true;
                rows.add(this.makeRealRow(rowOrder[dataIndex++]));
            }
            if (!matched) rows.add(row);
            if (!this.nextIndex()) break;
        }
        built = $.Array(this.fields.length, null);
        for (i = 0; i < this.fields.length; i++){
            built[i] = V.Fields.makeColumnField(this.fields[i].name, this.fields[i].label, this.extractColumn(rows, i));
            V.Fields.copyBaseProperties(this.fields[i], built[i]);
        }
        return built;
    },

    extractColumn: function(rows, index) {
        var i;
        var result = $.Array(rows.size(), null);
        for (i = 0; i < result.length; i++)
            result[i] = rows.get(i)[index];
        return result;
    },

    makeKeyRow: function() {
        var i;
        var row = $.Array(this.fields.length, null);
        for (i = 0; i < this.keyLength; i++){
            row[i] = this.categories[i][this.index[i]];
        }
        return row;
    },

    makeRealRow: function(index) {
        var i;
        var n = this.fields.length;
        var row = $.Array(n, null);
        for (i = 0; i < this.fields.length; i++)
            row[i] = this.fields[i].value(index);
        return row;
    },

    matchesCurrent: function(row, dataRowOrder, dataIndex) {
        var dataRow, i;
        if (dataIndex >= dataRowOrder.length) return false;
        dataRow = dataRowOrder[dataIndex];
        for (i = 0; i < this.keyLength; i++)
            if (V.Data.compare(row[i], this.fields[i].value(dataRow)) != 0) return false;
        return true;
    },

    nextIndex: function() {
        var p;
        for (p = this.index.length - 1; p >= 0; p--){
            if (++this.index[p] < this.categories[p].length) return true;
            this.index[p] = 0;
        }
        return false;
    }

});

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
    $.superconstruct(this);};

$.extend(V.modify_ConvertSeries, V.modify_DataOperation);

$.copy(V.modify_ConvertSeries, {

    transform: function(base, commands) {
        var _i, data, f, fieldName, fields, i, items, j, nR, nY, otherFields, resultFields, sections, series,
            seriesIndexing, temp, values, valuesIndexing, y, yFields;
        if ($.isEmpty(commands)) return base;
        sections = V.modify_DataOperation.strings(commands, ';');
        yFields = V.modify_DataOperation.strings(sections[0], ',');
        nY = yFields.length;
        nR = base.rowCount();
        if (nY < 2) return base;
        items = sections.length < 2 ? "" : sections[1];
        otherFields = V.modify_ConvertSeries.addRequired(V.modify_DataOperation.strings(items, ','));
        y = $.Array(nY, null);
        for (i = 0; i < nY; i++)
            y[i] = base.field(yFields[i]);
        seriesIndexing = $.Array(nY * nR, 0);
        valuesIndexing = $.Array(nY * nR, 0);
        data = $.Array(nY * nR, null);
        for (i = 0; i < nY; i++)
            for (j = 0; j < nR; j++){
                seriesIndexing[i * nR + j] = i;
                valuesIndexing[i * nR + j] = j;
                data[i * nR + j] = y[i].value(j);
            }
        values = V.Fields.makeColumnField("#values", V.Data.join(yFields), data);
        V.Fields.copyBaseProperties(y[0], values);
        temp = V.Fields.makeColumnField("#series", "Series", yFields);
        series = V.Fields.permute(temp, seriesIndexing, false);
        series.setCategories(yFields);
        resultFields = new $.List();
        resultFields.add(series);
        resultFields.add(values);
        for(_i=$.iter(otherFields), fieldName=_i.current; _i.hasNext(); fieldName=_i.next()) {
            if ((fieldName == "#series") || (fieldName == "#values")) continue;
            f = base.field(fieldName);
            resultFields.add(V.Fields.permute(f, valuesIndexing, false));
        }
        fields = resultFields.toArray();
        return base.replaceFields(fields);
    },

    addRequired: function(list) {
        var result = new $.List();
        $.addAll(result, list);
        if (!result.contains("#row")) result.add("#row");
        if (!result.contains("#count")) result.add("#count");
        return result.toArray();
    }

});

////////////////////// Each ////////////////////////////////////////////////////////////////////////////////////////////
//
//   This transform takes data and multiplies rows
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_Each = function() {
    $.superconstruct(this);};

$.extend(V.modify_Each, V.modify_DataOperation);

$.copy(V.modify_Each, {

    transform: function(base, command) {
        var _i, f, s;
        for(_i=$.iter(V.modify_DataOperation.strings(command, ';')), s=_i.current; _i.hasNext(); s=_i.next()) {
            f = base.field(s);
            if (f.isProperty("list"))
                base = V.modify_Each.splitFieldValues(base, f);
        }
        return base;
    },

    splitFieldValues: function(base, target) {
        var data, f, i, idx, j, list, results;
        var nulls = new V.util_ItemsList($.Array(1, null));
        var index = new $.List();
        var splitValues = new $.List();
        for (i = 0; i < target.rowCount(); i++){
            list = target.value(i);
            if (list == null) list = nulls;
            for (j = 0; j < list.size(); j++){
                splitValues.add(list.get(i));
                index.add(i);
            }
        }
        idx = V.Data.toPrimitive(index.toArray());
        results = $.Array(base.fields.length, null);
        for (i = 0; i < results.length; i++){
            f = base.fields[i];
            if (f == target) {
                data = splitValues.toArray();
                results[i] = V.Fields.makeColumnField(f.name, f.label, data);
            } else {
                results[i] = V.Fields.permute(f, idx, false);
            }
        }
        return base.replaceFields(results);
    }

});

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
    $.superconstruct(this);};

$.extend(V.modify_Filter, V.modify_DataOperation);

$.copy(V.modify_Filter, {

    transform: function(base, command) {
        var c, field, i, keep, p, par, params, q, t, type;
        var commands = V.modify_DataOperation.strings(command, ';');
        var N = commands.length;
        if (N == 0) return base;
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
        return keep == null ? base : base.retainRows(keep);
    },

    getRankedObjects: function(field, p1, p2) {
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
    },

    getType: function(s) {
        if ($.startsWith(s, "!"))
            return -V.modify_Filter.getType(s.substring(1).trim());
        if (s == "valid") return 1;
        if (s == "is") return 2;
        if (s == "in") return 3;
        if (s == "ranked") return 4;
        throw new $.Exception("Cannot use filter command " + s);
    },

    getParams: function(s, categorical) {
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
    },

    makeRowsToKeep: function(field, type, params) {
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
    },

    matchAny: function(v, params) {
        var _i, p;
        for(_i=$.iter(params), p=_i.current; _i.hasNext(); p=_i.next())
            if (V.Data.compare(v, p) == 0) return true;
        return false;
    }

});

////////////////////// Sort ////////////////////////////////////////////////////////////////////////////////////////////
//
//   This transform sorts a dataset into an order determined bya  set of fields.
//   Each field can be defined as "increasing" or "decreasing" and the order of fields is important!
//   This class may sort categories of data if the data type allows it (i.e. it is categorical, not ordinal)
//   It will always sort the rows of the data (so a PATH element will use that order, for example)
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.modify_Sort = function() {
    $.superconstruct(this);};

$.extend(V.modify_Sort, V.modify_DataOperation);

$.copy(V.modify_Sort, {

    transform: function(base, command, sortCategories) {
        var ascending, dimensions, f, field, fields, i, newCategoryOrder, rowOrder;
        var sortFields = V.modify_DataOperation.strings(command, ';');
        if (sortFields.length == 0) return base;
        dimensions = V.modify_Sort.getFields(base, sortFields);
        ascending = V.modify_Sort.getAscending(dimensions, sortFields);
        rowOrder = new V.summary_FieldRowComparison(dimensions, ascending, true).makeSortedOrder();
        for (i = base.fields.length - 1; i >= 0; i--){
            f = base.fields[i];
            if (f.isBinned() && f.preferCategorical())
                rowOrder = V.modify_Sort.moveCatchAllToEnd(rowOrder, f);
        }
        fields = $.Array(base.fields.length, null);
        for (i = 0; i < fields.length; i++){
            field = base.fields[i];
            fields[i] = V.Fields.permute(field, rowOrder, true);
            if (!field.ordered() && sortCategories) {
                newCategoryOrder = V.modify_Sort.makeOrder(field, dimensions, ascending);
                fields[i].setCategories(newCategoryOrder);
            }
        }
        return base.replaceFields(fields);
    },

    makeOrder: function(field, dimensions, ascending) {
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
            summaries[i] = V.Fields.makeColumnField("", null, dimensionData[i]);
            if (dimensions[i].isNumeric()) summaries[i].setNumeric();
        }
        order = new V.summary_FieldRowComparison(summaries, ascending, true).makeSortedOrder();
        result = $.Array(n, null);
        for (i = 0; i < n; i++)
            result[i] = categories[order[i]];
        return result;
    },

    sum: function(field, rows) {
        var _i, i, v;
        var sum = 0;
        for(_i=$.iter(rows), i=_i.current; _i.hasNext(); i=_i.next()) {
            v = V.Data.asNumeric(field.value(i));
            if (v != null) sum += v;
        }
        return sum;
    },

    mode: function(field, rows) {
        var _i, i;
        var mode = null;
        var max = 0;
        var count = new V.util_MapInt();
        for(_i=$.iter(rows), i=_i.current; _i.hasNext(); i=_i.next())
            count.increment(field.value(i));
        return count.mode();
    },

    makeRowRanking: function(order, comparison) {
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
    },

    getFields: function(base, names) {
        var i, name;
        var fields = $.Array(names.length, null);
        for (i = 0; i < fields.length; i++){
            name = names[i].split(":")[0];
            fields[i] = base.field(name.trim());
            if (fields[i] == null)
                throw new $.Exception("Could not find field: " + name);
        }
        return fields;
    },

    getAscending: function(dimensions, names) {
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
            } else {
                ascending[i] = dimensions[i].isDate() || !dimensions[i].isNumeric();
            }
        }
        return ascending;
    },

    moveCatchAllToEnd: function(order, f) {
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
    },

    categoriesFromRanks: function(field, rowRanking) {
        var cats, i, idx, index, o, which;
        var n = field.rowCount();
        var categories = field.categories();
        var counts = field.property("categoryCounts");
        var means = $.Array(counts.length, 0);
        for (i = 0; i < means.length; i++)
            means[i] = i / 100.0 / means.length;
        index = new V.util_MapInt().index(categories);
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
    }

});

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
    $.superconstruct(this);};

$.extend(V.modify_Stack, V.modify_DataOperation);

$.copy(V.modify_Stack, {

    transform: function(base, command) {
        var aesthetics, allFields, fields, full, keyFields, p, x, yField;
        if ($.isEmpty(command)) return base;
        p = V.modify_DataOperation.strings(command, ';');
        yField = p[0];
        x = V.modify_DataOperation.strings(p[1], ',');
        aesthetics = V.modify_DataOperation.strings(p[2], ',');
        full = $.equalsIgnoreCase(p[3], "true");
        keyFields = V.modify_Stack.getFields(base.fields, x, aesthetics, [yField]);
        allFields = V.modify_Stack.makeStackOrderedFields(base, keyFields, x.length);
        if (full)
            allFields = new V.modify_AllCombinations(allFields, x.length, aesthetics.length).make();
        fields = V.modify_Stack.makeStackedValues(allFields, V.modify_Stack.getField(allFields,
            yField), V.modify_Stack.getFields(allFields, x), full);
        return base.replaceFields(fields);
    },

    getField: function(fields, name) {
        var _i, f;
        for(_i=$.iter(fields), f=_i.current; _i.hasNext(); f=_i.next())
            if ($.equals(f.name, name)) return f;
        throw new $.Exception("Could not find field: " + name);
    },

    getFields: function(fields) {
        var namesList = Array.prototype.slice.call(arguments, 1);
        var _i, _j, fName, field, s;
        var result = new $.List();
        var found = new $.Set();
        for(_i=$.iter(namesList), s=_i.current; _i.hasNext(); s=_i.next())
            for(_j=$.iter(s), fName=_j.current; _j.hasNext(); fName=_j.next()) {
                fName = fName.trim();
                if (!$.isEmpty(fName)) {
                    field = V.modify_Stack.getField(fields, fName);
                    if (found.add(field)) result.add(field);
                }
            }
        return result.toArray();
    },

    makeStackedValues: function(allFields, y, x, full) {
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
        fields[n] = V.Fields.makeColumnField(y.name + "$lower", y.label, bounds[0]);
        fields[n + 1] = V.Fields.makeColumnField(y.name + "$upper", y.label, bounds[1]);
        V.Fields.copyBaseProperties(y, fields[n]);
        V.Fields.copyBaseProperties(y, fields[n + 1]);
        $.sort(fields);
        return fields;
    },

    makeStackOrderedFields: function(base, keyFields, xFieldCount) {
        var i;
        var baseFields = V.modify_Stack.orderFields(base, keyFields);
        var rowOrder = V.modify_Stack.makeStackDataOrder(baseFields, keyFields.length, xFieldCount);
        var fields = $.Array(baseFields.length, null);
        for (i = 0; i < baseFields.length; i++)
            fields[i] = V.Fields.permute(baseFields[i], V.Data.toPrimitive(rowOrder), true);
        return fields;
    },

    makeStackDataOrder: function(fields, keyFieldCount, xFieldCount) {
        var ascending, comparison, i, j, valid;
        var items = new $.List();
        var n = fields[0].rowCount();
        for (i = 0; i < n; i++){
            valid = true;
            for (j = 0; j < keyFieldCount; j++)
                if (fields[j].value(i) == null) valid = false;
            if (valid) items.add(i);
        }
        ascending = $.Array(keyFieldCount, false);
        for (i = 0; i < ascending.length; i++)
            ascending[i] = i < xFieldCount;
        comparison = new V.summary_FieldRowComparison(fields, ascending, true);
        $.sort(items, comparison);
        return items.toArray();
    },

    orderFields: function(base, keyFields) {
        var _i, at, f, i;
        var baseFields = $.Array(base.fields.length, null);
        var used = new $.Set();
        for (i = 0; i < keyFields.length; i++){
            baseFields[i] = keyFields[i];
            used.add(keyFields[i]);
        }
        at = keyFields.length;
        for(_i=$.iter(base.fields), f=_i.current; _i.hasNext(); f=_i.next())
            if (!used.contains(f)) baseFields[at++] = f;
        return baseFields;
    }

});

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
    $.superconstruct(this);this.measures = measures;
    this.dimensions = dimensions;
    this.percentBase = percentBase;
    this.rowCount = rowCount;
    percentNeeded = false;
    for(_i=$.iter(measures), m=_i.current; _i.hasNext(); m=_i.next())
        if (m.isPercent()) percentNeeded = true;
    this.percentNeeded = percentNeeded;
}
$.extend(V.modify_Summarize, V.modify_DataOperation);

$.copy(V.modify_Summarize, {

    transform: function(base, command) {
        var _i, baseField, containsCount, containsRow, dimensions, fields, measureField,
            measures, op, operations, percentBase, s, values;
        if (base.rowCount() == 0) return base;
        operations = V.modify_DataOperation.map(command);
        if ($.isEmpty(operations)) return base;
        measures = new $.List();
        dimensions = new $.List();
        percentBase = new $.List();
        containsCount = false;
        containsRow = false;
        for(_i=$.iter(operations), op=_i.current; _i.hasNext(); op=_i.next()) {
            if (op[0] == "#count") containsCount = true;
            if (op[0] == "#row") containsRow = true;
            values = op[1].split(":");
            baseField = base.field(values[0].trim());
            if (values.length == 1) {
                dimensions.add(new V.summary_DimensionField(baseField, op[0]));
            } else if (values[1].trim() == "base") {
                dimensions.add(new V.summary_DimensionField(baseField, op[0]));
                percentBase.add(baseField);
            } else {
                measureField = new V.summary_MeasureField(baseField, op[0], values[1].trim());
                if (values.length > 2) {
                    measureField.option = values[2].trim();
                }
                measures.add(measureField);
            }
        }
        $.sort(measures);
        $.sort(dimensions);
        if (!containsCount)
            measures.add(new V.summary_MeasureField(base.field("#count"), "#count", "sum"));
        if (!containsRow)
            measures.add(new V.summary_MeasureField(base.field("#row"), "#row", "list"));
        s = new V.modify_Summarize(measures, dimensions, percentBase, base.rowCount());
        fields = s.make();
        return base.replaceFields(fields);
    }

});

$.copy(V.modify_Summarize.prototype, {

    make: function() {
        var dimData, f, fields, g, i, m, measureData, originalRow, percentSums, result, row, v, value, values;
        var dimensionFields = this.getFields(this.dimensions);
        var percentBaseFields = this.percentBase.toArray();
        var measureFields = this.getFields(this.measures);
        var dimComparison = new V.summary_FieldRowComparison(dimensionFields, null, false);
        var percentBaseComparison = new V.summary_FieldRowComparison(percentBaseFields, null, false);
        var group = $.Array(this.rowCount, 0);
        var groupCount = this.buildGroups(group, dimComparison);
        var percentGroup = this.percentNeeded ? $.Array(this.rowCount, 0) : null;
        var percentGroupCount = this.percentNeeded ? this.buildGroups(percentGroup, percentBaseComparison) : 0;
        var summaries = $.Array(groupCount, null);
        for (i = 0; i < summaries.length; i++)
            summaries[i] = new V.summary_SummaryValues(measureFields, percentBaseFields, dimensionFields);
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
                measureData[i][g] = values.get(i, m);
            }
        }
        fields = $.Array(dimData.length + measureData.length, null);
        for (i = 0; i < dimData.length; i++){
            f = this.dimensions.get(i);
            fields[i] = V.Fields.makeColumnField(f.rename, f.label(), dimData[i]);
            V.Fields.copyBaseProperties(f.field, fields[i]);
        }
        for (i = 0; i < measureData.length; i++){
            m = this.measures.get(i);
            result = V.Fields.makeColumnField(m.rename, m.label(), measureData[i]);
            this.setProperties(m.method, result, m.field);
            result.set("summary", m.method);
            if (m.field != null)
                result.set("originalLabel", m.field.label);
            fields[dimData.length + i] = result;
        }
        return fields;
    },

    setProperties: function(f, to, src) {
        if (f == "list") {
            to.copyProperties(src, "dateFormat");
            to.set("list", true);
        } else if ((f == "count") || (f == "percent") || (f == "valid") || (f == "unique"))
            to.setNumeric();
        else
            V.Fields.copyBaseProperties(src, to);
    },

    getFields: function(list) {
        var i;
        var result = $.Array(list.size(), null);
        for (i = 0; i < result.length; i++)
            result[i] = list.get(i).field;
        return result;
    },

    buildGroups: function(group, dimComparison) {
        var currentGroup, i, order;
        if ($.isEmpty(dimComparison)) return 1;
        order = dimComparison.makeSortedOrder();
        currentGroup = 0;
        for (i = 0; i < group.length; i++){
            if (i > 0 && dimComparison.compare(order[i], order[i - 1]) != 0) currentGroup++;
            group[order[i]] = currentGroup;
        }
        return currentGroup + 1;
    }

});

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
    $.superconstruct(this);};

$.extend(V.modify_Transform, V.modify_DataOperation);

$.copy(V.modify_Transform, {

    transform: function(base, command) {
        var _i, fields, i, o, op, operations;
        if (base.rowCount() == 0) return base;
        operations = V.modify_DataOperation.map(command);
        if ($.isEmpty(operations)) return base;
        fields = $.Array(base.fields.length, null);
        for (i = 0; i < fields.length; i++){
            op = null;
            for(_i=$.iter(operations), o=_i.current; _i.hasNext(); o=_i.next())
                if ($.equals(o[0], base.fields[i].name)) op = o[1];
            fields[i] = V.modify_Transform.modify(base.fields[i], op);
            fields[i].set("summary", op);
        }
        return base.replaceFields(fields);
    },

    makeKey: function(groupFields, index) {
        var _i, f;
        var key = "|";
        for(_i=$.iter(groupFields), f=_i.current; _i.hasNext(); f=_i.next())
            key += f.value(index) + "|";
        return key;
    },

    modify: function(field, operation) {
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
    },

    rank: function(f, ascending) {
        var i, q, result, rowP;
        var N = f.rowCount();
        var comparison = new V.summary_FieldRowComparison([f], [ascending], true);
        var order = comparison.makeSortedOrder();
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
        result = V.Fields.makeColumnField(f.name, f.label, ranks);
        result.setNumeric();
        return result;
    },

    bin: function(f, desiredBinCount) {
        var field = f.preferCategorical() ? V.modify_Transform.binCategorical(f, desiredBinCount)
            : V.modify_Transform.binNumeric(f, desiredBinCount);
        field.set("binned", true);
        return field;
    },

    binCategorical: function(f, desiredBinCount) {
        var categories, data, i, newNames, order;
        if (desiredBinCount < 1) desiredBinCount = 7;
        categories = f.categories();
        if (categories.length <= desiredBinCount) return f;
        order = V.Data.order(f.property("categoryCounts"), false);
        newNames = new $.Map();
        for (i = 0; i < order.length; i++)
            newNames.put(categories[order[i]], i < desiredBinCount - 1 ? categories[order[i]] : "\u2026");
        data = $.Array(f.rowCount(), null);
        for (i = 0; i < data.length; i++)
            data[i] = newNames.get(f.value(i));
        return V.Fields.makeColumnField(f.name, f.label, data);
    },

    binNumeric: function(f, desiredBinCount) {
        var scale = V.auto_Auto.makeNumericScale(f, true, [0, 0], 0.0, desiredBinCount + 1, true);
        var divisions = scale.divisions;
        var isDate = f.isDate();
        var dateFormat = isDate ? f.property("dateFormat") : null;
        var ranges = V.modify_Transform.makeBinRanges(divisions, dateFormat, scale.granular);
        var data = V.modify_Transform.binData(f, divisions, ranges);
        var result = V.Fields.makeColumnField(f.name, f.label, data);
        if (f.isDate()) result.set("date", true);
        result.setNumeric();
        result.set("categories", ranges);
        result.set("transform", f.property("transform"));
        return result;
    },

    makeBinRanges: function(divisions, dateFormat, nameByCenter) {
        var a, b, i;
        var ranges = $.Array(divisions.length - 1, null);
        for (i = 0; i < ranges.length; i++){
            a = divisions[i];
            b = divisions[i + 1];
            ranges[i] = (dateFormat == null) ? V.util_Range.makeNumeric(a, b, nameByCenter)
                : V.util_Range.makeDate(a, b, nameByCenter, dateFormat);
        }
        return ranges;
    },

    binData: function(f, divisions, ranges) {
        var d, i, n;
        var data = $.Array(f.rowCount(), null);
        for (i = 0; i < data.length; i++){
            d = V.Data.asNumeric(f.value(i));
            if (d == null) continue;
            n = V.Data.indexOf(d, divisions);
            data[i] = ranges[Math.min(n, ranges.length - 1)];
        }
        return data;
    }

});

////////////////////// DateStats ///////////////////////////////////////////////////////////////////////////////////////

V.stats_DateStats = function() {};

$.copy(V.stats_DateStats, {

    populate: function(f) {
        var factor, granularity, unit;
        var days = f.max() - f.min();
        if (days == 0) days = f.max();
        unit = V.stats_DateStats.getUnit(days);
        f.set("dateUnit", unit);
        granularity = f.numProperty("granularity");
        factor = Math.min(1.0, Math.sqrt(f.valid()) / 7);
        f.set("dateFormat", V.stats_DateStats.getFormat(unit, granularity * factor));
    },

    getUnit: function(days) {
        var _i, d;
        for(_i=$.iter(V.util_DateUnit.values()), d=_i.current; _i.hasNext(); d=_i.next()) {
            if (days > 3.5 * d.approxDaysPerUnit) return d;
            if (d == V.util_DateUnit.day && days >= 2.5 * d.approxDaysPerUnit) return d;
        }
        return V.util_DateUnit.second;
    },

    getFormat: function(unit, granularity) {
        if (granularity > 360) return V.util_DateFormat.Year;
        if (granularity > 13) return V.util_DateFormat.YearMonth;
        if (granularity > 0.9)
            return V.util_DateFormat.YearMonthDay;
        if (unit.ordinal() < V.util_DateUnit.hour.ordinal()) return V.util_DateFormat.DayHour;
        if (granularity > 0.9 / 24 / 60) return V.util_DateFormat.HourMin;
        return V.util_DateFormat.HourMinSec;
    },

    creates: function(key) {
        return ("dateUnit" == key) || ("dateFormat" == key);
    }

});

////////////////////// NominalStats ////////////////////////////////////////////////////////////////////////////////////

V.stats_NominalStats = function() {};

$.copy(V.stats_NominalStats, {

    populate: function(f) {
        var i, naturalOrder;
        var counts = new V.util_MapInt();
        var N = f.rowCount();
        for (i = 0; i < N; i++)
            counts.increment(f.value(i));
        f.set("n", N);
        f.set("unique", counts.size());
        f.set("valid", counts.getTotalCount());
        f.set("mode", counts.mode());
        if (f.isProperty("categoriesOrdered")) {
            naturalOrder = f.categories();
        } else {
            if (f.name == "#selection") {
                naturalOrder = [V.Field.VAL_UNSELECTED, V.Field.VAL_SELECTED];
            } else {
                naturalOrder = counts.sortedKeys();
            }
            f.set("categories", naturalOrder);
        }
        f.set("categoryCounts", counts.getCounts(naturalOrder));
    },

    creates: function(key) {
        return ("n" == key) || ("mode" == key) || ("unique" == key) || ("valid" == key)
            || ("categories" == key) || ("categoryCounts" == key);
    }

});

////////////////////// NumericStats ////////////////////////////////////////////////////////////////////////////////////

V.stats_NumericStats = function() {};

$.copy(V.stats_NumericStats, {

    populate: function(f) {
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
        if (n == 0) return;
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
    },

    moment: function(data, c, p, N) {
        var _i, element, sum;
        if (N <= 0) return Number.NaN;
        sum = 0.0;
        for(_i=$.iter(data), element=_i.current; _i.hasNext(); element=_i.next())
            sum += Math.pow(element - c, p);
        return sum / N;
    },

    av: function(v, index) {
        return (v[Math.floor(index)] + v[Math.ceil(index)]) / 2.0;
    },

    creates: function(key) {
        return ("validNumeric" == key) || ("mean" == key) || ("stddev" == key) || ("variance" == key) || ("skew" ==
            key) || ("kurtosis" == key) || ("min" == key) || ("max" == key) || ("q1" == key) || ("q3" ==
            key) || ("median" == key) || ("granularity" == key);
    }

});

////////////////////// DimensionField //////////////////////////////////////////////////////////////////////////////////


V.summary_DimensionField = function(field, rename) {
    this.field = field;
    this.rename = rename == null ? field.name : rename;
}

$.copy(V.summary_DimensionField.prototype, {

    compareTo: function(o) {
        return V.Data.compare(this.rename, o.rename);
    },

    getDateFormat: function() {
        return this.isDate() ? this.field.property("dateFormat") : null;
    },

    isDate: function() {
        return this.field != null && this.field.isDate();
    },

    label: function() {
        return this.field == null ? this.rename : this.field.label;
    }

});

////////////////////// FieldRowComparison //////////////////////////////////////////////////////////////////////////////
//
//   Details on how to compare rows
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.summary_FieldRowComparison = function(fields, ascending, rowsBreakTies) {
    this.fields = fields;
    this.ascending = ascending;
    this.rowsBreakTies = rowsBreakTies;
    this.n = ascending == null ? fields.length : ascending.length;
}

$.copy(V.summary_FieldRowComparison.prototype, {

    compare: function(a, b) {
        var i, n;
        for (i = 0; i < this.n; i++){
            n = this.fields[i].compareRows(a, b);
            if (n != 0)
                return this.ascending != null && !this.ascending[i] ? -n : n;
        }
        return this.rowsBreakTies ? (a - b) : 0;
    },

    isEmpty: function() {
        return this.fields.length == 0;
    },

    makeSortedOrder: function() {
        var i;
        var n = this.fields[0].rowCount();
        var items = $.Array(n, 0);
        for (i = 0; i < n; i++)
            items[i] = i;
        $.sort(items, this);
        return V.Data.toPrimitive(items);
    }

});

////////////////////// MeasureField ////////////////////////////////////////////////////////////////////////////////////


V.summary_MeasureField = function(field, rename, measureFunction) {
    this.option = null;
    this.fits = new $.Map();
    $.superconstruct(this, field, rename == null && field == null ? measureFunction : rename);
    if (field != null && (measureFunction == "mean") && !field.isNumeric())
        this.method = "mode";
    else
        this.method = measureFunction;
}
$.extend(V.summary_MeasureField, V.summary_DimensionField);

$.copy(V.summary_MeasureField.prototype, {

    getFit: function(groupFields, index) {
        return this.fits.get(V.modify_Transform.makeKey(groupFields, index));
    },

    setFit: function(groupFields, index, fit) {
        this.fits.put(V.modify_Transform.makeKey(groupFields, index), fit);
    },

    isPercent: function() {
        return this.method == "percent";
    },

    label: function() {
        var a, b;
        if (this.method == "list") return this.field.label;
        if (this.method == "count") return "Count";
        if (this.field == null || (this.field.name == "#count")) {
            if (this.method == "sum") return this.field.label;
            if (this.method == "percent") return "Percent";
        }
        a = this.method.substring(0, 1).toUpperCase();
        b = this.method.substring(1);
        return a + b + "(" + this.field.label + ")";
    }

});

////////////////////// Regression //////////////////////////////////////////////////////////////////////////////////////
//
//   Calculates a regression function
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.summary_Regression = function(y, x, rows) {
    var i, mx, my, sxx, sxy, xv, yv;
    this.m = null;
    this.b = null;
    var data = V.summary_Regression.asPairs(y, x, rows);
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
}

$.copy(V.summary_Regression, {

    mean: function(values) {
        var _i, value;
        var s = 0;
        for(_i=$.iter(values), value=_i.current; _i.hasNext(); value=_i.next())
            s += value;
        return s / values.length;
    },

    asPairs: function(y, x, rows) {
        var _i, i, n, order, xv, xx, yv, yy;
        var xList = new $.List();
        var yList = new $.List();
        for(_i=$.iter(rows), i=_i.current; _i.hasNext(); i=_i.next()) {
            xv = V.Data.asNumeric(x.value(i));
            yv = V.Data.asNumeric(y.value(i));
            if (xv != null && yv != null) {
                xList.add(xv);
                yList.add(yv);
            }
        }
        n = xList.size();
        order = V.Data.order(xList.toArray(), true);
        xx = $.Array(n, 0);
        yy = $.Array(n, 0);
        for (i = 0; i < n; i++){
            xx[i] = xList.get(order[i]);
            yy[i] = yList.get(order[i]);
        }
        return [yy, xx];
    }

});

$.copy(V.summary_Regression.prototype, {

    get: function(value) {
        return this.m == null || value == null ? null : this.m * V.Data.asNumeric(value) + this.b;
    }

});

////////////////////// Smooth //////////////////////////////////////////////////////////////////////////////////////////
//
//   Calculates a smooth fit function
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


V.summary_Smooth = function(y, x, windowPercent, rows) {
    var n, pairs;
    if (windowPercent == null) {
        n = V.auto_Auto.optimalBinCount(x);
        this.window = (x.max() - x.min()) / n;
    } else {
        this.window = (x.max() - x.min()) * windowPercent / 200;
    }
    pairs = V.summary_Regression.asPairs(y, x, rows);
    this.x = pairs[1];
    this.y = pairs[0];
    this.mean = y.numProperty("mean");
}

$.copy(V.summary_Smooth.prototype, {

    get: function(value) {
        var at = V.Data.asNumeric(value);
        if (at == null) return null;
        return this.eval(at, this.window);
    },

    eval: function(at, h) {
        var d, i, w;
        var low = this.search(at - h, this.x);
        var high = this.search(at + h, this.x);
        var sy = 0;
        var sw = 0;
        for (i = low; i <= high; i++){
            d = (this.x[i] - at) / h;
            w = 0.75 * (1 - d * d);
            if (w > 1e-5) {
                sw += w;
                sy += w * this.y[i];
            }
        }
        if (sw < 1e-4)
            return h < this.window * 10 ? this.eval(at, h * 2) : this.mean;
        return sy / sw;
    },

    search: function(at, x) {
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
    }

});

////////////////////// SummaryValues ///////////////////////////////////////////////////////////////////////////////////


V.summary_SummaryValues = function(fields, xFields, allDimensions) {
    var _i, _j, f, isGroup, x;
    this.rows = new $.List();
    this.percentSums = null;this.fields = fields;
    this.xFields = xFields;
    this.groupFields = new $.List();
    for(_i=$.iter(allDimensions), f=_i.current; _i.hasNext(); f=_i.next()) {
        isGroup = true;
        for(_j=$.iter(xFields), x=_j.current; _j.hasNext(); x=_j.next())
            if (x == f) isGroup = false;
        if (isGroup) this.groupFields.add(f);
    }
}

$.copy(V.summary_SummaryValues.prototype, {

    firstRow: function() {
        return this.rows.get(0);
    },

    get: function(fieldIndex, m) {
        var categories, data, displayCount, f, fit, i, index, mean, sum, windowPercent, x;
        var summary = m.method;
        if (summary == "count") return this.rows.size();
        x = this.xFields.length == 0 ? null : this.xFields[this.xFields.length - 1];
        index = this.rows.get(0);
        if (summary == "fit") {
            fit = m.getFit(this.groupFields, index);
            if (fit == null)
                fit = new V.summary_Regression(m.field, x, this.validForGroup(index));
            m.setFit(this.groupFields, index, fit);
            return fit.get(x.value(index));
        }
        if (summary == "smooth") {
            fit = m.getFit(this.groupFields, index);
            if (fit == null) {
                windowPercent = null;
                if (m.option != null)
                    windowPercent = Number(m.option);
                fit = new V.summary_Smooth(m.field, x, windowPercent, this.validForGroup(index));
            }
            m.setFit(this.groupFields, index, fit);
            return fit.get(x.value(this.rows.get(0)));
        }
        data = $.Array(this.rows.size(), null);
        for (i = 0; i < data.length; i++)
            data[i] = this.fields[fieldIndex].value(this.rows.get(i));
        f = V.Fields.makeColumnField("temp", null, data);
        mean = f.numProperty("mean");
        if (summary == "percent") {
            if (mean == null) return null;
            if ("overall" == m.option)
                sum = m.field.valid() * m.field.numProperty("mean");
            else
                sum = this.percentSums[fieldIndex];
            return sum > 0 ? 100 * mean * f.valid() / sum : null;
        }
        if (summary == "range")
            return this.makeRange(m, f, "min", "max");
        if (summary == "iqr")
            return this.makeRange(m, f, "q1", "q3");
        if (summary == "sum") {
            if (mean == null) return null;
            return mean * f.numProperty("valid");
        }
        if (summary == "list") {
            categories = new V.util_ItemsList(f.property("categories"));
            if (m.option != null) {
                displayCount = Number(m.option);
                categories.setDisplayCount(displayCount);
            }
            return categories;
        }
        return f.property(summary);
    },

    makeRange: function(m, f, a, b) {
        return V.util_Range.make(f.numProperty(a), f.numProperty(b), m.getDateFormat());
    },

    validForGroup: function(index) {
        var _i, f, i, valid;
        var list = new $.List();
        var n = this.fields[0].rowCount();
        for (i = 0; i < n; i++){
            valid = true;
            for(_i=$.iter(this.groupFields), f=_i.current; _i.hasNext(); f=_i.next())
                if (f.compareRows(index, i) != 0) valid = false;
            if (valid) list.add(i);
        }
        return list;
    }

});

////////////////////// DateFormat //////////////////////////////////////////////////////////////////////////////////////

V.util_DateFormat = function() {};

$.copy(V.util_DateFormat.prototype, {

    format: function(date) {
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
    }

});

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
}

$.copy(V.util_DateUnit, {

    floor: function(d, u, multiple) {
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
    },

    floorNumeric: function(value, multiple, offset) {
        return multiple * Math.floor((value - offset) / multiple) + offset;
    },

    increment: function(d, u, delta) {
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
    }

});

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


V.util_ItemsList = function(items) {
    this.displayCount = 12;this.items = items;
}

$.copy(V.util_ItemsList.prototype, {

    equals: function(obj) {
        return this == obj || obj instanceof V.util_ItemsList && this.compareTo(obj) == 0;
    },

    compareTo: function(o) {
        var d, i;
        var n = Math.min(this.size(), o.size());
        for (i = 0; i < n; i++){
            d = V.Data.compare(this.get(i), o.get(i));
            if (d != 0) return d;
        }
        return this.size() - o.size();
    },

    get: function(i) {
        return this.items[i];
    },

    size: function() {
        return this.items.length;
    },

    setDisplayCount: function(displayCount) {
        this.displayCount = displayCount;
    },

    toString: function(dateFormat) {
        var d, i, p, t, v;
        var s = "";
        var n = this.size();
        for (i = 0; i < n; i++){
            if (i > 0) s += ", ";
            if (i == this.displayCount - 1 && n > this.displayCount) return s + "\u2026";
            v = this.get(i);
            if (dateFormat != null) {
                t = dateFormat.format(V.Data.asDate(v));
                p = t.indexOf(',');
                if (p > 0) {
                    s += t.substring(0, p);
                    s += t.substring(p + 1);
                } else {
                    s += t;
                }
            } else {
                d = V.Data.asNumeric(v);
                if (d != null)
                    s += V.Data.formatNumeric(d, false);
                else
                    s += v.toString();
            }
        }
        return s;
    }

});

////////////////////// MapInt //////////////////////////////////////////////////////////////////////////////////////////
//
//   Associates items with integers
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

V.util_MapInt = function() {
    this.map = new $.Map();
    this.totalCount = null;
    this.maxCount = null;
};

$.copy(V.util_MapInt.prototype, {

    get: function(o) {
        var v = this.map.get(o);
        return v == null ? 0 : v;
    },

    getCounts: function(vals) {
        var i;
        var result = $.Array(vals.length, 0);
        for (i = 0; i < result.length; i++)
            result[i] = this.get(vals[i]);
        return result;
    },

    getIndexedKeys: function() {
        var _i, o;
        var results = $.Array(this.size(), null);
        for(_i=$.iter(this.map.keySet()), o=_i.current; _i.hasNext(); o=_i.next())
            results[this.map.get(o)] = o;
        return results;
    },

    increment: function(o) {
        var v;
        if (o != null) {
            v = this.get(o) + 1;
            this.map.put(o, v);
            this.totalCount++;
            this.maxCount = Math.max(this.maxCount, v);
        }
    },

    getTotalCount: function() {
        return this.totalCount;
    },

    mode: function() {
        var _i, array, list, s;
        if ($.isEmpty(this)) return null;
        list = new $.List();
        for(_i=$.iter(this.map.keySet()), s=_i.current; _i.hasNext(); s=_i.next())
            if (this.map.get(s) == this.maxCount) list.add(s);
        array = list.toArray();
        V.Data.sort(array);
        return array[(array.length - 1) / 2];
    },

    index: function(keys) {
        var _i, o;
        for(_i=$.iter(keys), o=_i.current; _i.hasNext(); o=_i.next())
            if (!this.map.containsKey(o)) {
                this.map.put(o, this.map.size());
            }
        return this;
    },

    isEmpty: function() {
        return $.isEmpty(this.map);
    },

    size: function() {
        return this.map.size();
    },

    sortedKeys: function() {
        var s = this.map.keySet();
        var array = s.toArray();
        V.Data.sort(array);
        return array;
    }

});

////////////////////// Range ///////////////////////////////////////////////////////////////////////////////////////////


V.util_Range = function(low, high, mid, name) {
    this.low = low;
    this.high = high;
    this.mid = mid;
    this.name = name;
}

$.copy(V.util_Range, {

    make: function(low, high, dateFormat) {
        if (low == null || high == null) return null;
        return dateFormat == null ? V.util_Range.makeNumeric(low, high, false)
            : V.util_Range.makeDate(low, high, false, dateFormat);
    },

    makeNumeric: function(low, high, nameAtMid) {
        var mid = (high + low) / 2;
        var name = nameAtMid ? V.Data.formatNumeric(mid, true) : V.Data.formatNumeric(low,
            true) + "\u2026" + V.Data.formatNumeric(high, true);
        return new V.util_Range(low, high, mid, name);
    },

    makeDate: function(low, high, nameAtMid, df) {
        var lowDate = V.Data.asDate(low);
        var highDate = V.Data.asDate(high);
        var midDate = V.Data.asDate((high + low) / 2);
        var name = nameAtMid ? df.format(midDate) : df.format(lowDate) + "\u2026" + df.format(highDate);
        return new V.util_Range(lowDate, highDate, midDate, name);
    }

});

$.copy(V.util_Range.prototype, {

    compareTo: function(o) {
        return V.Data.compare(this.asNumeric(), o.asNumeric());
    },

    asNumeric: function() {
        return (V.Data.asNumeric(this.low) + V.Data.asNumeric(this.high)) / 2.0;
    },

    extent: function() {
        return V.Data.asNumeric(this.high) - V.Data.asNumeric(this.low);
    },

    hashCode: function() {
        return this.name;
    },

    equals: function(obj) {
        return this == obj || obj instanceof V.util_Range && obj.low == this.low && obj.high == this.high;
    },

    toString: function() {
        return this.name;
    }

});

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
}

$.copy(V.values_ColumnProvider, {

    copy: function(base) {
        var i;
        var data = $.Array(base.count(), null);
        for (i = 0; i < data.length; i++)
            data[i] = base.value(i);
        return new V.values_ColumnProvider(data);
    }

});

$.copy(V.values_ColumnProvider.prototype, {

    count: function() {
        return this.column.length;
    },

    expectedSize: function() {
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
    },

    setValue: function(o, index) {
        this.column[index] = o;
        return this;
    },

    compareRows: function(a, b, categoryOrder) {
        var p = this.column[a];
        var q = this.column[b];
        if (p == q) return 0;
        if (p == null) return 1;
        if (q == null) return -1;
        if ($.isEmpty(categoryOrder))
            return V.Data.compare(p, q);
        else
            return categoryOrder.get(p) - categoryOrder.get(q);
    },

    value: function(index) {
        return this.column[index];
    }

});

////////////////////// ConstantProvider ////////////////////////////////////////////////////////////////////////////////


V.values_ConstantProvider = function(o, len) {
    this.o = o;
    this.len = len;
}

$.copy(V.values_ConstantProvider.prototype, {

    compareRows: function(a, b, categoryOrder) {
        return 0;
    },

    count: function() {
        return this.len;
    },

    expectedSize: function() {
        return 24;
    },

    setValue: function(o, index) {
        return V.values_ColumnProvider.copy(this).setValue(o, index);
    },

    value: function(index) {
        return this.o;
    }

});

////////////////////// ReorderedProvider ///////////////////////////////////////////////////////////////////////////////


V.values_ReorderedProvider = function(base, order) {
    var i, other;
    if (base instanceof V.values_ReorderedProvider) {
        other = base;
        this.base = other.base;
        this.order = $.Array(order.length, 0);
        for (i = 0; i < order.length; i++)
            this.order[i] = other.order[order[i]];
    } else {
        this.base = base;
        this.order = order;
    }
}

$.copy(V.values_ReorderedProvider.prototype, {

    compareRows: function(a, b, categoryOrder) {
        return this.base.compareRows(this.order[a], this.order[b], categoryOrder);
    },

    count: function() {
        return this.order.length;
    },

    expectedSize: function() {
        return 24 + this.order.length * 4 + this.base.expectedSize();
    },

    setValue: function(o, index) {
        return V.values_ColumnProvider.copy(this).setValue(o, index);
    },

    value: function(index) {
        return this.base.value(this.order[index]);
    }

});

////////////////////// RowProvider /////////////////////////////////////////////////////////////////////////////////////


V.values_RowProvider = function(len) {
    this.len = len;
}

$.copy(V.values_RowProvider.prototype, {

    count: function() {
        return this.len;
    },

    expectedSize: function() {
        return 24;
    },

    setValue: function(o, index) {
        return V.values_ColumnProvider.copy(this).setValue(o, index);
    },

    value: function(index) {
        return index + 1;
    },

    compareRows: function(a, b, categoryOrder) {
        return a - b;
    }

});

return V;
})();
