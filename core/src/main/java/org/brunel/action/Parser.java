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

package org.brunel.action;

import org.brunel.action.parse.GrammarItem;
import org.brunel.action.parse.ParseGrammar;
import org.brunel.data.Data;
import org.brunel.model.VisException;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Builds actions for Brunel
 */

public class Parser {

    public static Action parse(String text) {
        Parser parse = new Parser();
        List<BrunelToken> tokens = parse.tokenize(text);
        return parse.makeActionFromTokens(tokens, text);
    }

    private static int findQuoteEnd(String text, int start, char quoteChar) {
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                // If we meet a backslash, ignore the next item as it is to be taken as-is
                i++;
            } else if (c == quoteChar) {
                // We have found the end
                return i + 1;
            }
            // Anything else we ignore
        }
        return text.length();
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'';
    }

    private static Param parseModifier(BrunelToken t) {
        if (t.content.length() == 0) throw new IllegalStateException("Empty modifier");
        Param result;
        if (Data.isQuoted(t.content))
            result = Param.makeString(Data.deQuote(t.content));
        else {
            Double d = Data.asNumeric(t.content);
            if (d != null)
                result = Param.makeNumber(d);
            else
                result = Param.makeOption(t.content.toLowerCase());
        }
        t.parsedType = result.type().toString();
        return result;
    }

    private final ParseGrammar grammar = ParseGrammar.instance();

    public Action makeActionFromTokens(List<BrunelToken> tokens, String text) {
        try {
            if (tokens.isEmpty()) throw new IllegalStateException("Empty action string");
            List<ActionStep> actions = getActionSteps(tokens);
            return new Action(actions.toArray(new ActionStep[actions.size()]));
        } catch (Exception e) {
            throw VisException.makeParsing(e, text);
        }
    }

    Param parseField(String param, String errorMessage) {
        String fieldName = asField(param);
        if (fieldName == null)
            throw new IllegalStateException(errorMessage + ": " + param);
        return Param.makeField(fieldName);
    }

    public List<BrunelToken> tokenize(String text) {
        try {
            ArrayList<BrunelToken> list = new ArrayList<BrunelToken>();
            int runStart = 0;
            while (runStart < text.length()) {
                char c = text.charAt(runStart);
                // Do not care about runs of whitespace
                if (Character.isWhitespace(c)) {
                    runStart++;
                    continue;
                }
                int runEnd = findRunEnd(text, runStart, c);
                list.add(new BrunelToken(text, runStart, runEnd));
                runStart = runEnd;
            }
            return list;
        } catch (Exception e) {
            throw VisException.makeParsing(e, text);
        }
    }

    List<ActionStep> getActionSteps(List<BrunelToken> tokens) {
        List<ActionStep> actions = new ArrayList<ActionStep>();
        for (int at = 0; at < tokens.size(); at++) {
            BrunelToken s = tokens.get(at);
            ActionStep action;
            GrammarItem definition = grammar.get(s.content);
            if (definition == null) {
                if (!Character.isJavaIdentifierStart(s.content.charAt(0)))
                    throw new IllegalStateException("Expected an action, but found '" + s.content + "'");

                throw new IllegalStateException("Unknown action '" + s + "'");
            }

            // A parameter ending in ! is optional, and so if there is no parenthesis following, we treat it as no parameter
            String param = definition.parameter;
            if (param != null && param.endsWith("!") && !tokens.get(at + 1).content.equals("(")) param = null;
            boolean noParametersExpected = definition.options == null &&  param == null;

            if (noParametersExpected) {
                // This item has no form of parameters at all
                action = new ActionStep(s.content);
            } else {
                // Has parameters
                expect("(",  tokens.get(++at));
                int parametersEnd = findParametersEnd(tokens, ++at);
                if (parametersEnd < at + 1) throw new IllegalArgumentException("Empty parameters in " + s);
                Stack<Param> params = new Stack<Param>();

                boolean expectParameter = true;              // false if we expect a colon or comma
                for (int i = at; i < parametersEnd; i++) {
                    BrunelToken token = tokens.get(i);
                    if (expectParameter) {
                        Param p = parseParameter(token, definition, i == at);
                        token.parsedType = p.type().toString();
                        params.push(p);
                        expectParameter = false;
                    } else {
                        if (":".equals(token.content)) {
                            // A modifier for the field
                            token.parsedType = "syntax";
                            if (i > parametersEnd - 2) throw new IllegalStateException("Unterminated option ':'");
                            Param modifier = parseModifier(tokens.get(++i)); // We have handled an extra token so increment i
                            params.push(params.pop().addModifiers(modifier));
                        } else if (",".equals(token.content)) {
                            // Separates field
                            token.parsedType = "syntax";
                            expectParameter = true;
                        } else {
                            expect(",' or ':'", token);
                            expectParameter = true;
                        }
                    }
                }
                Param[] parameters = params.toArray(new Param[params.size()]);
                action = new ActionStep(s.content, parameters);
                at = parametersEnd;
            }
            actions.add(action);
            s.parsedType = "name";
        }
        return actions;
    }

    private String asField(String param) {
        if (param.startsWith("#")) {
            if (param.equalsIgnoreCase("#all")) return "#all";
            if (param.equalsIgnoreCase("#row")) return "#row";
            if (param.equalsIgnoreCase("#count")) return "#count";
            if (param.equalsIgnoreCase("#series")) return "#series";
            if (param.equalsIgnoreCase("#values")) return "#values";
            if (param.equalsIgnoreCase("#selection")) return "#selection";
            throw new IllegalStateException("Unknown special field: " + param);
        }
        // Check that it is all OK
        for (int i = 0; i < param.length(); i++) {
            char c = param.charAt(i);
            if (c >= 'a' && c <= 'z') continue;
            if (c >= 'A' && c <= 'Z') continue;
            if (i > 0 && c >= '0' && c <= '9') continue;
            if (c == '_' || c == '%' || c == '$') continue;
            return null;
        }
        // Yup, it's good
        return param;
    }

    private void expect(String expected, BrunelToken actual) {
        if (!expected.equals(actual.content))
            throw new IllegalStateException("Expected token '" + expected + "', but was '" + actual.content + "'");
        actual.parsedType = "syntax";
    }

    private int findParametersEnd(List<BrunelToken> tokens, int at) {
        while (at < tokens.size()) {
            if (tokens.get(at).content.equals(")")) {
                tokens.get(at).parsedType = "syntax";
                return at;
            } else
                at++;
        }
        throw new IllegalStateException("Unterminated parameters list");
    }

    private int findRunEnd(String text, int start, char startChar) {
        if (isQuote(startChar)) return findQuoteEnd(text, start, startChar);
        if (isSpecialChar(startChar)) return start + 1;
        int end = start + 1;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (isSpecialChar(c) || Character.isWhitespace(c) || isQuote(c)) return end;
            end++;
        }
        return end;
    }

    private boolean isSpecialChar(char startChar) {
        return startChar == ',' || startChar == '(' || startChar == ')' || startChar == ':';
    }

    private Param parseParameter(BrunelToken token, GrammarItem definition, boolean first) {

        String content = token.content;

        if (definition.options != null) {
            // This command takes an option as parameters, so look for it in the allowed ones
            if (!first) throw new IllegalStateException("Only one parameter allowed for: " + definition.name);
            if (definition.options.contains(content))
                return Param.makeOption(content);
            throw new IllegalStateException("Unknown option for " + definition.name + ": " + content +
                    ". Expected one of " + definition.options);
        } else {
            // This command has a parameter as content -- a field, number or similar
            String def = definition.parameter;

            // Check for multiples when only one allowed
            if (!first && !def.endsWith("+"))
                throw new IllegalStateException("Only one parameter allowed for " + definition.name);

            if (def.startsWith("NUMBER")) {
                Double d = Data.asNumeric(content);
                if (d == null)
                    throw new IllegalStateException("Expected numeric parameter for " + definition.name + ", but was: " + content);
                return Param.makeNumber(d);
            }

            if (def.startsWith("STRING")) {
                if (!Data.isQuoted(content))
                    throw new IllegalStateException("Expected quoted parameter for " + definition.name + ", but was: " + content);
                return Param.makeString(Data.deQuote(content));
            }

            if (def.startsWith("FIELD/LIT")) {
                if (Data.isQuoted(content))
                    return Param.makeString(Data.deQuote(content));
                Double d = Data.asNumeric(content);
                if (d != null) return Param.makeNumber(d);
                return parseField(content, "Could not parse as literal or field");
            }

            if (def.startsWith("FIELD")) {
                if (Data.isQuoted(content))
                    return Param.makeString(Data.deQuote(content));
                Double d = Data.asNumeric(content);
                if (d != null) return Param.makeNumber(d);
                return parseField(content, "Could not parse as field");
            }

            if (def.startsWith("LIT")) {
                if (Data.isQuoted(content))
                    return Param.makeString(Data.deQuote(content));
                Double d = Data.asNumeric(content);
                if (d != null) return Param.makeNumber(d);
                return parseField(content, "Could not parse as literal");
            }

            throw new IllegalStateException("Internal error: Strange definition for " + definition.name);
        }
    }

    /**
     * For parsing, mostly to identify errors and allow syntax coloring
     */
    public static class BrunelToken {

        public final String content;
        public final int start, end;
        public String parsedType;

        /* Create a token and give it the 'error' type initially */
        public BrunelToken(String all, int start, int end) {
            this.start = start;
            this.end = end;
            this.content = all.substring(start, end);
            this.parsedType = "?";
        }

        public String toString() {
            return content + '(' + parsedType + ')';
        }
    }
}
