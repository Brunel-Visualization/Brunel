/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 *  This code defines the external symbols we define in BrunelD3.js and BrunelData.js
 *  that can be used by the calling code. Any symbol (method name, field etc.) that
 *  does not appear below will have its name changed by javascript compression and
 *  so will cause a failure
 */

function exportStatic(parent, items) {
    for (i in items) parent[i] = items[i];
}
function exportPrototype(parent, items) {
    for (i in items) parent.prototype[i] = items[i];
}

exportStatic(window, {'BrunelData': BrunelData, 'BrunelD3': BrunelD3});
exportStatic(BrunelData, {'Data': BrunelData.Data, 'Dataset': BrunelData.Dataset});
exportStatic(BrunelData.Dataset, {'makeFromRows': BrunelData.Dataset.makeFromRows});

exportPrototype(BrunelData.Dataset, {
    // Information on the data set
    'fields': BrunelData.Dataset.prototype.fields,
    'field': BrunelData.Dataset.prototype.field,
    'rowCount': BrunelData.Dataset.prototype.rowCount,

    // Operations to modify it
    'bin': BrunelData.Dataset.prototype.bin,
    'addConstants': BrunelData.Dataset.prototype.addConstants,
    'filter': BrunelData.Dataset.prototype.filter,
    'reduce': BrunelData.Dataset.prototype.reduce,
    'series': BrunelData.Dataset.prototype.series,
    'sort': BrunelData.Dataset.prototype.sort,
    'stack': BrunelData.Dataset.prototype.stack,
    'summarize': BrunelData.Dataset.prototype.summarize
});

exportPrototype(BrunelData.Field, {
    // Basic information
    'name': BrunelData.Field.prototype.name,
    'label': BrunelData.Field.prototype.label,
    'rowCount': BrunelData.Field.prototype.rowCount,
    // Field values and properties
    'property': BrunelData.Field.prototype.property,
    'value': BrunelData.Field.prototype.value,
    'valueFormatted': BrunelData.Field.prototype.valueFormatted,
    // Set field values; should not be called from user code
    'setValue': BrunelData.Field.prototype.setValue
});

