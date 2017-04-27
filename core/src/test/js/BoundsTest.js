/**
 * Test code to be run in a browser to check that bounds work well
 */

if (typeof d3 === 'undefined' && typeof require === 'function') d3 = require('d3');

// Detect if this is NOT a chromium browser we are in
var notChrome = window.navigator.userAgent.toLowerCase().indexOf("chrome") === -1;


function box(item) {

    try {
        var box = item.getBBox();
    } catch (e) {
        return null;
    }

    if (notChrome && item.tagName === 'use') {
        // Chrome adds the parent coordinate system into the mix
        box.x += +item.getAttribute("x");
        box.y += +item.getAttribute("y");
    }

    return box.width || box.height ? box : null;
}

function transformBox(box, item) {
    var m = item.getCTM();
    if (m && box)
        return {
            x: m.a * box.x + m.c * box.y + m.e,
            y: m.b * box.x + m.d * box.y + m.f,
            width: m.a * box.width + m.c * box.height,
            height: m.b * box.width + m.d * box.height
        };
    else
        return box;
}


function describeBox(x, y, width, height) {
    return "[x:" + x + ", y:" + y + ", width:" + width + ", height:" + height + "]";
}

function expectNull(id, target) {
    target = target || d3.select(id).node();
    var b = box(target);
    var t = transformBox(b, target);
    var baseResult = "[" + id + "] ";

    if (b == null && t == null) return baseResult + "BOTH PASS -- expected nulls";
    if (b != null) return baseResult + "FAIL -- BASE != NULL: " + describeBox(b.x, b.y, b.width, b.height);
    if (t != null) return baseResult + "FAIL -- TRAN != NULL: " + describeBox(t.x, t.y, t.width, t.height);
}

function sameRect(b, x, y, w, h) {
    return Math.abs(b.x - x) + Math.abs(b.y - y) + Math.abs(b.width - w) + Math.abs(b.height - h) < 0.001;
}
function expect(id, x, y, w, h, tx, ty, tw, th) {
    var target = d3.select(id).node();
    var baseResult = "[" + id + "] ";
    var b = box(target);
    if (b == null) return baseResult + "FAIL -- NULL returned; expected = " + describeBox(x, y, w, h);
    if (sameRect(b, x, y, w, h))
        baseResult += "BASE PASS";
    else
        baseResult += "BASE FAIL";

    baseResult += " expected = " + describeBox(x, y, w, h) +
        ", calculated = " + describeBox(b.x, b.y, b.width, b.height);

    baseResult += "<br/>[" + id + "] ";
    var t = transformBox(b, target);

    if (sameRect(t, tx, ty, tw, th))
        baseResult += "TRAN PASS";
    else
        baseResult += "TRAN FAIL";


    return baseResult + " expected = " + describeBox(tx, ty, tw, th) +
        ", calculated = " + describeBox(t.x, t.y, t.width, t.height);
}


function report() {

    var before = expect("#rd1", 20, 30, 50, 60, 20, 30, 50, 60);
    var itemkillme = d3.select("#rd1");
    var svgItem = itemkillme.node();
    itemkillme.remove();


    var results = [
        expect("#ra1", 20, 30, 50, 60, 20, 30, 50, 60),
        expectNull("#ra2"),
        expect("#ra3", 20, 30, 50, 60, 20, 30, 50, 60),

        expect("#rb1", 20, 30, 50, 60, 27, 39, 50, 60),
        expectNull("#rb2"),
        expect("#rb3", 20, 30, 50, 60, 27, 39, 50, 60),

        expect("#sa1", 92.5, 22.5, 25, 25, 92.5, 22.5, 25, 25),

        expect("#sb1", 92.5, 22.5, 25, 25, 95.5, 18.5, 25, 25),
        expectNull("#sb2"),
        expect("#sb3", 92.5, 22.5, 25, 25, 95.5, 18.5, 25, 25),

        expectNull("#rc1"),
        expectNull("#sc1"),

        before + " BEFORE REMOVAL",
        expectNull("#rd1", svgItem) + " AFTER REMOVAL",

        "-------"
    ];
    return "<ol><li>" + results.join("</li><li>") + "</li></ol>";
}
