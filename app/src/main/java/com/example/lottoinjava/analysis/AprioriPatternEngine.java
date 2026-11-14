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

public class AprioriPatternEngine {

    public static class AprioriItem {
        public int number;
        public int count;
        public double confidence;
        public double lift;
        public double chiSquare;
        public String status;

        public String getFormatted() {
            return String.format(Locale.ITALIAN, "Numero %02d | Confidenza: %.1f%% | Lift: %.2fx | χ²: %.2f -> %s",
                    number, confidence * 100.0, lift, chiSquare, status);
        }
    }

    public static class AprioriPair {
        public int num1;
        public int num2;
        public int count;
        public double confidence;
        public double lift;

        public String getFormatted() {
            return String.format(Locale.ITALIAN, "Ambo (%02d - %02d) | Frequenza: %d volte | Confidenza: %.1f%% | Lift: %.2fx",
                    num1, num2, count, confidence * 100.0, lift);
        }
    }

    public static class AprioriResult {
        public int spyNumber;
        public String targetWheel;
        public int totalSpyOccurrences;
        public List<AprioriItem> topItems = new ArrayList<>();
        public List<AprioriPair> topPairs = new ArrayList<>();
    }

    public static AprioriResult runAprioriMining(List<Extraction> archive, int spyNumber, String targetWheel, int colpi) {
        AprioriResult result = new AprioriResult();
        result.spyNumber = spyNumber;
        result.targetWheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;
        if (archive == null || archive.isEmpty()) return result;
        int y0 = 9999, y1 = 0;
        for (Extraction e : archive) { y0 = Math.min(y0, e.getYear()); y1 = Math.max(y1, e.getYear()); }
        // Calcolo allineato al main.py (analizza_ia_market_basket_apriori): vedi SantoSpiaCore
        SantoSpiaCore.Apriori a = SantoSpiaCore.apriori(archive, spyNumber, result.targetWheel, y0, y1, colpi);
        if (a.errore != null) return result;
        result.totalSpyOccurrences = a.nSpia;
        for (SantoSpiaCore.AprioriNumero x : a.singoli) {
            AprioriItem it = new AprioriItem();
            it.number = x.numero; it.count = x.count; it.confidence = x.confidence;
            it.lift = x.lift; it.chiSquare = x.chi2; it.status = x.esito;
            result.topItems.add(it);
        }
        for (SantoSpiaCore.AprioriAmbo x : a.ambi) {
            AprioriPair p = new AprioriPair();
            p.num1 = x.a; p.num2 = x.b; p.count = x.count; p.confidence = x.confidence; p.lift = x.lift;
            result.topPairs.add(p);
        }
        return result;
    }
}
