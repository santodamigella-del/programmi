package com.example.lottoinjava.desktop;

import com.example.lottoinjava.analysis.FibonacciLottoEngine;
import com.example.lottoinjava.analysis.IsotopiaEngine;
import com.example.lottoinjava.analysis.MetodoCappuccinoEngine;
import com.example.lottoinjava.analysis.MetodoDartagnanEngine;
import com.example.lottoinjava.analysis.MultistaticoEngine;
import com.example.lottoinjava.analysis.PredictionEngine;
import com.example.lottoinjava.analysis.SantoSpiaCore;
import com.example.lottoinjava.analysis.CollaudoPrevisioni;
import com.example.lottoinjava.analysis.SpiaTutteEngine;
import com.example.lottoinjava.analysis.SpyBacktestEngine;
import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Calcolo e rapporti di tutte le tecniche (nessuna dipendenza da JavaFX: testabile da solo). */
final class TecnicheRunner {

    private final int minYear, maxYear;

    TecnicheRunner(int minYear, int maxYear) {
        this.minYear = minYear;
        this.maxYear = maxYear;
    }

    static final Map<String, String> CODICI = new LinkedHashMap<>();
    static {
        String[][] p = {{"Bari", "BA"}, {"Cagliari", "CA"}, {"Firenze", "FI"}, {"Genova", "GE"}, {"Milano", "MI"},
                {"Napoli", "NA"}, {"Palermo", "PA"}, {"Roma", "RM"}, {"Torino", "TO"}, {"Venezia", "VE"}, {"Nazionale", "NZ"}};
        for (String[] x : p) CODICI.put(x[0], x[1]);
    }

    static final class In {
        String ruota, ruota2, target, wheelB, modalita;
        int spia, y0, y1, colpi, dim, pos1, pos2, fisso, posSpia, nGioco = 5, annoTest = 0, tipoGioco = 2;
        List<String> ruoteSel = new ArrayList<>();
        List<Integer> spie = new ArrayList<>();
    }

    static final class Result {
        final String text;
        final HighlightSpec spec;
        Result(String text, HighlightSpec spec) { this.text = text; this.spec = spec; }
    }

    // ------------------------------------------------------------------ calcolo e rapporti

    private static String nn(int n) { return String.format(Locale.ROOT, "%02d", n); }

    private static String nums(List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(" - ");
            sb.append(nn(l.get(i)));
        }
        return sb.toString();
    }

    private static String nums(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(nn(a[i]));
        }
        return sb.toString();
    }

    private static String nomeRuota(String r) { return r + " (" + CODICI.getOrDefault(r, "?") + ")"; }

    private static final String LINEA = "============================================================================";
    private static final String TRATTI = "----------------------------------------------------------------------------";

    private static double comb(int n, int k) {
        double r = 1;
        for (int i = 0; i < k; i++) r = r * (n - i) / (i + 1);
        return r;
    }

    private HighlightSpec spec(String titolo, List<Integer> spie, List<String> ruoteSpia, List<Integer> numeri,
                               List<String> target, int colpi, int y0, int y1) {
        HighlightSpec s = new HighlightSpec(titolo, spie, ruoteSpia, numeri, target, colpi, y0, y1);
        return s;
    }

    private static List<Integer> prime(List<Integer> l, int n) {
        return new ArrayList<>(l.subList(0, Math.min(n, l.size())));
    }

    Result calcola(int idx, In in, List<Extraction> arch) {
        switch (idx) {
            case 0: return ricercaSpia(in, arch);
            case 1: return previsione(in, arch);
            case 2: return ciclometria(in, arch);
            case 3: return terniMagici(in, arch);
            case 4: return unificato(in, arch);
            case 5: return convergenze(in, arch);
            case 6: return verifica(in, arch);
            case 7: return apriori(in, arch);
            case 8: return sommaRuote(in, arch);
            case 9: return sommaFisso(in, arch);
            case 10: return previsione5(in, arch);
            case 11: return dartagnan(in, arch);
            case 12: return isotopia(in, arch);
            case 13: return fibonacci(in, arch);
            case 14: return cappuccino(in, arch);
            case 15: return multistatico(in, arch);
            case 16: return superPrevisione(in, arch);
            case 17: return previsioneIa(in, arch);
            case 18: return spiaATutte(in, arch, false);
            case 19: return spiaATutte(in, arch, true);
            case 20: return verificaSpiaTutte(in, arch);
            case 21: return spiaInPosizione(in, arch);
            case 22: return spiaMultipla(in, arch);
            case 23: return scannerSpie(in, arch);
            case 24: return collaudoPrevisioni(in, arch);
            default: throw new IllegalArgumentException("Tecnica non riconosciuta.");
        }
    }

    private SantoSpiaCore.Modello modelloValido(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        SantoSpiaCore.validaDisponibilita(arch, in.ruota, in.y0, in.y1);
        SantoSpiaCore.Modello m = SantoSpiaCore.costruisciModello(arch, in.spia, in.ruota, in.y0, in.y1, in.colpi);
        if (m.nEventi() == 0) {
            throw new IllegalArgumentException("Nessun ciclo post-spia utile per la ruota e il periodo scelti.");
        }
        return m;
    }

    private String intestazione(String titolo, In in) {
        return "* " + titolo + "\n" + LINEA + "\n"
                + "Spia: " + nn(in.spia) + " - Ruota: " + nomeRuota(in.ruota)
                + " - Periodo: " + in.y0 + "-" + in.y1 + " - " + in.colpi + " colpi\n";
    }

    // 0 ----------------------------------------------------------------
    private Result ricercaSpia(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        SantoSpiaCore.validaDisponibilita(arch, in.ruota, in.y0, in.y1);
        SpyBacktestEngine.BacktestResult r = SpyBacktestEngine.runBacktest(arch, in.spia, in.ruota, in.y0, in.y1, in.colpi);
        StringBuilder sb = new StringBuilder(intestazione("RICERCA SPIA & RESOCONTO ESITI", in));
        sb.append("Estrazioni della ruota nel periodo: ").append(r.wheelDraws).append("\n");
        sb.append("Presenze della spia: ").append(r.spyPresences)
                .append(" - cicli post-spia completi (estrazioni analizzate): ").append(r.totalSpyOccurrences).append("\n\n");
        if (r.totalSpyOccurrences == 0) {
            sb.append("Nessun ciclo post-spia completo nel periodo scelto.\n");
        } else {
            sb.append("TOP 5 PER FREQUENZA: ").append(nums(r.top5AssociatedNumbers)).append("\n");
            sb.append("TOP 5 PER PUNTEGGIO: ").append(nums(r.top5ByScore)).append("\n\n");
            sb.append("FREQUENZE NEI CICLI (primi 10)\n").append(TRATTI).append("\n");
            List<Integer> ord = new ArrayList<>();
            for (int n = 1; n <= 90; n++) if (n != in.spia) ord.add(n);
            ord.sort((a, b) -> {
                int c = Integer.compare(r.numberFrequencyMap.get(b), r.numberFrequencyMap.get(a));
                return c != 0 ? c : Integer.compare(a, b);
            });
            for (int i = 0; i < 10; i++) {
                int n = ord.get(i);
                sb.append(String.format(Locale.ROOT, "%02d. Numero %s - uscito %d volte in %d cicli\n", i + 1, nn(n),
                        r.numberFrequencyMap.get(n), r.totalSpyOccurrences));
            }
            sb.append("\nTEST WALK-FORWARD (senza dati futuri)\n").append(TRATTI).append("\n");
            if (r.wfEvents == 0) {
                sb.append("Non eseguibile: servono almeno 6 cicli completi (disponibili ").append(r.totalSpyOccurrences).append(").\n");
            } else {
                sb.append(String.format(Locale.ROOT, "Eventi testati: %d\nMedia numeri centrati su 5: %.4f\nAtteso dal puro caso: %.4f (differenza %+.4f)\nMigliore: %d/5\n",
                        r.wfEvents, r.wfAvgHits, r.wfExpected, r.wfAvgHits - r.wfExpected, r.wfBest));
                sb.append("Distribuzione (numeri centrati -> eventi): ").append(r.wfDistribution).append("\n");
            }
            sb.append("\nNota: gli score storici non modificano la probabilità matematica reale.\n");
        }
        HighlightSpec s = spec("Ricerca spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                prime(r.top5AssociatedNumbers, 5), Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 1 ----------------------------------------------------------------
    private Result previsione(In in, List<Extraction> arch) {
        SantoSpiaCore.Modello m = modelloValido(in, arch);
        StringBuilder sb = new StringBuilder(intestazione("PREVISIONE POST-SPIA LOTTO", in));
        sb.append("Eventi post-spia utili: ").append(m.nEventi()).append("\n\n");
        sb.append("TOP 10 NUMERI PER PUNTEGGIO\n").append(TRATTI).append("\n");
        List<SantoSpiaCore.Punteggio> cl = SantoSpiaCore.classificaPunteggi(m);
        for (int i = 0; i < Math.min(10, cl.size()); i++) {
            SantoSpiaCore.Punteggio p = cl.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. %s  punteggio %.2f  freq %d  cicli %d/%d  posizione media %.2f\n",
                    i + 1, nn(p.numero), p.punteggio, p.frequenza, p.cicli, m.nEventi(), p.mediaPosizione));
        }
        List<Integer> top5 = SantoSpiaCore.topPost(m, 5);
        sb.append("\nPREVISIONE A 5 NUMERI: ").append(nums(top5)).append("\n");
        for (int dim = 2; dim <= 3; dim++) {
            sb.append("\nTOP ").append(dim == 2 ? "AMBI" : "TERNI").append("\n").append(TRATTI).append("\n");
            List<SantoSpiaCore.Combinazione> l = SantoSpiaCore.generaCombinazioni(m, dim, 10);
            for (int i = 0; i < l.size(); i++) {
                sb.append(String.format(Locale.ROOT, "%02d. %s  score %.2f  co-presenze %d\n", i + 1, nums(l.get(i).numeri), l.get(i).score, l.get(i).coPresenze));
            }
        }
        sb.append("\nNota: il metodo ordina dati storici; non modifica la probabilità matematica delle estrazioni future.\n");
        HighlightSpec s = spec("Previsione post-spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                top5, Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 2 ----------------------------------------------------------------
    private Result ciclometria(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        SantoSpiaCore.validaDisponibilita(arch, in.ruota, in.y0, in.y1);
        SantoSpiaCore.Ciclometria c = SantoSpiaCore.analizzaCiclometria(arch, in.spia, in.ruota, in.y0, in.y1, in.colpi);
        if (c == null) throw new IllegalArgumentException("Nessun ciclo post-spia utile per la ruota e il periodo scelti.");
        StringBuilder sb = new StringBuilder(intestazione("CICLOMETRIA SPIA LOTTO", in));
        sb.append("Eventi post-spia utili: ").append(c.eventi).append("\n\nTOP 10 CICLOMETRICI\n").append(TRATTI).append("\n");
        for (int i = 0; i < Math.min(10, c.risultati.size()); i++) {
            SantoSpiaCore.CicloNumero n = c.risultati.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. %s  score %.2f  distanza %d  freq %d  presenza %.1f%%  lift %.2f  stabilità %.1f%%\n",
                    i + 1, nn(n.numero), n.score, n.distanza, n.frequenza, n.presenza, n.lift, n.stabilita));
        }
        sb.append("\nNota: la ciclometria (distanza sul cerchio 1-90) ordina lo storico; non modifica le probabilità.\n");
        HighlightSpec s = spec("Ciclometria spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                c.top(5), Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 3 ----------------------------------------------------------------
    private Result terniMagici(In in, List<Extraction> arch) {
        SantoSpiaCore.Modello m = modelloValido(in, arch);
        List<SantoSpiaCore.TernoMagico> terni = SantoSpiaCore.generaTerniMagici(m, 10, 18, 2);
        StringBuilder sb = new StringBuilder(intestazione("TERNI MAGICI - SOLO LOTTO", in));
        sb.append("Eventi post-spia utili: ").append(m.nEventi()).append("\n\n");
        if (terni.isEmpty()) {
            sb.append("Dati insufficienti per i terni magici.\n");
            return new Result(sb.toString(), null);
        }
        sb.append("TOP 10 TERNI (Magic Score 0-100)\n").append(TRATTI).append("\n");
        List<Integer> evid = new ArrayList<>();
        for (int i = 0; i < terni.size(); i++) {
            SantoSpiaCore.TernoMagico t = terni.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. %s  Score %.2f  co-presenze %d  cicli %d/%d  lift %.2fx  stabilità %.2f\n",
                    i + 1, nums(t.numeri), t.score, t.frequenza, t.cicli, m.nEventi(), t.lift, t.stabilita));
            if (i < 3) for (int n : t.numeri) if (!evid.contains(n)) evid.add(n);
        }
        sb.append("\nL'evidenziazione colora i numeri dei primi 3 terni.\n");
        sb.append("Nota: il metodo ordina i terni con dati storici; la probabilità del terno resta 1/11.748.\n");
        HighlightSpec s = spec("Terni magici, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                evid, Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 4 ----------------------------------------------------------------
    private Result unificato(In in, List<Extraction> arch) {
        SantoSpiaCore.Resoconto r = SantoSpiaCore.resocontoUnificato(arch, in.spia, in.ruota, in.y0, in.y1, in.colpi);
        double pDraw = 5.0 / 90.0;
        double pPeriodo = 1 - Math.pow(1 - pDraw, in.colpi);
        double pTop6Draw = 1 - comb(84, 5) / comb(90, 5);
        double pTop6Periodo = 1 - Math.pow(1 - pTop6Draw, in.colpi);
        StringBuilder sb = new StringBuilder(intestazione("RESOCONTO UNIFICATO LOTTO", in));
        sb.append("Eventi post-spia utili: ").append(r.modello.nEventi()).append("\n\n");
        sb.append("1) POST-SPIA LOTTO\n").append(TRATTI).append("\nTOP 10: ").append(nums(r.topPost)).append("\n\n");
        sb.append("2) CICLOMETRIA LOTTO\n").append(TRATTI).append("\nTOP 10: ")
                .append(r.topCiclo.isEmpty() ? "nessun dato" : nums(r.topCiclo)).append("\n\n");
        sb.append("3) INCROCIO POST-SPIA + CICLOMETRIA\n").append(TRATTI).append("\nTOP 5: ")
                .append(r.topIncrocio.isEmpty() ? "nessun incrocio" : nums(r.topIncrocio)).append("\n\n");
        sb.append("4) TERNI MAGICI - SOLO LOTTO\n").append(TRATTI).append("\n");
        if (r.terni.isEmpty()) sb.append("Dati insufficienti per i terni magici.\n");
        for (int i = 0; i < r.terni.size(); i++) {
            sb.append(String.format(Locale.ROOT, "%02d. %s  Score %.2f  Co-presenze %d\n", i + 1, nums(r.terni.get(i).numeri),
                    r.terni.get(i).score, r.terni.get(i).frequenza));
        }
        sb.append("\n5) PROBABILITÀ TEORICHE LOTTO\n").append(TRATTI).append("\n");
        sb.append(String.format(Locale.ROOT, "Numero fisso su una ruota, singola estrazione: %.3f%% (5/90)\n", pDraw * 100));
        sb.append(String.format(Locale.ROOT, "Numero fisso almeno una volta in %d colpi: %.3f%%\n", in.colpi, pPeriodo * 100));
        sb.append(String.format(Locale.ROOT, "Almeno uno specifico TOP 6 in una estrazione: %.3f%%\n", pTop6Draw * 100));
        sb.append(String.format(Locale.ROOT, "Almeno uno specifico TOP 6 in %d colpi: %.3f%%\n", in.colpi, pTop6Periodo * 100));
        sb.append("Nota: gli score storici non modificano la probabilità matematica reale.\n");
        HighlightSpec s = spec("Resoconto unificato, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                r.topIncrocio, Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 5 ----------------------------------------------------------------
    private Result convergenze(In in, List<Extraction> arch) {
        SantoSpiaCore.Convergenze c = SantoSpiaCore.convergenze(arch, in.spia, in.ruota, in.y0, in.y1, in.colpi);
        StringBuilder sb = new StringBuilder("# CONVERGENZE TECNICHE - LOTTO\n" + LINEA + "\n");
        sb.append("Spia: ").append(nn(in.spia)).append(" - Ruota: ").append(nomeRuota(in.ruota))
                .append(" - Periodo ").append(in.y0).append("-").append(in.y1).append(" - ").append(in.colpi).append(" colpi\n");
        sb.append("Moduli confrontati: POST-SPIA, CICLOMETRIA, TERNI MAGICI\n\n");
        sb.append("NUMERI IN CONVERGENZA (presenti in 2 o più moduli)\n").append(TRATTI).append("\n");
        if (c.topConvergenza.isEmpty()) sb.append("Nessuna convergenza tra 2 o più moduli in questo periodo.\n");
        for (int n : c.topConvergenza) {
            sb.append(nn(n)).append("  ->  ").append(c.conteggio.get(n)).append("/").append(c.totaleModuli)
                    .append(" moduli (").append(String.join(" + ", c.dettaglio.get(n))).append(")\n");
        }
        sb.append("\nDETTAGLIO COMPLETO PER LIVELLO DI CONVERGENZA\n").append(TRATTI).append("\n");
        for (int livello = c.totaleModuli; livello >= 1; livello--) {
            List<Integer> l = new ArrayList<>();
            for (int n = 1; n <= 90; n++) if (c.conteggio.containsKey(n) && c.conteggio.get(n) == livello) l.add(n);
            if (!l.isEmpty()) sb.append(livello).append("/").append(c.totaleModuli).append(" moduli: ").append(nums(l).replace(" - ", " - ")).append("\n");
        }
        sb.append("\nNota: la convergenza segnala solo l'accordo tra moduli già calcolati.\nNon è una previsione e non modifica la probabilità matematica reale.\n");
        HighlightSpec s = spec("Convergenze, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                c.topConvergenza, Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 6 ----------------------------------------------------------------
    private Result verifica(In in, List<Extraction> arch) {
        if (in.spia < 1 || in.spia > 90) throw new IllegalArgumentException("La spia deve essere tra 1 e 90.");
        if (in.colpi < 1 || in.colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (in.ruoteSel.isEmpty()) throw new IllegalArgumentException("Seleziona almeno una ruota.");
        SantoSpiaCore.Verifica v = SantoSpiaCore.verificaSpia(arch, in.spia, in.ruoteSel, in.colpi, in.dim, in.modalita);
        String tipo = new String[]{"", "", "AMBO", "TERNO", "QUATERNA", "CINQUINA"}[in.dim];
        StringBuilder sb = new StringBuilder("* VERIFICA LOTTO DOPO LA SPIA\n" + LINEA + "\n");
        sb.append("Spia: ").append(nn(in.spia)).append("\nRuote: ").append(String.join(", ", in.ruoteSel))
                .append("\nModalità: ").append(in.modalita).append("\nColpi successivi: ").append(in.colpi)
                .append("\nTipo: ").append(tipo).append("\nEventi spia: ").append(v.eventi).append("\n\n");
        sb.append("TOP COMBINAZIONI OSSERVATE\n").append(TRATTI).append("\n");
        if (v.top.isEmpty()) sb.append("Nessuna combinazione trovata.\n");
        for (int i = 0; i < v.top.size(); i++) {
            SantoSpiaCore.RigaVerifica r = v.top.get(i);
            double pct = v.eventi > 0 ? (double) r.cicli / v.eventi * 100 : 0;
            sb.append(String.format(Locale.ROOT, "%02d. %s  - cicli %d/%d  - freq %d  - presenza %.2f%%\n", i + 1, nums(r.combo), r.cicli, v.eventi, r.frequenza, pct));
        }
        sb.append("\nBENCHMARK DI UNA COMBINAZIONE SPECIFICA\n").append(TRATTI).append("\n");
        sb.append(String.format(Locale.ROOT, "P(singolo colpo) ~ %.10f\nP(almeno una volta in %d colpi) ~ %.8f\n", v.pSingolo, in.colpi, v.pOrizzonte));
        sb.append("\nNota: il TOP viene scelto dopo aver guardato molte combinazioni,\nquindi la classifica descrive lo storico ma non dimostra causalità.\n");
        List<Integer> numeri = new ArrayList<>();
        if (!v.top.isEmpty()) for (int n : v.top.get(0).combo) numeri.add(n);
        HighlightSpec s = spec("Verifica " + tipo.toLowerCase(Locale.ROOT) + " dopo la spia " + nn(in.spia), Arrays.asList(in.spia),
                new ArrayList<>(in.ruoteSel), numeri, new ArrayList<>(in.ruoteSel), in.colpi, minYear, maxYear);
        return new Result(sb.toString() + "\nL'evidenziazione colora la prima combinazione della classifica.\n", s);
    }

    // 7 ----------------------------------------------------------------
    private Result apriori(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        SantoSpiaCore.Apriori a = SantoSpiaCore.apriori(arch, in.spia, in.ruota, in.y0, in.y1, in.colpi);
        if (a.errore != null) throw new IllegalArgumentException(a.errore);
        StringBuilder sb = new StringBuilder("IA MARKET BASKET & APRIORI PATTERN MINING\n" + LINEA + "\n");
        sb.append("Gioco: LOTTO - Ruota: ").append(in.ruota).append(" - Spia: ").append(nn(in.spia)).append("\n");
        sb.append("Uscite Spia in archivio: ").append(a.nSpia).append(" - Colpi analisi: ").append(in.colpi).append("\n");
        sb.append(TRATTI).append("\n TOP NUMERI FREQUENTI (CONFIDENCE - LIFT - χ²)\n").append(TRATTI).append("\n");
        for (int i = 0; i < a.singoli.size(); i++) {
            SantoSpiaCore.AprioriNumero x = a.singoli.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. Numero %s | Conf: %.1f%% | Lift: %.2f | χ²: %.2f (p=%.3f) -> %s\n",
                    i + 1, nn(x.numero), x.confidence * 100, x.lift, x.chi2, x.pVal, x.esito));
        }
        sb.append("\n").append(TRATTI).append("\n TOP AMBI FREQUENT PATTERN (APRIORI PAIRS)\n").append(TRATTI).append("\n");
        if (a.ambi.isEmpty()) sb.append("Nessun ambo sopra la soglia minima di confidenza.\n");
        for (int i = 0; i < a.ambi.size(); i++) {
            SantoSpiaCore.AprioriAmbo x = a.ambi.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. Ambo %s-%s | Uscito %d/%d volte | Conf: %.1f%% | Lift: %.2f\n",
                    i + 1, nn(x.a), nn(x.b), x.count, a.nSpia, x.confidence * 100, x.lift));
        }
        HighlightSpec s = spec("Apriori, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                prime(a.topNumeri, 5), Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 8 ----------------------------------------------------------------
    private Result sommaRuote(In in, List<Extraction> arch) {
        if (in.y0 > in.y1) throw new IllegalArgumentException("Anno iniziale maggiore dell'anno finale.");
        if (in.colpi < 1 || in.colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (in.pos1 < 1 || in.pos1 > 5 || in.pos2 < 1 || in.pos2 > 5) throw new IllegalArgumentException("Le posizioni devono essere tra 1 e 5.");
        if (in.fisso < 1 || in.fisso > 90) throw new IllegalArgumentException("Il fisso deve essere tra 1 e 90.");
        SantoSpiaCore.SommaRuote r = SantoSpiaCore.sommaRuote(arch, in.ruota, in.pos1, in.ruota2, in.pos2, in.fisso, in.target, in.colpi, in.y0, in.y1);
        StringBuilder sb = new StringBuilder("COSTRUTTORE METODO SOMMATIVO RUOTE\n" + LINEA + "\n");
        sb.append("Regola: ").append(in.pos1).append("° ").append(in.ruota).append(" + ").append(in.pos2).append("° ").append(in.ruota2)
                .append(" + ").append(in.fisso).append(" (mod 90)\n");
        sb.append("Ruota Target Esito: ").append(in.target).append(" - Colpi: ").append(in.colpi).append(" - Periodo ").append(in.y0).append("-").append(in.y1).append("\n");
        sb.append("Eventi calcolati in archivio: ").append(r.eventi).append("\n").append(TRATTI).append("\n TOP 10 NUMERI FREQUENTI POST-SOMMA\n").append(TRATTI).append("\n");
        int ev = Math.max(1, r.eventi);
        for (int i = 0; i < r.topSingoli.size(); i++) {
            int[] x = r.topSingoli.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. Numero %s | Uscito %d/%d volte (%.1f%%)\n", i + 1, nn(x[0]), x[1], ev, x[1] * 100.0 / ev));
        }
        sb.append("\n").append(TRATTI).append("\n TOP AMBI FREQUENTI POST-SOMMA\n").append(TRATTI).append("\n");
        if (r.topAmbi.isEmpty()) sb.append("Nessun ambo trovato sopra la soglia.\n");
        for (int i = 0; i < r.topAmbi.size(); i++) {
            int[] x = r.topAmbi.get(i);
            sb.append(String.format(Locale.ROOT, "%02d. Ambo %s-%s | Uscito %d/%d volte (%.1f%%)\n", i + 1, nn(x[0]), nn(x[1]), x[2], ev, x[2] * 100.0 / ev));
        }
        sb.append("\nL'evidenziazione colora i primi 5 numeri dove escono sulla ruota esito (stessa estrazione).\n");
        List<String> target = "TUTTE".equals(in.target) ? new ArrayList<>(Extraction.ALL_RUOTE) : Arrays.asList(in.target);
        HighlightSpec s = spec("Somma ruote " + in.ruota + "+" + in.ruota2, null, null, prime(r.topNumeri, 5), target, 0, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 9 ----------------------------------------------------------------
    private Result sommaFisso(In in, List<Extraction> arch) {
        SantoSpiaCore.SommaFisso r = SantoSpiaCore.sommaFisso(arch, in.ruota, in.y0, in.y1);
        StringBuilder sb = new StringBuilder("COSTRUTTORE SOMMA + FISSO\n" + LINEA + "\n");
        sb.append(in.ruota).append(" - ").append(in.y0).append("-").append(in.y1).append("\n");
        sb.append("Ultimi 5 numeri: ").append(nums(r.ultimaEstrazione)).append("\n");
        sb.append("Somma ultima estrazione: ").append(r.somma).append("\n");
        sb.append("Fisso scelto tra 1 e 90: ").append(nn(r.bestFisso)).append("\n");
        sb.append("Numero ottenuto: ").append(nn(r.numeroBase)).append("\n");
        sb.append(String.format(Locale.ROOT, "Test fisso %s: %d/%d hit = %.2f%%\n", nn(r.bestFisso), r.hits, r.prove, r.percentuale));
        sb.append("Co-uscite storiche del base: ").append(r.eventiBase).append("\n\n4 NUMERI CON MAGGIORE CO-USCITA\n");
        StringBuilder c = new StringBuilder();
        for (int i = 0; i < r.compagni.size(); i++) {
            if (i > 0) c.append(" - ");
            c.append(nn(r.compagni.get(i))).append(" (").append(r.coUscite.get(r.compagni.get(i))).append(" volte)");
        }
        sb.append(c).append("\n\nCINQUINA COSTRUITA\n").append(nums(r.previsione)).append("\n");
        sb.append("\nNota: il metodo ordina dati storici; non modifica la probabilità matematica delle estrazioni future.\n");
        sb.append("L'evidenziazione colora il numero base (verde) e i 4 compagni (rosso) nella stessa estrazione.\n");
        HighlightSpec s = spec("Somma + fisso su " + in.ruota, Arrays.asList(r.numeroBase), Arrays.asList(in.ruota),
                new ArrayList<>(r.compagni), Arrays.asList(in.ruota), 0, in.y0, in.y1);
        return new Result(sb.toString(), s);
    }

    // 10 ---------------------------------------------------------------
    private Result previsione5(In in, List<Extraction> arch) {
        if (in.ruota == null || !Extraction.ALL_RUOTE.contains(in.ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        PredictionEngine.PredictionResult p = PredictionEngine.generatePrediction(arch, in.ruota);
        StringBuilder sb = new StringBuilder("PREVISIONE A 5 NUMERI (frequenza / ritardo / ciclometria) - " + in.ruota + "\n" + LINEA + "\n");
        sb.append("Ultima estrazione considerata: ").append(p.getLastConcorsoDate()).append("\n\n");
        for (int n : p.getTop5Numbers()) {
            sb.append(nn(n)).append(String.format(Locale.ROOT, "  score %.2f  ", p.getScoreForNumber(n)))
                    .append(p.getExplanations().getOrDefault(n, "")).append("\n");
        }
        sb.append("\nNota: metodo statistico descrittivo; non modifica la probabilità reale.\n");
        HighlightSpec s = spec("Previsione a 5 numeri su " + in.ruota, null, null, new ArrayList<>(p.getTop5Numbers()),
                Arrays.asList(in.ruota), 0, minYear, maxYear);
        return new Result(sb.toString(), s);
    }

    // 11 ---------------------------------------------------------------
    private Result dartagnan(In in, List<Extraction> arch) {
        SantoSpiaCore.validaSpiaColpiRuota(in.spia, in.ruota, in.colpi);
        MetodoDartagnanEngine.DartagnanResult r = MetodoDartagnanEngine.calcolaMetodoDartagnan(arch, in.spia, in.ruota, in.colpi);
        HighlightSpec s = spec("D'Artagnan, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                new ArrayList<>(r.quartettaDartagnan), Arrays.asList(in.ruota), in.colpi, minYear, maxYear);
        return new Result(r.reportText + "\n\nQuartetta evidenziata: " + nums(r.quartettaDartagnan) + "\n", s);
    }

    // 12 ---------------------------------------------------------------
    private Result isotopia(In in, List<Extraction> arch) {
        if (in.colpi < 1 || in.colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (in.ruota == null || in.wheelB == null || in.ruota.equals(in.wheelB))
            throw new IllegalArgumentException("Scegli due ruote diverse.");
        IsotopiaEngine.IsotopiaResult r = IsotopiaEngine.analyzeIsotopia(arch, in.ruota, in.wheelB, in.colpi);
        HighlightSpec s = spec("Isotopia " + in.ruota + " / " + in.wheelB, null, null, prime(r.topConvergentNumbers, 5),
                Arrays.asList(in.ruota, in.wheelB), 0, minYear, maxYear);
        return new Result(r.reportText, s);
    }

    // 13 ---------------------------------------------------------------
    private Result fibonacci(In in, List<Extraction> arch) {
        if (in.ruota == null || !Extraction.ALL_RUOTE.contains(in.ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        FibonacciLottoEngine.FibonacciResult r = FibonacciLottoEngine.analyzeFibonacci(arch, in.ruota);
        HighlightSpec s = spec("Fibonacci su " + in.ruota, null, null, new ArrayList<>(r.top5FibonacciNumbers),
                Arrays.asList(in.ruota), 0, minYear, maxYear);
        return new Result(r.reportText, s);
    }

    // 14 ---------------------------------------------------------------
    private Result cappuccino(In in, List<Extraction> arch) {
        if (in.ruota == null || !Extraction.ALL_RUOTE.contains(in.ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        MetodoCappuccinoEngine.CappuccinoResult r = MetodoCappuccinoEngine.calcolaMetodoCappuccino(arch, in.ruota);
        HighlightSpec s = spec("Cappuccino su " + in.ruota, null, null, new ArrayList<>(r.cinquinaCappuccino),
                Arrays.asList(in.ruota), 0, minYear, maxYear);
        return new Result(r.reportText, s);
    }

    // 16 ---------------------------------------------------------------
    private Result superPrevisione(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        com.example.lottoinjava.analysis.PrevisioniIa.Previsione p =
                com.example.lottoinjava.analysis.PrevisioniIa.superPrevisioneLight(arch, in.spia, in.ruota, in.colpi, in.y0, in.y1);
        if (p.errore != null) throw new IllegalArgumentException(p.errore);
        HighlightSpec s = spec("Super previsione light, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                new ArrayList<>(p.topNumeri), Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(p.testo + "\n\nNota: gli indici storici non modificano la probabilità reale di nessun numero.\n", s);
    }

    // 17 ---------------------------------------------------------------
    private Result previsioneIa(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        com.example.lottoinjava.analysis.PrevisioniIa.Previsione p =
                com.example.lottoinjava.analysis.PrevisioniIa.previsioneAlgoritmiIa(arch, in.spia, in.y0, in.y1, in.colpi, in.ruota);
        if (p.errore != null) throw new IllegalArgumentException(p.errore);
        HighlightSpec s = spec("Previsione IA, spia " + nn(in.spia) + " su " + in.ruota, Arrays.asList(in.spia), Arrays.asList(in.ruota),
                new ArrayList<>(p.topNumeri), Arrays.asList(in.ruota), in.colpi, in.y0, in.y1);
        return new Result(p.testo + com.example.lottoinjava.analysis.PrevisioniIa.NOTA_IA + "\n", s);
    }

    // 15 ---------------------------------------------------------------
    private Result multistatico(In in, List<Extraction> arch) {
        if (in.colpi < 1 || in.colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        MultistaticoEngine.MultistaticoResult r = MultistaticoEngine.runMultistaticoAnalysis(arch, in.spie, in.ruota, in.colpi);
        HighlightSpec s = spec("Multistatico " + in.spie + " su " + in.ruota, in.spie, Arrays.asList(in.ruota),
                prime(r.topConvergentNumbers, 5), Arrays.asList(in.ruota), in.colpi, minYear, maxYear);
        return new Result(r.reportText, s);
    }
    // 18-19 -------------------------------------------------------------
    /**
     * Analisi post-spia con ensemble statistico deterministico.
     * Variante 18 aggrega le ruote selezionate; variante 19 applica gli stessi
     * indicatori esclusivamente alla ruota origine.
     */
    private Result spiaATutte(In in, List<Extraction> arch, boolean soloRuotaOrigine) {
        if (in.spia < 1 || in.spia > 90) throw new IllegalArgumentException("La spia deve essere compresa tra 1 e 90.");
        if (in.ruota == null || !CODICI.containsKey(in.ruota)) throw new IllegalArgumentException("Seleziona una ruota di origine valida.");
        if (in.y0 > in.y1) throw new IllegalArgumentException("L'anno iniziale deve precedere quello finale.");
        if (in.colpi < 1 || in.colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");

        // La variante 2 calcola il TOP 5 esclusivamente sulla ruota origine,
        // ma consente di selezionare le ruote su cui visualizzare/evidenziare il risultato.
        List<String> targets = new ArrayList<>();
        if (in.ruoteSel != null) {
            for (String w : in.ruoteSel) if (CODICI.containsKey(w) && !targets.contains(w)) targets.add(w);
        }
        if (targets.isEmpty()) targets.addAll(Extraction.ALL_RUOTE);
        List<String> analysisWheels = soloRuotaOrigine
                ? new ArrayList<>(Arrays.asList(in.ruota)) : new ArrayList<>(targets);

        List<Extraction> seq = new ArrayList<>();
        for (Extraction e : SantoSpiaCore.cronologico(arch)) {
            int y = e.getYear();
            if (y < in.y0 || y > in.y1) continue;
            List<Integer> source = e.getNumbersForRuota(in.ruota);
            if (source != null && source.size() == 5) seq.add(e);
        }
        if (seq.size() < 2) throw new IllegalArgumentException("Archivio insufficiente per la ruota e il periodo scelti.");

        int[] freq = new int[91];
        int[] cycleHits = new int[91];
        double[] decay = new double[91];
        int events = 0;
        int[] delay = new int[91];
        java.util.Arrays.fill(delay, seq.size());
        int totalObservedDraws = 0;
        for (int i = 0; i < seq.size(); i++) {
            List<Integer> source = seq.get(i).getNumbersForRuota(in.ruota);
            if (source == null || !source.contains(in.spia)) continue;
            int end = Math.min(seq.size() - 1, i + in.colpi);
            if (end <= i) continue;
            events++;
            boolean[] seenCycle = new boolean[91];
            for (int j = i + 1; j <= end; j++) {
                Extraction draw = seq.get(j);
                totalObservedDraws++;
                for (String wheel : analysisWheels) {
                    List<Integer> ns = draw.getNumbersForRuota(wheel);
                    if (ns == null || ns.size() != 5) continue;
                    for (int pos = 0; pos < ns.size(); pos++) {
                        Integer boxed = ns.get(pos);
                        if (boxed == null || boxed < 1 || boxed > 90 || boxed == in.spia) continue;
                        int n = boxed;
                        freq[n]++;
                        decay[n] += Math.pow(0.89, j - i - 1);
                        seenCycle[n] = true;
                    }
                }
            }
            for (int n = 1; n <= 90; n++) if (seenCycle[n]) cycleHits[n]++;
        }
        if (events == 0) throw new IllegalArgumentException("La spia non è stata trovata nel periodo selezionato.");

        // Ritardo calcolato nella sequenza di uscite post-spia aggregata, non sull'archivio completo.
        int[] lastSeen = new int[91];
        java.util.Arrays.fill(lastSeen, -1);
        int step = 0;
        for (int i = 0; i < seq.size(); i++) {
            if (!seq.get(i).getNumbersForRuota(in.ruota).contains(in.spia)) continue;
            int end = Math.min(seq.size() - 1, i + in.colpi);
            for (int j = i + 1; j <= end; j++, step++) {
                for (String wheel : analysisWheels) {
                    List<Integer> ns = seq.get(j).getNumbersForRuota(wheel);
                    if (ns == null) continue;
                    for (Integer n : ns) if (n != null && n >= 1 && n <= 90 && n != in.spia) lastSeen[n] = step;
                }
            }
        }
        int maxF = 1, maxCycles = 1, maxDelay = 1;
        for (int n = 1; n <= 90; n++) if (n != in.spia) {
            maxF = Math.max(maxF, freq[n]);
            maxCycles = Math.max(maxCycles, cycleHits[n]);
            delay[n] = lastSeen[n] < 0 ? Math.max(1, step) : Math.max(0, step - 1 - lastSeen[n]);
            maxDelay = Math.max(maxDelay, delay[n]);
        }
        final double[] score = new double[91];
        final double[] freqNorm = new double[91], cycleNorm = new double[91], delayNorm = new double[91], decayNorm = new double[91];
        final double[] lift = new double[91], chiSquare = new double[91];
        List<Integer> ranked = new ArrayList<>();
        for (int n = 1; n <= 90; n++) {
            if (n == in.spia) continue;
            freqNorm[n] = freq[n] / (double) maxF;
            cycleNorm[n] = cycleHits[n] / (double) maxCycles;
            delayNorm[n] = delay[n] / (double) maxDelay;
            decayNorm[n] = decay[n] / Math.max(1.0, events);
            double baseProb = 1.0 - Math.pow(1.0 - 5.0 / 90.0, in.colpi);
            double observedRate = cycleHits[n] / (double) Math.max(1, events);
            lift[n] = baseProb > 0 ? observedRate / baseProb : 0.0;
            double expected = events * baseProb;
            chiSquare[n] = expected > 0 ? Math.pow(cycleHits[n] - expected, 2.0) / expected : 0.0;
            double liftNorm = Math.min(1.0, lift[n] / 2.0);
            double chiNorm = Math.min(1.0, Math.sqrt(chiSquare[n]) / 4.0);
            // Ensemble multi-indicatore: frequenza, cicli, ritardo, recenza, lift e chi-quadrato.
            score[n] = 100.0 * (0.28 * freqNorm[n] + 0.27 * cycleNorm[n] + 0.12 * delayNorm[n]
                    + 0.15 * Math.min(1.0, decayNorm[n]) + 0.10 * liftNorm + 0.08 * chiNorm);
            ranked.add(n);
        }
        ranked.sort((a, b) -> {
            int c = Double.compare(score[b], score[a]);
            if (c != 0) return c;
            c = Integer.compare(cycleHits[b], cycleHits[a]);
            if (c != 0) return c;
            c = Integer.compare(freq[b], freq[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        List<Integer> top5 = prime(ranked, 5);
        String titolo = soloRuotaOrigine ? "SPIA A TUTTE — RUOTA ORIGINE" : "SPIA A TUTTE — MULTI-RUOTA";
        StringBuilder report = new StringBuilder();
        report.append("* ").append(titolo).append("\n").append(LINEA).append("\n");
        report.append("Ruota origine: ").append(nomeRuota(in.ruota)).append(" | Spia: ").append(nn(in.spia)).append("\n");
        report.append("Periodo: ").append(in.y0).append('-').append(in.y1).append(" | Colpi: ").append(in.colpi).append("\n");
        report.append("Ruote usate per il calcolo: ").append(String.join(", ", analysisWheels)).append("\n");
        if (soloRuotaOrigine) report.append("Ruote selezionate per evidenziazione: ").append(String.join(", ", targets)).append("\n");
        report.append("Cicli con spia: ").append(events).append(" | Osservazioni ruota/ciclo: ").append(totalObservedDraws).append("\n\n");
        report.append("TOP 5 STATISTICO (ensemble frequenza + cicli + ritardo + recenza)\n").append(TRATTI).append("\n");
        for (int i = 0; i < top5.size(); i++) {
            int n = top5.get(i);
            report.append(String.format(Locale.ROOT,
                    "%d) %s | score %.2f/100 | frequenza %d | cicli %d | ritardo %d | recenza %.2f | lift %.2f | chi² %.2f\n",
                    i + 1, nn(n), score[n], freq[n], cycleHits[n], delay[n], decayNorm[n], lift[n], chiSquare[n]));
        }
        report.append("\nIndicatori: frequenza, ricorrenza nei cicli, ritardo post-spia, recenza pesata, lift rispetto al riferimento teorico e chi-quadrato descrittivo.\n");
        report.append("Il punteggio è un indice descrittivo interno, non una probabilità di vincita. Le estrazioni restano casuali.\n");
        HighlightSpec hs = spec(soloRuotaOrigine ? "Spia a tutte - ruota origine " + in.ruota : "Spia a tutte multi-ruota " + in.ruota,
                Arrays.asList(in.spia), Arrays.asList(in.ruota), top5, targets, in.colpi, in.y0, in.y1);
        return new Result(report.toString(), hs);
    }

    // 20-22 -------------------------------------------------------------
    /** Quanti numeri giocare: se non indicato 5. */
    private static int nGioco(In in) { return in.nGioco >= SpiaTutteEngine.MIN_GIOCO && in.nGioco <= SpiaTutteEngine.MAX_GIOCO ? in.nGioco : 5; }

    private Result esitoSpiaTutte(In in, SpiaTutteEngine.Risultato r, String titolo, List<Integer> spie, int posizione,
                                  boolean tutteInsieme) {
        List<Integer> numeri = r.numeriGioco();
        HighlightSpec s = spec(titolo + ": " + (spie.size() == 1 ? nn(spie.get(0)) : spie.toString()) + " su " + in.ruota,
                spie, Arrays.asList(in.ruota), numeri, Arrays.asList(in.ruota), in.colpi, in.y0, in.y1)
                .withPosizioneSpia(posizione).withSpieTutteInsieme(tutteInsieme);
        return new Result(r.report, s);
    }

    private Result verificaSpiaTutte(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        List<Integer> spie = Arrays.asList(in.spia);
        SpiaTutteEngine.Risultato r = SpiaTutteEngine.analizza(arch, "VERIFICA SPIA SU TUTTE LE RUOTE", in.ruota, spie, 0,
                false, in.y0, in.y1, in.colpi, nGioco(in), in.annoTest, in.tipoGioco);
        return esitoSpiaTutte(in, r, "Verifica spia su tutte", spie, 0, false);
    }

    private Result spiaInPosizione(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(in.spia, in.ruota, in.y0, in.y1, in.colpi);
        if (in.posSpia < 1 || in.posSpia > 5) throw new IllegalArgumentException("La posizione deve essere tra 1 e 5.");
        List<Integer> spie = Arrays.asList(in.spia);
        SpiaTutteEngine.Risultato r = SpiaTutteEngine.analizza(arch, "SPIA IN POSIZIONE - VERIFICA SU TUTTE LE RUOTE", in.ruota,
                spie, in.posSpia, false, in.y0, in.y1, in.colpi, nGioco(in), in.annoTest, in.tipoGioco);
        return esitoSpiaTutte(in, r, "Spia in " + in.posSpia + "ª posizione", spie, in.posSpia, false);
    }

    private Result spiaMultipla(In in, List<Extraction> arch) {
        if (in.spie == null || in.spie.isEmpty()) throw new IllegalArgumentException("Inserisci almeno una spia.");
        if (in.spie.size() > SpiaTutteEngine.MAX_SPIE)
            throw new IllegalArgumentException("Puoi inserire al massimo " + SpiaTutteEngine.MAX_SPIE + " spie.");
        SantoSpiaCore.validaParametri(in.spie.get(0), in.ruota, in.y0, in.y1, in.colpi);
        boolean tutte = !SpiaTutteEngine.ALMENO_UNA.equals(in.modalita);
        SpiaTutteEngine.Risultato r = SpiaTutteEngine.analizza(arch, "SPIA MULTIPLA - VERIFICA SU TUTTE LE RUOTE", in.ruota,
                new ArrayList<>(in.spie), 0, tutte, in.y0, in.y1, in.colpi, nGioco(in), in.annoTest, in.tipoGioco);
        return esitoSpiaTutte(in, r, "Spia multipla", new ArrayList<>(in.spie), 0, tutte);
    }

    private Result scannerSpie(In in, List<Extraction> arch) {
        SantoSpiaCore.validaParametri(1, in.ruota, in.y0, in.y1, in.colpi);
        SpiaTutteEngine.RisultatoScan r = SpiaTutteEngine.scansiona(arch, in.ruota, in.y0, in.y1, in.colpi,
                nGioco(in), in.annoTest, in.tipoGioco);
        SpiaTutteEngine.RigaScan m = r.migliore();
        if (m == null) return new Result(r.report, null);
        // in archivio si evidenzia la spia in testa alla classifica con i suoi numeri da giocare
        HighlightSpec s = spec("Scanner di spie: prima in classifica " + nn(m.spia) + " su " + in.ruota,
                Arrays.asList(m.spia), Arrays.asList(in.ruota), new ArrayList<>(m.numeri), Arrays.asList(in.ruota),
                in.colpi, in.y0, in.y1);
        return new Result(r.report + "\nIn archivio viene evidenziata la prima della classifica (spia " + nn(m.spia) + ").\n", s);
    }

    private Result collaudoPrevisioni(In in, List<Extraction> arch) {
        CollaudoPrevisioni.Risultato r = CollaudoPrevisioni.esegui(arch, in.y0, in.y1, in.annoTest, in.colpi,
                nGioco(in), in.tipoGioco, 20240901L);
        return new Result(r.report, null);
    }
}
