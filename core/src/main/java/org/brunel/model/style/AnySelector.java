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

package org.brunel.model.style;

class AnySelector extends StyleSelector {

    public AnySelector() {
        super(0);
    }

    public String debug() {
        return "ANY";
    }

    // This matches anything
    public boolean match(StyleTarget target) {
        return true;
    }

    // No class here so no need to replace
    public StyleSelector replaceClass(String target, String replace) {
        return this;
    }

    public String toString() {
        return "*";
    }

}
