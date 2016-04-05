/*
 * Copyright (c) 2016 IBM Corporation and others.
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

package org.brunel.build.d3.element;

import org.brunel.build.d3.D3Util;
import org.brunel.build.info.ElementStructure;
import org.brunel.data.Field;

/**
 * This class builds the information needed to build locations for the shapes
 */
 class DefineLocations {
    static final String CLUSTER_SPACING = "0.75";       // Spacing between clusters

    private static String addClusterMultiplier(Field cluster) {
        if (isRange(cluster))
            return " + w1 * scale_inner(" + D3Util.writeCall(cluster) + ".mid)";
        else
            return " + w1 * scale_inner(" + D3Util.writeCall(cluster) + ")";
    }

    static boolean isRange(Field field) {
        if (field.isBinned() && field.isNumeric()) return true;
        String s = field.strProperty("summary");
        return s != null && (s.equals("iqr") || s.equals("range"));
    }

    static void setDependentLocations(ElementStructure structure, ElementDimensionDefinition dim, String dimName, Field[] keys, String scaleName) {
        // Use the keys to get the X and Y locations from other items
        if (keys.length == 1) {
            // One key gives the center
            dim.center = "function(d) { return " + scaleName + "(" + structure.keyedLocation(dimName, keys[0]) + ") }";
        } else {
            // Two keys give ends
            dim.left = "function(d) { return " + scaleName + "(" + structure.keyedLocation(dimName, keys[0]) + ") }";
            dim.right = "function(d) { return " + scaleName + "(" + structure.keyedLocation(dimName, keys[1]) + ") }";
            dim.center = "function() { return " + scaleName + "(0.5) }";        // Not sure what is best here -- should not get used
        }
    }

    static void setLocations(ElementStructure structure, ElementDimensionDefinition dim, String dimName, Field[] fields, Field[] keys, boolean categorical) {

        String scaleName = "scale_" + dimName;

        if (structure.isGraphEdge()) {
            // These are edges in a network layout
            dim.left = "function(d) { return d.source." + dimName + " }";
            dim.right = "function(d) { return d.target." + dimName + " }";
            return;
        }

        if (structure.dependent) {
            // Positions are dependent on other elements
            setDependentLocations(structure, dim, dimName, keys, scaleName);
            return;
        }

        if (fields.length == 0) {
            // There are no fields -- we have a notional [0,1] extent, so use the center of that
            dim.center = "function() { return " + scaleName + "(0.5) }";
            dim.left = "function() { return " + scaleName + "(0) }";
            dim.right = "function() { return " + scaleName + "(1) }";
            return;
        }

        Field main = fields[0];
        boolean numericBins = main.isBinned() && !categorical;

        // X axis only ever has one main field at most -- rest are clustered
        boolean oneMainField = fields.length == 1 || dimName.equals("x");

        if (oneMainField) {

            // If defined, this is the cluster field on the X dimension
            Field cluster = fields.length > 1 ? fields[1] : null;

            String dataFunction = D3Util.writeCall(main);          // A call to that field using the datum 'd'

            if (numericBins) {
                // A Binned value on a non-categorical axes
                if (cluster == null) {
                    dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ".mid) }";
                    dim.left = "function(d) { return " + scaleName + "(" + dataFunction + ".low) }";
                    dim.right = "function(d) { return " + scaleName + "(" + dataFunction + ".high) }";
                } else {
                    // Left of the cluster bar, right of the cluster bar, and distance along it
                    String L = scaleName + "(" + dataFunction + ".low)";
                    String R = scaleName + "(" + dataFunction + ".high)";
                    String D;
                    if (isRange(cluster))
                        D = "scale_inner(" + D3Util.writeCall(cluster) + ".mid)";
                    else
                        D = "scale_inner(" + D3Util.writeCall(cluster) + ")";

                    dim.center = "function(d) { var L=" + L + ", R=" + R + "; return (L+R)/2 + (L-R) * "
                            + CLUSTER_SPACING + " * " + D + " }";
                }
            } else if (isRange(main)) {
                // This is a range field, but we have not been asked to show both ends,
                // so we use the midpoint
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ".mid)";
                if (cluster != null) dim.center += addClusterMultiplier(cluster);
                dim.center += " }";
            } else {
                // Nothing unusual -- just define the center
                dim.center = "function(d) { return " + scaleName + "(" + dataFunction + ")";
                if (cluster != null) dim.center += addClusterMultiplier(cluster);
                dim.center += " }";
            }

        } else {
            // The dimension contains two fields: a range
            String lowDataFunc = D3Util.writeCall(main);          // A call to the low field using the datum 'd'
            String highDataFunc = D3Util.writeCall(fields[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(main)) lowDataFunc += ".low";
            if (isRange(fields[1])) highDataFunc += ".high";

            dim.left = "function(d) { return " + scaleName + "(" + lowDataFunc + ") }";
            dim.right = "function(d) { return " + scaleName + "(" + highDataFunc + ") }";
            dim.center = "function(d) { return " + scaleName + "( (" + highDataFunc + " + " + lowDataFunc + " )/2) }";
        }

    }
}
