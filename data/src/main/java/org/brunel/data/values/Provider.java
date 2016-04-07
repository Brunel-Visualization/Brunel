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

package org.brunel.data.values;

import org.brunel.data.util.MapInt;

public interface Provider {

    /**
     * Fast comparison of rows
     * @param a base row
     * @param b to be compared to
     * @param categoryOrder map for the category orders (null if it should not be used)
     * @return -ve, 0 or +ve depending on comparison based on standard comparisons
     */
    int compareRows(int a, int b, MapInt categoryOrder);

    /**
     * The number of rows in this data provider
     *
     * @return number of rows in this data provider
     */
    int count();

    int expectedSize();

    /**
     * Sets the value at an index. If the provider cannot set the value, we
     * convert to a new provider, set the value in that and then return the new one
     *
     * @param o     object to set
     * @param index row to set the object
     * @return new provider
     */
    Provider setValue(Object o, int index);

    /**
     * Return the object stored at the given row
     *
     * @param index row to access
     * @return stored datum
     */
    Object value(int index);
}
