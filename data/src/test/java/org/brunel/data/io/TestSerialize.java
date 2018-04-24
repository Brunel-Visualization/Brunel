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

package org.brunel.data.io;

import org.brunel.data.CannedData;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.Fields;
import org.brunel.translator.JSTranslation;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests data serialization and deserialization
 */
public class TestSerialize {

  @JSTranslation(ignore = true)
  @Test
  public void testJavaSerialization() throws Exception {
    Dataset dataset = Dataset.make(CSV.read(CannedData.whiskey));

    Path f = Files.createTempFile("data", "ser");
    FileOutputStream fileOut = new FileOutputStream(f.toFile());
    ObjectOutputStream out = new ObjectOutputStream(fileOut);
    out.writeObject(dataset);
    out.close();
    fileOut.close();

    FileInputStream fileIn = new FileInputStream(f.toFile());
    ObjectInputStream in = new ObjectInputStream(fileIn);
    Dataset copy = (Dataset) in.readObject();
    in.close();
    fileIn.close();

    assertEquals(dataset.field("Rating").numProperty("mean"), copy.field("Rating").numProperty("mean"));
  }

  @Test
  public void testSerializeFieldBasics() {
    Field a = Fields.makeConstantField("foo", "bar", 10, 1000);
    assertEquals(true, a.isNumeric());
    byte[] bytes = Serialize.serializeField(a);
    Field b = (Field) Serialize.deserialize(bytes);
    assertEquals("foo", b.name);
    assertEquals("bar", b.label);
    assertEquals(1000, b.rowCount());
    assertEquals(true, b.isNumeric());
    assertEquals(10, b.min(), 1e-6);
  }

  @Test
  public void testSerializeFieldNumericData() {
    Field a = Fields.makeColumnField("a", "b", new Object[]{1.0, 2.0, null, 9.0});
    a = Data.toNumeric(a);

    byte[] bytes = Serialize.serializeField(a);
    Field b = (Field) Serialize.deserialize(bytes);
    assertEquals("a", b.name);
    assertEquals("b", b.label);
    assertEquals(4, b.rowCount());
    assertEquals(4.0, b.numProperty("mean"), 1e-6);
  }

  @Test
  public void testSerializeFieldDateData() {
    Date date1 = new Date();
    Date date2 = new Date(date1.getTime() + 86400000 * 12);
    Field a = Fields.makeColumnField("a", "b", new Object[]{date1, null, date2});
    a = Data.toDate(a);
    assertTrue(a.isNumeric());
    assertTrue(a.isDate());
    assertEquals(12, a.max() - a.min(), 1e-6);

    byte[] bytes = Serialize.serializeField(a);
    Field b = (Field) Serialize.deserialize(bytes);
    assertEquals(true, b.isNumeric());
    assertEquals(true, b.isDate());
    assertEquals(12, b.max() - b.min(), 1e-6);
  }

  @Test
  public void testWhiskeyDataset() {
    Dataset dataset = Dataset.make(CSV.read(CannedData.whiskey));
    byte[] bytes = Serialize.serializeDataset(dataset);
    assertEquals(2019, bytes.length);
    assertEquals(Serialize.VERSION, bytes[0]);

    Dataset d = (Dataset) Serialize.deserialize(bytes);
    assertEquals(dataset.rowCount(), d.rowCount());
    assertEquals(dataset.field("rating").numProperty("mean"), d.field("rating").numProperty("mean"), 0.001);
    assertEquals("Count", d.field("#count").label);
  }

  @Test
  public void testNoData() {
    String DATA = "a,b\n,";
    Dataset dataset = Dataset.make(CSV.read(DATA));
    byte[] bytes = Serialize.serializeDataset(dataset);
    assertEquals("6 1 1 2 2 97 0 65 0 1 4 3 1 0 2 98 0 66 0 1 4 3 1 0", dump(bytes));
  }

  @Test
  public void testNastyData() {
    String DATA = "a,b\n,\n\u00e9,1.23456789e-213\n\u2026,NaN";
    Dataset dataset = Dataset.make(CSV.read(DATA));
    byte[] bytes = Serialize.serializeDataset(dataset);
    assertEquals("6 1 1 2 2 97 0 65 0 3 4 3 195 169 0 226 128 166 0 3 0 1 2 2 98 0 " +
      "66 0 3 4 3 49 46 50 51 52 53 54 55 56 57 101 45 50 49 51 0 78 97 78 0 3 0 1 2", dump(bytes));
  }

  @Test
  public void testBankDataset() {
    Dataset dataset = Dataset.make(CSV.read(CannedData.bank));
    byte[] bytes = Serialize.serializeDataset(dataset);
    assertEquals("6 1 1 8 2 103 101 110 100 101 114 0 71 101 110 100 101 114 0 2 4 77 97 108 101 0 70 101 109 97 108 101 0 25 0 0 1 1 0 0 0 1 1 1 1 0 0 1 0 0 0 0 0 1 1 0 1 1 1 2 98 100 97 116 101 0 66 100 97 116 101 0 25 3 253 83 74 253 80 83 253 48 42 253 120 67 253 161 78 253 171 83 253 91 80 253 169 94 253 185 65 253 206 65 253 125 71 253 54 94 253 98 86 253 35 70 253 103 89 253 146 92 253 61 89 253 54 80 253 93 89 253 41 57 253 21 90 253 30 58 253 8 93 253 108 47 253 163 60 25 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 2 101 100 117 99 0 69 100 117 99 0 4 3 15 16 12 8 25 0 1 2 3 0 0 0 2 0 2 1 3 0 0 2 2 0 1 2 2 1 2 0 2 0 2 106 111 98 99 97 116 0 74 111 98 99 97 116 0 2 4 77 97 110 97 103 101 114 0 67 108 101 114 105 99 97 108 0 25 0 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 0 1 1 1 1 1 1 1 2 115 97 108 97 114 121 0 83 97 108 97 114 121 0 23 3 253 168 222 253 8 157 253 202 83 253 140 85 253 200 175 253 100 125 253 160 140 253 252 108 253 192 93 253 92 118 253 190 110 253 102 108 253 28 137 253 164 106 253 96 159 253 176 179 254 49 48 51 55 53 48 0 253 60 165 253 138 102 253 194 151 253 246 84 253 54 66 253 158 82 25 0 1 2 3 4 5 6 3 7 8 9 10 11 12 13 14 15 16 17 18 19 20 8 21 22 2 115 97 108 98 101 103 105 110 0 83 97 108 98 101 103 105 110 0 16 3 253 120 105 253 62 73 253 224 46 253 144 51 253 8 82 253 188 52 253 22 38 253 206 49 253 116 64 253 170 55 253 160 65 253 152 58 253 118 107 253 30 45 253 92 43 253 40 35 25 0 1 2 3 4 5 1 6 7 5 8 2 9 10 5 11 9 12 9 13 11 7 14 15 15 2 106 111 98 116 105 109 101 0 74 111 98 116 105 109 101 0 2 3 98 97 25 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 1 1 1 1 1 1 1 1 1 1 2 109 105 110 111 114 105 116 121 0 77 105 110 111 114 105 116 121 0 2 4 78 111 0 89 101 115 0 25 0 0 0 0 0 0 0 0 0 0 0 1 1 1 0 0 0 0 0 0 0 1 1 1 1", dump(bytes));
    assertEquals(Serialize.VERSION, bytes[0]);
  }

  @Test
  public void testEncodingsByte() {
    byte[] bytes;
    bytes = new ByteOutput().addByte(4).asBytes();
    assertEquals("4", dump(bytes));
    assertEquals(4, new ByteInput(bytes).readByte());
  }

  @Test
  public void testEncodingsInteger() {
    byte[] bytes;

    bytes = new ByteOutput().addNumber(null).asBytes();
    assertEquals("255", dump(bytes));
    assertEquals(null, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(0).asBytes();
    assertEquals("0", dump(bytes));
    assertEquals(0, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(4).asBytes();
    assertEquals("4", dump(bytes));
    assertEquals(4, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(200).asBytes();
    assertEquals("200", dump(bytes));
    assertEquals(200, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(267).asBytes();
    assertEquals("253 11 1", dump(bytes));
    assertEquals(267, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(32767).asBytes();
    assertEquals("253 255 127", dump(bytes));
    assertEquals(32767, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(32768).asBytes();
    assertEquals("253 0 128", dump(bytes));
    assertEquals(32768, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(-32767).asBytes();
    assertEquals("254 45 51 50 55 54 55 0", dump(bytes));
    assertEquals(-32767.0, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(-32768).asBytes();
    assertEquals("254 45 51 50 55 54 56 0", dump(bytes));
    assertEquals(-32768.0, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(65535 + 256).asBytes();
    assertEquals("254 54 53 55 57 49 0", dump(bytes));
    assertEquals(65535 + 256.0, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(-1).asBytes();
    assertEquals("254 45 49 0", dump(bytes));
    assertEquals(-1.0, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(-1024).asBytes();
    assertEquals("254 45 49 48 50 52 0", dump(bytes));
    assertEquals(-1024.0, new ByteInput(bytes).readNumber());
  }

  @Test
  public void testEncodingsString() {
    byte[] bytes;

    bytes = new ByteOutput().addString("foo\nis bar").asBytes();
    assertEquals("102 111 111 10 105 115 32 98 97 114 0", dump(bytes));
    assertEquals("foo\nis bar", new ByteInput(bytes).readString());

    bytes = new ByteOutput().addString("foo\nis \u2026").asBytes();
    assertEquals("102 111 111 10 105 115 32 226 128 166 0", dump(bytes));
    assertEquals("foo\nis \u2026", new ByteInput(bytes).readString());

    bytes = new ByteOutput().addString("").asBytes();
    assertEquals("0", dump(bytes));
    assertEquals("", new ByteInput(bytes).readString());

    bytes = new ByteOutput().addString(null).asBytes();
    assertEquals("3", dump(bytes));
    assertEquals(null, new ByteInput(bytes).readString());
  }

  @Test
  public void testEncodingsNumber() {
    byte[] bytes;

    bytes = new ByteOutput().addNumber(Double.NaN).asBytes();
    assertEquals("254 78 97 78 0", dump(bytes));
    assertTrue(Double.isNaN(new ByteInput(bytes).readNumber().doubleValue()));

    bytes = new ByteOutput().addNumber(null).asBytes();
    assertEquals("255", dump(bytes));
    assertEquals(null, new ByteInput(bytes).readNumber());

    bytes = new ByteOutput().addNumber(1.1).asBytes();
    assertEquals("254 49 46 49 0", dump(bytes));
    assertEquals(1.1, new ByteInput(bytes).readNumber().doubleValue(), 1e-6);

    bytes = new ByteOutput().addNumber(-1.1).asBytes();
    assertEquals("254 45 49 46 49 0", dump(bytes));
    assertEquals(-1.1, new ByteInput(bytes).readNumber().doubleValue(), 1e-6);

    bytes = new ByteOutput().addNumber(103750.0).asBytes();
    assertEquals("254 49 48 51 55 53 48 0", dump(bytes));
    assertEquals(103750.0, new ByteInput(bytes).readNumber().doubleValue(), 1e-6);

    bytes = new ByteOutput().addNumber(1.2e200).asBytes();
    assertEquals("254 49 46 50 101 43 50 48 48 0", dump(bytes));
    assertEquals(1.2e200, new ByteInput(bytes).readNumber().doubleValue(), 1e-6);

    bytes = new ByteOutput().addNumber(-1.2e200).asBytes();
    assertEquals("254 45 49 46 50 101 43 50 48 48 0", dump(bytes));
    assertEquals(-1.2e200, new ByteInput(bytes).readNumber().doubleValue(), 1e-6);
  }

  @Test
  public void testWrongVersion() {

    Dataset dataset = Dataset.make(CSV.read(CannedData.bank));
    byte[] bytes = Serialize.serializeDataset(dataset);
    bytes[1] = (byte) 0;
    try {
      Serialize.deserialize(bytes);
    } catch (IllegalStateException e) {
      assertTrue(true);
      return;
    }

    //Should not get here.  If we did, then this means the serialization exception was not thrown as expected.
    assertTrue(false);

  }

  private String dump(byte[] bytes) {
    String b = "";
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) {
        b += " ";
      }
      b += (bytes[i] & 0xff);
    }
    return b;
  }
}
