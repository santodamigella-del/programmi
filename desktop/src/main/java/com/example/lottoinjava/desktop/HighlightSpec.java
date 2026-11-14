package com.example.lottoinjava.desktop;

import com.example.lottoinjava.analysis.SantoSpiaCore;
import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Descrive come colorare l'archivio per verificare a mano il risultato di una tecnica:
 *  - SPIA in VERDE (nelle ruote della spia);
 *  - RISULTATO in ROSSO (nei colpi successivi alla spia; colpi = 0 significa "nella stessa estrazione").
 */
final class HighlightSpec {

    final String title;
    final List<Integer> spies;        // vuota = tecnica senza spia
    final List<String> spyWheels;     // ruote in cui cercare la spia
    final List<Integer> numbers;      // numeri risultato
    final List<String> targetWheels;  // ruote in cui colorare i risultati
    final int colpi;                  // 0 = stessa estrazione
    final int yearFrom;
    final int yearTo;

    // calcolati da prepare()
    final Set<Integer> spyKeys = new HashSet<>();     // date (aaaammgg) in cui è uscita la spia
    final Set<Integer> windowKeys = new HashSet<>();  // date in cui i risultati vanno colorati
    int events;                                       // cicli completi (o estrazioni, se colpi = 0)
    // Cinquina dei numeri più associati (BLU NOTTE). Se vuota viene calcolata in prepare().
    final List<Integer> cinquina = new ArrayList<>();
    private boolean cinquinaManual = false;
    private int spyPos = 0;           // 0 = spia in qualsiasi posizione; 1-5 = solo in quella posizione
    private boolean spyAll = false;   // true = con più spie, il ciclo scatta solo se escono tutte nella stessa estrazione
    private final List<Integer> manualPool = new ArrayList<>(); // cinquina indicata dalla tecnica (es. Numeretti)

    /** Quanti numeri colorare in blu scuro scelti dall'utente: 0 = automatico, altrimenti da 1 a 5. */
    static int bluScelto() {
        try { return Math.max(0, Math.min(5, DesktopStorage.bluCount())); } catch (Throwable t) { return 0; }
    }

    /** Il numero scelto dall'utente, oppure il valore predefinito se è su automatico. */
    static int quantiBlu(int predefinito) {
        int b = bluScelto();
        return b > 0 ? b : predefinito;
    }

    /** La spia vale solo se esce nella posizione indicata (1-5); 0 = qualsiasi posizione. */
    HighlightSpec withPosizioneSpia(int posizione) {
        spyPos = Math.max(0, Math.min(5, posizione));
        return this;
    }

    /** Con più spie: true = tutte nella stessa estrazione, false = basta una. */
    HighlightSpec withSpieTutteInsieme(boolean tutte) {
        spyAll = tutte;
        return this;
    }

    HighlightSpec withCinquina(List<Integer> c) {
        cinquina.clear();
        if (c != null) for (int x : c) if (!cinquina.contains(x) && cinquina.size() < 5) cinquina.add(x);
        cinquinaManual = !cinquina.isEmpty();
        manualPool.clear();
        manualPool.addAll(cinquina);
        return this;
    }

    HighlightSpec(String title, List<Integer> spies, List<String> spyWheels, List<Integer> numbers,
                  List<String> targetWheels, int colpi, int yearFrom, int yearTo) {
        this.title = title;
        this.spies = spies == null ? new ArrayList<Integer>() : spies;
        this.spyWheels = spyWheels == null ? new ArrayList<String>() : spyWheels;
        this.numbers = numbers == null ? new ArrayList<Integer>() : numbers;
        this.targetWheels = (targetWheels == null || targetWheels.isEmpty()) ? this.spyWheels : targetWheels;
        this.colpi = Math.max(0, colpi);
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
    }

    /** Calcola le date da colorare con la stessa logica dei calcoli (solo cicli completi nel periodo). */
    void prepare(List<Extraction> archive) {
        spyKeys.clear();
        windowKeys.clear();
        events = 0;

        List<Extraction> seq = new ArrayList<>();
        for (Extraction e : SantoSpiaCore.cronologico(archive)) {
            int y = e.getYear();
            if (y < yearFrom || y > yearTo) continue;
            if (!spyWheels.isEmpty()) {
                boolean has = false;
                for (String w : spyWheels) {
                    List<Integer> n = e.getNumbersForRuota(w);
                    if (n != null && n.size() == 5) { has = true; break; }
                }
                if (!has) continue;
            }
            seq.add(e);
        }

        List<Integer> completeEventIdx = new ArrayList<>();
        for (int i = 0; i < seq.size(); i++) {
            if (!spies.isEmpty() && hasSpy(seq.get(i))) {
                spyKeys.add(seq.get(i).getDateKey());
                if (colpi > 0) {
                    // Evidenzia anche i colpi disponibili dei cicli ancora aperti in fondo all'archivio.
                    // Le statistiche contano solo cicli completi, ma la verifica visiva non deve
                    // perdere le ultime estrazioni solo perché non sono ancora trascorsi tutti i colpi.
                    int available = Math.min(colpi, seq.size() - i - 1);
                    for (int k = 1; k <= available; k++) windowKeys.add(seq.get(i + k).getDateKey());
                    if (i + colpi < seq.size()) completeEventIdx.add(i);
                }
            }
        }

        if (colpi == 0) {
            if (spies.isEmpty()) {
                for (Extraction e : seq) windowKeys.add(e.getDateKey());
                events = seq.size();
            } else {
                windowKeys.addAll(spyKeys);
                events = spyKeys.size();
            }
        } else {
            // Gli eventi incompleti colorano le estrazioni disponibili, ma non entrano nel conteggio statistico.
            events = completeEventIdx.size();
        }
        if (cinquinaManual && bluScelto() == 0) {
            cinquina.clear();
            cinquina.addAll(manualPool);
        } else {
            computeCinquina(seq);
        }
    }

    /**
     * Stessa funzione per tutte le tecniche: i numeri risultato più usciti nelle finestre evidenziate
     * diventano BLU SCURO (come nel metodo Numeretti). In automatico: con più di 5 numeri se ne colorano 5,
     * con 5 o meno circa la metà (minimo 1). Se l'utente ha scelto un numero (1-5) vale quello.
     */
    private void computeCinquina(List<Extraction> seq) {
        cinquina.clear();
        LinkedHashSet<Integer> insieme = new LinkedHashSet<>(numbers);
        insieme.addAll(manualPool);
        List<Integer> pool = new ArrayList<>(insieme);
        int scelto = bluScelto();
        if (pool.isEmpty() || (scelto == 0 && pool.size() < 2)) return; // un solo numero: niente da distinguere
        int quanti = scelto > 0 ? scelto : (pool.size() > 5 ? 5 : Math.max(1, pool.size() / 2));
        int[] freq = new int[100];
        for (Extraction e : seq) {
            if (!windowKeys.contains(e.getDateKey())) continue;
            for (String w : Extraction.ALL_RUOTE) {
                List<Integer> n = e.getNumbersForRuota(w);
                if (n == null) continue;
                for (int x : n) if (x >= 1 && x <= 90 && pool.contains(x)) freq[x]++;
            }
        }
        pool.sort((a, b) -> freq[b] != freq[a] ? Integer.compare(freq[b], freq[a]) : Integer.compare(a, b));
        for (int x : pool) { if (cinquina.size() >= quanti || freq[x] == 0) break; cinquina.add(x); }
    }

    boolean isCinquinaBall(String ruota, int number, int dateKey) {
        return cinquina.contains(number) && (windowKeys.contains(dateKey) || spyKeys.contains(dateKey));
    }

    private boolean hasSpy(Extraction e) {
        for (String w : spyWheels) {
            List<Integer> n = e.getNumbersForRuota(w);
            if (n == null) continue;
            if (spyAll) {
                boolean tutte = !spies.isEmpty();
                for (int s : spies) if (!n.contains(s)) { tutte = false; break; }
                if (tutte) return true;
                continue;
            }
            for (int s : spies) {
                if (spyPos > 0) {
                    if (n.size() >= spyPos && n.get(spyPos - 1) == s) return true;
                } else if (n.contains(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isSpyBall(String ruota, int number, int dateKey) {
        return !spies.isEmpty() && spies.contains(number) && containsIgnoreCase(spyWheels, ruota) && spyKeys.contains(dateKey);
    }

    boolean isResultBall(String ruota, int number, int dateKey) {
        // Le selezioni della previsione vanno cercate ed evidenziate su TUTTE le ruote, non solo sulla ruota bersaglio.
        return numbers.contains(number) && windowKeys.contains(dateKey);
    }

    boolean isVisibleDate(int dateKey) {
        return spyKeys.contains(dateKey) || windowKeys.contains(dateKey);
    }

    /** Ruote da mostrare nelle card quando l'evidenziazione è attiva e non c'è un filtro di ruota. */
    List<String> wheelsToShow() {
        Set<String> s = new LinkedHashSet<>();
        // Per verificare le previsioni incrociate, mostra sempre tutte le ruote:
        // i numeri selezionati possono ricomparire anche su ruote diverse da quella bersaglio.
        return new ArrayList<>(Extraction.ALL_RUOTE);
    }

    String summary() {
        StringBuilder sb = new StringBuilder(title);
        sb.append("  |  periodo ").append(yearFrom).append("-").append(yearTo);
        if (colpi > 0) sb.append("  |  ").append(colpi).append(" colpi  |  cicli completi: ").append(events);
        else sb.append("  |  stessa estrazione  |  date evidenziate: ").append(windowKeys.size());
        sb.append("  |  SPIA verde, RISULTATO rosso");
        if (!cinquina.isEmpty()) sb.append(", numeri più usciti in BLU SCURO ").append(cinquina);
        return sb.toString();
    }

    private static boolean containsIgnoreCase(List<String> l, String x) {
        for (String s : l) if (s.equalsIgnoreCase(x)) return true;
        return false;
    }
}
