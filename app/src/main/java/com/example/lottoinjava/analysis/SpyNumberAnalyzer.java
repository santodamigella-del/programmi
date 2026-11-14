package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpyNumberAnalyzer {

    /**
     * Calculates Spy Number scores for candidate numbers (1..90)
     * based on the spy numbers present in the latest extraction on targetWheel.
     */
    public static Map<Integer, Double> calculateSpyScores(List<Extraction> archive, String targetWheel, int windowSize) {
        Map<Integer, Double> spyScores = new HashMap<>();
        for (int i = 1; i <= 90; i++) {
            spyScores.put(i, 0.0);
        }

        if (archive == null || archive.isEmpty()) {
            return spyScores;
        }

        // Latest extraction triggers the spy search
        Extraction latest = archive.get(0);
        List<Integer> triggerNumbers = latest.getNumbersForRuota(targetWheel);

        if (triggerNumbers == null || triggerNumbers.isEmpty()) {
            // Fallback to Bari or any available wheel if empty
            triggerNumbers = latest.getNumbersForRuota("Bari");
        }

        if (triggerNumbers == null || triggerNumbers.isEmpty()) {
            return spyScores;
        }

        // Search history in reverse (chronological) order
        int archiveSize = archive.size();
        for (int idx = 1; idx < archiveSize; idx++) {
            Extraction pastExt = archive.get(idx);
            List<Integer> pastNums = pastExt.getNumbersForRuota(targetWheel);

            if (pastNums == null || pastNums.isEmpty()) {
                continue;
            }

            // Check how many trigger numbers appeared in this past extraction
            int triggerCount = 0;
            for (int trigger : triggerNumbers) {
                if (pastNums.contains(trigger)) {
                    triggerCount++;
                }
            }

            if (triggerCount > 0) {
                // Look at the subsequent windowSize extractions (which happened after pastExt, i.e. idx-1 down to idx-windowSize)
                for (int step = 1; step <= windowSize; step++) {
                    int nextIdx = idx - step;
                    if (nextIdx >= 0) {
                        Extraction nextExt = archive.get(nextIdx);
                        List<Integer> nextNums = nextExt.getNumbersForRuota(targetWheel);
                        if (nextNums != null) {
                            double weight = (double) triggerCount / step; // Higher weight for closer extractions
                            for (int num : nextNums) {
                                spyScores.put(num, spyScores.getOrDefault(num, 0.0) + weight);
                            }
                        }
                    }
                }
            }
        }

        // Normalize scores between 0 and 100
        double maxScore = 0.0;
        for (double val : spyScores.values()) {
            if (val > maxScore) {
                maxScore = val;
            }
        }

        if (maxScore > 0) {
            for (Map.Entry<Integer, Double> entry : spyScores.entrySet()) {
                spyScores.put(entry.getKey(), (entry.getValue() / maxScore) * 100.0);
            }
        }

        return spyScores;
    }
}
