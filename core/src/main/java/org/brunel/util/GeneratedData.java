package org.brunel.util;

import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

/**
 * Simple class so we can generate data sets simply
 */
public class GeneratedData {
    // The format for this is N , a , b , c ...
    // Where N is the length and a,b,c et.c are methods to generate data
    public static Dataset make(String description) {
        String[] parts = description.split("\\+");
        int N = Integer.parseInt(parts[0].trim());
        int M = parts.length - 1;
        Field[] fields = new Field[M];
        for (int i = 0; i < M; i++) {
            String what = parts[i + 1].trim();
            Object[] data = new Object[N];
            for (int r = 0; r < N; r++) {
                if (what.equals("row0")) data[r] = r;
                else if (what.equals("row")) data[r] = r + 1;
                else if (what.equals("random")) data[r] = Math.random();
                else data[i] = what;
            }
            fields[i] = Data.makeColumnField("" + (char) ('a' + i), "" + (char) ('A' + i), data);
        }
        return Dataset.make(fields);
    }
}
