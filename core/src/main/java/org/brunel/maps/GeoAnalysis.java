package org.brunel.maps;

import org.brunel.build.util.ScriptWriter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This class reads in information needed to analyze geographic names, and provides a method to
 * build the mapping needed for a set of feature names.
 */
public class GeoAnalysis {

    private static final Pattern PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+"); // Removes diacretics
    private static GeoAnalysis INSTANCE;                                                         // The singleton

    /**
     * Gets the singleton instance
     *
     * @return the analysis instance to use
     */
    public static synchronized GeoAnalysis instance() {
        if (INSTANCE == null) INSTANCE = new GeoAnalysis();
        return INSTANCE;
    }

    public static void writeMapping(ScriptWriter out, GeoMapping map) {
        // We assume only one file for now -- will change later
        out.add("{").indentMore();
        int i = 0;
        for (Map.Entry<Object, int[]> s : map.mapping.entrySet()) {
            if (i++ > 0) out.add(", ");
            if (i % 5 == 0) out.onNewLine();
            out.add("'").add(s.getKey()).add("':").add(s.getValue()[1]);
        }
        out.indentLess().onNewLine().add("}");
    }
    final Map<String, int[][]> featureMap;                // For each feature, a pair of [fileIndex,featureIndex]
    final GeoFile[] geoFiles;                             // Feature files we can use

    private GeoAnalysis() {
        try {
            // Read in the information file
            InputStream is = GeoAnalysis.class.getResourceAsStream("/org/brunel/maps/geoindex.txt");
            LineNumberReader rdr = new LineNumberReader(new InputStreamReader(is, "utf-8"));

            // Read the names of the files and their sizes (in K)
            String[] fileLine = rdr.readLine().split("\\|");
            int n = fileLine.length / 3;
            geoFiles = new GeoFile[n];
            for (int i = 0; i < n; i++) {
                geoFiles[i] = new GeoFile(fileLine[3 * i], fileLine[3 * i + 1], fileLine[3 * i + 2]);
            }

            // Read the features
            featureMap = new HashMap<String, int[][]>();
            while (true) {
                String line = rdr.readLine();
                if (line == null) break;
                String[] featureLine = line.split("\\|");
                String name = featureLine[0];
                int m = featureLine.length - 1;
                int[][] data = new int[m][2];
                for (int i = 0; i < m; i++) {
                    String[] s = featureLine[i + 1].split(":");
                    data[i][0] = Integer.parseInt(s[0]);
                    data[i][1] = Integer.parseInt(s[1]);
                }
                featureMap.put(name.toLowerCase(), data);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Add variants of names by normalizing removing accent marks and periods
        List<String> keys = new ArrayList<String>(featureMap.keySet());
        for (String s : keys) {
            String t = removeAccents(s);
            if (!featureMap.containsKey(t)) {
                featureMap.put(t, featureMap.get(s));
            }
            t = removePeriods(t);
            if (!featureMap.containsKey(t)) {
                featureMap.put(t, featureMap.get(s));
            }
        }
    }

    static String removeAccents(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return PATTERN.matcher(decomposed).replaceAll("");
    }

    static String removePeriods(String s) {
        // Do not remove from XX.YY pattern
        if (s.length() == 5 && s.charAt(2) == '.') return s;
        return s.replaceAll("\\.", "");
    }

    /**
     * For a set of features, returns the mapping to use for them
     *
     * @param names feature names
     * @return resulting mapping
     */
    public GeoMapping make(Object[] names) {
        return new GeoMapping(names, this);
    }
}
