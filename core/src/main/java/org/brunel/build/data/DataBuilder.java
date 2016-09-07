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

package org.brunel.build.data;

import org.brunel.action.Param;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A class for manipulating and building data structures
 */
public class DataBuilder {

    private final DataModifier modifier;
    private final VisSingle vis;

    /**
     * Constructor
     *
     * @param vis the vis to build the data for
     */
    public DataBuilder(VisSingle vis) {
        this(vis, null);
    }

    /**
     * Constructor
     *
     * @param vis      the vis to build the data for
     * @param modifier a class that modifies the data parameters after they have been created (may be null)
     */
    public DataBuilder(VisSingle vis, DataModifier modifier) {
        this.vis = vis;
        this.modifier = modifier;
    }

    /**
     * This builds the data and reports the built data to the builder
     *
     * @return built dataset
     */
    public Dataset build() {

        String constantsCommand = makeConstantsCommand();
        String filterCommand = makeFilterCommands();
        String eachCommand = makeEachCommands();
        String binCommand = makeTransformCommands();
        String summaryCommand = buildSummaryCommands();
        String sortCommand = makeFieldCommands();
        String seriesYFields = makeSeriesCommand();
        String setRowCountCommand = makeSetRowCountCommand();
        String usedFields = required();

        DataTransformParameters params = new DataTransformParameters(constantsCommand,
                filterCommand, eachCommand, binCommand, summaryCommand, "", sortCommand, "", seriesYFields,
                setRowCountCommand, usedFields);

        // Call the engine to see if it has any special needs
        if (modifier != null) params = modifier.modifyParameters(params, vis);

        Dataset data = vis.getDataset();                                                // The data to use
        data = data.addConstants(params.constantsCommand);                              // add constant fields
        data = data.each(params.eachCommand);                                           // divide up fields into parts
        data = data.filter(params.filterCommand);                                       // filter data
        data = data.transform(params.transformCommand);                                 // bin, rank, ... on data
        data = data.summarize(params.summaryCommand);                                   // summarize data
        data = data.series(params.seriesCommand);                                       // convert series
        data = data.setRowCount(params.rowCountCommand);                                // set the number of rows
        data = data.sort(params.sortCommand);                                           // sort data
        data = data.sortRows(params.sortRowsCommand);                                   // sort rows only
        data = data.stack(params.stackCommand);                                         // stack data
        data.set("parameters", params);                                                 // Params used to build this
        return data;
    }

    /**
     * Utility to get the built data from a Vis
     *
     * @param vis target to get the built data from
     * @return transformed data set
     */
    public static Dataset getTransformedData(VisSingle vis) {
        return new DataBuilder(vis.makeCanonical()).build();
    }

    private int getParameterIntValue(Param param, int defaultValue) {
        if (param == null) return defaultValue;
        if (param.isField()) {
            // The parameter is a field, so we examine the modifier for the int value
            return getParameterIntValue(param.firstModifier(), defaultValue);
        } else {
            // The parameter is a value
            return (int) param.asDouble();
        }
    }

    String buildSummaryCommands() {
        Map<String, String> spec = new LinkedHashMap<>();

        // We must account for all of these except for the special fields series and values
        // These are handled later in the pipeline and need no changes right now
        Set<String> fields = new LinkedHashSet<>(Arrays.asList(vis.usedFields(false)));
        fields.remove("#series");
        fields.remove("#values");
        fields.remove("#all");

        // Add the summary measures
        for (Entry<Param, String> e : vis.fSummarize.entrySet()) {
            Param p = e.getKey();
            String name = p.asField();
            String measure = e.getValue();
            if (p.hasModifiers()) measure += ":" + p.firstModifier().asString();
            spec.put(name, name + ":" + measure);
            fields.remove(name);
        }

        // If #count is used (and not summarized) add it in as a sum
        if (fields.contains("#count")) {
            spec.put("#count", "#count:sum");
            fields.remove("#count");
        }

        // If we have nothing to summarize, we are done -- no responses mean no summarization needed
        if (spec.isEmpty()) return "";

        // X fields are used for the percentage bases unless they have been declared as summaries
        for (Param s : vis.fX) {
            String field = s.asField();
            if (fields.contains(field)) {
                spec.put(field, field + ":base");
                fields.remove(field);
            }
        }

        // Anything remaining is used as a simple factor
        for (String s : fields) spec.put(s, s);

        // Assemble into a string
        List<String> result = new ArrayList<>();
        for (Entry<String, String> e : spec.entrySet())
            result.add(e.getKey() + "=" + e.getValue());
        return Data.join(result, "; ");
    }

    private String getParameterFieldValue(Param param) {

        if (param != null && param.isField()) {
            // Usual case of a field specified
            return param.asField();
        } else {
            // Try Y fields then aesthetic fields
            if (vis.fY.size() == 1) {
                String s = vis.fY.get(0).asField();
                if (!s.startsWith("'") && !s.startsWith("#")) return s;       // If it's a real field
            }
            if (vis.aestheticFields().length > 0) return vis.aestheticFields()[0];
            return "#row";      // If all else fails
        }
    }

    private String makeConstantsCommand() {
        List<String> toAdd = new ArrayList<>();
        for (String f : vis.usedFields(false)) {
            if (!f.startsWith("#") && vis.getDataset().field(f) == null) {
                // Field does not exist -- assume it is a constant and add it
                toAdd.add(f);
            }
        }
        return Data.join(toAdd, "; ");
    }

    private String makeFieldCommands() {
        List<Param> params = vis.fSort;
        String[] commands = new String[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Param p = params.get(i);
            String s = p.asField();
            if (p.hasModifiers())
                commands[i] = s + ":" + p.firstModifier().asString();
            else
                commands[i] = s;
        }
        return Data.join(commands, "; ");
    }

    private String makeFilterCommands() {
        List<String> commands = new ArrayList<>();

        // All position fields must be valid for coordinate charts-- filter if not
        if (vis.tDiagram == null) {
            String[] pos = vis.positionFields();
            for (String s : pos) {
                Field f = vis.getDataset().field(s);
                if (f == null) continue;        // May have been added as a constant -- no need to filter
                if (f.numProperty("valid") < f.rowCount())
                    commands.add(s + " valid");
            }
        }

        for (Entry<Param, String> e : vis.fTransform.entrySet()) {
            String operation = e.getValue();
            Param key = e.getKey();
            String name = getParameterFieldValue(key);
            Field f = vis.getDataset().field(name);
            int N;
            if (f == null) {
                // The field must be a constant or created field -- get length from data set
                N = vis.getDataset().rowCount();
            } else {
                name = f.name;              // Make sure we use the canonical (not lax) name
                N = f.valid();              // And we can use the valid ones
            }

            if (name.equals("#row")) {
                // Invert 'top' and 'bottom' as row #1 is the top one, not the bottom
                if (operation.equals("top")) operation = "bottom";
                else if (operation.equals("bottom")) operation = "top";
            }

            int n = getParameterIntValue(key, 10);
            if (operation.equals("top"))
                commands.add(name + " ranked 1," + n);
            else if (operation.equals("bottom")) {
                commands.add(name + " ranked " + (N - n) + "," + N);
            } else if (operation.equals("inner")) {
                commands.add(name + " ranked " + n + "," + (N - n));
            } else if (operation.equals("outer")) {
                commands.add(name + " !ranked " + n + "," + (N - n));
            }
        }

        return Data.join(commands, "; ");
    }

    private String makeSetRowCountCommand() {

        String field = null;
        int count = 100;                                                    // Default to 100 rows

        for (Entry<Param, String> e : vis.fTransform.entrySet()) {
            if (!e.getValue().equals("rows")) continue;
            Param p = e.getKey();

            // We default to using the count field
            if (p.isField()) {
                field = p.asField();
                p = p.firstModifier();
            } else {
                field = "#count";
            }
            if (p != null) count = (int) p.asDouble();
        }

        return field == null ? "" : field + "," + count;
    }

    private String makeEachCommands() {
        List<String> commands = new ArrayList<>();
        for (Entry<Param, String> e : vis.fTransform.entrySet()) {
            String operation = e.getValue();
            Param key = e.getKey();
            String name = getParameterFieldValue(key);
            Field f = vis.getDataset().field(name);
            if (operation.equals("each"))
                commands.add(f.name);
        }
        return Data.join(commands, "; ");
    }

    private String makeSeriesCommand() {
        if (!needsSeries()) return "";
        /*
            The command is of the form:
                    y1, y2, y3; a1, a2
            Where the fields y1 ... are the fields to makes the series`
            and the additional fields a1... are ones required to be kept as-is.
            #series and #values are always generated, so need to retain them additionally
        */

        LinkedHashSet<String> keep = new LinkedHashSet<>();
        for (Param p : vis.fX) keep.add(p.asField());
        Collections.addAll(keep, vis.nonPositionFields());
        keep.remove("#series");
        keep.remove("#values");
        StringBuilder b = new StringBuilder();
        for (Param p : vis.fY) {
            if (b.length() > 0) b.append(",");
            b.append(p.asField());
        }
        b.append(";").append(Data.join(keep));
        return b.toString();
    }

    private boolean needsSeries() {
        for (String s : vis.usedFields(false))
            if (s.equals("#series") || s.equals("#values")) return true;
        return false;
    }

    /*
        Builds the command to transform fields without summarizing -- ranks, bin, inside and outside
        The commands look like this:
            salary=bin:10; age=rank; education=outside:90
     */
    private String makeTransformCommands() {
        if (vis.fTransform.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (Entry<Param, String> e : vis.fTransform.entrySet()) {
            Param p = e.getKey();
            String name = p.asField();
            String measure = e.getValue();
            if (measure.equals("bin") || measure.equals("rank")) {
                if (p.hasModifiers()) measure += ":" + p.firstModifier().asString();
                if (b.length() > 0) b.append("; ");
                b.append(name).append("=").append(measure);
            }
        }
        return b.toString();
    }

    private String required() {
        String[] fields = vis.usedFields(true);
        List<String> result = new ArrayList<>();
        Collections.addAll(result, fields);

        // ensure we always have #row and #count
        if (!result.contains("#row")) result.add("#row");
        if (!result.contains("#count")) result.add("#count");
        return Data.join(result, "; ");
    }
}
