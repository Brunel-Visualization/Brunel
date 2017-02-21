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

package org.brunel.build.element;

import org.brunel.build.util.BuildUtil;
import org.brunel.build.info.ElementStructure;
import org.brunel.data.Field;
import org.brunel.model.VisTypes;

/**
 * This class builds the information needed to build locations for the shapes
 */
class DefineLocations {
    static final String CLUSTER_SPACING = "0.8";       // Spacing between clusters

    private static String addClusterMultiplier(Field cluster, boolean widthIsFunc) {
        return " + "
                + (widthIsFunc ? "clusterWidth(d)" : "clusterWidth")    // The cluster width
                + " * scale_inner("                                     // times the inner scale position
                + BuildUtil.writeCall(cluster)                             // for the cluster field
                + (isRange(cluster) ? ".mid" : "")                      // taking the midpoint if necessary
                + ")";

    }

    static boolean isRange(Field field) {
        if (field.isBinned() && field.isNumeric()) return true;
        String s = field.strProperty("summary");
        return s != null && (s.equals("iqr") || s.equals("range") || s.equals("stderr"));
    }

    static void setDependentLocations(ElementStructure structure, ElementDetails details) {
        // The referenced locations will be added as a parameters to the object, so we need only access them

        // Geo elements do not need scaling -- the projection takes care of it
        boolean geo = structure.chart.geo != null;

        /* Example Dependent Geo:

                map('usa')
                + data('edges.csv') edge color(flights:blues) key(origin, dest) top(flights:500)
                + data('nodes.csv') x(long) y(lat) size(flights:400%) key(iata) tooltip(airport)

         */

        if (details.representation == ElementRepresentation.segment) {
            // Need four coordinates
            details.x.left = GeomAttribute.makeFunction(geo ? "this.r[0][0]" : "scale_x(this.r[0][0])");
            details.x.right = GeomAttribute.makeFunction(geo ? "this.r[1][0]" : "scale_x(this.r[1][0])");
            details.y.left = GeomAttribute.makeFunction(geo ? "this.r[0][1]" : "scale_y(this.r[0][1])");
            details.y.right = GeomAttribute.makeFunction(geo ? "this.r[1][1]" : "scale_y(this.r[1][1])");
        } else {
            details.x.center = GeomAttribute.makeFunction(geo ? "this.r[0][0]" : "scale_x(this.r[0][0])");
            details.y.center = GeomAttribute.makeFunction(geo ? "this.r[0][1]" : "scale_y(this.r[0][1])");
        }
    }

    static void setLocations(ElementRepresentation rep, ElementStructure structure, ElementDimension dim, String dimName, Field[] fields, boolean categorical) {
        String scaleName = "scale_" + dimName;

        // No need -- they have been defined already
        if (structure.isDependent()) return;

        if (fields.length == 0) {
            // There are no fields -- we have a notional [0,1] extent, so use the center of that
            if (rep == ElementRepresentation.rect) {
                dim.left = GeomAttribute.makeConstant(scaleName + ".range()[0]");
                dim.right = GeomAttribute.makeConstant(scaleName + ".range()[1]");
            } else {
                dim.center = GeomAttribute.makeConstant(scaleName + "(0.5)");
            }
            return;
        }

        Field main = fields[0];
        boolean useRangesFromBins = main.isBinned() && !categorical;

        // For the x axis, only rectangles use the ranges
        if (dimName.equals("x") && !rep.isBoxlike()) useRangesFromBins = false;

        if (defineForTwoFields(rep, dimName, fields)) {
            // The dimension contains two fields: a range
            String lowDataFunc = BuildUtil.writeCall(main);          // A call to the low field using the datum 'd'
            String highDataFunc = BuildUtil.writeCall(fields[1]);         // A call to the high field using the datum 'd'

            // When one of the fields is a range, use the outermost value of that
            if (isRange(main)) lowDataFunc += ".low";
            if (isRange(fields[1])) highDataFunc += ".high";

            dim.left = GeomAttribute.makeFunction(scaleName + "(" + lowDataFunc + ")");
            dim.right = GeomAttribute.makeFunction(scaleName + "(" + highDataFunc + ")");
            dim.center = GeomAttribute.makeFunction(scaleName + "( (" + highDataFunc + " + " + lowDataFunc + " )/2)");
        } else {

            // If defined, this is the cluster field on the X dimension
            Field cluster = dimName.equals("x") && structure.isClustered() ? fields[1] : null;

            String dataFunction = BuildUtil.writeCall(main);          // A call to that field using the datum 'd'

            if (useRangesFromBins) {
                // A Binned value on a non-categorical axes
                if (cluster == null) {
                    dim.center = GeomAttribute.makeFunction(scaleName + "(" + dataFunction + ".mid)");
                    dim.left = GeomAttribute.makeFunction(scaleName + "(" + dataFunction + ".low)");
                    dim.right = GeomAttribute.makeFunction(scaleName + "(" + dataFunction + ".high)");
                } else {
                    // Left of the cluster bar, right of the cluster bar, and distance along it
                    String L = scaleName + "(" + dataFunction + ".low)";
                    String R = scaleName + "(" + dataFunction + ".high)";
                    String D;
                    if (isRange(cluster))
                        D = "scale_inner(" + BuildUtil.writeCall(cluster) + ".mid)";
                    else
                        D = "scale_inner(" + BuildUtil.writeCall(cluster) + ")";

                    dim.center = GeomAttribute.makeFunction("BrunelD3.interpolate(" + L + ", " + R + ", " + CLUSTER_SPACING + " * " + D + ")");
                }
            } else if (isRange(main)) {
                // This is a range field, but we have not been asked to show both ends,
                // so we use the midpoint
                String def = scaleName + "(" + dataFunction + ".mid)";
                if (cluster != null) def += addClusterMultiplier(cluster, dim.clusterSize.isFunc());
                dim.center = GeomAttribute.makeFunction(def);
            } else if (structure.vis.tElement == VisTypes.Element.bar && dimName.equals("y")) {
                // // Bars implicitly drop from top to zero point
                dim.right = GeomAttribute.makeFunction(scaleName + "(" + dataFunction + ")");
                dim.left = GeomAttribute.makeConstant(scaleName + ".range()[0]");
            } else {
                // Nothing unusual -- just define the center
                String def = scaleName + "(" + dataFunction + ")";
                if (cluster != null) def += addClusterMultiplier(cluster, dim.clusterSize.isFunc());
                dim.center = GeomAttribute.makeFunction(def);
            }

        }

    }

    private static boolean defineForTwoFields(ElementRepresentation rep, String dimName, Field[] fields) {
        if (fields.length < 2) return false;                                        // Need two fields
        return dimName.equals("y") || rep == ElementRepresentation.segment;         // Edges or y dimensions need two
    }
}
