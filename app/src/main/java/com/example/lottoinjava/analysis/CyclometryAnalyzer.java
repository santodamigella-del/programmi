package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CyclometryAnalyzer {

    /**
     * Calculates the cyclometric distance between two numbers on the 90-circle (1..90).
     */
    public static int getCyclometricDistance(int a, int b) {
        int diff = Math.abs(a - b);
        return Math.min(diff, 90 - diff);
    }

    /**
     * Calculates the diametral in distance 45.
     */
    public static int getDiametral(int a) {
        int res = a + 45;
        return res > 90 ? res - 90 : res;
    }

    /**
     * Calculates the complementary number (Sum = 90).
     */
    public static int getComplementary(int a) {
        int res = 90 - a;
        return res <= 0 ? res + 90 : res;
    }

    /**
     * Calculates cyclometric harmonic closure scores for numbers (1..90)
     * based on geometric patterns in the latest extraction on targetWheel.
     */
    public static Map<Integer, Double> calculateCyclometricScores(List<Extraction> archive, String targetWheel) {
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 1; i <= 90; i++) {
            scores.put(i, 0.0);
        }

        if (archive == null || archive.isEmpty()) {
            return scores;
        }

        // Analyze recent extractions for cyclometric structures
        int checkLimit = Math.min(3, archive.size());

        for (int extIdx = 0; extIdx < checkLimit; extIdx++) {
            Extraction ext = archive.get(extIdx);
            List<Integer> nums = ext.getNumbersForRuota(targetWheel);

            if (nums == null || nums.size() < 2) {
                continue;
            }

            double recencyWeight = (checkLimit - extIdx) * 1.5;

            // 1. Diametral and Complementary closures
            for (int n : nums) {
                int diam = getDiametral(n);
                int comp = getComplementary(n);

                scores.put(diam, scores.get(diam) + 15.0 * recencyWeight);
                scores.put(comp, scores.get(comp) + 12.0 * recencyWeight);
            }

            // 2. Pair distances and missing closure points (E.g. Equal Distances 30, 15, 45, 18)
            int len = nums.size();
            for (int i = 0; i < len; i++) {
                for (int j = i + 1; j < len; j++) {
                    int a = nums.get(i);
                    int b = nums.get(j);
                    int dist = getCyclometricDistance(a, b);

                    // Symmetric closures on the circle
                    int closure1 = normalize90(a + dist);
                    int closure2 = normalize90(a - dist);
                    int closure3 = normalize90(b + dist);
                    int closure4 = normalize90(b - dist);

                    addScore(scores, closure1, 10.0 * recencyWeight);
                    addScore(scores, closure2, 10.0 * recencyWeight);
                    addScore(scores, closure3, 10.0 * recencyWeight);
                    addScore(scores, closure4, 10.0 * recencyWeight);

                    // Special harmonic distances: 30 (Triangle), 45 (Diametral), 15, 18
                    if (dist == 30 || dist == 45 || dist == 15 || dist == 18 || dist == 10) {
                        int midpoint = normalize90((a + b) / 2);
                        addScore(scores, midpoint, 14.0 * recencyWeight);
                    }
                }
            }
        }

        // Normalize scores (0 to 100)
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

    private static void addScore(Map<Integer, Double> scores, int num, double add) {
        if (num >= 1 && num <= 90) {
            scores.put(num, scores.getOrDefault(num, 0.0) + add);
        }
    }

    private static int normalize90(int val) {
        while (val <= 0) {
            val += 90;
        }
        while (val > 90) {
            val -= 90;
        }
        return val;
    }
}
