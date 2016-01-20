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

package org.brunel.build.chart;

import org.brunel.action.Param;
import org.brunel.build.util.ModelUtil;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.auto.Auto;
import org.brunel.model.VisSingle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Keep information on fields used for position within a visualization
 */
public class ChartCoordinates {

    public final Field[] allXFields, allYFields, allXClusterFields;
    public final String xTransform, yTransform;
    public final boolean xCategorical, yCategorical;

    private final Map<VisSingle, Field[]> x = new HashMap<VisSingle, Field[]>();
    private final Map<VisSingle, Field[]> y = new HashMap<VisSingle, Field[]>();

    public ChartCoordinates(VisSingle[] elements, Dataset[] elementData) {

        String xTransform = null, yTransform = null;                // If defined by the VisSingle

        ArrayList<Field> allX = new ArrayList<Field>();
        ArrayList<Field> allY = new ArrayList<Field>();
        ArrayList<Field> allCluster = new ArrayList<Field>();
        for (int i = 0; i < elements.length; i++) {
            Field[] visXFields = getXFields(elements[i], elementData[i]);
            Field[] visYFields = getYFields(elements[i], elementData[i]);
            if (xTransform == null) xTransform = getDefinedXTransform(elements[i]);
            if (yTransform == null) yTransform = getDefinedYTransform(elements[i]);
            x.put(elements[i], visXFields);
            y.put(elements[i], visYFields);
            if (visXFields.length > 0) allX.add(visXFields[0]);             // Only first X field (rest are clustered)
            if (visXFields.length > 1) allCluster.add(visXFields[1]);       // Add the clustered X field
            Collections.addAll(allY, visYFields);                           // All Y fields (used in ranges)
        }

        this.allXFields = allX.toArray(new Field[allX.size()]);
        this.allYFields = allY.toArray(new Field[allY.size()]);
        this.allXClusterFields = allCluster.toArray(new Field[allCluster.size()]);

        // Set ordinal / categorical and derive transforms (if not explicitly set above)
        this.xCategorical = ModelUtil.combinationIsCategorical(allXFields, true);
        this.yCategorical = ModelUtil.combinationIsCategorical(allYFields, true);

        if (xTransform == null)
            this.xTransform = xCategorical ? "linear" : chooseTransform(allXFields);
        else
            this.xTransform = xTransform;

        if (yTransform == null)
            this.yTransform = yCategorical ? "linear" : chooseTransform(allYFields);
        else
            this.yTransform = yTransform;
    }

    private Field[] getXFields(VisSingle vis, Dataset data) {
        Field[] result = new Field[vis.fX.size()];
        for (int i = 0; i < vis.fX.size(); i++)
            result[i] = data.field(vis.fX.get(i).asField());
        return result;
    }

    private Field[] getYFields(VisSingle vis, Dataset data) {
        if (vis.fRange != null) {
            // Range is a pair
            return new Field[]{data.field(vis.fRange[0].asField(data)),
                    data.field(vis.fRange[1].asField(data))};
        } else if (vis.fY.isEmpty()) {
            return new Field[0];
        } else if (vis.fY.size() > 1) {
            // Handle series
            if (vis.stacked)
                return new Field[]{data.field("#values$lower"), data.field("#values$upper")};
            else
                return new Field[]{data.field("#values")};
        }

        // We have a single Y field
        String s = vis.fY.get(0).asField();
        if (vis.stacked) {
            // Stacked has been handled by adding two new fields, so add them
            return new Field[]{data.field(s + "$lower"), data.field(s + "$upper")};
        } else {
            // Simple case, a single y field
            return new Field[]{data.field(s)};
        }
    }

    private String getDefinedXTransform(VisSingle v) {
        for (Param p : v.fX)
            if (p.isField() && p.hasModifiers()) return p.firstModifier().asString();
        return null;
    }

    private String getDefinedYTransform(VisSingle v) {
        for (Param p : v.fY)
            if (p.isField() && p.hasModifiers()) return p.firstModifier().asString();
        if (v.fRange != null) {
            if (v.fRange[0].isField() && v.fRange[0].hasModifiers()) return v.fRange[0].firstModifier().asString();
            if (v.fRange[1].isField() && v.fRange[1].hasModifiers()) return v.fRange[1].firstModifier().asString();
        }
        return null;
    }

    private String chooseTransform(Field[] fields) {
        if (fields.length == 0) return "linear";

        // Go for the transform that "does the most": log > root > linear
        String best = "linear";
        double min = Double.MAX_VALUE;
        for (Field f : fields) {
            if (f.min() == null) continue;
            Auto.setTransform(f);
            String s = f.stringProperty("transform");
            if ("log".equals(s)) best = "log";
            else if ("root".equals(s) && !best.equals("log")) best = "root";
            if (f.isNumeric())
                min = Math.min(min, f.min());
        }
        if ("log".equals(best) && min <= 0) return "linear";
        return best;
    }

    public Field[] getX(VisSingle vis) {
        return x.get(vis);
    }

    public Field[] getY(VisSingle vis) {
        return y.get(vis);
    }

}
