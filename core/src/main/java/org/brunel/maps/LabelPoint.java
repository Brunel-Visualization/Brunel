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

package org.brunel.maps;

/**
 * A label point is a point that has labeling information
 * Sortign orders them by rank
 */
public class LabelPoint extends Point {
    public final String label;                      // text for the location
    public final int rank;                          // 0 is most important, 8 is least
    public final int size;                          // 1 is smallest, 14 the largest

    public static LabelPoint parse(String line) {
        String[] p = line.split("\\|");
        return new LabelPoint(
                Double.parseDouble(p[1]),           // longitude is second
                Double.parseDouble(p[0]),           // latitude is first
                p[4],                               // name last
                Integer.parseInt(p[2]),             // rank
                Integer.parseInt(p[3])              // size
        );
    }

    public LabelPoint(double x, double y, String label, int rank, int size) {
        super(x, y);
        this.label = label;
        this.rank = rank;
        this.size = size;
    }
}
