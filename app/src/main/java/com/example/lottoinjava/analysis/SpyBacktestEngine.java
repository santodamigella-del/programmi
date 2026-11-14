package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Analisi "numero spia" su una ruota. Calcolo allineato al programma Python (Santo Spia Evolutions):
 *  - si considerano solo le estrazioni della ruota nel periodo scelto (anno inizio - anno fine);
 *  - un evento è valido solo se dopo la spia ci sono TUTTI i colpi richiesti (cicli completi);
 *  - frequenze, presenze nei cicli e posizione di prima uscita escludono la spia stessa;
 *  - il test di bontà è walk-forward: per ogni evento si usano solo cicli già conclusi prima di quella spia
 *    (nessuna informazione futura) e si confronta con ciò che ci si aspetta dal puro caso.
 */
public class SpyBacktestEngine {

    /** Come nel programma Python: il test usa al massimo gli ultimi 30 eventi. */
    public static final int DEFAULT_MAX_EVENTS = 30;
    private static final int MIN_TRAIN_EVENTS = 3;

    public static class BacktestResult {
        public int spyNumber;
        public String selectedWheel;
        public int startYear;
        public int endYear;
        public int maxColpi;

        public int wheelDraws;            // estrazioni della ruota nel periodo
        public int spyPresences;          // volte in cui la spia è uscita (anche senza ciclo completo)
        public int totalSpyOccurrences;   // eventi con ciclo completo (= "Estrazioni analizzate" del Python)

        public List<Integer> top5AssociatedNumbers = new ArrayList<>(); // per frequenza (escl. spia)
        public List<Integer> top5ByScore = new ArrayList<>();           // per punteggio (frequenza+cicli+posizione)
        public Map<Integer, Integer> numberFrequencyMap = new HashMap<>();

        // test walk-forward
        public int wfEvents;              // eventi effettivamente testati
        public double wfAvgHits;          // media dei numeri (su 5) centrati nei colpi
        public double wfExpected;         // media attesa dal puro caso
        public int wfBest;
        public Map<Integer, Integer> wfDistribution = new TreeMap<>();
    }

    public static BacktestResult runBacktest(List<Extraction> archive,
                                             int spyNumber,
                                             String targetWheel,
                                             int startYear,
                                             int endYear,
                                             int maxColpi) {
        BacktestResult r = new BacktestResult();
        r.spyNumber = spyNumber;
        r.selectedWheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;
        r.startYear = startYear;
        r.endYear = endYear;
        r.maxColpi = maxColpi;
        r.wfExpected = expectedHits(maxColpi);
        for (int i = 1; i <= 90; i++) r.numberFrequencyMap.put(i, 0);

        if (archive == null || archive.isEmpty() || maxColpi < 1) return r;

        // 1) estrazioni della ruota nel periodo, dalla più vecchia alla più recente
        List<Extraction> sorted = new ArrayList<>(archive);
        Collections.sort(sorted, (a, b) -> {
            int c = Integer.compare(a.getDateKey(), b.getDateKey());
            return c != 0 ? c : Integer.compare(a.getConcorsoNumber(), b.getConcorsoNumber());
        });
        List<int[]> draws = new ArrayList<>();
        for (Extraction e : sorted) {
            int y = e.getYear();
            if (y < startYear || y > endYear) continue;
            List<Integer> nums = e.getNumbersForRuota(r.selectedWheel);
            if (nums == null || nums.size() != 5) continue;
            int[] d = new int[5];
            for (int k = 0; k < 5; k++) d[k] = nums.get(k);
            draws.add(d);
        }
        int n = draws.size();
        r.wheelDraws = n;

        // 2) eventi: spia presente e ciclo completo
        List<Integer> events = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (contains(draws.get(i), spyNumber)) {
                r.spyPresences++;
                if (i + maxColpi < n) events.add(i);
            }
        }
        r.totalSpyOccurrences = events.size();
        if (events.isEmpty()) return r;

        // 3) frequenze / cicli / posizioni (spia esclusa)
        Stats all = collect(draws, events, spyNumber, maxColpi, false);
        for (int x = 1; x <= 90; x++) r.numberFrequencyMap.put(x, all.freq[x]);
        r.top5AssociatedNumbers = topByFrequency(all, spyNumber, 5);
        r.top5ByScore = topByScore(all, spyNumber, maxColpi, events.size(), 5);

        // 4) test walk-forward (senza uso di dati futuri)
        if (events.size() >= MIN_TRAIN_EVENTS) {
            int from = Math.max(0, events.size() - DEFAULT_MAX_EVENTS);
            List<Integer> tested = events.subList(from, events.size());
            int sumHits = 0;
            for (int pos = 0; pos < tested.size(); pos++) {
                int i = tested.get(pos);
                List<Integer> train = new ArrayList<>();
                for (int q = 0; q < pos; q++) {
                    int j = tested.get(q);
                    if (j + maxColpi < i) train.add(j); // ciclo di addestramento concluso prima della spia testata
                }
                if (train.size() < MIN_TRAIN_EVENTS) continue;

                // come nel Python (backtest_lotto): nelle frequenze di addestramento rientra anche la spia
                Stats st = collect(draws, train, spyNumber, maxColpi, true);
                List<Integer> top5 = topByScore(st, spyNumber, maxColpi, train.size(), 5);

                boolean[] real = new boolean[91];
                for (int step = 1; step <= maxColpi; step++) {
                    for (int x : draws.get(i + step)) real[x] = true;
                }
                int hits = 0;
                for (int x : top5) if (real[x]) hits++;
                sumHits += hits;
                r.wfEvents++;
                r.wfBest = Math.max(r.wfBest, hits);
                r.wfDistribution.merge(hits, 1, Integer::sum);
            }
            r.wfAvgHits = r.wfEvents > 0 ? (double) sumHits / r.wfEvents : 0.0;
        }
        return r;
    }

    /** Media attesa dal puro caso di numeri (su 5) che escono almeno una volta in 'colpi' estrazioni. */
    public static double expectedHits(int colpi) {
        return 5.0 * (1.0 - Math.pow(85.0 / 90.0, colpi));
    }

    // ------------------------------------------------------------------ interni

    private static final class Stats {
        final int[] freq = new int[91];
        final int[] cicli = new int[91];
        final int[] sumPos = new int[91];
    }

    private static boolean contains(int[] d, int x) {
        for (int v : d) if (v == x) return true;
        return false;
    }

    private static Stats collect(List<int[]> draws, List<Integer> events, int spy, int colpi, boolean includeSpy) {
        Stats s = new Stats();
        for (int i : events) {
            int[] first = new int[91];
            for (int step = 1; step <= colpi; step++) {
                for (int x : draws.get(i + step)) {
                    if (!includeSpy && x == spy) continue;
                    s.freq[x]++;
                    if (first[x] == 0) first[x] = step;
                }
            }
            for (int x = 1; x <= 90; x++) {
                if (first[x] > 0) {
                    s.cicli[x]++;
                    s.sumPos[x] += first[x];
                }
            }
        }
        return s;
    }

    private static List<Integer> topByFrequency(Stats s, int spy, int howMany) {
        List<Integer> cand = new ArrayList<>();
        for (int x = 1; x <= 90; x++) if (x != spy) cand.add(x);
        Collections.sort(cand, (a, b) -> {
            int c = Integer.compare(s.freq[b], s.freq[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        return new ArrayList<>(cand.subList(0, Math.min(howMany, cand.size())));
    }

    /** Punteggio come calcola_punteggi del Python: 40*freq/max + 40*cicli/eventi + 20*(colpi+1-posMedia)/colpi. */
    private static List<Integer> topByScore(Stats s, int spy, int colpi, int nEvents, int howMany) {
        if (nEvents <= 0) return new ArrayList<>();
        int maxF = 1;
        for (int x = 1; x <= 90; x++) maxF = Math.max(maxF, s.freq[x]);
        final double[] score = new double[91];
        List<Integer> cand = new ArrayList<>();
        for (int x = 1; x <= 90; x++) {
            if (x == spy || s.freq[x] == 0) continue;
            double sf = (double) s.freq[x] / maxF * 40.0;
            double sc = (double) s.cicli[x] / nEvents * 40.0;
            double mediaPos = s.cicli[x] > 0 ? (double) s.sumPos[x] / s.cicli[x] : 0.0;
            double sp = s.cicli[x] > 0 ? (colpi + 1 - mediaPos) / colpi * 20.0 : 0.0;
            score[x] = sf + sc + sp;
            cand.add(x);
        }
        Collections.sort(cand, (a, b) -> {
            int c = Double.compare(score[b], score[a]);
            if (c != 0) return c;
            c = Integer.compare(s.freq[b], s.freq[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        return new ArrayList<>(cand.subList(0, Math.min(howMany, cand.size())));
    }
}
