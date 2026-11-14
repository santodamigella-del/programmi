package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrequencyAnalyzer {

    public static Map<Integer, Integer> getFrequencies(List<Extraction> archive, String targetWheel, int limit) {
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int i = 1; i <= 90; i++) {
            freqMap.put(i, 0);
        }

        if (archive == null || archive.isEmpty()) {
            return freqMap;
        }

        int count = Math.min(limit, archive.size());
        for (int idx = 0; idx < count; idx++) {
            Extraction ext = archive.get(idx);
            List<Integer> nums = ext.getNumbersForRuota(targetWheel);
            if (nums != null) {
                for (int num : nums) {
                    if (num >= 1 && num <= 90) {
                        freqMap.put(num, freqMap.get(num) + 1);
                    }
                }
            }
        }

        return freqMap;
    }

    public static Map<Integer, Integer> getDelays(List<Extraction> archive, String targetWheel) {
        Map<Integer, Integer> delayMap = new HashMap<>();
        for (int i = 1; i <= 90; i++) {
            delayMap.put(i, archive.size()); // Max delay if not found
        }

        if (archive == null || archive.isEmpty()) {
            return delayMap;
        }

        for (int idx = 0; idx < archive.size(); idx++) {
            Extraction ext = archive.get(idx);
            List<Integer> nums = ext.getNumbersForRuota(targetWheel);
            if (nums != null) {
                for (int num : nums) {
                    if (num >= 1 && num <= 90 && delayMap.get(num) == archive.size()) {
                        delayMap.put(num, idx); // Number of extractions elapsed since last appearance
                    }
                }
            }
        }

        return delayMap;
    }

    public static Map<Integer, Double> calculateFrequencyScores(List<Extraction> archive, String targetWheel) {
        Map<Integer, Integer> freqMap = getFrequencies(archive, targetWheel, 30);
        Map<Integer, Integer> delayMap = getDelays(archive, targetWheel);

        Map<Integer, Double> scores = new HashMap<>();

        // Score combines high frequency in recent extractions with optimal delay balance
        for (int i = 1; i <= 90; i++) {
            int freq = freqMap.get(i);
            int delay = delayMap.get(i);

            // Frequent numbers (frequenti) + ideal delay window (ritardo tra 5 e 25 estrazioni)
            double freqScore = freq * 15.0;
            double delayScore = 0.0;
            if (delay >= 5 && delay <= 25) {
                delayScore = 20.0;
            } else if (delay > 25 && delay <= 50) {
                delayScore = 15.0;
            } else if (delay > 50) {
                delayScore = 25.0; // High delay bonus (ritardatario)
            }

            scores.put(i, freqScore + delayScore);
        }

        // Normalize (0 to 100)
        double maxScore = 0.0;
        for (double val : scores.values()) {
            if (val > maxScore) {
                maxScore = val;
            }
        }

        if (maxScore > 0) {
            for (Map.Entry<Integer, Double> entry : scores.entrySet()) {
                scores.put(entry.getKey(), (entry.getValue() / maxScore) * 100.0);
            }
        }

        return scores;
    }
}
