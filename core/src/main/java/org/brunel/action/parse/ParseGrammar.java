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

package org.brunel.action.parse;

import org.brunel.action.Parser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * This class reads in the allowed set of Brunel tokens and allows
 * them to be queried. It is a singleton class, accessed via ParseGrammar.instance()
 */
public class ParseGrammar {
    private static final ParseGrammar instance = new ParseGrammar();

    public static ParseGrammar instance() {
        return instance;
    }

    public static Collection<String> getCommands() {
        return instance().grammar.keySet();
    }

    private final LinkedHashMap<String, GrammarItem> grammar;
    private final Set<String> summaryMethods = new LinkedHashSet<>();
    private final Set<String> transformMethods = new LinkedHashSet<>();

    private ParseGrammar() {
        // Read the definitions into the map
        grammar = new LinkedHashMap<>();
        Scanner scanner = new Scanner(Parser.class.getResourceAsStream("brunel-tokens.txt"));
        while (scanner.hasNext()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] words = line.split("[\n\t ]+");
            grammar.put(words[0], new GrammarItem(words));
            if (words[1].equals("data")) summaryMethods.add(words[0]);
            if (words[1].equals("transform")) transformMethods.add(words[0]);
        }
    }

    public GrammarItem get(String command) {
        return grammar.get(command);
    }

    public Set<String> getSummaryMethods() {
        return summaryMethods;
    }

    public Set<String> getTransformMethods() {
        return transformMethods;
    }
}
