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
        assertEquals("WesternEurope", a.files[0].name);
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
        GeoMapping a = GeoAnalysis.instance().make("france,germany,UK,IRE".split(","));
        assertEquals(1, a.files.length);
        assertEquals("Europe", a.files[0].name);
        assertEquals(a.unmatched.toString(), 0, a.unmatched.size());
        assertEquals("IRE:[0, 102] UK:[0, 77] france:[0, 73] germany:[0, 58]", dump(a.mapping));
    }

//    @Test
//    public void testExamples3() {
//        GeoMapping a = GeoAnalysis.instance().make("FRA,GER,NY,TX,IA,AL,IN,IL,Nowhere".split(","));
//        assertEquals(2, a.files.length);
//        assertEquals("UnitedStatesofAmerica", a.files[0].name);
//        assertEquals("WesternEurope", a.files[1].name);
//        assertEquals(1, a.unmatched.size());
//        assertEquals("Nowhere", a.unmatched.get(0));
//        assertEquals("AL:[0, 29] FRA:[1, 56] GER:[1, 42] IA:[0, 16] IL:[0, 34] IN:[0, 35] NY:[0, 47] TX:[0, 23]", dump(a.mapping));
//    }
}
