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

package org.brunel.app;

import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Created by graham on 8/19/15.
 */
public class DataSetMemoryTaken {

    private static byte[] store;


    public static void main(String[] args) throws FileNotFoundException {
        // Load in the statics
        Dataset kill = read(args[0]);
        if (kill.rowCount() ==0) throw new IllegalStateException();
        kill = null;

        int initial = addTillDeath();
        System.out.println("With Nothing:\t" + initial +"k");
        Dataset d = read(args[0]);

        int withData = addTillDeath();
        System.out.println("With Data:\t" + withData +"k");
        System.out.println("Dataset:\t" + (initial-withData) +"k");

        if (d.fields.length ==0) throw new IllegalStateException();


    }

    private static Dataset read(String loc) throws FileNotFoundException {
        String text = new Scanner(new FileInputStream(loc)).useDelimiter("\\A").next();
        Dataset dataset = Dataset.make(CSV.read(text));
        System.out.println("Expected Size = " + dataset.expectedSize()/1024 + "k");
        return dataset;
    }

    private static int addTillDeath() {
        final int BLOCK = 1024;
        int max = 1;
        try {
            for (;;) {
                store = null;
                store = new byte[max*BLOCK];
                store[store.length/2] = 3;
                max++;
            }
        } catch (Throwable ignored) {
            // Expected
        }

        // needed to make sure the store isn't optimized away
        if (store != null && store[4] > 10000) throw new IllegalStateException();
        return max;
    }
}
