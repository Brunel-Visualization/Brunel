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

package org.brunel.build.d3;

/* The purpose of a scale (inner is the inner coordinate of a clustered chart) */
public enum ScalePurpose {
    x(true), y(true), inner(true), size(false), color(false);
    public final boolean isCoord;

    ScalePurpose(boolean isCoord) {
        this.isCoord = isCoord;
    }
}
