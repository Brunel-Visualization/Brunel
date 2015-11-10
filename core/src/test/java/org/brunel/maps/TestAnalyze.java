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
        GeoMapping a = GeoAnalysis.instance().make("France,Germany,Lux.".split(","), null);
        assertEquals(1, a.result.length);
        assertEquals("WesternEurope", a.result[0].name);
        assertEquals(a.unmatched.toString(), 0, a.unmatched.size());
        assertEquals("France:[0, 73] Germany:[0, 58] Lux.:[0, 131]", dump(a.mapping));
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
        GeoMapping a = GeoAnalysis.instance().make("france,germany,UK,IRE".split(","), null);
        assertEquals(1, a.result.length);
        assertEquals("Europe", a.result[0].name);
        assertEquals(a.unmatched.toString(), 0, a.unmatched.size());
        assertEquals("IRE:[0, 102] UK:[0, 77] france:[0, 73] germany:[0, 58]", dump(a.mapping));
    }

    @Test
    public void testExamples3() {
        GeoMapping a = GeoAnalysis.instance().make("FRA,GER,NY,TX,IA,AL,IN,IL,Nowhere".split(","), null);
        assertEquals(2, a.result.length);
        assertEquals("USAMain", a.result[0].name);
        assertEquals("WesternEurope", a.result[1].name);
        assertEquals(1, a.unmatched.size());
        assertEquals("Nowhere", a.unmatched.get(0));
        assertEquals("AL:[0, 3482] FRA:[1, 73] GER:[1, 58] IA:[0, 3470] IL:[0, 3487] IN:[0, 3488] NY:[0, 3500] TX:[0, 3477]", dump(a.mapping));
    }
}
