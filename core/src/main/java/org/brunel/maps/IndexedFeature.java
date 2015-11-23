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

package org.brunel.maps;

/**
 * For a named feature, indicate which feature index it is within the file
 */
class IndexedFeature {
    final String name;                  // The data name of the feature
    final int indexWithinFile;          // its index within the file

    IndexedFeature(String name, int indexWithinFile) {
        this.name = name;
        this.indexWithinFile = indexWithinFile;
    }

    public int hashCode() {
        return name.hashCode();
    }

    // This unsafe for general use because we use it in a very limited domain
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object o) {
        return name.equals(((IndexedFeature) o).name);
    }
}
