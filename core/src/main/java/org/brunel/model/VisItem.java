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

/* A visualization; either a single element, or a composition */
public abstract class VisItem {

    /* This is the last element in the composition, or this item if we are a VisSingle */
    public abstract VisSingle getSingle();

    /* Return child parts -- will be null for a VisSingle */
    public abstract VisItem[] children();

    /* Return a string containing validation errors, or NULL if there are no errors */
    public abstract String validate();

    public abstract Dataset[] getDataSets();

}
