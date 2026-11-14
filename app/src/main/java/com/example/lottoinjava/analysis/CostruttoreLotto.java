package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CostruttoreLotto {

    public static class SommaFissoResult {
        public int bestFisso;
        public double bestHitPercentage;
        public int totalHits;
        public int totalTests;
        public int baseNumber;
        public List<Integer> cinquinaPrevisione = new ArrayList<>();
        public Map<Integer, Integer> coOccurrences = new HashMap<>();

        public String getSummaryText(String ruotaName, String date) {
            return String.format(Locale.ITALIAN,
                    "★ COSTRUTTORE SOMMA + FISSO (%s - Estrazione del %s)\n" +
                            "• Miglior Fisso Storico: %02d (Affidabilità: %.2f%% - %d/%d successi)\n" +
                            "• NUMERO BASE ottenuto: %02d\n" +
                            "• CINQUINA COSTRUITA: %02d - %02d - %02d - %02d - %02d",
                    ruotaName, date, bestFisso, bestHitPercentage, totalHits, totalTests,
                    baseNumber,
                    cinquinaPrevisione.get(0), cinquinaPrevisione.get(1), cinquinaPrevisione.get(2),
                    cinquinaPrevisione.get(3), cinquinaPrevisione.get(4)
            );
        }
    }

    public static SommaFissoResult calcolaSommaFisso(List<Extraction> archive, String targetWheel, int colpi) {
        SommaFissoResult result = new SommaFissoResult();
        if (archive == null || archive.size() < 2) {
            for (int i = 0; i < 5; i++) result.cinquinaPrevisione.add(i + 1);
            return result;
        }
        String wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;
        int y0 = 9999, y1 = 0;
        for (Extraction e : archive) { y0 = Math.min(y0, e.getYear()); y1 = Math.max(y1, e.getYear()); }
        // Calcolo allineato al main.py (_v38_costruttore_somma_fisso): vedi SantoSpiaCore
        try {
            SantoSpiaCore.SommaFisso r = SantoSpiaCore.sommaFisso(archive, wheel, y0, y1);
            result.bestFisso = r.bestFisso;
            result.bestHitPercentage = r.percentuale;
            result.totalHits = r.hits;
            result.totalTests = r.prove;
            result.baseNumber = r.numeroBase;
            result.cinquinaPrevisione.addAll(r.previsione);
            result.coOccurrences.putAll(r.coUscite);
        } catch (IllegalArgumentException e) {
            for (int i = 0; i < 5; i++) result.cinquinaPrevisione.add(i + 1);
        }
        return result;
    }

    public static class SommaRuoteResult {
        public String ruleDescription;
        public int totalEvents;
        public List<Integer> topNumbers = new ArrayList<>();
        public Map<String, Integer> topAmbi = new LinkedHashMap<>();
    }

    public static SommaRuoteResult calcolaSommaRuote(List<Extraction> archive, String wheelA, int posA, String wheelB, int posB, int fisso, String targetWheel, int colpi) {
        SommaRuoteResult result = new SommaRuoteResult();
        result.ruleDescription = String.format(Locale.ITALIAN, "%d° %s + %d° %s + %d", posA, wheelA, posB, wheelB, fisso);
        if (archive == null || archive.isEmpty()) return result;
        int y0 = 9999, y1 = 0;
        for (Extraction e : archive) { y0 = Math.min(y0, e.getYear()); y1 = Math.max(y1, e.getYear()); }
        // Calcolo allineato al main.py (calcola_costruttore_somma_ruote): vedi SantoSpiaCore
        SantoSpiaCore.SommaRuote r = SantoSpiaCore.sommaRuote(archive, wheelA, posA, wheelB, posB, fisso, targetWheel, colpi, y0, y1);
        result.totalEvents = r.eventi;
        result.topNumbers.addAll(r.topNumeri);
        for (int[] x : r.topAmbi) {
            result.topAmbi.put(String.format(Locale.ROOT, "%02d-%02d", x[0], x[1]), x[2]);
        }
        return result;
    }

    private static List<Integer> getAllWheelNumbers(Extraction ext) {
        List<Integer> all = new ArrayList<>();
        for (String w : Extraction.ALL_RUOTE) {
            List<Integer> nums = ext.getNumbersForRuota(w);
            if (nums != null) {
                all.addAll(nums);
            }
        }
        return all;
    }
}
