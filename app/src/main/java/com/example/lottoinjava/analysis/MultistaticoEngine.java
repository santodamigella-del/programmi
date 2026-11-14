package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MultistaticoEngine {

    public static class MultistaticoResult {
        public List<Integer> spyNumbers = new ArrayList<>();
        public String targetWheel;
        public int totalCoOccurrences;
        public List<Integer> topConvergentNumbers = new ArrayList<>();
        public Map<String, Integer> topAmbi = new HashMap<>();
        public String reportText;
    }

    public static MultistaticoResult runMultistaticoAnalysis(List<Extraction> archive, List<Integer> spyList, String targetWheel, int colpi) {
        MultistaticoResult result = new MultistaticoResult();
        result.spyNumbers = spyList != null ? spyList : new ArrayList<>();
        result.targetWheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;

        if (archive == null || archive.isEmpty() || result.spyNumbers.isEmpty()) {
            result.reportText = "Parametri o archivio insufficienti.";
            return result;
        }

        List<Extraction> chronological = new ArrayList<>(archive);
        Collections.reverse(chronological);
        int nExt = chronological.size();

        Set<Integer> spySet = new HashSet<>(result.spyNumbers);
        Map<Integer, Integer> freqMap = new HashMap<>();
        Map<String, Integer> ambiMap = new HashMap<>();

        int coOccurrences = 0;

        for (int i = 0; i < nExt; i++) {
            Extraction ext = chronological.get(i);
            List<Integer> nums = ext.getNumbersForRuota(result.targetWheel);

            if (nums != null && nums.containsAll(spySet)) {
                coOccurrences++;

                for (int step = 1; step <= colpi && (i + step) < nExt; step++) {
                    Extraction future = chronological.get(i + step);
                    List<Integer> futureNums = future.getNumbersForRuota(result.targetWheel);

                    if (futureNums != null) {
                        List<Integer> filteredFuture = new ArrayList<>();
                        for (int fn : futureNums) {
                            if (!spySet.contains(fn)) {
                                freqMap.put(fn, freqMap.getOrDefault(fn, 0) + 1);
                                filteredFuture.add(fn);
                            }
                        }

                        // Track ambi
                        Collections.sort(filteredFuture);
                        for (int a = 0; a < filteredFuture.size(); a++) {
                            for (int b = a + 1; b < filteredFuture.size(); b++) {
                                String key = filteredFuture.get(a) + "-" + filteredFuture.get(b);
                                ambiMap.put(key, ambiMap.getOrDefault(key, 0) + 1);
                            }
                        }
                    }
                }
            }
        }

        result.totalCoOccurrences = coOccurrences;

        List<Integer> sortedNums = new ArrayList<>(freqMap.keySet());
        sortedNums.sort((a, b) -> Integer.compare(freqMap.get(b), freqMap.get(a)));

        result.topConvergentNumbers = new ArrayList<>(sortedNums.subList(0, Math.min(6, sortedNums.size())));

        StringBuilder sb = new StringBuilder();
        sb.append("📊 COSTRUTTORE MULTISTATICO / MATRICE MULTISPIA\n");
        sb.append("====================================================\n");
        sb.append(String.format(Locale.ITALIAN, "Spie Contemporanee: %s su Ruota %s\n", spySet.toString(), result.targetWheel.toUpperCase()));
        sb.append(String.format(Locale.ITALIAN, "Presenze Contemporanee nello Storico: %d volte\n", coOccurrences));
        sb.append("----------------------------------------------------\n");
        sb.append("NUMERI CONVERGENTI MULTISPIA:\n");

        if (result.topConvergentNumbers.isEmpty()) {
            sb.append("Nessuna presenza contemporanea trovata delle spie nell'archivio.\n");
        } else {
            for (int i = 0; i < result.topConvergentNumbers.size(); i++) {
                int num = result.topConvergentNumbers.get(i);
                int freq = freqMap.getOrDefault(num, 0);
                double pct = coOccurrences > 0 ? ((double) freq / coOccurrences) * 100.0 : 0.0;
                sb.append(String.format(Locale.ITALIAN, "%02d. Numero %02d | Presenze: %d (%1.1f%%)\n", (i + 1), num, freq, pct));
            }
        }

        result.reportText = sb.toString();
        return result;
    }
}
