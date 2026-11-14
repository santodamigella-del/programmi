package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Collaudo onesto dei metodi di previsione dell'app.
 *
 * Per ogni ruota e a intervalli regolari (una finestra ogni N colpi, quindi finestre che NON si sovrappongono) ogni metodo
 * sceglie K numeri usando SOLO le estrazioni precedenti; poi si guarda cosa esce nei successivi N colpi sulla stessa ruota.
 * I risultati si confrontano con il caso (ipergeometrica) e con un metodo casuale di controllo.
 *
 * Misure: copertura (quanti dei K numeri escono almeno una volta), successo (almeno un ambo/terno/... dei K numeri nella
 * stessa estrazione) e bilancio di gioco con i premi ufficiali. Il verdetto usa lo z della copertura con la soglia di
 * Bonferroni sul numero di metodi provati: provando più metodi, qualcuno sembra buono per fortuna.
 *
 * Nessuna dipendenza da Android o JavaFX.
 */
public final class CollaudoPrevisioni {

    private CollaudoPrevisioni() { }

    public static final String[] METODI = {
            "Previsione a 5 numeri (app)",
            "Solo spia dell'ultima estrazione (app)",
            "Più frequenti (ultime 30)",
            "Più frequenti (tutto lo storico)",
            "Ritardatari",
            "Casuale (controllo)"
    };
    private static final int CONTROLLO = 5;
    /** z unilaterale per alfa 5% diviso 5 metodi (Bonferroni): 1% circa. */
    public static final double Z_SOGLIA = 2.33;
    /** z minimo che il risultato deve avere in ciascuna delle due metà del periodo di test per essere confermato. */
    public static final double Z_META = 1.0;
    /** Estrazioni minime di storia sulla ruota prima di poter fare una previsione. */
    public static final int STORIA_MIN = 100;

    public static final class Riga {
        public String metodo;
        public long provaCop, hitCop;        // numeri previsti / numeri usciti nella finestra
        public int punti, successi;          // previsioni (ruota x finestra) / con successo
        public double spesa, vincita;
        public double copPct, succPct, resa, zCop, zSucc;
        public double zA, zB;                // z della copertura nella prima e nella seconda metà del periodo di test
        public boolean supera;               // z alto E confermato in entrambe le metà
        public boolean nonConfermato;        // z alto ma non confermato nelle due metà
    }

    public static final class Risultato {
        public final List<Riga> righe = new ArrayList<>();
        public int punti, ruoteUsate, primoAnnoTest;
        public double pCop, pSucc, resaCaso, scartoMinimo;
        /** corrente.get(metodo).get(ruota): numeri che il metodo darebbe oggi. */
        public final List<Map<String, List<Integer>>> corrente = new ArrayList<>();
        public String report;
    }

    // ------------------------------------------------------------------ utilità

    private static double comb(int n, int k) {
        if (k < 0 || k > n) return 0;
        double r = 1;
        for (int i = 0; i < k; i++) r = r * (n - i) / (i + 1);
        return r;
    }

    private static String nn(int n) { return String.format(Locale.ROOT, "%02d", n); }

    private static double zScore(double hit, double prove, double p) {
        if (prove <= 0 || p <= 0 || p >= 1) return 0;
        return (hit - prove * p) / Math.sqrt(prove * p * (1 - p));
    }

    /** I K numeri col punteggio più alto (a parità, il numero più basso). */
    private static int[] primiK(double[] score, int k) {
        Integer[] idx = new Integer[90];
        for (int i = 0; i < 90; i++) idx[i] = i + 1;
        Arrays.sort(idx, (a, b) -> {
            int c = Double.compare(score[b], score[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        int[] out = new int[k];
        for (int i = 0; i < k; i++) out[i] = idx[i];
        return out;
    }

    private static double[] daMappa(Map<Integer, Double> m) {
        double[] s = new double[91];
        if (m != null) for (Map.Entry<Integer, Double> e : m.entrySet()) if (e.getKey() >= 1 && e.getKey() <= 90) s[e.getKey()] = e.getValue();
        return s;
    }

    /** Parziali di una ruota. */
    private static final class Parziale {
        final long[] provaCop = new long[METODI.length], hitCop = new long[METODI.length];
        final long[][] provaMeta = new long[2][METODI.length], hitMeta = new long[2][METODI.length];
        final int[] punti = new int[METODI.length], successi = new int[METODI.length];
        final double[] spesa = new double[METODI.length], vincita = new double[METODI.length];
        final int[][] corrente = new int[METODI.length][];
    }

    // ------------------------------------------------------------------ calcolo

    /**
     * @param y0        primo anno di storia usato dai metodi
     * @param y1        ultimo anno valutato
     * @param annoTest  primo anno valutato (0 = gli ultimi 10 anni del periodo)
     */
    public static Risultato esegui(List<Extraction> archivio, int y0, int y1, int annoTest, int colpi, int nGioco,
                                   int tipoGioco, long seme) {
        if (y0 > y1) throw new IllegalArgumentException("Anno iniziale maggiore dell'anno finale.");
        if (colpi < 1 || colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (nGioco < SpiaTutteEngine.MIN_GIOCO || nGioco > SpiaTutteEngine.MAX_GIOCO)
            throw new IllegalArgumentException("I numeri da giocare devono essere tra " + SpiaTutteEngine.MIN_GIOCO + " e " + SpiaTutteEngine.MAX_GIOCO + ".");
        if (tipoGioco < 2 || tipoGioco > 5 || tipoGioco > nGioco)
            throw new IllegalArgumentException("Per questa giocata servono almeno " + Math.max(2, tipoGioco) + " numeri da giocare.");

        final List<Extraction> cron = new ArrayList<>();
        for (Extraction e : SantoSpiaCore.cronologico(archivio)) if (e.getYear() <= y1) cron.add(e);
        final int n = cron.size();
        int s0 = -1;
        for (int i = 0; i < n; i++) if (cron.get(i).getYear() >= y0) { s0 = i; break; }
        if (s0 < 0) throw new IllegalArgumentException("Nessuna estrazione nel periodo selezionato.");
        final int inizio = s0;
        int primo = annoTest > 0 ? annoTest : Math.max(y0 + 1, y1 - 9);
        if (primo > y1) throw new IllegalArgumentException("Il primo anno del test deve essere compreso nel periodo.");
        int fe = -1;
        for (int i = inizio; i < n; i++) if (cron.get(i).getYear() >= primo) { fe = i; break; }
        if (fe < 0 || fe - inizio < STORIA_MIN) throw new IllegalArgumentException(
                "Servono almeno " + STORIA_MIN + " estrazioni di storia prima del primo anno del test: anticipa l'anno iniziale o posticipa il test.");
        final int firstEval = fe;

        // elenco dal più recente (come lo vogliono i motori dell'app) e cinquine per ruota
        final List<Extraction> nf = new ArrayList<>(cron);
        java.util.Collections.reverse(nf);
        final List<String> ruote = Extraction.ALL_RUOTE;
        final int nw = ruote.size();
        final int[][][] ord = new int[nw][n][];
        for (int w = 0; w < nw; w++) {
            for (int j = 0; j < n; j++) {
                List<Integer> l = cron.get(j).getNumbersForRuota(ruote.get(w));
                if (l == null || l.size() != 5) continue;
                int[] a = new int[5];
                for (int i = 0; i < 5; i++) a[i] = l.get(i);
                ord[w][j] = a;
            }
        }

        // la prima metà dei punti di test e la seconda (per la conferma del risultato)
        final int meta = firstEval + (((n - colpi - firstEval) / colpi + 1) / 2) * colpi;
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors())));
        List<Future<Parziale>> fut = new ArrayList<>();
        try {
            for (int w = 0; w < nw; w++) {
                final int wf = w;
                Callable<Parziale> task = () -> ruota(wf, ruote.get(wf), ord, nf, n, inizio, firstEval, colpi, nGioco, tipoGioco, seme, meta);
                fut.add(pool.submit(task));
            }
            Risultato res = new Risultato();
            res.primoAnnoTest = primo;
            Parziale[] parz = new Parziale[nw];
            for (int w = 0; w < nw; w++) parz[w] = fut.get(w).get();
            for (int m = 0; m < METODI.length; m++) res.corrente.add(new java.util.LinkedHashMap<>());

            res.pCop = 1 - Math.pow(1 - 5.0 / 90.0, colpi);
            res.pSucc = 1 - Math.pow(1 - probEstrazione(nGioco, tipoGioco), colpi);
            res.resaCaso = SpiaTutteEngine.QUOTA[tipoGioco] * comb(5, tipoGioco) / comb(90, tipoGioco) * (1 - SpiaTutteEngine.RITENUTA) * 100;
            for (int m = 0; m < METODI.length; m++) {
                Riga r = new Riga();
                long pa = 0, ha = 0, pb = 0, hb = 0;
                r.metodo = METODI[m];
                for (int w = 0; w < nw; w++) {
                    r.provaCop += parz[w].provaCop[m];
                    r.hitCop += parz[w].hitCop[m];
                    pa += parz[w].provaMeta[0][m]; ha += parz[w].hitMeta[0][m];
                    pb += parz[w].provaMeta[1][m]; hb += parz[w].hitMeta[1][m];
                    r.punti += parz[w].punti[m];
                    r.successi += parz[w].successi[m];
                    r.spesa += parz[w].spesa[m];
                    r.vincita += parz[w].vincita[m];
                    if (parz[w].corrente[m] != null) {
                        List<Integer> l = new ArrayList<>();
                        for (int x : parz[w].corrente[m]) l.add(x);
                        res.corrente.get(m).put(ruote.get(w), l);
                    }
                }
                r.copPct = r.provaCop > 0 ? r.hitCop * 100.0 / r.provaCop : 0;
                r.succPct = r.punti > 0 ? r.successi * 100.0 / r.punti : 0;
                r.resa = r.spesa > 0 ? r.vincita * 100 / r.spesa : 0;
                r.zCop = zScore(r.hitCop, r.provaCop, res.pCop);
                r.zSucc = zScore(r.successi, r.punti, res.pSucc);
                r.zA = zScore(ha, pa, res.pCop);
                r.zB = zScore(hb, pb, res.pCop);
                boolean alto = m != CONTROLLO && r.zCop >= Z_SOGLIA;
                r.supera = alto && r.zA >= Z_META && r.zB >= Z_META;
                r.nonConfermato = alto && !r.supera;
                res.righe.add(r);
            }
            res.punti = res.righe.get(0).punti;
            for (int w = 0; w < nw; w++) if (parz[w].punti[0] > 0) res.ruoteUsate++;
            if (res.righe.get(0).provaCop > 0) {
                // scarto minimo (in punti percentuali di copertura) che il test riuscirebbe a rilevare con la soglia scelta
                res.scartoMinimo = Z_SOGLIA * Math.sqrt(res.pCop * (1 - res.pCop) / res.righe.get(0).provaCop) * 100;
            }
            res.report = report(res, y0, y1, colpi, nGioco, tipoGioco);
            return res;
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable c = ex.getCause() != null ? ex.getCause() : ex;
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            throw new IllegalStateException(c);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Collaudo interrotto.");
        } finally {
            pool.shutdownNow();
        }
    }

    private static double probEstrazione(int k, int d) {
        double p = 0;
        for (int x = d; x <= Math.min(k, 5); x++) p += comb(k, x) * comb(90 - k, 5 - x) / comb(90, 5);
        return p;
    }

    /** Percorre in ordine cronologico le estrazioni di una ruota e, a intervalli di "colpi" estrazioni, mette alla prova ogni metodo. */
    private static Parziale ruota(int w, String nomeRuota, int[][][] ord, List<Extraction> nf, int n, int inizio, int firstEval,
                                  int colpi, int nGioco, int tipo, long seme, int meta) {
        Parziale p = new Parziale();
        int[] freqTutte = new int[91];
        int[] ultimoIdx = new int[91];
        Arrays.fill(ultimoIdx, -1);
        int[][] anello = new int[30][];
        int[] freq30 = new int[91];
        int vistiRuota = 0;
        Random rnd = new Random(seme * 31 + w);

        for (int t = inizio; t < n; t++) {
            boolean punto = t >= firstEval && (t - firstEval) % colpi == 0 && t + colpi <= n;
            if (punto && vistiRuota >= STORIA_MIN) {
                boolean completa = true;
                for (int s = 0; s < colpi && completa; s++) if (ord[w][t + s] == null) completa = false;
                if (completa) {
                    double[][] punteggi = new double[METODI.length][];
                    List<Extraction> storia = nf.subList(n - t, n - inizio);
                    punteggi[0] = daMappa(PredictionEngine.generatePrediction(storia, nomeRuota).getTotalScores());
                    punteggi[1] = daMappa(SpyNumberAnalyzer.calculateSpyScores(storia, nomeRuota, 3));
                    punteggi[2] = new double[91];
                    punteggi[3] = new double[91];
                    punteggi[4] = new double[91];
                    punteggi[5] = new double[91];
                    for (int x = 1; x <= 90; x++) {
                        punteggi[2][x] = freq30[x];
                        punteggi[3][x] = freqTutte[x];
                        punteggi[4][x] = ultimoIdx[x] < 0 ? vistiRuota : vistiRuota - 1 - ultimoIdx[x];
                        punteggi[5][x] = rnd.nextDouble();
                    }
                    for (int m = 0; m < METODI.length; m++) valuta(p, m, primiK(punteggi[m], nGioco), ord[w], t, colpi, tipo, t < meta ? 0 : 1);
                }
            }
            int[] d = ord[w][t];
            if (d != null) {
                for (int x : d) { freqTutte[x]++; freq30[x]++; ultimoIdx[x] = vistiRuota; }
                int pos = vistiRuota % 30;
                if (anello[pos] != null) for (int x : anello[pos]) freq30[x]--;
                anello[pos] = d;
                vistiRuota++;
            }
        }

        // cosa darebbe oggi ogni metodo
        if (vistiRuota >= STORIA_MIN) {
            double[][] pr = new double[METODI.length][];
            List<Extraction> storia = nf.subList(0, n - inizio);
            pr[0] = daMappa(PredictionEngine.generatePrediction(storia, nomeRuota).getTotalScores());
            pr[1] = daMappa(SpyNumberAnalyzer.calculateSpyScores(storia, nomeRuota, 3));
            for (int m = 2; m < METODI.length; m++) pr[m] = new double[91];
            for (int x = 1; x <= 90; x++) {
                pr[2][x] = freq30[x];
                pr[3][x] = freqTutte[x];
                pr[4][x] = ultimoIdx[x] < 0 ? vistiRuota : vistiRuota - 1 - ultimoIdx[x];
                pr[5][x] = rnd.nextDouble();
            }
            for (int m = 0; m < METODI.length; m++) {
                int[] top = primiK(pr[m], nGioco);
                Arrays.sort(top);
                p.corrente[m] = top;
            }
        }
        return p;
    }

    /** Esito di una previsione (K numeri) sui "colpi" successivi: copertura, successo e bilancio di gioco. */
    private static void valuta(Parziale p, int m, int[] scelti, int[][] ordRuota, int t, int colpi, int tipo, int meta) {
        boolean[] g = new boolean[91];
        for (int x : scelti) g[x] = true;
        boolean[] usciti = new boolean[91];
        int k = scelti.length;
        boolean successo = false;
        double spesa = 0, vincita = 0;
        boolean vinto = false;
        for (int s = 0; s < colpi; s++) {
            int[] d = ordRuota[t + s];
            int match = 0;
            for (int x : d) if (g[x]) { match++; usciti[x] = true; }
            if (!vinto) {
                spesa += comb(k, tipo);
                if (match >= tipo) {
                    vincita += comb(match, tipo) * SpiaTutteEngine.QUOTA[tipo] * (1 - SpiaTutteEngine.RITENUTA);
                    vinto = true;
                }
            }
            if (match >= tipo) successo = true;
        }
        int cop = 0;
        for (int x : scelti) if (usciti[x]) cop++;
        p.provaCop[m] += k;
        p.hitCop[m] += cop;
        p.provaMeta[meta][m] += k;
        p.hitMeta[meta][m] += cop;
        p.punti[m]++;
        if (successo) p.successi[m]++;
        p.spesa[m] += spesa;
        p.vincita[m] += vincita;
    }

    // ------------------------------------------------------------------ rapporto

    private static String report(Risultato r, int y0, int y1, int colpi, int nGioco, int tipo) {
        final String LINEA = "============================================================================";
        final String TRATTI = "----------------------------------------------------------------------------";
        String t = SpiaTutteEngine.TIPO[tipo].toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("* COLLAUDO ONESTO DEI METODI DI PREVISIONE\n").append(LINEA).append("\n");
        sb.append("Storia usata dal ").append(y0).append(" - previsioni valutate dal ").append(r.primoAnnoTest).append(" al ").append(y1)
                .append(" - ").append(colpi).append(" colpi - ").append(nGioco).append(" numeri - sorte: ").append(t).append("\n");
        sb.append("Ogni previsione usa solo le estrazioni precedenti. Finestre non sovrapposte: ").append(r.punti)
                .append(" previsioni su ").append(r.ruoteUsate).append(" ruote.\n\n");
        sb.append(String.format(Locale.ROOT, "Per puro caso: copertura %.1f%% (quota dei %d numeri usciti almeno una volta), successo %.1f%%, resa %.0f%%.\n",
                r.pCop * 100, nGioco, r.pSucc * 100, r.resaCaso));
        sb.append(String.format(Locale.ROOT, "Con questi dati il test rileva solo scarti di copertura di almeno %.1f punti percentuali.\n\n", r.scartoMinimo));

        sb.append("RISULTATI\n").append(TRATTI).append("\n");
        sb.append(String.format(Locale.ROOT, "%-38s %9s %6s %6s %6s %9s %6s %7s  %s\n", "Metodo", "Copertura", "z", "zA", "zB", "Successo", "z", "Resa", "Verdetto"));
        for (Riga x : r.righe) {
            String v = x.metodo.contains("controllo") ? "controllo"
                    : (x.supera ? "SUPERA IL CASO" : (x.nonConfermato ? "non confermato" : "come il caso"));
            sb.append(String.format(Locale.ROOT, "%-38s %8.1f%% %+6.2f %+6.2f %+6.2f %8.1f%% %+6.2f %6.1f%%  %s\n",
                    x.metodo, x.copPct, x.zCop, x.zA, x.zB, x.succPct, x.zSucc, x.resa, v));
        }
        sb.append("\nVerdetto: un metodo supera il caso solo se lo z della copertura è almeno ").append(Z_SOGLIA)
                .append(" (soglia corretta per i 5 metodi provati) E il risultato si conferma in entrambe le metà del periodo di test\n")
                .append("(zA e zB, ciascuno almeno ").append(Z_META).append("). Con 5 metodi provati, uno sembra buono per fortuna una volta su venti: ")
                .append("la conferma serve a scartarlo.\n");
        sb.append("Il metodo casuale di controllo deve restare vicino a z = 0: se non lo fa, c'è un problema nei dati.\n\n");

        Riga migliore = null;
        for (Riga x : r.righe) if (!x.metodo.contains("controllo") && (migliore == null || x.zCop > migliore.zCop)) migliore = x;
        List<Riga> nonConf = new ArrayList<>();
        for (Riga x : r.righe) if (x.nonConfermato) nonConf.add(x);
        List<Riga> passano = new ArrayList<>();
        for (Riga x : r.righe) if (x.supera) passano.add(x);

        sb.append("CONCLUSIONE\n").append(TRATTI).append("\n");
        if (!nonConf.isEmpty()) {
            sb.append("Scartati perché non confermati nelle due metà del test (probabile fortuna): ");
            for (int i = 0; i < nonConf.size(); i++) sb.append(i > 0 ? ", " : "").append(nonConf.get(i).metodo);
            sb.append(".\n");
        }
        if (passano.isEmpty()) {
            sb.append("Nessun metodo supera il caso in modo significativo. In un gioco equo è quello che ci si aspetta: ogni numero ha\n")
                    .append("probabilità 5/90 a estrazione, qualunque sia la storia. Le previsioni qui sotto restano ordinamenti di dati\n")
                    .append("storici, non indicazioni di probabilità. Prima di fidarti di un nuovo metodo, provalo con questo collaudo.\n");
        } else {
            sb.append("Metodi sopra la soglia: ");
            for (int i = 0; i < passano.size(); i++) sb.append(i > 0 ? ", " : "").append(passano.get(i).metodo);
            sb.append(".\nUn solo collaudo non basta: ripetilo su un altro periodo (cambia l'anno del test) e, se regge ancora, il\n")
                    .append("risultato merita attenzione; verifica anche che l'archivio non abbia errori o estrazioni mancanti.\n");
        }
        if (migliore != null) {
            int mi = Arrays.asList(METODI).indexOf(migliore.metodo);
            sb.append("\nNUMERI DI OGGI (").append(migliore.metodo).append(migliore.supera ? ", sopra la soglia" : ", NON supera il caso")
                    .append(")\n").append(TRATTI).append("\n");
            for (Map.Entry<String, List<Integer>> e : r.corrente.get(mi).entrySet()) {
                sb.append(String.format(Locale.ROOT, "%-10s ", e.getKey()));
                for (int i = 0; i < e.getValue().size(); i++) sb.append(i > 0 ? " - " : "").append(nn(e.getValue().get(i)));
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
