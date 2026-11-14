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

public class LottoPunteggiEngine {

    public static class NumeroPunteggio {
        public int numero;
        public int frequenza;
        public int cicli;
        public double percentualeCicli;
        public double mediaPosizione;
        public double punteggio;

        public String getFormatted() {
            return String.format(Locale.ITALIAN, "%02d | Score: %.2f | Freq: %d | Cicli: %d (%.1f%%) | Pos: %.2f",
                    numero, punteggio, frequenza, cicli, percentualeCicli, mediaPosizione);
        }
    }

    public static class PunteggiResult {
        public int spyNumber;
        public String wheel;
        public int annoInizio;
        public int annoFine;
        public int colpi;
        public int totalCycles;
        public List<NumeroPunteggio> top5Frequenza = new ArrayList<>();
        public List<NumeroPunteggio> top5Cicli = new ArrayList<>();
        public List<NumeroPunteggio> top5StatisticaAvanzata = new ArrayList<>();
        public List<NumeroPunteggio> classificaCompleta = new ArrayList<>();
        public String reportText;
    }

    /**
     * Exact 1:1 implementation of 'analizza_numero_spia', 'calcola_punteggi', and 'costruisci_risultato' from main.py
     */
    public static PunteggiResult calcolaPunteggiMainPy(List<Extraction> archive, int spyNumber, String targetWheel, int startYear, int endYear, int colpi) {
        PunteggiResult result = new PunteggiResult();
        result.spyNumber = spyNumber;
        result.wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Nazionale" : targetWheel;
        result.annoInizio = startYear;
        result.annoFine = endYear;
        result.colpi = colpi;

        if (archive == null || archive.isEmpty()) {
            result.reportText = "Archivio vuoto.";
            return result;
        }

        // Filter chronological extractions (oldest first) for target wheel and year range
        List<Extraction> chronological = new ArrayList<>();
        for (Extraction ext : archive) {
            int year = ext.getYear();
            if (year >= startYear && year <= endYear) {
                List<Integer> nums = ext.getNumbersForRuota(result.wheel);
                if (nums != null && !nums.isEmpty()) {
                    chronological.add(ext);
                }
            }
        }
        Collections.reverse(chronological); // Oldest first

        Map<Integer, Integer> frequenze = new HashMap<>();
        Map<Integer, Integer> presenzeCicli = new HashMap<>();
        Map<Integer, List<Integer>> posizioniMap = new HashMap<>();

        for (int i = 1; i <= 90; i++) {
            frequenze.put(i, 0);
            presenzeCicli.put(i, 0);
            posizioniMap.put(i, new ArrayList<>());
        }

        int numeroCicli = 0;

        for (int i = 0; i < chronological.size(); i++) {
            Extraction ext = chronological.get(i);
            List<Integer> nums = ext.getNumbersForRuota(result.wheel);

            if (nums != null && nums.contains(spyNumber)) {
                // Look at subsequent colpi extractions
                if (i + colpi < chronological.size()) {
                    numeroCicli++;
                    Set<Integer> numeriCiclo = new HashSet<>();
                    Map<Integer, Integer> primaPosizione = new HashMap<>();

                    for (int step = 1; step <= colpi; step++) {
                        Extraction future = chronological.get(i + step);
                        List<Integer> futureNums = future.getNumbersForRuota(result.wheel);

                        if (futureNums != null) {
                            for (int fn : futureNums) {
                                if (fn != spyNumber) {
                                    frequenze.put(fn, frequenze.getOrDefault(fn, 0) + 1);
                                    numeriCiclo.add(fn);
                                    if (!primaPosizione.containsKey(fn)) {
                                        primaPosizione.put(fn, step);
                                    }
                                }
                            }
                        }
                    }

                    for (int fn : numeriCiclo) {
                        presenzeCicli.put(fn, presenzeCicli.getOrDefault(fn, 0) + 1);
                        if (primaPosizione.containsKey(fn)) {
                            posizioniMap.get(fn).add(primaPosizione.get(fn));
                        }
                    }
                }
            }
        }

        result.totalCycles = numeroCicli;
        if (numeroCicli == 0) {
            result.reportText = String.format(Locale.ITALIAN,
                    "Nessuna occorrenza del Numero Spia %d trovata sulla ruota %s nel periodo %d - %d.",
                    spyNumber, result.wheel, startYear, endYear);
            return result;
        }

        // Calculate max frequency
        int maxFreq = 1;
        for (int f : frequenze.values()) {
            if (f > maxFreq) maxFreq = f;
        }

        // Calculate 1:1 formula scores
        List<NumeroPunteggio> listAll = new ArrayList<>();

        for (int n = 1; n <= 90; n++) {
            if (n == spyNumber) continue;

            int freq = frequenze.getOrDefault(n, 0);
            int cicli = presenzeCicli.getOrDefault(n, 0);

            if (freq == 0) continue;

            double scoreFreq = ((double) freq / maxFreq) * 40.0;
            double scoreCicli = ((double) cicli / numeroCicli) * 40.0;

            List<Integer> posList = posizioniMap.get(n);
            double mediaPos = 0.0;
            double scorePos = 0.0;

            if (posList != null && !posList.isEmpty()) {
                int sum = 0;
                for (int p : posList) sum += p;
                mediaPos = (double) sum / posList.size();
                scorePos = (((double) colpi + 1.0 - mediaPos) / colpi) * 20.0;
            }

            double punteggioT = scoreFreq + scoreCicli + scorePos;

            NumeroPunteggio np = new NumeroPunteggio();
            np.numero = n;
            np.frequenza = freq;
            np.cicli = cicli;
            np.percentualeCicli = ((double) cicli / numeroCicli) * 100.0;
            np.mediaPosizione = mediaPos;
            np.punteggio = punteggioT;

            listAll.add(np);
        }

        // 1. Sort for Top Frequenza
        List<NumeroPunteggio> byFreq = new ArrayList<>(listAll);
        byFreq.sort((a, b) -> {
            int cmp = Integer.compare(b.frequenza, a.frequenza);
            return cmp != 0 ? cmp : Integer.compare(a.numero, b.numero);
        });
        result.top5Frequenza = new ArrayList<>(byFreq.subList(0, Math.min(5, byFreq.size())));

        // 2. Sort for Top Cicli
        List<NumeroPunteggio> byCicli = new ArrayList<>(listAll);
        byCicli.sort((a, b) -> {
            int cmp = Integer.compare(b.cicli, a.cicli);
            return cmp != 0 ? cmp : Integer.compare(a.numero, b.numero);
        });
        result.top5Cicli = new ArrayList<>(byCicli.subList(0, Math.min(5, byCicli.size())));

        // 3. Sort for Top Statistica Avanzata (Punteggio -> Frequenza -> Numero)
        List<NumeroPunteggio> byScore = new ArrayList<>(listAll);
        byScore.sort((a, b) -> {
            int cmp = Double.compare(b.punteggio, a.punteggio);
            if (cmp != 0) return cmp;
            int cmpF = Integer.compare(b.frequenza, a.frequenza);
            return cmpF != 0 ? cmpF : Integer.compare(a.numero, b.numero);
        });

        result.top5StatisticaAvanzata = new ArrayList<>(byScore.subList(0, Math.min(5, byScore.size())));
        result.classificaCompleta = byScore;

        // Build 1:1 Report Text matching main.py
        StringBuilder sb = new StringBuilder();
        sb.append("ANALISI NUMERO SPIA (Formato Identico a main.py)\n");
        sb.append("=============================================\n");
        sb.append(String.format(Locale.ITALIAN, "Numero spia: %02d\n", spyNumber));
        sb.append(String.format(Locale.ITALIAN, "Ruota: %s\n", result.wheel));
        sb.append(String.format(Locale.ITALIAN, "Periodo: %d - %d\n", startYear, endYear));
        sb.append(String.format(Locale.ITALIAN, "Colpi in gioco: %d\n", colpi));
        sb.append(String.format(Locale.ITALIAN, "Estrazioni/Cicli analizzati: %d\n\n", numeroCicli));

        sb.append("TOP 5 PER FREQUENZA\n---------------------------------------------\n");
        for (int i = 0; i < result.top5Frequenza.size(); i++) {
            NumeroPunteggio np = result.top5Frequenza.get(i);
            sb.append(String.format(Locale.ITALIAN, "%d. %02d  %d uscite\n", (i + 1), np.numero, np.frequenza));
        }

        sb.append("\nTOP 5 PER PRESENZA NEI CICLI\n---------------------------------------------\n");
        for (int i = 0; i < result.top5Cicli.size(); i++) {
            NumeroPunteggio np = result.top5Cicli.get(i);
            sb.append(String.format(Locale.ITALIAN, "%d. %02d  %d/%d cicli (%.2f%%)\n", (i + 1), np.numero, np.cicli, numeroCicli, np.percentualeCicli));
        }

        sb.append("\nTOP 5 STATISTICA AVANZATA (main.py Score)\n---------------------------------------------\n");
        for (int i = 0; i < result.top5StatisticaAvanzata.size(); i++) {
            NumeroPunteggio np = result.top5StatisticaAvanzata.get(i);
            sb.append(String.format(Locale.ITALIAN, "%d. %02d  Score: %.2f  Freq: %d  Cicli: %d  Pos: %.2f\n",
                    (i + 1), np.numero, np.punteggio, np.frequenza, np.cicli, np.mediaPosizione));
        }

        result.reportText = sb.toString();
        return result;
    }
}
