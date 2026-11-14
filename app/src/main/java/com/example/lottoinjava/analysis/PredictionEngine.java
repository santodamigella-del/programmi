package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PredictionEngine {

    public static class PredictionResult {
        private final List<Integer> top5Numbers;
        private final String targetWheel;
        private final int lastConcorsoNumber;
        private final String lastConcorsoDate;
        private final Map<Integer, Double> totalScores;
        private final Map<Integer, String> explanations;

        public PredictionResult(List<Integer> top5Numbers,
                                String targetWheel,
                                int lastConcorsoNumber,
                                String lastConcorsoDate,
                                Map<Integer, Double> totalScores,
                                Map<Integer, String> explanations) {
            this.top5Numbers = top5Numbers;
            this.targetWheel = targetWheel;
            this.lastConcorsoNumber = lastConcorsoNumber;
            this.lastConcorsoDate = lastConcorsoDate;
            this.totalScores = totalScores;
            this.explanations = explanations;
        }

        public List<Integer> getTop5Numbers() {
            return top5Numbers;
        }

        public String getTargetWheel() {
            return targetWheel;
        }

        public int getLastConcorsoNumber() {
            return lastConcorsoNumber;
        }

        public String getLastConcorsoDate() {
            return lastConcorsoDate;
        }

        public Map<Integer, Double> getTotalScores() {
            return totalScores;
        }

        public Map<Integer, String> getExplanations() {
            return explanations;
        }

        public double getScoreForNumber(int num) {
            return totalScores.getOrDefault(num, 0.0);
        }

        public String getExplanationForNumber(int num) {
            return explanations.getOrDefault(num, "Analisi incrociata completata.");
        }
    }

    public static PredictionResult generatePrediction(List<Extraction> archive, String targetWheel) {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("Archivio estrazioni vuoto.");
        }

        String wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;
        Extraction latest = archive.get(0);

        // 1. Calculate Spy Scores (Weight 40%)
        Map<Integer, Double> spyScores = SpyNumberAnalyzer.calculateSpyScores(archive, wheel, 3);

        // 2. Calculate Cyclometric Scores (Weight 35%)
        Map<Integer, Double> cycloScores = CyclometryAnalyzer.calculateCyclometricScores(archive, wheel);

        // 3. Calculate Frequency & Delay Scores (Weight 25%)
        Map<Integer, Double> freqScores = FrequencyAnalyzer.calculateFrequencyScores(archive, wheel);

        // 4. Cross-Verification Combined Scores
        Map<Integer, Double> totalScores = new HashMap<>();
        Map<Integer, String> explanations = new HashMap<>();

        List<Integer> candidateList = new ArrayList<>();
        for (int i = 1; i <= 90; i++) {
            candidateList.add(i);

            double spy = spyScores.getOrDefault(i, 0.0);
            double cyclo = cycloScores.getOrDefault(i, 0.0);
            double freq = freqScores.getOrDefault(i, 0.0);

            // Cross-verification formula
            double combined = (spy * 0.40) + (cyclo * 0.35) + (freq * 0.25);
            totalScores.put(i, combined);

            // Generate detailed explanation
            StringBuilder exp = new StringBuilder();
            exp.append(String.format(Locale.ITALIAN, "★ Punteggio Incrociato: %.1f%%\n", combined));

            if (spy > 40) {
                exp.append(String.format(Locale.ITALIAN, "• Numero Spia: Fortissima convergenza spia (%.0f%%)\n", spy));
            } else if (spy > 15) {
                exp.append(String.format(Locale.ITALIAN, "• Numero Spia: Rilevato da estrazioni recenti (%.0f%%)\n", spy));
            }

            if (cyclo > 40) {
                exp.append(String.format(Locale.ITALIAN, "• Ciclometria: Chiusura armonica figura/diametrale (%.0f%%)\n", cyclo));
            } else if (cyclo > 15) {
                exp.append(String.format(Locale.ITALIAN, "• Ciclometria: Simmetria sul cerchio dei 90 numeri (%.0f%%)\n", cyclo));
            }

            if (freq > 40) {
                exp.append(String.format(Locale.ITALIAN, "• Frequenza: Alta frequenza recente + ritardo bilanciato (%.0f%%)", freq));
            } else {
                exp.append(String.format(Locale.ITALIAN, "• Frequenza: Trend favorevole sul ciclo (%.0f%%)", freq));
            }

            explanations.put(i, exp.toString());
        }

        // Sort 1..90 by combined total score descending
        candidateList.sort((a, b) -> Double.compare(totalScores.get(b), totalScores.get(a)));

        // Select top 5 numbers
        List<Integer> top5 = new ArrayList<>(candidateList.subList(0, 5));
        Collections.sort(top5); // Sort the 5 numbers ascending for clean display

        return new PredictionResult(
                top5,
                wheel,
                latest.getConcorsoNumber(),
                latest.getDate(),
                totalScores,
                explanations
        );
    }
}
