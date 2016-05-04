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

import org.brunel.geom.Point;

/**
 * A label point is a point that has labeling information
 */
public class LabelPoint extends Point {
    public final String label;                      // text for the location
    public final int pop;                           // 0 is smallest, 120ish the largest
    public final int importance;                    // 5-capital, 4-region capital, 3-local capital, 2-important, 1-other

    public static LabelPoint makeFromArray(String[] p) {
        return new LabelPoint(
                Double.parseDouble(p[2]),           // longitude
                Double.parseDouble(p[1]),           // latitude
                p[0],                               // label
                Integer.parseInt(p[3]),             // size
                Integer.parseInt(p[4])              // class
        );
    }

    private LabelPoint(double x, double y, String label, int pop, int importance) {
        super(x, y);
        this.label = label;
        this.pop = pop;
        this.importance = importance;
    }

    public String toString() {
        return label + "[" + importance + "," + pop + "]";
    }

    public int compareTo(Point p) {
        if (p instanceof LabelPoint) {
            LabelPoint o = (LabelPoint) p;
            int diff = o.rank() - rank();
            return diff != 0 ? diff : label.compareTo(o.label);
        } else {
            return super.compareTo(p);
        }

    }

    private int rank() {
        return pop + 7 * importance;
    }

}
