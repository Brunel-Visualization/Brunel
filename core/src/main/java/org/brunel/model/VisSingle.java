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

package org.brunel.model;

import org.brunel.action.Param;
import org.brunel.build.util.DataCache;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.style.StyleFactory;
import org.brunel.model.style.StyleSheet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* A single item -- an "element" in grammar terms */
public class VisSingle extends VisItem implements Cloneable {

    public StyleSheet styles;              // Specific styles for this vis (null is the default)
    public Param[] bounds;                 // If defined, bounds
    public VisTypes.Coordinates coords;    // Coordinate util
    public List<Param> fColor, fSize, fOpacity;  // Aesthetics
    public List<Param> fFilter;            // Fields for filtering
    public List<Param> fSort;              // Fields used to sort the data
    public List<Param> fSplits;            // "Split" Aesthetics
    public List<Param> fX;                 // X dimension and then cluster dimensions
    public List<Param> fY;                 // Y Coordinates, each element defines a series
    public Map<Param, String> fTransform;  // Fields to transform(bin, rank, jitter, etc.) with method
    public Map<Param, String> fSummarize;  // Fields to summarize, with method
    public Param[] fRange;                 // If the element is a range, this is it
    public List<Param> itemsLabel;         // Items to concatenate as the labels
    public List<Param> itemsTooltip;       // Items to use as tooltips
    public boolean stacked;                // If true, stack the shapes
    public VisTypes.Axes tAxes;            // Which axes to display
    public VisTypes.Using tUsing;          // Any element modifications?
    public VisTypes.Diagram tDiagram;      // If defined, the layout to use instead of dimensions and axes
    public Param[] tDiagramParameters;     // If defined diagram parameters
    public VisTypes.Element tElement;      // Element util (bar, line, point, ...)
    public VisTypes.Legends tLegends;      // Which legends to display (when aesthetic present)
    public boolean flipX;
    public boolean flipY;                  // reverse the X or y scale
    public List<Param> fKeys;              // Fields used as fKeys
    public List<String> fData;             // Data sets used
    public Map<VisTypes.Interaction, Param> tInteraction;   // Which interactive features to support (with maps to options)

    private Dataset dataset;                // Dataset in which dataset fields are to be found (may be null)
    private String[] used;                  // Data that is used in the vis (does not include filters)
    private String[] includingFilters;      // All data including filters
    private String[] aesthetics;            // aesthetics
    private String[] pos;                   // Position fields only
    private String[] nonPos;                // non-position fields (but not filters)

    public VisSingle() {
        this(null);
    }

    public String toString() {
        return (tElement == null ? "element" : tElement) + "[" + fX.size() + "x" + fY.size() + "]";
    }

    @SuppressWarnings("unchecked")
    public VisSingle(Dataset base) {
        this.dataset = base;
        coords = VisTypes.Coordinates.regular;
        coords = VisTypes.Coordinates.regular;
        tAxes = VisTypes.Axes.auto;
        tUsing = VisTypes.Using.none;
        tLegends = VisTypes.Legends.auto;
        tInteraction = Collections.EMPTY_MAP;

        // For memory and speed, these are all fixed as empty until used
        fX = Collections.EMPTY_LIST;
        fY = Collections.EMPTY_LIST;
        fColor = Collections.EMPTY_LIST;
        fData = Collections.EMPTY_LIST;
        fKeys = Collections.EMPTY_LIST;
        fOpacity = Collections.EMPTY_LIST;
        fSize = Collections.EMPTY_LIST;
        fSort = Collections.EMPTY_LIST;
        fFilter = Collections.EMPTY_LIST;
        fSplits = Collections.EMPTY_LIST;
        itemsLabel = Collections.EMPTY_LIST;
        itemsTooltip = Collections.EMPTY_LIST;
        fTransform = Collections.EMPTY_MAP;
        fSummarize = Collections.EMPTY_MAP;
    }

    public String[] aestheticFields() {
        if (aesthetics == null) makeUsedFields();
        return aesthetics;
    }

    public VisSingle at(Param... locations) {
        bounds = locations;
        return this;
    }

    public VisSingle axes(Param type) {
        if (type != null) tAxes = VisTypes.Axes.valueOf(type.asString());
        return this;
    }

    public VisSingle transform(String type, Param[] fieldNames) {
        if (fTransform.isEmpty()) fTransform = new HashMap<Param, String>();
        for (Param param : fieldNames)
            fTransform.put(param, type);
        return this;
    }

    public Dataset getDataset() {
        if (dataset == null) {
            // In future may handle more cases, for now, assume one item
            if (fData.size() != 1)
                throw new IllegalArgumentException("Currently Brunel requires exactly one data statement");
            try {
                dataset = DataCache.get(fData.get(0));
            } catch (IOException e) {
                throw VisException.makeBuilding(e, this);
            }
        }
        return dataset;
    }

    public VisSingle color(Param... fieldNames) {
        if (fColor.isEmpty()) fColor = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fColor, fieldNames);
        return this;
    }

    public VisSingle diagram(VisTypes.Diagram type, Param[] parameters) {
        tDiagram = type;
        tDiagramParameters = parameters;
        return this;
    }

    public VisSingle element(VisTypes.Element e) {
        tElement = e;
        return this;
    }

    /**
     * Define data references, usually as a URL.
     * Multiple data statements are not currently supported, but in future are likely to support joins
     *
     * @param dataReferences each defines a dataset
     * @return this
     */
    public VisSingle data(Param... dataReferences) {
        // Replaces all previous data statements
        dataset = null;
        fData = new ArrayList<String>(dataReferences.length);
        for (Param d : dataReferences) fData.add(d.asString());
        return this;
    }

    public VisSingle filter(Param... fieldNames) {
        if (fFilter.isEmpty()) fFilter = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fFilter, fieldNames);
        return this;
    }

    public VisSingle flip() {
        this.flipY = !flipY;
        return this;
    }

    public VisSingle flipx() {
        this.flipX = !flipX;
        return this;
    }

    /**
     * Sets interactivity. Note the following:
     * Setting an interactivity of NONE or AUTO will clear all previous settings. Setting a specific interactivity
     * will override a general (AUTO/NONE) settings. So "interaction(none, panzoom)" will set only panzoom
     *
     * @param types options to set
     * @return this object, for chaining calls
     */
    public VisSingle interaction(Param... types) {
        if (tInteraction.isEmpty()) tInteraction = new HashMap<VisTypes.Interaction, Param>();
        for (Param a : types) {
            VisTypes.Interaction option = VisTypes.Interaction.valueOf(a.asString());
            if (option == VisTypes.Interaction.auto || option == VisTypes.Interaction.none) tInteraction.clear();
            tInteraction.put(option, a);
        }

        return this;
    }

    public VisSingle key(Param... fieldNames) {
        if (fKeys.isEmpty()) fKeys = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fKeys, fieldNames);
        return this;
    }

    public VisSingle label(Param... items) {
        if (itemsLabel.isEmpty()) itemsLabel = new ArrayList<Param>();
        Collections.addAll(itemsLabel, items);
        return this;
    }

    public VisSingle legends(Param type) {
        if (type != null) tLegends = VisTypes.Legends.valueOf(type.asString());
        return this;
    }

    public VisSingle getSingle() {
        return this;
    }

    public VisItem[] children() {
        return null;
    }

    public String validate() {
        final boolean elementNeeds2Fields = tElement == VisTypes.Element.area || tElement == VisTypes.Element.line;
        final boolean diagramNeeds2Fields = tDiagram == VisTypes.Diagram.chord;
        final int fields = fX.size() + fY.size() + (fRange == null ? 0 : 1);
        String error = null;
        if (elementNeeds2Fields && fields < 2)
            error = addError(null, "Element used requires two fields");
        if (diagramNeeds2Fields && fields < 2)
            error = addError(error, "Diagram used requires two fields");

        if (duplicatesWithin(fX)) error = addError(error, "X contains duplicate fields");
        if (duplicatesWithin(fY)) error = addError(error, "Y contains duplicate fields");
        if (duplicatesWithin(fColor)) error = addError(error, "color contains duplicate fields");
        if (duplicatesWithin(fFilter)) error = addError(error, "filter contains duplicate fields");
        if (duplicatesWithin(fSplits)) error = addError(error, "splits contains duplicate fields");

        if (tDiagram != null && stacked) error = addError(error, "diagrams cannot be stacked");

        Dataset dataset = getDataset();
        if (fX.size() > 1 && tElement != VisTypes.Element.edge) {
            Field x = dataset.field(fX.get(0).asField(dataset));
            if (!x.preferCategorical() && !"bin".equals(fTransform.get(fX.get(0))))
                error = addError(error, "when using multiple x fields, the first must be categorical");
        }

        if (fY.size() < 2 && containsSeriesField(usedFields(false)))
            error = addError(error, "#series and #values can only be used when there are multiple Y fields");

        // Handle cases where the range is defined
        if (fRange != null) {
            Field fY1 = dataset.field(fRange[0].asField(dataset));
            Field fY2 = dataset.field(fRange[1].asField(dataset));
            if (tElement == VisTypes.Element.path || tElement == VisTypes.Element.point || tElement == VisTypes.Element.polygon || tElement == VisTypes.Element.text)
                error = addError(error, "Element '" + tElement + "' should not be used with a y range");
            if (fY1 != null && fY2 != null) {
                if (fY1.preferCategorical() != fY2.preferCategorical())
                    error = addError(error, "y range contains mix of categorical and non-categorical");
            }
        }

        return error;
    }

    public Dataset[] getDataSets() {
        Dataset dataset = getDataset();
        return dataset == null ? new Dataset[0] : new Dataset[]{dataset};
    }

    private String addError(String error, String toAdd) {
        return error == null ? toAdd : error + "; " + toAdd;
    }

    private boolean duplicatesWithin(List<Param> list) {
        for (int i = 1; i < list.size(); i++)
            for (int j = 0; j < i; j++)
                if (list.get(i).equals(list.get(j))) return true;
        return false;
    }

    private boolean containsSeriesField(String[] ff) {
        for (String f : ff)
            if (f.equals("#series") || f.equals("#values")) return true;
        return false;
    }

    public String[] usedFields(boolean withFilters) {
        if (used == null) makeUsedFields();
        return withFilters ? includingFilters : used;
    }

    @SuppressWarnings("unchecked")
    private void makeUsedFields() {

        // Position Fields -- Note that these are a LIST -- we may have repeated values
        List<String> posFields = new ArrayList<String>();
        addFieldNames(posFields, true, fX, fY);
        if (fRange != null) {
            Dataset dataset = getDataset();
            posFields.add((fRange[0].asField(dataset)));
            posFields.add((fRange[1].asField(dataset)));
        }
        pos = posFields.toArray(new String[posFields.size()]);

        // Aesthetic Fields
        Set<String> nonPosFields = new LinkedHashSet<String>();
        addFieldNames(nonPosFields, true, fColor, fSize, fOpacity, fSplits);

        // Move the selection to the end -- it is always the least important
        if (nonPosFields.remove("#selection")) nonPosFields.add("#selection");

        // These ones are the aesthetics
        aesthetics = nonPosFields.toArray(new String[nonPosFields.size()]);

        // Non-Position fields -- does not clear aesthetics as they are included
        addFieldNames(nonPosFields, true, fSort, fKeys);
        addFieldNames(nonPosFields, false, itemsLabel, itemsTooltip);
        nonPos = nonPosFields.toArray(new String[nonPosFields.size()]);

        // All used fields are: position and non-position fields, also #selection and any transform field
        LinkedHashSet<String> all = new LinkedHashSet<String>();
        Collections.addAll(all, pos);
        Collections.addAll(all, nonPos);
        if (tInteraction.containsKey(VisTypes.Interaction.filter)) all.add("#selection");

        addFields(all, fTransform.keySet());
        addFields(all, fSummarize.keySet());

        used = all.toArray(new String[all.size()]);

        // Add the filters
        addFieldNames(all, true, fFilter);
        includingFilters = all.toArray(new String[all.size()]);
    }

    private void addFields(Set<String> all, Set<Param> params) {
        for (Param p : params) if (p.isField()) all.add(p.asField());
    }

    private void addFieldNames(Collection<String> target, boolean forceToBeField, List<Param>... sources) {
        for (List<Param> s : sources)
            for (Param p : s)
                if (p.isField() || forceToBeField) {
                    String field = p.asField(getDataset());
                    if (field != null) target.add(field);
                }
    }

    public VisSingle polar() {
        coords = VisTypes.Coordinates.polar;
        return this;
    }

    public String[] positionFields() {
        if (pos == null) makeUsedFields();
        return pos;
    }

    public String[] nonPositionFields() {
        if (nonPos == null) makeUsedFields();
        return nonPos;
    }

    public VisSingle resolve() {

        ensureCanonical(fColor);
        ensureCanonical(fSize);
        ensureCanonical(fOpacity);
        ensureCanonical(fFilter);
        ensureCanonical(fSort);
        ensureCanonical(fKeys);
        ensureCanonical(fSplits);
        ensureCanonical(fX);
        ensureCanonical(fY);
        ensureCanonical(itemsLabel);
        ensureCanonical(itemsTooltip);
        ensureCanonical(fSummarize);
        ensureCanonical(fTransform);

        makeUsedFields();

        // Collect a replacement for the "#all" field, if needed
        LinkedHashSet<String> replacement = new LinkedHashSet<String>();
        for (String f : used) if (!f.equals("#all")) replacement.add(f);
        boolean containsAll = replacement.size() != used.length;

        boolean addSeriesSplit = false;
        boolean convertYsToRange = false;
        if (fY.size() > 1) {
            if (tElement == VisTypes.Element.edge)
                convertYsToRange = true;
            else {
                addSeriesSplit = true;
                for (String s : aestheticFields()) if (s.equals("#series")) addSeriesSplit = false;
            }
        }

        // See if we need to add a Y field to stack with
        boolean addY = stacked && fY.isEmpty() && fRange == null;

        // If no changes, we can return this vis
        if (tElement != null && !addY && !containsAll && !addSeriesSplit && !convertYsToRange) return this;

        VisSingle result;
        try {
            result = (VisSingle) clone();
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }

        if (addSeriesSplit) result.split(Param.makeField("#series"));
        if (convertYsToRange) {
            result.fRange = new Param[] {fY.get(0), fY.get(1)};
            result.fY = Collections.emptyList();
        }

        // Set the default element
        if (tElement == null) {
            if (tDiagram != null) {
                // Diagrams know what they like
                result.tElement = tDiagram.defaultElement;
            } else if (stacked) {
                // Bars work well for stacking usually
                result.tElement = VisTypes.Element.bar;
            } else {
                // The default
                result.tElement = VisTypes.Element.point;
            }
        }

        if (containsAll) {
            result.fColor = replaceAllField(result.fColor, replacement);
            result.fSize = replaceAllField(result.fSize, replacement);
            result.fOpacity = replaceAllField(result.fOpacity, replacement);
            result.fFilter = replaceAllField(result.fFilter, replacement);
            result.fSort = replaceAllField(result.fSort, replacement);
            result.fKeys = replaceAllField(result.fKeys, replacement);
            result.fSplits = replaceAllField(result.fSplits, replacement);
            result.fX = replaceAllField(result.fX, replacement);
            result.fY = replaceAllField(result.fY, replacement);
            result.itemsLabel = replaceAllField(result.itemsLabel, replacement);
            result.itemsTooltip = replaceAllField(result.itemsTooltip, replacement);
            result.fSummarize = replaceAllField(result.fSummarize, replacement);
            result.fTransform = replaceAllField(result.fTransform, replacement);
        }

        // Need to stack something
        if (addY) result.y(Param.makeNumber(1.0));

        result.makeUsedFields();
        return result;
    }

    private void ensureCanonical(List<Param> list) {
        if (list.isEmpty()) return;
        Dataset dataset = getDataset();
        for (int i = 0; i < list.size(); i++) {
            Param p = list.get(i);
            if (p.isField()) {
                String name = p.asField(dataset);
                // If the name is not the canonical one, replace it with the correct one
                if (!name.equals(p.asString())) list.set(i, Param.makeField(name).addModifiers(p.modifiers()));
            }
        }
    }

    private void ensureCanonical(Map<Param, String> map) {
        if (map.isEmpty()) return;
        Dataset dataset = getDataset();
        for (Param p : new ArrayList<Param>(map.keySet())) {
            if (p.isField() && !p.asField(dataset).startsWith("#")) {
                String name = p.asField(getDataset());
                // If the name is not the canonical one, replace it with the correct one
                if (!name.equals(p.asString())) {
                    Param newParameter = Param.makeField(name).addModifiers(p.modifiers());
                    map.put(newParameter, map.get(p));
                    map.remove(p);
                }
            }
        }
    }

    private List<Param> replaceAllField(List<Param> items, LinkedHashSet<String> replacementFieldNames) {
        // A zero length list is easy
        if (items.isEmpty()) return items;
        Dataset dataset = getDataset();

        // Search for all and create a set of fields that are in items
        LinkedHashSet<String> itemFields = new LinkedHashSet<String>();
        Param allParam = null;
        for (Param p : items)
            if (p.isField()) {
                String s = p.asField(dataset);
                if (s.equals("#all")) allParam = p;
                else itemFields.add(s);
            }

        // If we have no #all field, we are done
        if (allParam == null) return items;

        // Build the fields that are in replacementFieldNames, bit NOT in itemFields
        List<Param> replacement = new ArrayList<Param>();
        for (String s : replacementFieldNames) {
            // Only fields not already in there will be added
            // We copy the modifiers over too so they are preserved
            if (!itemFields.contains(s))
                replacement.add(Param.makeField(s).addModifiers(allParam.modifiers()));
        }

        // Build the final list from the original list, replacing '#all' with the replacements
        ArrayList<Param> result = new ArrayList<Param>();
        for (Param p : items)
            if (p.asField(dataset).equals("#all"))
                result.addAll(replacement);
            else
                result.add(p);
        return result;
    }

    private Map<Param, String> replaceAllField(Map<Param, String> items, LinkedHashSet<String> replacementFieldNames) {
        // A zero length list is easy
        if (items.isEmpty()) return items;
        Dataset data = getDataset();

        // Search for all and create a set of fields that are in items
        LinkedHashSet<String> itemFields = new LinkedHashSet<String>();
        Param allParam = null;
        for (Param p : items.keySet())
            if (p.isField()) {
                String s = p.asField(data);
                if (s.equals("#all")) allParam = p;
                else itemFields.add(s);
            }

        // If we have no #all field, we are done
        if (allParam == null) return items;

        // Build the fields that are in replacementFieldNames, bit NOT in itemFields
        List<Param> replacement = new ArrayList<Param>();
        for (String s : replacementFieldNames) {
            // Only fields not already in there will be added
            // We copy the modifiers over too so they are preserved
            if (!itemFields.contains(s))
                replacement.add(Param.makeField(s).addModifiers(allParam.modifiers()));
        }

        // Build the final list from the original list, replacing '#all' with the nonAllItems
        Map<Param, String> result = new HashMap<Param, String>();
        for (Map.Entry<Param, String> o : items.entrySet()) {
            Param p = o.getKey();
            String value = o.getValue();
            if (p.asField(data).equals("#all")) {
                for (Param s : replacement) result.put(s, value);
            } else
                result.put(p, value);
        }
        return result;
    }

    public VisSingle size(Param... fieldNames) {
        if (fSize.isEmpty()) fSize = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fSize, fieldNames);
        return this;
    }

    public VisSingle opacity(Param... fieldNames) {
        if (fOpacity.isEmpty()) fOpacity = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fOpacity, fieldNames);
        return this;
    }

    public VisSingle sort(Param... fieldNames) {
        if (fSort.isEmpty()) fSort = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fSort, fieldNames);
        return this;
    }

    public VisSingle split(Param... fieldNames) {
        if (fSplits.isEmpty()) fSplits = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fSplits, fieldNames);
        return this;
    }

    public VisSingle stack() {
        stacked = true;
        return this;
    }

    public VisSingle style(Param style) {
        String text = style.asString();
        if (!text.contains("{")) {
            // As a short cut, we allow a simple string to be the content for an element label
            // Note that "currentElement" will be replaced when written out with the current element ID
            text = ".currentElement .element {" + text + "}";
        }
        StyleSheet sheet = StyleFactory.instance().makeStyleSheet(text);
        if (styles == null)
            styles = sheet;
        else
            styles.add(sheet);
        return this;
    }

    public VisSingle summarize(String method, Param... fieldNames) {
        if (fSummarize.isEmpty()) fSummarize = new HashMap<Param, String>();
        for (Param fieldName : fieldNames)
            fSummarize.put(fieldName, method);
        return this;
    }

    public VisSingle tooltip(Param... items) {
        if (itemsTooltip.isEmpty()) itemsTooltip = new ArrayList<Param>(items.length);
        Collections.addAll(itemsTooltip, items);
        return this;
    }

    public VisSingle transpose() {
        coords = VisTypes.Coordinates.transposed;
        return this;
    }

    public VisSingle using(Param type) {
        tUsing = VisTypes.Using.valueOf(type.asString());
        return this;
    }

    public VisSingle x(Param... fieldNames) {
        if (fX.isEmpty()) fX = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fX, fieldNames);
        return this;
    }

    public VisSingle y(Param... fieldNames) {
        // This overrides an attempt to make a range called previously
        fRange = null;
        if (fY.isEmpty()) fY = new ArrayList<Param>(fieldNames.length);
        Collections.addAll(fY, fieldNames);
        return this;
    }

    @SuppressWarnings("unchecked")
    public VisSingle yrange(Param fieldA, Param fieldB) {
        fY = Collections.EMPTY_LIST;
        fRange = new Param[]{fieldA, fieldB};
        return this;
    }

}
