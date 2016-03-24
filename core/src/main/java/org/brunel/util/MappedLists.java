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

package org.brunel.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Manages a collection that maps from one class to a list of items of another class
 */
public class MappedLists<S, T> extends HashMap<S, List<T>> {

    /**
     * If the list does not exist, this methpds will add an empty list and return it
     *
     * @param key key value. Must be of the correct class
     * @return a non-null list. May be empty
     */
    @SuppressWarnings("unchecked")
    public List<T> get(Object key) {
        List<T> list = super.get(key);
        if (list == null) {
            list = new ArrayList<>();
            super.put((S) key, list);
        }
        return list;
    }

    public List<T> put(S key, List<T> value) {
        throw new UnsupportedOperationException("Cannot manually add a list");
    }

    /**
     * Add the value to the list defined for the key
     *
     * @param key   key into the map of lists
     * @param value the value to add to the list
     * @return the list with the newly added value
     */
    public List<T> add(S key, T value) {
        List<T> list = get(key);
        list.add(value);
        return list;
    }

    /**
     * Add the values to the list defined for the key
     *
     * @param key    key into the map of lists
     * @param values the value to add to the list
     * @return the list with the newly added value
     */
    public List<T> addAll(S key, List<T> values) {
        List<T> list = get(key);
        list.addAll(values);
        return list;
    }

    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Cannot check for a contained value");
    }
}
