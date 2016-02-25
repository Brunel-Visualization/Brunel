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

package org.brunel.match;

import org.brunel.action.Param;
import org.brunel.action.Param.Type;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates a set of the best alternate ActionParameters for a given Action based on scores.
 * Non field Actions and synthetic fields are preserved as is from
 * the original.  Dual encoded field actions will retain an index to the first use of the field in the original action.
 *
 * Scoring is currently done by comparing the field names as well as the categorical preference and the number
 * of unique values.  Continuous fields are
 * matched using closeness of their distribution properties (variance, skewness and kurtosis).
 *
 * @author drope
 */
class BestActionParameterSet {

    private ArrayList<ActionParameterChoice> actionChoices;     // Individual parameter choices
    private final int numChoices;                               // Upper limit on number choices to retain.
    private static final double REALLY_BAD = .000001;           //A very poor score

    public BestActionParameterSet(Dataset originalData, Dataset newData, Param[] originalParams,
                                  int actionParameterIndex, int numChoices) {

        this.numChoices = numChoices;
        Param p = originalParams[actionParameterIndex];

        //Builds up the list of choices using the new data set.
        //Preserve non field actions, synthetic fields and dual encoded
        if (!addNonFieldChoice(p) && !addSyntheticFieldChoice(originalData, p)
                && !addDualEncodedFieldChoice(originalParams, actionParameterIndex)) {
            addFieldChoices(originalData, newData, p);
        }

    }

    /**
     * Remove and return next best alternative.  The last choice is always retained.
     *
     * @param restrictedFields fields that cannot be used (typically because they have already been used).
     * @return the next best action parameter.
     */
    public ActionParameterChoice pullNextActionParameterChoice(List<String> restrictedFields) {

        if (actionChoices.size() == 1) return actionChoices.get(0);

        for (ActionParameterChoice c : actionChoices) {
            if (!containsRestrictedField(c, restrictedFields)) {
                actionChoices.remove(c);
                return c;
            }
        }

        ActionParameterChoice c = actionChoices.get(0);
        actionChoices.remove(c);
        return c;
    }

    /**
     * Number of action parameters remaining.
     *
     * @return
     */
    public int size() {
        return actionChoices.size();
    }

    private boolean containsRestrictedField(ActionParameterChoice c, List<String> restrictedFields) {

        for (String f : restrictedFields) {
            if (f.equals(c.getActionParameter().asField())) return true;
        }
        return false;
    }

    //Ensures only one alternative
    private void leaveAsIs(ActionParameterChoice choice) {
        actionChoices = new ArrayList<ActionParameterChoice>(1);
        actionChoices.add(choice);
    }

    //Synthetic fields left in place.
    private boolean addSyntheticFieldChoice(Dataset originalData, Param parm) {

        //Note, #all is not actually a field but seems to be reported as such
        String s = parm.asField();

        if (s.equals("#all") || s.equals("#series") || s.equals("#values") ||
                (parm.isField() && originalData.field(s, true).isSynthetic())) {
            leaveAsIs(new ActionParameterChoice(parm, 1.0));
            return true;
        }

        return false;
    }

    //Non field actions left in place.
    private boolean addNonFieldChoice(Param parm) {
        if (!parm.isField()) {
            leaveAsIs(new ActionParameterChoice(parm, 1.0));
            return true;
        }
        return false;
    }

    //Dual encoded fields retain the index for the first use
    private boolean addDualEncodedFieldChoice(Param[] originalParams, int actionParameterIndex) {
        Param parm = originalParams[actionParameterIndex];
        if (!parm.isField()) return false;

        for (int i = 0; i < actionParameterIndex; i++) {
            Param p = originalParams[i];
            if (p.isField() && parm.asField().equals(p.asField())) {
                leaveAsIs(new ActionParameterChoice(i, parm));
                return true;
            }
        }
        return false;

    }

    private void addFieldChoices(Dataset originalData, Dataset newData, Param parm) {

        if (!parm.isField()) return;
        Field[] fields = newData.fields;
        Field originalField = originalData.field(parm.asField(), true);

        actionChoices = new ArrayList<ActionParameterChoice>(fields.length);

        for (Field f : fields) {
            //skip any synthetic fields
            if (!f.isSynthetic()) {
                //geo-mean the current two scores
                double scoreName = scoreFieldByNameCloseness(originalField.label, f.label);
                double scoreFieldValueCloseness = scoreFieldByValueCloseness(originalField, f, declaredNominal(parm));
                double score = Double.isNaN(scoreName) ? scoreFieldValueCloseness : Math.sqrt(scoreName * scoreFieldValueCloseness);
                Param newParam = Param.makeField(f.name).addModifiers(parm.modifiers());
                actionChoices.add(new ActionParameterChoice(newParam, score));
            }
        }

        //Retain only top ones
        Collections.sort(actionChoices);
        if (actionChoices.size() > numChoices) {
            actionChoices.subList(numChoices, actionChoices.size()).clear();
        }

    }

    //Whether the field is being used as nominal in the action
    private boolean declaredNominal(Param p) {
        Param[] modifiers = p.modifiers();
        for (Param m : modifiers) {
            if (m.type() == Type.option) {
                String o = m.asString();
                if (o.equals("nominal")) return true;
            }
        }
        return false;
    }

    //Score based on field name closeness
    private double scoreFieldByNameCloseness(String origName, String newName) {

//		String nice1 = w.nicelyReadable(origName);
//		String nice2 = w.nicelyReadable(newName);

        int lcs = getLongestCommonSubstringLength(origName, newName);

        double score = (double) lcs / (double) origName.length();

        //If we didn't org.brunel.app.match at least 75% of the characters then it probably does not matter.
        return score >= .75 ? score : Double.NaN;

    }

    private int getLongestCommonSubstringLength(String a, String b) {
        if (a.length() == 0 || b.length() == 0)
            return 0;

        int maxLen = 0;
        final int lenA = a.length();
        final int lenB = b.length();
        final int[][] table = new int[lenA + 1][lenB + 1];

        for (int i = 1; i <= lenA; i++)
            for (int j = 1; j <= lenB; j++)
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    if (i == 1 || j == 1)
                        table[i][j] = 1;
                    else
                        table[i][j] = table[i - 1][j - 1] + 1;

                    if (table[i][j] > maxLen)
                        maxLen = table[i][j];
                }
        return maxLen;
    }

    //Score based closeness of the values of the fields
    private double scoreFieldByValueCloseness(Field origField, Field newField, boolean declaredNominal) {

        //Nominal not specifically requested, so simple.
        if (!declaredNominal) {
            //Mismatch in categorical preference is generally a bad choice
            if (origField.preferCategorical() != newField.preferCategorical()) return REALLY_BAD;

            //Use distribution properties if comparing a continuous field to a continuous slot
            if (!origField.preferCategorical() && !newField.preferCategorical())
                return scoreByDistributionCloseness(origField, newField);
        }

        //Pct difference in number unique values
        int oc = origField.uniqueValuesCount();
        int nc = newField.uniqueValuesCount();
        double score = pctDiffScore((double) oc, (double) nc);

        //score is a decent measure if original field preferred categorical or the action wished to use it as such
        if (origField.preferCategorical() || (declaredNominal && newField.preferCategorical())) return score;
        return score * .5;
    }

    //Will do all positive value comparisons.
    //Assumption on skewness is that the sign does not really make a difference when choosing fields.
    private double pctDiffScore(double v1, double v2) {
        double av1 = Math.abs(v1);
        double av2 = Math.abs(v2);
        double max = Math.max(av1, av2);
        return 1.0 - (double) Math.abs(av1 - av2) / max;
    }

    private double propertyDiffScore(String numericProperty, Field f1, Field f2) {

        Double p1 = f1.numProperty(numericProperty);
        Double p2 = f2.numProperty(numericProperty);
        if (p1 == null || p2 == null) return Double.NaN;
        return pctDiffScore(p1, p2);

    }

    private double scoreByDistributionCloseness(Field origField, Field newField) {

        //Pct. differences for distribution properties
        double varianceScore = propertyDiffScore("variance", origField, newField);
        double skewScore = propertyDiffScore("skew", origField, newField);
        double kurtosisScore = propertyDiffScore("kurtosis", origField, newField);

        //Geo-mean of the three measures
        double geoMean = geoMeanNoMissing(varianceScore, skewScore, kurtosisScore);

        //No distribution properties probably a poor choice for a continuous field
        return Double.isNaN(geoMean) ? REALLY_BAD : geoMean;

    }

    //Skip any missing values
    private double geoMeanNoMissing(double... vals) {
        int count = 0;
        double mult = 1.0;

        for (double val : vals) {
            if (!Double.isNaN(val)) {
                mult *= val;
                count++;
            }
        }

        return count > 0 ? Math.pow(mult, 1.0 / (double) count) : Double.NaN;
    }

}
