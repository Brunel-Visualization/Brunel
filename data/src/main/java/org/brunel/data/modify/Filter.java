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

package org.brunel.data.modify;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * This transform takes data and removes rows based on filter commands
 * Commands are one of the following:
 *
 * FIELD is a,b ...                -- one of those values
 * FIELD not a,b, ...              -- not one of those values
 * FIELD in a,b                    -- in that range of values (exactly two)
 * FIELD valid                     -- not null
 */
public class Filter extends DataOperation {

    /*
     * Commands are one of the following:
     *
     *      FIELD is a,b ...                -- one of those values                      [type 1]
     *      FIELD not a,b, ...              -- not one of those values                  [type 2]
     *      FIELD in a,b                    -- in that range of values (exactly two)    [type 3]
     *      FIELD valid                     -- not null                                 [type 4]
     */
    public static Dataset transform(Dataset base, String command) {
        // We may need to remove filtered info from the categories?
        if (!base.fields[0].hasProvider()) return base;

        String[] commands = parts(command);
        if (commands == null) return base;

        int N = commands.length;

        // Parse and assemble info for the commands

        Field[] field = new Field[N];
        int[] type = new int[N];
        Object[][] params = new Object[N][];

        for (int i = 0; i < N; i++) {
            String c = commands[i].trim();
            int p = c.indexOf(" ");
            int q = c.indexOf(" ", p + 1);
            if (q < 0) q = c.length();
            field[i] = base.field(c.substring(0, p).trim());
            type[i] = getType(c.substring(p, q).trim());
            params[i] = getParams(c.substring(q).trim(), field[i].preferCategorical());
        }

        // Returns null when indexing is the same as the whole data
        int[] keep = makeRowsToKeep(field, type, params);
        if (keep == null) return base;

        // Make the reduced fields and return them
        Field[] results = new Field[base.fields.length];
        for (int i = 0; i < results.length; i++)
            results[i] = Data.permute(base.fields[i], keep, false);
        return Data.replaceFields(base, results);

    }

    private static int getType(String s) {
        if (s.equals("is")) return 1;
        if (s.equals("not")) return 2;
        if (s.equals("valid")) return 3;
        if (s.equals("in")) return 4;
        throw new IllegalArgumentException("Cannot use filter command " + s);
    }

    private static Object[] getParams(String s, boolean categorical) {
        String[] parts = s.split(",");
        Object[] result = new Object[parts.length];
        for (int i = 0; i < result.length; i++) {
            if (categorical) result[i] = parts[i].trim();
            else result[i] = Data.asNumeric(parts[i].trim());
        }
        return result;
    }

    private static int[] makeRowsToKeep(Field[] field, int[] type, Object[][] params) {
        List<Integer> rows = new ArrayList<Integer>();
        int n = field[0].rowCount();
        for (int row = 0; row < n; row++) {
            boolean bad = false;
            for (int i = 0; i < field.length; i++) {
                Object v = field[i].value(row);
                int t = type[i];
                Object[] pars = params[i];
                if (t == 1)
                    bad = !matchAny(v, pars);
                else if (t == 2)
                    bad = matchAny(v, pars);
                else if (t == 3)
                    bad = (v == null);
                else
                    bad = Data.compare(v, pars[0]) < 0 || Data.compare(v, pars[1]) > 0;

                if (bad) break;             // Known to be bad
            }
            if (!bad) rows.add(row);
        }
        if (rows.size() == n) return null;          // No change needed
        int[] keep = new int[rows.size()];
        for (int i = 0; i < keep.length; i++) keep[i] = rows.get(i);
        return keep;
    }

    private static boolean matchAny(Object v, Object[] params) {
        for (Object p : params) if (Data.compare(v, p) == 0) return true;
        return false;
    }

}
