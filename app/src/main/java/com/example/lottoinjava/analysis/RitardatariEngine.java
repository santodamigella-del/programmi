package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Ritardi dei numeri (1-90) su una ruota: ritardo attuale, ritardo massimo storico, uscite totali. */
public final class RitardatariEngine {

    public static class Entry {
        public final int number;
        public final int delay;     // estrazioni passate dall'ultima uscita
        public final int maxDelay;  // massimo ritardo storico
        public final int hits;      // volte uscito

        Entry(int number, int delay, int maxDelay, int hits) {
            this.number = number;
            this.delay = delay;
            this.maxDelay = maxDelay;
            this.hits = hits;
        }
    }

    public static class Result {
        public final String ruota;
        public final int drawsConsidered;
        public final List<Entry> entries; // ordinati per ritardo attuale decrescente

        Result(String ruota, int drawsConsidered, List<Entry> entries) {
            this.ruota = ruota;
            this.drawsConsidered = drawsConsidered;
            this.entries = entries;
        }
    }

    private RitardatariEngine() { }

    public static Result compute(List<Extraction> all, String ruota) {
        List<Extraction> sorted = new ArrayList<>();
        if (all != null) sorted.addAll(all);
        Collections.sort(sorted, (a, b) -> {
            int c = Integer.compare(a.getDateKey(), b.getDateKey());
            return c != 0 ? c : Integer.compare(a.getConcorsoNumber(), b.getConcorsoNumber());
        });

        int[] lastIdx = new int[91];
        int[] maxDelay = new int[91];
        int[] hits = new int[91];
        Arrays.fill(lastIdx, -1);

        int idx = 0;
        for (Extraction e : sorted) {
            List<Integer> nums = e.getNumbersForRuota(ruota);
            if (nums == null || nums.size() < 5) continue;
            for (int n = 1; n <= 90; n++) {
                if (nums.contains(n)) {
                    int gap = idx - lastIdx[n] - 1; // se mai uscito: lastIdx = -1 -> gap = idx
                    if (gap > maxDelay[n]) maxDelay[n] = gap;
                    lastIdx[n] = idx;
                    hits[n]++;
                }
            }
            idx++;
        }

        List<Entry> entries = new ArrayList<>();
        for (int n = 1; n <= 90; n++) {
            int delay = lastIdx[n] == -1 ? idx : idx - 1 - lastIdx[n];
            entries.add(new Entry(n, delay, Math.max(maxDelay[n], delay), hits[n]));
        }
        Collections.sort(entries, (a, b) -> {
            int c = Integer.compare(b.delay, a.delay);
            return c != 0 ? c : Integer.compare(a.number, b.number);
        });
        return new Result(ruota, idx, entries);
    }
}
