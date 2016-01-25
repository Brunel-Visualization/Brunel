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

import java.util.ArrayList;
import java.util.List;

/**
 * Common code for Dataset transforms
 */
public abstract class DataOperation {

    /**
     * Splits the command into parts using the semi-colon as separator, then the indicated separator for map keys
     *
     * @param command command to be split into sections
     * @return array of commands, or null if there are none
     */
    static List<String[]> map(String command, String sep) {
        String[] parts = parts(command);
        if (parts == null) return null;
        List<String[]> result = new ArrayList<String[]>();
        for (String c : parts) {
            String[] s = c.split(sep);
            String key = s[0].trim();
            String value = s.length > 1 ? s[1].trim() : "";
            result.add(new String[]{key, value});
        }
        return result;
    }

    /**
     * Splits the command into parts using the semi-colon as separator
     *
     * @param command
     * @return array of commands, or null if there are none
     */
    public static String[] parts(String command) {
        if (command.endsWith(";")) command = command.substring(0, command.length() - 1);
        String[] parts = command.split(";");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts.length == 1 && parts[0].isEmpty() ? null : parts;
    }

    /**
     * Splits the command into parts using the comma as separator
     *
     * @param items items as a comm-separated list
     * @return array of commands, or null if there are none
     */
    static String[] list(String items) {
        String[] parts = items.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts.length == 1 && parts[0].isEmpty() ? null : parts;
    }

}
