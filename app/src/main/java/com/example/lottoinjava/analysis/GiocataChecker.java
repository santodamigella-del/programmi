package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.Giocata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Verifica una giocata sulle estrazioni successive alla sua creazione. */
public final class GiocataChecker {

    public enum Status { IN_CORSO, VINTA, SCADUTA }

    public static class Result {
        public Status status = Status.IN_CORSO;
        public int colpiEffettuati = 0;
        public int colpoVincente = 0;
        public String ruotaVincente = null;
        public Extraction estrazioneVincente = null;
        public List<Integer> numeriUsciti = new ArrayList<>();
    }

    private GiocataChecker() { }

    public static Result check(Giocata g, List<Extraction> all) {
        Result r = new Result();
        if (all == null) return r;

        // estrazioni successive alla data di creazione, dalla più vecchia alla più recente
        List<Extraction> later = new ArrayList<>();
        for (Extraction e : all) {
            if (e.getDateKey() > g.fromKey) later.add(e);
        }
        Collections.sort(later, (a, b) -> {
            int c = Integer.compare(a.getDateKey(), b.getDateKey());
            return c != 0 ? c : Integer.compare(a.getConcorsoNumber(), b.getConcorsoNumber());
        });

        List<String> ruote = new ArrayList<>();
        if (Giocata.TUTTE.equalsIgnoreCase(g.ruota)) {
            for (String name : Extraction.ALL_RUOTE) {
                if (!"Nazionale".equalsIgnoreCase(name)) ruote.add(name);
            }
        } else {
            ruote.add(g.ruota);
        }

        int limit = Math.min(g.colpi, later.size());
        for (int i = 0; i < limit; i++) {
            Extraction e = later.get(i);
            r.colpiEffettuati = i + 1;

            String bestRuota = null;
            List<Integer> bestHits = null;
            for (String ruota : ruote) {
                List<Integer> nums = e.getNumbersForRuota(ruota);
                if (nums == null || nums.isEmpty()) continue;
                List<Integer> hits = new ArrayList<>();
                for (int n : g.numeri) {
                    if (nums.contains(n)) hits.add(n);
                }
                if (hits.size() >= g.hitsNeeded && (bestHits == null || hits.size() > bestHits.size())) {
                    bestRuota = ruota;
                    bestHits = hits;
                }
            }
            if (bestHits != null) {
                r.status = Status.VINTA;
                r.colpoVincente = i + 1;
                r.ruotaVincente = bestRuota;
                r.estrazioneVincente = e;
                r.numeriUsciti = bestHits;
                return r;
            }
        }

        r.status = r.colpiEffettuati >= g.colpi ? Status.SCADUTA : Status.IN_CORSO;
        return r;
    }
}
