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
        GeoMapping a = GeoAnalysis.instance().make("France,Germany,Lux.".split(","));
        assertEquals(1, a.files.length);
        assertEquals("WesternEurope", a.files[0]);
        assertEquals(a.unmatched.toString(), 0, a.unmatched.size());
        assertEquals("France:[0, 77] Germany:[0, 61] Lux.:[0, 137]", dump(a.mapping));
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
        GeoMapping a = GeoAnalysis.instance().make("france,germany,UK,IRE".split(","));
        assertEquals(1, a.files.length);
        assertEquals("NorthernEurope-WesternEurope", a.files[0]);
        assertEquals(a.unmatched.toString(), 0, a.unmatched.size());
        assertEquals("IRE:[0, 75] UK:[0, 58] france:[0, 56] germany:[0, 42]", dump(a.mapping));
    }

    @Test
    public void testExamples3() {
        GeoMapping a = GeoAnalysis.instance().make("FRA,GER,NY,TX,IA,AL,IN,IL,Nowhere".split(","));
        assertEquals(2, a.files.length);
        assertEquals("UnitedStatesofAmerica", a.files[0]);
        assertEquals("WesternEurope", a.files[1]);
        assertEquals(1, a.unmatched.size());
        assertEquals("Nowhere", a.unmatched.get(0));
        assertEquals("AL:[0, 51] FRA:[1, 77] GER:[1, 61] IA:[0, 62] IL:[0, 64] IN:[0, 65] NY:[0, 84] TX:[0, 93]", dump(a.mapping));
    }
}
