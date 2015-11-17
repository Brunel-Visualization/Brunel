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

import org.brunel.action.Param;
import org.brunel.data.Data;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;

/**
 * Tests the output
 */
public class TestAnalyze {
    @Test
    public void testExamples() {
        GeoMapping a = GeoAnalysis.instance().make("France,Germany,Lux.".split(","), new Param[0]);
        assertEquals(1, a.getFiles().length);
        assertEquals("WesternEurope", a.getFiles()[0].name);
        assertEquals(a.getUnmatched().toString(), 0, a.getUnmatched().size());
        assertEquals("France:[0, 73] Germany:[0, 58] Lux.:[0, 131]", dump(a.getFeatureMap()));
    }

    private String dump(Map<Object, int[]> mapping) {
        Set<Object> keySet = mapping.keySet();
        Object[] keys = keySet.toArray(new Object[keySet.size()]);
        Data.sort(keys);
        StringBuilder b = new StringBuilder();
        for (Object s : keys) {
            if (b.length() > 0) b.append(" ");
            b.append(s).append(":").append(Arrays.toString(mapping.get(s)));
        }
        return b.toString();
    }

    @Test
    public void testExamples2() {
        GeoMapping a = GeoAnalysis.instance().make("france,germany,UK,IRE".split(","), new Param[0]);
        assertEquals(1, a.getFiles().length);
        assertEquals("Europe", a.getFiles()[0].name);
        assertEquals(a.getUnmatched().toString(), 0, a.getUnmatched().size());
        assertEquals("IRE:[0, 102] UK:[0, 77] france:[0, 73] germany:[0, 58]", dump(a.getFeatureMap()));
    }

    @Test
    public void testExamples3() {
        GeoMapping a = GeoAnalysis.instance().make("FRA,GER,NY,TX,IA,AL,IN,IL,Nowhere".split(","), new Param[0]);
        assertEquals(2, a.getFiles().length);
        assertEquals("USAMain", a.getFiles()[0].name);
        assertEquals("WesternEurope", a.getFiles()[1].name);
        assertEquals(1, a.getUnmatched().size());
        assertEquals("Nowhere", a.getUnmatched().iterator().next());
        assertEquals("AL:[0, 3482] FRA:[1, 73] GER:[1, 58] IA:[0, 3470] IL:[0, 3487] IN:[0, 3488] NY:[0, 3500] TX:[0, 3477]", dump(a.getFeatureMap()));
    }

    @Test
    public void testNameMatching() {

        String[] names = new String[]{
                "Afghanistan", "Afghanistan", "Albania", "Albania", "Albania", "Albania", "Algeria", "Andorra", "Andorra", "Angola", "Angola", "Antigua and Barbuda",
                "Argentina", "Armenia", "Armenia", "Australia", "Australia", "Australia", "Austria", "Austria", "Azerbaijan", "Azerbaijan", "Bahamas, The", "Bahamas,The",
                "Bahrain", "Bangladesh", "Bangladesh", "Bangladesh", "Bangladesh", "Barbados", "Belarus", "Belgium", "Belize", "Benin", "Bhutan", "Bolivia",
                "Bosnia and Herzegovina", "Botswana", "Brazil", "Britain", "Brunei", "Bulgaria", "Burkina Faso", "Burundi", "Cambodia", "Cameroon", "Canada",
                "Cape Verde", "Central African Republic", "Chad", "Chile", "China", "Colombia", "Comoros", "Congo,Democratic Republic of the", "Costa Rica", "Cote D'Ivoire",
                "Croatia", "Cuba", "Cyprus", "Czech Republic", "Denmark", "Djibouti", "Dominica", "Dominican Republic", "Ecuador", "Egypt", "El Salvador",
                "England", "Equatorial Guinea", "Eritrea", "Estonia", "Ethiopia", "Fiji", "Finland", "France", "Gabon", "Gambia,The", "Georgia", "Germany", "Ghana",
                "Great Britain", "Greece", "Greenland", "Grenada", "Guatemala", "Guinea", "Guinea-Bissau", "Guyana", "Haiti", "Honduras", "Hungary", "Iceland", "India",
                "Indonesia", "Iran", "Iraq", "Ireland", "Ireland,Northern", "Israel", "Italy", "Jamaica", "Japan", "Jordan", "Kazakhstan", "Kenya", "Kiribati",
                "Korea,North", "Korea,South", "Kuwait", "Kyrgyzstan", "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia", "Libya", "Liechtenstein", "Lithuania", "Luxembourg",
                "Macedonia", "Madagascar", "Malawi", "Malaysia", "Maldives", "Mali", "Malta", "Marshall Islands", "Mauritania", "Mauritius", "Mexico", "Micronesia", "Moldova",
                "Monaco", "Mongolia", "Morocco", "Mozambique", "Myanmar(Burma)", "Namibia", "Nauru", "Nepal", "Netherlands,The", "New Zealand", "Nicaragua", "Niger", "Nigeria",
                "North Korea", "Norway", "Northern Ireland", "Oman", "Pakistan", "Palau", "Panama", "Papua New Guinea", "Paraguay", "Peru", "Philippines,The", "Poland", "Portugal",
                "Qatar", "Romania", "Russia", "Rwanda", "St.Kitts and Nevis", "St.Lucia", "St.Vincent and the Grenadines", "Samoa", "San Marino", "Sao Tome and Principe", "Saudi Arabia",
                "Scotland", "Senegal", "Seychelles", "Sierra Leone", "Singapore", "Slovakia", "Slovenia", "Solomon Islands", "Somalia", "South Africa", "South Korea",
                "Spain", "Sri Lanka", "Sudan", "Suriname", "Swaziland", "Sweden", "Switzerland", "Syria", "Taiwan", "Tajikistan", "Tanzania", "Thailand", "Togo", "Tonga",
                "Trinidad and Tobago", "Tunisia", "Turkey", "Turkmenistan", "Uganda", "Ukraine", "United Arab Emirates", "United Kingdom", "United States",
                "Uruguay", "Uzbekistan", "Vanuatu", "Vatican City", "Venezuela", "Vietnam", "Wales", "Yemen", "Zambia", "Zimbabwe",
                "Afghanistan", "Albania", "Algeria", "Andorra", "Angola", "Anguilla", "Antigua and Barbuda", "Argentina", "Armenia", "Aruba", "Australia",
                "Austria", "Azerbaijan", "Bahamas, The", "Bahrain", "Bangladesh", "Barbados", "Belarus", "Belgium", "Belize", "Benin", "Bermuda", "Bhutan", "Bolivia",
                "Botswana", "Bougainville", "Brazil", "British Virgin Islands", "Brunei", "Bulgaria", "Burkina Faso", "Burundi", "Cambodia", "Cameroon",
                "Canada", "Cape Verde Islands", "Cayman Islands", "Central African Republic", "Chad", "Chile", "China, Hong Kong", "China, Macau", "China, People?s Republic",
                "China, Taiwan", "Colombia", "Comoros", "Congo, Democratic Republic of", "Congo, Republic of", "Costa Rica", "Cote d\u2019Ivoire", "Croatia", "Cuba",
                "Cyprus", "Czech Republic", "Denmark", "Djibouti", "Dominica", "Dominican Republic", "Ecuador", "Egypt", "El Salvador", "Equatorial Guinea", "Eritrea", "Estonia",
                "Ethiopia", "Faeroe Islands", "Falkland Islands", "Fiji", "Finland", "France", "French Guiana", "Gabon",
                "Gambia, The", "Georgia", "Germany", "Ghana", "Greece", "Greenland", "Grenada", "Guadeloupe", "Guam", "Guatemala", "Guinea", "Guinea-Bissau", "Guyana",
                "Haiti", "Holy See (Vatican City State)", "Honduras", "Hungary", "Iceland", "India", "Indonesia", "Iran", "Iraq", "Ireland", "Israel", "Italy", "Jamaica", "Japan",
                "Jordan", "Kazakhstan", "Kenya", "Kiribati", "Korea, Democratic People?s Rep", "Korea, Republic of", "Kosovo", "Kuwait", "Kyrgyzstan", "Laos", "Latvia", "Lebanon",
                "Lesotho", "Liberia", "Libya", "Liechtenstein", "Lithuania", "Luxembourg", "Macedonia", "Madagascar", "Malawi", "Malaysia", "Maldives", "Mali", "Malta",
                "Martinique", "Mauritania", "Mauritius", "Mayotte", "Mexico", "Moldova", "Monaco", "Mongolia", "Montenegro", "Montserrat", "Morocco", "Mozambique", "Myanmar",
                "Namibia", "Nauru", "Nepal", "Netherlands", "New Caledonia", "New Zealand", "Nicaragua", "Niger", "Nigeria", "Norway", "Oman",
                "Pakistan", "Palestine", "Panama", "Papua New Guinea", "Paraguay", "Peru", "Philippines", "Poland", "Portugal", "Puerto Rico", "Qatar", "R\u00e9union", "Romania",
                "Russia", "Rwanda", "Saint Barthelemy", "Saint Kitts & Nevis", "Saint Lucia", "Saint Martin", "Saint Pierre & Miquelon", "Saint Vincent",
                "Samoa", "San Marino", "Sao Tom\u00e9 & Principe", "Saudi Arabia", "Senegal", "Serbia", "Seychelles", "Sierra Leone", "Singapore", "Slovakia", "Slovenia",
                "Solomon Islands", "Somalia", "South Africa", "Spain", "Sri Lanka", "Sudan", "Suriname", "Swaziland", "Sweden", "Switzerland", "Syria", "Tajikistan",
                "Tanzania", "Thailand", "Togo", "Tonga", "Trinidad & Tobago", "Tunisia", "Turkey", "Turkmenistan", "Turks & Caicos Islands",
                "Uganda", "Ukraine", "United Arab Emirates", "United Kingdom of GB & NI", "United States of America", "Uruguay", "US Virgin Islands", "Uzbekistan",
                "Vanuatu", "Venezuela", "Vietnam", "Wallis & Futuna Islands", "Yemen", "Zambia", "Zimbabwe"
        };

        GeoMapping a = GeoAnalysis.instance().make(names, new Param[0]);
        assertEquals(a.getUnmatched().toString(), 6, a.getUnmatched().size());          // We get all except 6 small islands

    }

}
