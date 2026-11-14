package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IsotopiaEngine {

    public static class IsotopicMatch {
        public String date;
        public String wheelA;
        public String wheelB;
        public int position; // 1 to 5
        public int numberA;
        public int numberB;
        public int distance;
        public int sum;
    }

    public static class IsotopiaResult {
        public String wheelA;
        public String wheelB;
        public List<IsotopicMatch> matches = new ArrayList<>();
        public List<Integer> topConvergentNumbers = new ArrayList<>();
        public String reportText;
    }

    /**
     * Finds Isotopic occurrences (same position extracted on Wheel A and Wheel B)
     */
    public static IsotopiaResult analyzeIsotopia(List<Extraction> archive, String wheelA, String wheelB, int colpi) {
        IsotopiaResult result = new IsotopiaResult();
        result.wheelA = (wheelA == null || wheelA.isEmpty()) ? "Bari" : wheelA;
        result.wheelB = (wheelB == null || wheelB.isEmpty()) ? "Milano" : wheelB;

        if (archive == null || archive.isEmpty()) {
            result.reportText = "Archivio vuoto.";
            return result;
        }

        List<Extraction> chronological = new ArrayList<>(archive);
        Collections.reverse(chronological); // Oldest first
        int nExt = chronological.size();

        Map<Integer, Integer> freqMap = new HashMap<>();

        for (int i = 0; i < nExt; i++) {
            Extraction ext = chronological.get(i);
            List<Integer> numsA = ext.getNumbersForRuota(result.wheelA);
            List<Integer> numsB = ext.getNumbersForRuota(result.wheelB);

            if (numsA != null && numsB != null && numsA.size() >= 5 && numsB.size() >= 5) {
                for (int pos = 0; pos < 5; pos++) {
                    int nA = numsA.get(pos);
                    int nB = numsB.get(pos);

                    // Isotopic condition: check distance or sum
                    int dist = CyclometryAnalyzer.getCyclometricDistance(nA, nB);
                    int sum = (nA + nB) % 90;

                    IsotopicMatch match = new IsotopicMatch();
                    match.date = ext.getDate();
                    match.wheelA = result.wheelA;
                    match.wheelB = result.wheelB;
                    match.position = pos + 1;
                    match.numberA = nA;
                    match.numberB = nB;
                    match.distance = dist;
                    match.sum = sum;

                    result.matches.add(match);

                    // Track post-isotopic outputs in subsequent colpi
                    for (int step = 1; step <= colpi && (i + step) < nExt; step++) {
                        Extraction future = chronological.get(i + step);
                        List<Integer> futureA = future.getNumbersForRuota(result.wheelA);
                        List<Integer> futureB = future.getNumbersForRuota(result.wheelB);

                        if (futureA != null) {
                            for (int fn : futureA) freqMap.put(fn, freqMap.getOrDefault(fn, 0) + 1);
                        }
                        if (futureB != null) {
                            for (int fn : futureB) freqMap.put(fn, freqMap.getOrDefault(fn, 0) + 1);
                        }
                    }
                }
            }
        }

        List<Integer> sortedNums = new ArrayList<>(freqMap.keySet());
        sortedNums.sort((a, b) -> Integer.compare(freqMap.get(b), freqMap.get(a)));
        result.topConvergentNumbers = new ArrayList<>(sortedNums.subList(0, Math.min(5, sortedNums.size())));

        StringBuilder sb = new StringBuilder();
        sb.append("📐 ANALISI ISOTOPIA ESTRAZIONALE\n");
        sb.append("====================================================\n");
        sb.append(String.format(Locale.ITALIAN, "Ruota A: %s | Ruota B: %s\n", result.wheelA.toUpperCase(), result.wheelB.toUpperCase()));
        sb.append(String.format(Locale.ITALIAN, "Condizioni Isotope Trovate: %d eventi\n\n", result.matches.size()));

        sb.append("TOP 5 NUMERI CONVERGENTI POST-ISOTOPIA:\n");
        for (int i = 0; i < result.topConvergentNumbers.size(); i++) {
            int num = result.topConvergentNumbers.get(i);
            int freq = freqMap.getOrDefault(num, 0);
            sb.append(String.format(Locale.ITALIAN, "%d. Numero %02d (Frequenza Post-Isotopia: %d)\n", (i + 1), num, freq));
        }

        result.reportText = sb.toString();
        return result;
    }
}
