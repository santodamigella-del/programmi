package com.example.lottoinjava.data;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.lottoinjava.model.Extraction;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LottoCsvImporter {

    private static final String TAG = "LottoCsvImporter";

    public static List<Extraction> parseCsvUri(Context context, Uri uri) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IllegalArgumentException("Impossibile aprire il file selezionato.");
            }
            return parseCsvStream(is);
        }
    }

    public static List<Extraction> parseCsvStream(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        Map<Integer, ExtractionBuilder> builderMap = new LinkedHashMap<>();
        int autoConcorsoCounter = 1000;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.toLowerCase().startsWith("concorso") || line.toLowerCase().startsWith("data")) {
                continue; // Skip header/comments
            }

            // Determine delimiter: semicolon, comma or tab
            String delimiter = line.contains(";") ? ";" : (line.contains("\t") ? "\t" : ",");
            String[] tokens = line.split(delimiter);

            if (tokens.length < 3) {
                continue;
            }

            try {
                // Format B: Concorso;Data;Ruota;N1;N2;N3;N4;N5 (length 8)
                if (tokens.length >= 8 && isRuotaName(tokens[2])) {
                    int concorso = parseInteger(tokens[0], autoConcorsoCounter);
                    java.util.regex.Matcher dm = java.util.regex.Pattern
                            .compile("^(\\d{4})-(\\d{2})-(\\d{2})$").matcher(tokens[0].trim());
                    final String date = dm.matches()
                            ? dm.group(3) + "/" + dm.group(2) + "/" + dm.group(1)
                            : tokens[1].trim();
                    String ruota = normalizeRuotaName(tokens[2].trim());
                    List<Integer> nums = new ArrayList<>();
                    for (int i = 3; i < 8; i++) {
                        int val = Integer.parseInt(tokens[i].trim());
                        if (val >= 1 && val <= 90) {
                            nums.add(val);
                        }
                    }

                    if (nums.size() == 5) {
                        ExtractionBuilder builder = builderMap.computeIfAbsent(concorso, k -> new ExtractionBuilder(concorso, date));
                        builder.wheels.put(ruota, nums);
                    }
                }
                // Format A: Concorso;Data; (and 11 wheels sequentially: 55 numbers)
                else if (tokens.length >= 57) {
                    int concorso = parseInteger(tokens[0], autoConcorsoCounter--);
                    String date = tokens[1].trim();

                    Map<String, List<Integer>> wheelsMap = new LinkedHashMap<>();
                    int tokenIdx = 2;

                    for (String ruota : Extraction.ALL_RUOTE) {
                        if (tokenIdx + 5 <= tokens.length) {
                            List<Integer> nums = new ArrayList<>();
                            for (int i = 0; i < 5; i++) {
                                int val = Integer.parseInt(tokens[tokenIdx++].trim());
                                if (val >= 1 && val <= 90) {
                                    nums.add(val);
                                }
                            }
                            if (nums.size() == 5) {
                                wheelsMap.put(ruota, nums);
                            }
                        }
                    }

                    if (!wheelsMap.isEmpty()) {
                        ExtractionBuilder builder = new ExtractionBuilder(concorso, date);
                        builder.wheels = wheelsMap;
                        builderMap.put(concorso, builder);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Skipping malformed CSV line: " + line, e);
            }
        }

        List<Extraction> extractions = new ArrayList<>();
        for (ExtractionBuilder builder : builderMap.values()) {
            extractions.add(builder.build());
        }

        return extractions;
    }

    private static boolean isRuotaName(String text) {
        String name = text.trim().toLowerCase();
        for (String r : Extraction.ALL_RUOTE) {
            if (r.toLowerCase().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRuotaName(String text) {
        String t = text.trim().toLowerCase();
        for (String r : Extraction.ALL_RUOTE) {
            if (r.toLowerCase().equals(t)) {
                return r;
            }
        }
        return text;
    }

    private static int parseInteger(String val, int defaultVal) {
        try {
            return Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static int extractYear(String dateStr) {
        try {
            if (dateStr.contains("20")) {
                int idx = dateStr.indexOf("20");
                return Integer.parseInt(dateStr.substring(idx, idx + 4));
            }
        } catch (Exception ignored) {}
        return 2025;
    }

    private static class ExtractionBuilder {
        int concorso;
        String date;
        Map<String, List<Integer>> wheels = new LinkedHashMap<>();

        ExtractionBuilder(int concorso, String date) {
            this.concorso = concorso;
            this.date = date;
        }

        Extraction build() {
            return new Extraction(concorso, date, extractYear(date), wheels);
        }
    }
}
