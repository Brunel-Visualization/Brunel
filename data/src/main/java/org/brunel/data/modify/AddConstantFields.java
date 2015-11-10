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

package org.brunel.data.modify;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

/**
 * This transform takes data and adds constant fields.
 * The fields are given in the command as semi-colon separated items, with quoted values used as strings,
 * The fields are named the same as their constants, so a constant field with value 4.3 is called '4.3'
 */
public class AddConstantFields extends DataOperation {

    public static Dataset transform(Dataset base, String command) {
        String[] additional = parts(command);
        if (additional == null) return base;

        // This is the new set of fields
        Field[] fields = new Field[base.fields.length + additional.length];

        for (int i = 0; i < additional.length; i++) {
            // Quoted constants are text, unquoted are numeric
            String name = additional[i];
            if (Data.isQuoted(name))
                fields[i] = Data.makeConstantField(name, Data.deQuote(name), Data.deQuote(name), base.rowCount());
            else
                fields[i] = Data.makeConstantField(name, name, Data.asNumeric(name), base.rowCount());
        }

        // Add the old fields
        for (int i = 0; i < base.fields.length; i++)
            fields[i + additional.length] = base.fields[i];

        // And done

        return base.replaceFields(fields);
    }
}
