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

package org.brunel.data.io;

import org.brunel.data.Data;
import org.brunel.translator.JSTranslation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Date;

/**
 * A class for writing bytes to
 */
class ByteOutput {

    @JSTranslation(ignore = true)
    private final ByteArrayOutputStream out;

    @JSTranslation(ignore = true)
    static final Charset ENCODING = Charset.forName("utf-8");

    @JSTranslation(js = {"this.out=[];"})
    ByteOutput() {
        out = new ByteArrayOutputStream();
    }

    @JSTranslation(js = {"this.out.push(b); return this"})
    public ByteOutput addByte(int b) {
        assert (b >= 0 && b <= 255);
        out.write(b);
        return this;
    }

    public ByteOutput addNumber(Number value) {
        /*
            Encoding rules decided by first byte
            0 - 252 -- this is the exact value
            253     -- next two bytes give an unsigned integer
            254     -- next 8 bytes give a double
            255     -- the value is null
         */

        // Handle nulls rapidly
        if (value == null) {
            addByte(255);
            return this;
        }

        double d = value.doubleValue();

        int e = (int) Math.floor(d);
        if (e == d && e >= 0 && e < 256 * 256) {
            // Check to see if we can encode  as a single byte
            if (e <= 252) return addByte(e);
            // Encode as two bytes
            addByte(253);
            addByte(e & 0xff);
            addByte((e >> 8) & 0xff);
            return this;
        }

        addByte(254);
        addDouble(value);
        return this;
    }

    private void addDouble(Number value) {
        if (Double.isNaN(value.doubleValue()))
            addString("NaN");
        else
            addString(Data.formatNumeric(value.doubleValue(), false));
    }

    public ByteOutput addDate(Date date) {
        addDouble(date == null ? null : Data.asNumeric(date));
        return this;
    }

    @JSTranslation(js = {
            "if (s==null) return this.addByte(3);    // null encoded is '03'",
            "for (var i = 0; i < s.length; i++) {",
            "  var c = s.charCodeAt(i);",
            "  if (c < 128)",
            "    this.addByte(c)",
            "  else if (c < 2048)",
            "    this.addByte((c >> 6) | 192).addByte((c & 63) | 128);",
            "  else",
            "    this.addByte((c >> 12) | 224).addByte(((c >> 6) & 63) | 128).addByte((c & 63) | 128);",
            "}",
            "return this.addByte(0);"
    })
    ByteOutput addString(String s) {
        // Encode a null as '3'
        if (s == null)
            out.write(3);
        else {
            for (byte i : s.getBytes(ENCODING)) out.write(i);
            out.write(0);
        }
        return this;
    }

    @JSTranslation(js = {"return this.out;"})
    byte[] asBytes() {
        return out.toByteArray();
    }
}
