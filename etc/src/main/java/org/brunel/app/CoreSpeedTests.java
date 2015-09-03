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

package org.brunel.app;

import org.brunel.action.Action;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.brunel.model.VisItem;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * A set of tests to investigate speed
 */
public class CoreSpeedTests {

    private static final Dataset DATA = Dataset.make(readResourceAsCSV("US States.csv"));

    private static final String COMMAND = "bar x(region) y(water) mean(water) + line x(region) y(under_18) mean(under_18) label('% under 18')" +
            " | bar x(region) yrange(income) range(income) + bar x(region) yrange(income) iqr(income) + " +
            "point x(region) y(income) median(income) style(\"fill:white\")";

    private static final Action ACTION = Action.parse(COMMAND);

    private static Field[] readResourceAsCSV(String resourceName) {
        InputStream stream = VisualTests.class.getResourceAsStream("/org/brunel/app/data-csv/" + resourceName);
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        String s = scanner.hasNext() ? scanner.next() : "";
        return CSV.read(s);
    }

    static class ActionParsing implements Callable<Integer> {
        private final int N;

        public ActionParsing(int calls) {
            this.N = calls;
        }

        public Integer call() throws Exception {
            for (int i = 0; i < N; i++) {
                Action a = Action.parse(COMMAND);
                if (a == null) throw new IllegalStateException();
            }
            return N;
        }
    }

    static class ActionApplying implements Callable<Integer> {
        private final int N;

        public ActionApplying(int calls) {
            this.N = calls;
        }

        public Integer call() throws Exception {
            int n = 0;
            for (int i = 0; i < N; i++) {
                VisItem v = ACTION.apply(DATA);
                n += v.children().length;
            }
            if (n != 2*N) throw new IllegalArgumentException("Wrong Count: " + n);
            return N;
        }
    }

    public static void main(String[] args) throws Exception {
        // Warm up parsing, then call it
        int parseCount = callsPerSecond(new ActionParsing(1000), 5);
        assert parseCount > 0;
        parseCount = callsPerSecond(new ActionParsing(10000), 15);

        // Currently around 28-34K
        System.out.println("Parse calls per second = " + parseCount);

        // Warm up apply, then call it
        int applyCount = callsPerSecond(new ActionApplying(1000), 5);
        assert applyCount > 0;
        applyCount = callsPerSecond(new ActionApplying(100000), 15);

        // Currently around 358K
        System.out.println("Apply calls per second = " + applyCount);

    }

    private static int callsPerSecond(Callable<Integer> callable, int repeats) throws Exception {
        double[] times = new double[repeats];
        for (int i = 0; i < repeats; i++) {
            long t1 = System.nanoTime();
            int iterations = callable.call();
            long t2 = System.nanoTime();
            times[i] = iterations * 1e9 / (t2 - t1);
        }

        // Return median
        Arrays.sort(times);
        return (int) times[(times.length - 1) / 2];
    }

}
