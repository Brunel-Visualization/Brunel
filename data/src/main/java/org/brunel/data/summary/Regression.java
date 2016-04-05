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

package org.brunel.data.summary;

import org.brunel.data.Field;

import java.util.List;

/**
 * Calculates a regression function
 */
public class Regression extends Fit {
    private final Double m, b;                             // Slope and intercept

    public Regression(Field fy, Field fx, List<Integer> rows) {
        super(fy, fx, rows);
        int n = x.length;
        double sxy = 0, sxx = 0;                           // sum of XY and XX values
        for (int i = 0; i < n; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
        }
        m = sxx == 0 ? 0 : sxy / sxx;                      // We have a slope
        b = my - m * mx;                                   // and intercept
    }


    public Object get(Object value) {
        if (m == null || value == null) return null;
        else return reverseY(m * vx(value) + b);
    }

}
