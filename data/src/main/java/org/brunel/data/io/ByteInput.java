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

import java.util.Date;

/**
 * A class for writing bytes to
 */
class ByteInput {

    private final byte[] data;
    private int p;

    public Number readNumber() {
        /*
            Encoding rules decided by first byte
            0 - 252 -- this is the exact value
            253     -- next two bytes give an unsigned integer
            254     -- next 8 bytes give a double
            255     -- the value is null
         */

        int a = readByte() & 0xff;
        if (a <= 252) {
            return a;
        } else if (a == 253) {
            int d3 = readByte() & 0xff;
            int d4 = readByte() & 0xff;
            return d3 + d4 * 256;
        } else if (a == 254) {
            return readDouble();
        } else if (a == 255) {
            return null;
        } else {
            throw new IllegalStateException("Serializing " + a);
        }

    }

    public Date readDate() {
        // millis since jan 1, 1970
        return Data.asDate(readNumber());
    }

    private Number readDouble() {
        String s = readString();
        return s.equals("NaN") ? Double.NaN : Double.parseDouble(s);
    }

    @JSTranslation(js = {
            "var i, len, c, d, char2, char3, out=''",
            "for(;;) {",
            "  c = this.readByte();",
            "  if (c == 3 && out == '') return null;    // 03 at start encodes a null",
            "  if (c == 0) return out;                  // 00 terminates the string",
            "  d = c >> 4;                              // handle the UTF-8 top nibble encoding",
            "  if (d<8) out += String.fromCharCode(c);  // One byte",
            "  else if (d == 12 || d == 13) {           // Two bytes",
            "    out += String.fromCharCode(((c & 0x1F) << 6) | (this.readByte() & 0x3F));",
            "  } else if (d == 14) {                    // Three bytes",
            "    var c2 = this.readByte(), c3 = this.readByte();",
            "    out += String.fromCharCode( ((c & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F) );",
            "  }",
            "}"
    })
    String readString() {
        if (data[p] == 3) {
            p++;
            return null;
        }
        int s = p;
        while (data[p] != 0) p++;
        return new String(data, s, (p++) - s, ByteOutput.ENCODING);
    }

    byte readByte() {
        return data[p++];
    }

    ByteInput(byte[] data) {
        this.data = data;
        this.p = 0;
    }
}
