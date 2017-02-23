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

package org.brunel.data.modify;

import org.brunel.data.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Common code for Dataset transforms
 */
public abstract class DataOperation {

    /**
     * Splits the command into parts using the semi-colon as separator, then = for map keys
     *
     * @param command command to be split into sections
     * @return array of commands, or null if there are none
     */
    public static List<String[]> map(String command) {
        List<String[]> result = new ArrayList<>();
        for (String c : strings(command, ';'))
            result.add(strings(c, '='));
        return result;
    }

    public static String[] strings(String items, char sep) {
        String[] parts = Data.split(items, sep);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts.length == 1 && parts[0].isEmpty() ? new String[0] : parts;
    }

}
