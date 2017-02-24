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

import org.brunel.data.Dataset;
import org.brunel.model.VisTypes.Composition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/* One or more single items, composed together */
public class VisComposition extends VisItem {

    public static VisComposition inside(VisItem... items) {
        return new VisComposition(Composition.inside, items);
    }

    public static VisComposition nested(VisItem... items) {
        return new VisComposition(Composition.nested, items);
    }

    public static VisComposition overlay(VisItem... items) {
        return new VisComposition(Composition.overlay, items);
    }

    public static VisComposition tile(VisItem... items) {
        return new VisComposition(Composition.tile, items);
    }

    public final Composition method;
    private final VisItem[] items;

    private VisComposition(Composition method, VisItem... items) {
        this.method = method;
        this.items = items;
    }

    public VisElement getSingle() {
        return items[0].getSingle();
    }

    public VisItem[] children() {
        return items;
    }

    public String validate() {
        String error = null;
        for (VisItem i : items) {
            String e = i.validate();
            if (e != null) if (error == null)
                error = e;
            else
                error = error + "; " + e;
        }
        return error;
    }

    public Dataset[] getDataSets() {
        Set<Dataset> datas = new LinkedHashSet<>();
        for (VisItem v : items) {
            if (v instanceof VisElement)
                datas.add(((VisElement) v).getDataset());
            else
                Collections.addAll(datas, v.getDataSets());
        }
        return datas.toArray(new Dataset[datas.size()]);
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int i=0; i<items.length; i++) {
            if (i>1) b.append(method);
            b.append(items[i]).toString();
        }
        return b.toString();
    }
}
