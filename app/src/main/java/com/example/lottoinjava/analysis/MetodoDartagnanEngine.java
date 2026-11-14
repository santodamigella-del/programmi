package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MetodoDartagnanEngine {

    public static class DartagnanResult {
        public int targetSpy;
        public String wheel;
        public int moschettiere1; // Spy + 30
        public int moschettiere2; // Spy - 30 (or + 60)
        public int moschettiere3; // Spy + 15 or + 45
        public int dartagnanNumber; // Diametrale (Spy + 45)
        public List<Integer> quartettaDartagnan = new ArrayList<>();
        public Map<Integer, Integer> postSpyHits = new HashMap<>();
        public double hitRatePercentage;
        public String reportText;
    }

    /**
     * Metodo D'Artagnan:
     * Constructs the 3 Musketeers (I Tre Moschettieri) based on 30-degree equilateral distance on the 90-circle
     * plus D'Artagnan (the Diametral number = Spy + 45).
     */
    public static DartagnanResult calcolaMetodoDartagnan(List<Extraction> archive, int spyNumber, String targetWheel, int colpi) {
        DartagnanResult result = new DartagnanResult();
        result.targetSpy = spyNumber;
        result.wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;

        // 1. Calculate Musketeers on the 90-circle
        int m1 = ((spyNumber + 30 - 1) % 90) + 1;
        int m2 = ((spyNumber + 60 - 1) % 90) + 1;
        int m3 = ((spyNumber + 15 - 1) % 90) + 1;
        int dartagnan = CyclometryAnalyzer.getDiametral(spyNumber);

        result.moschettiere1 = m1;
        result.moschettiere2 = m2;
        result.moschettiere3 = m3;
        result.dartagnanNumber = dartagnan;

        List<Integer> quartetta = new ArrayList<>();
        quartetta.add(m1);
        if (!quartetta.contains(m2)) quartetta.add(m2);
        if (!quartetta.contains(m3)) quartetta.add(m3);
        if (!quartetta.contains(dartagnan)) quartetta.add(dartagnan);

        Collections.sort(quartetta);
        result.quartettaDartagnan = quartetta;

        if (archive == null || archive.isEmpty()) {
            return result;
        }

        // Backtest historical hits of the D'Artagnan quartetta
        int spyEvents = 0;
        int winningEvents = 0;

        for (int i = 0; i < archive.size(); i++) {
            Extraction ext = archive.get(i);
            List<Integer> nums = ext.getNumbersForRuota(result.wheel);

            if (nums != null && nums.contains(spyNumber)) {
                spyEvents++;
                boolean hit = false;

                for (int step = 1; step <= colpi && (i - step) >= 0; step++) {
                    Extraction future = archive.get(i - step);
                    List<Integer> futureNums = future.getNumbersForRuota(result.wheel);

                    if (futureNums != null) {
                        for (int qNum : quartetta) {
                            if (futureNums.contains(qNum)) {
                                hit = true;
                                result.postSpyHits.put(qNum, result.postSpyHits.getOrDefault(qNum, 0) + 1);
                            }
                        }
                    }
                }
                if (hit) winningEvents++;
            }
        }

        result.hitRatePercentage = spyEvents > 0 ? ((double) winningEvents / spyEvents) * 100.0 : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("🗡️ METODO D'ARTAGNAN & I TRE MOSCHETTIERI\n");
        sb.append("====================================================\n");
        sb.append(String.format(Locale.ITALIAN, "Spia di Origine: %02d (Ruota %s)\n", spyNumber, result.wheel.toUpperCase()));
        sb.append(String.format(Locale.ITALIAN, "• 1° Moschettiere (Distanza 30) : %02d\n", m1));
        sb.append(String.format(Locale.ITALIAN, "• 2° Moschettiere (Distanza 60) : %02d\n", m2));
        sb.append(String.format(Locale.ITALIAN, "• 3° Moschettiere (Distanza 15) : %02d\n", m3));
        sb.append(String.format(Locale.ITALIAN, "• D'ARTAGNAN (Diametrale +45)   : %02d\n\n", dartagnan));

        sb.append(String.format(Locale.ITALIAN, "QUARTETTA D'ARTAGNAN: %02d - %02d - %02d - %02d\n",
                quartetta.get(0), quartetta.get(1),
                quartetta.size() > 2 ? quartetta.get(2) : 0,
                quartetta.size() > 3 ? quartetta.get(3) : 0));

        sb.append("----------------------------------------------------\n");
        sb.append(String.format(Locale.ITALIAN, "Esito Storico Post-Spia: %d/%d eventi vincenti (%.1f%% nei %d colpi)\n",
                winningEvents, spyEvents, result.hitRatePercentage, colpi));

        result.reportText = sb.toString();
        return result;
    }
}
