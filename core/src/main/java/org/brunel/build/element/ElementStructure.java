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

package org.brunel.build.element;

import org.brunel.build.chart.ChartStructure;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

/**
 * Created by graham on 12/15/15.
 */
public class ElementStructure {
    public final ChartStructure chartStructure;
    public final int elementIndex;
    public final VisSingle vis;
    public final Dataset original;
    public final Dataset data;

    public ElementDefinition definition;
    public ElementDetails details;

    public ElementStructure(ChartStructure chartStructure, int elementIndex, VisSingle vis, Dataset data) {
        this.chartStructure = chartStructure;
        this.elementIndex = elementIndex;
        this.vis = vis;
        this.data = data;
        this.original = vis.getDataset();
    }

    public String getElementID() {
        return "element" + elementIndex;
    }

    public int getIndexOfBaseData() {
        for (int i = 0; i < chartStructure.baseDataSets.length; i++) if (original == chartStructure.baseDataSets[i]) return i;
        throw new IllegalStateException("Could not find data set in array of datasets");
    }
}
