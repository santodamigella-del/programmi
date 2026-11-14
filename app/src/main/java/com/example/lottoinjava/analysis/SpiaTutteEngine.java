package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spia sulla ruota di ORIGINE, numeri da giocare dalla ruota di origine, verifica su TUTTE le ruote.
 *
 * 1) Ogni volta che la spia scatta sulla ruota di origine (ciclo completo nel periodo) si guardano i successivi N colpi
 *    sulla stessa ruota: i numeri che escono più spesso in quei cicli sono i NUMERI DA GIOCARE (da 2 a 10).
 * 2) Quegli stessi numeri vengono verificati su ognuna delle 11 ruote con gli stessi parametri (periodo, colpi):
 *    per ogni ruota si conta in quanti cicli sono usciti almeno un ambo, un terno, una quaterna, una cinquina
 *    formati con i numeri giocati nella stessa estrazione, con le percentuali e il confronto con il caso.
 * 3) TEST WALK-FORWARD: per ogni ciclo i numeri vengono scelti usando solo i cicli precedenti, poi verificati sul ciclo
 *    successivo (nessun dato futuro). È il test onesto: sulla ruota di origine la verifica "in campione" è favorevole
 *    per costruzione, perché i numeri sono scelti proprio da quei dati.
 *
 * Tre varianti di spia: singola, in una posizione precisa (1ª-5ª), multipla (fino a 5 spie: tutte insieme / almeno una).
 * Nessuna dipendenza da Android o JavaFX.
 */
public final class SpiaTutteEngine {

    private SpiaTutteEngine() { }

    public static final int MAX_SPIE = 5;
    public static final int MIN_GIOCO = 2, MAX_GIOCO = 10;
    /** Cicli minimi già osservati prima di poter fare una previsione nel walk-forward. */
    public static final int MIN_STORICO = 8;
    /** Premi ufficiali del Lotto per 1 euro su una ruota (lordi): estratto, ambo, terno, quaterna, cinquina. */
    public static final double[] QUOTA = {0, 11.23, 250, 4500, 120000, 6000000};
    /** Ritenuta sulle vincite (Sisal: 250 euro lordi diventano 230 netti). */
    public static final double RITENUTA = 0.08;
    public static final String TUTTE_INSIEME = "TUTTE INSIEME";
    public static final String ALMENO_UNA = "ALMENO UNA";
    public static final String[] TIPO = {"", "", "AMBO", "TERNO", "QUATERNA", "CINQUINA"};

    private static final Map<String, String> CODICI = new LinkedHashMap<>();
    static {
        String[][] p = {{"Bari", "BA"}, {"Cagliari", "CA"}, {"Firenze", "FI"}, {"Genova", "GE"}, {"Milano", "MI"},
                {"Napoli", "NA"}, {"Palermo", "PA"}, {"Roma", "RM"}, {"Torino", "TO"}, {"Venezia", "VE"}, {"Nazionale", "NZ"}};
        for (String[] x : p) CODICI.put(x[0], x[1]);
    }

    private static final String LINEA = "============================================================================";
    private static final String TRATTI = "----------------------------------------------------------------------------";

    /** Un numero da giocare con le sue statistiche sulla ruota di origine. */
    public static final class Numero {
        public int numero, cicli, frequenza;
        public double percentuale, lift;
    }

    public static final class Risultato {
        public int estrazioniRuota;   // estrazioni valide della ruota di origine nel periodo
        public int presenzeSpia;      // uscite che hanno fatto scattare il ciclo (anche quelle non complete)
        public int eventi;            // cicli completi analizzati
        public final int[] perPosizione = new int[6]; // spia singola: uscite per posizione (1-5)
        public final List<Numero> gioco = new ArrayList<>();
        /** hit[w][d]: cicli in cui sulla ruota w è uscito almeno un tipo d (2-5) con i numeri giocati. */
        public final int[][] hit = new int[Extraction.ALL_RUOTE.size()][6];
        /** Cicli in cui la ruota aveva almeno un'estrazione nei colpi (la Nazionale esiste solo dal 2005). */
        public final int[] prove = new int[Extraction.ALL_RUOTE.size()];
        public int wfProve;                                  // cicli testati nel walk-forward
        public final int[][] wfHit = new int[Extraction.ALL_RUOTE.size()][6];
        public final int[] wfProveRuota = new int[Extraction.ALL_RUOTE.size()];
        public final double[] atteso = new double[6];        // probabilità di un ciclo "vincente" per puro caso
        // test su due periodi: numeri scelti nel primo, verificati nel secondo
        public int ptAnno;                                   // primo anno del secondo periodo
        public int ptAddestramento, ptTest;                  // cicli nei due periodi
        public String ptMotivo;                              // se non eseguibile: perché
        public final List<Integer> ptGioco = new ArrayList<>();
        public final int[][] ptHit = new int[Extraction.ALL_RUOTE.size()][6];
        public final int[] ptProveRuota = new int[Extraction.ALL_RUOTE.size()];
        // bilancio di gioco sul walk-forward (1 euro a combinazione, si smette alla prima vincita)
        public int bilTipo;
        public final double[] bilSpesa = new double[Extraction.ALL_RUOTE.size()];
        public final double[] bilVincita = new double[Extraction.ALL_RUOTE.size()];
        public final int[] bilProve = new int[Extraction.ALL_RUOTE.size()];
        public final int[] bilVinti = new int[Extraction.ALL_RUOTE.size()];
        public String report;

        public List<Integer> numeriGioco() {
            List<Integer> l = new ArrayList<>();
            for (Numero n : gioco) l.add(n.numero);
            return l;
        }
    }

    // ------------------------------------------------------------------ utilità

    private static String nn(int n) { return String.format(Locale.ROOT, "%02d", n); }
    private static String nomeRuota(String r) { return r + " (" + CODICI.getOrDefault(r, "?") + ")"; }

    private static double comb(int n, int k) {
        if (k < 0 || k > n) return 0;
        double r = 1;
        for (int i = 0; i < k; i++) r = r * (n - i) / (i + 1);
        return r;
    }

    /** P(in una cinquina estratta ci sono almeno d dei k numeri giocati) - ipergeometrica. */
    private static double probEstrazione(int k, int d) {
        double p = 0;
        for (int x = d; x <= Math.min(k, 5); x++) p += comb(k, x) * comb(90 - k, 5 - x) / comb(90, 5);
        return p;
    }

    private static List<Integer> primiK(int[] cicli, int[] freq, boolean[] escludi, int k) {
        List<Integer> cand = new ArrayList<>();
        for (int x = 1; x <= 90; x++) if (!escludi[x]) cand.add(x);
        cand.sort((a, b) -> {
            int c = Integer.compare(cicli[b], cicli[a]);
            if (c != 0) return c;
            c = Integer.compare(freq[b], freq[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        return new ArrayList<>(cand.subList(0, Math.min(k, cand.size())));
    }

    /** Valuta un ciclo (spia all'indice i) su ogni ruota con i numeri giocati: incrementa hit[w][d]. */
    private static void valutaCiclo(int[][][] ord, int i, int colpi, boolean[] gioco, int[][] hit, int[] prove) {
        for (int w = 0; w < ord.length; w++) {
            int max = 0;
            boolean presente = false;
            for (int step = 1; step <= colpi; step++) {
                int[] d = ord[w][i + step];
                if (d == null) continue;
                presente = true;
                int m = 0;
                for (int x : d) if (gioco[x]) m++;
                if (m > max) max = m;
            }
            if (!presente) continue; // ruota senza estrazioni in questa finestra: il ciclo non conta per lei
            prove[w]++;
            for (int d = 2; d <= Math.min(5, max); d++) hit[w][d]++;
        }
    }

    /** Conta nei colpi successivi alla spia (indice i) quali numeri escono sulla ruota di origine. */
    private static void accumula(int[][][] ord, int wo, int i, int colpi, int[] cicli, int[] freq) {
        int[] cnt = new int[91];
        for (int step = 1; step <= colpi; step++) for (int x : ord[wo][i + step]) cnt[x]++;
        for (int x = 1; x <= 90; x++) if (cnt[x] > 0) { cicli[x]++; freq[x] += cnt[x]; }
    }

    /**
     * Simula la giocata di un ciclo su ogni ruota: a ogni colpo si gioca 1 euro su ognuna delle combinazioni di "tipo" numeri
     * ricavabili dai k numeri; si smette alla prima estrazione che vince. Vincite al netto della ritenuta.
     */
    private static void simulaGioco(int[][][] ord, int i, int colpi, boolean[] gioco, int tipo, int k,
                                    double[] spesa, double[] vincita, int[] vinti, int[] prove) {
        double costoColpo = comb(k, tipo);
        for (int w = 0; w < ord.length; w++) {
            boolean presente = false, vinto = false;
            double sp = 0, vi = 0;
            for (int step = 1; step <= colpi; step++) {
                int[] d = ord[w][i + step];
                if (d == null) continue;
                presente = true;
                sp += costoColpo;
                int m = 0;
                for (int x : d) if (gioco[x]) m++;
                if (m >= tipo) {
                    vi += comb(m, tipo) * QUOTA[tipo] * (1 - RITENUTA);
                    vinto = true;
                    break;
                }
            }
            if (!presente) continue;
            prove[w]++;
            spesa[w] += sp;
            vincita[w] += vi;
            if (vinto) vinti[w]++;
        }
    }

    // ------------------------------------------------------------------ calcolo

    /**
     * @param ruota        ruota di origine della spia (e dei numeri da giocare)
     * @param spie         1-5 numeri (1-90)
     * @param posizione    0 = qualsiasi posizione; 1-5 = la spia deve uscire in quella posizione (solo con una spia)
     * @param tutteInsieme con più spie: true = tutte nella stessa estrazione, false = almeno una
     * @param nGioco       quanti numeri giocare (2-10)
     * @param annoTest     primo anno del secondo periodo per il test su due periodi (0 = automatico: ultimo 30% degli anni)
     * @param tipoGioco    sorte del bilancio di gioco: 2 ambo, 3 terno, 4 quaterna, 5 cinquina
     */
    public static Risultato analizza(List<Extraction> archivio, String titolo, String ruota, List<Integer> spie, int posizione,
                                     boolean tutteInsieme, int y0, int y1, int colpi, int nGioco, int annoTest, int tipoGioco) {
        if (ruota == null || !Extraction.ALL_RUOTE.contains(ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        if (spie == null || spie.isEmpty()) throw new IllegalArgumentException("Inserisci almeno una spia.");
        if (spie.size() > MAX_SPIE) throw new IllegalArgumentException("Puoi usare al massimo " + MAX_SPIE + " spie.");
        for (int s : spie) if (s < 1 || s > 90) throw new IllegalArgumentException("La spia deve essere tra 1 e 90.");
        if (posizione < 0 || posizione > 5) throw new IllegalArgumentException("La posizione deve essere tra 1 e 5.");
        if (posizione > 0 && spie.size() != 1) throw new IllegalArgumentException("La posizione si può scegliere solo con una spia.");
        if (y0 > y1) throw new IllegalArgumentException("Anno iniziale maggiore dell'anno finale.");
        if (colpi < 1 || colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (tipoGioco < 2 || tipoGioco > 5) throw new IllegalArgumentException("La giocata deve essere ambo, terno, quaterna o cinquina.");
        if (annoTest != 0 && (annoTest <= y0 || annoTest > y1))
            throw new IllegalArgumentException("L'anno di inizio del test deve essere dopo l'anno iniziale e non oltre quello finale (oppure 0).");
        if (nGioco < MIN_GIOCO || nGioco > MAX_GIOCO)
            throw new IllegalArgumentException("I numeri da giocare devono essere tra " + MIN_GIOCO + " e " + MAX_GIOCO + ".");

        List<Extraction> seq = new ArrayList<>();
        for (Extraction e : SantoSpiaCore.cronologico(archivio)) {
            int y = e.getYear();
            if (y < y0 || y > y1) continue;
            List<Integer> n = e.getNumbersForRuota(ruota);
            if (n != null && n.size() == 5) seq.add(e);
        }
        if (seq.isEmpty()) throw new IllegalArgumentException("Nessuna estrazione valida per ruota e periodo selezionati.");
        final int n = seq.size();
        final List<String> ruote = Extraction.ALL_RUOTE;
        final int nw = ruote.size();
        final int wo = ruote.indexOf(ruota);

        int[][][] ord = new int[nw][n][];
        for (int w = 0; w < nw; w++) {
            for (int j = 0; j < n; j++) {
                List<Integer> l = seq.get(j).getNumbersForRuota(ruote.get(w));
                if (l == null || l.size() != 5) continue;
                int[] a = new int[5];
                for (int i = 0; i < 5; i++) a[i] = l.get(i);
                ord[w][j] = a;
            }
        }

        boolean[] escludi = new boolean[91];
        for (int s : spie) escludi[s] = true;

        Risultato res = new Risultato();
        res.estrazioniRuota = n;
        List<Integer> eventiIdx = new ArrayList<>();
        List<Integer> apertiIdx = new ArrayList<>();
        final boolean singola = spie.size() == 1;
        for (int i = 0; i < n; i++) {
            List<Integer> origine = seq.get(i).getNumbersForRuota(ruota);
            int idx = singola ? origine.indexOf(spie.get(0)) : -1;
            if (singola && idx >= 0) res.perPosizione[idx + 1]++;
            boolean scatta;
            if (singola) scatta = posizione == 0 ? idx >= 0 : idx == posizione - 1;
            else if (tutteInsieme) scatta = origine.containsAll(spie);
            else {
                scatta = false;
                for (int s : spie) if (origine.contains(s)) { scatta = true; break; }
            }
            if (!scatta) continue;
            res.presenzeSpia++;
            if (i + colpi >= n) apertiIdx.add(i); else eventiIdx.add(i);
        }
        res.eventi = eventiIdx.size();

        if (res.eventi > 0) {
            // cicli cumulativi sulla ruota di origine + walk-forward (i numeri di ogni ciclo vengono scelti coi cicli precedenti)
            int[] cicli = new int[91], freq = new int[91];
            for (int k = 0; k < eventiIdx.size(); k++) {
                int i = eventiIdx.get(k);
                if (k >= MIN_STORICO) {
                    boolean[] g = new boolean[91];
                    for (int x : primiK(cicli, freq, escludi, nGioco)) g[x] = true;
                    valutaCiclo(ord, i, colpi, g, res.wfHit, res.wfProveRuota);
                    if (tipoGioco <= nGioco)
                        simulaGioco(ord, i, colpi, g, tipoGioco, nGioco, res.bilSpesa, res.bilVincita, res.bilVinti, res.bilProve);
                    res.wfProve++;
                }
                accumula(ord, wo, i, colpi, cicli, freq);
            }
            res.bilTipo = tipoGioco;
            // numeri da giocare: calcolati su tutti i cicli
            List<Integer> top = primiK(cicli, freq, escludi, nGioco);
            double base = 1 - Math.pow(1 - 5.0 / 90.0, colpi);
            boolean[] g = new boolean[91];
            for (int x : top) {
                g[x] = true;
                Numero nm = new Numero();
                nm.numero = x;
                nm.cicli = cicli[x];
                nm.frequenza = freq[x];
                nm.percentuale = cicli[x] * 100.0 / res.eventi;
                nm.lift = (cicli[x] / (double) res.eventi) / base;
                res.gioco.add(nm);
            }
            for (int i : eventiIdx) valutaCiclo(ord, i, colpi, g, res.hit, res.prove);
            for (int d = 2; d <= 5; d++) res.atteso[d] = 1 - Math.pow(1 - probEstrazione(nGioco, d), colpi);

            // test su due periodi: numeri scelti nel primo, verificati nel secondo (nessuna sovrapposizione)
            int split = annoTest > 0 ? annoTest : (int) Math.round(y0 + (y1 - y0 + 1) * 0.7);
            res.ptAnno = split;
            if (split <= y0 || split > y1) {
                res.ptMotivo = "il periodo è troppo breve per essere diviso in due";
            } else {
                int[] c2 = new int[91], f2 = new int[91];
                List<Integer> test = new ArrayList<>();
                for (int i : eventiIdx) {
                    if (seq.get(i + colpi).getYear() < split) { accumula(ord, wo, i, colpi, c2, f2); res.ptAddestramento++; }
                    else if (seq.get(i).getYear() >= split) test.add(i);
                    // i cicli a cavallo dei due periodi vengono esclusi per non usare dati del secondo periodo
                }
                res.ptTest = test.size();
                if (res.ptAddestramento < MIN_STORICO) {
                    res.ptMotivo = "servono almeno " + MIN_STORICO + " cicli nel primo periodo (trovati " + res.ptAddestramento + ")";
                } else if (test.isEmpty()) {
                    res.ptMotivo = "nessun ciclo completo nel secondo periodo";
                } else {
                    List<Integer> top2 = primiK(c2, f2, escludi, nGioco);
                    boolean[] g2 = new boolean[91];
                    for (int x : top2) g2[x] = true;
                    res.ptGioco.addAll(top2);
                    for (int i : test) valutaCiclo(ord, i, colpi, g2, res.ptHit, res.ptProveRuota);
                }
            }
        }

        res.report = costruisciReport(res, titolo, ruota, spie, posizione, tutteInsieme, y0, y1, colpi, nGioco, seq, apertiIdx);
        return res;
    }

    // ------------------------------------------------------------------ rapporto

    private static String cella(int hit, int prove, int d, int nGioco) {
        if (d > nGioco) return "-";
        if (prove <= 0) return "n.d.";
        return String.format(Locale.ROOT, "%d/%d %.1f%%", hit, prove, hit * 100.0 / prove);
    }

    private static double pct(int[][] hit, int[] prove, int w, int d) {
        return prove[w] > 0 ? hit[w][d] / (double) prove[w] : -1;
    }

    private static void tabella(StringBuilder sb, int[][] hit, int[] prove, double[] atteso, int nGioco, int wo, boolean marcaOrigine) {
        final List<String> ruote = Extraction.ALL_RUOTE;
        sb.append(String.format(Locale.ROOT, "%-16s %-14s %-14s %-14s %-14s\n", "Ruota", "AMBO", "TERNO", "QUATERNA", "CINQUINA"));
        List<Integer> ordine = new ArrayList<>();
        for (int w = 0; w < ruote.size(); w++) ordine.add(w);
        ordine.sort((a, b) -> {
            for (int d = 2; d <= 5; d++) {
                int c = Double.compare(pct(hit, prove, b, d), pct(hit, prove, a, d));
                if (c != 0) return c;
            }
            return Integer.compare(a, b);
        });
        for (int w : ordine) {
            String nome = nomeRuota(ruote.get(w)) + (marcaOrigine && w == wo ? "*" : "");
            sb.append(String.format(Locale.ROOT, "%-16s", nome));
            for (int d = 2; d <= 5; d++) sb.append(String.format(Locale.ROOT, " %-14s", cella(hit[w][d], prove[w], d, nGioco)));
            sb.append("\n");
        }
        sb.append(String.format(Locale.ROOT, "%-16s", "Atteso dal caso"));
        for (int d = 2; d <= 5; d++) {
            sb.append(String.format(Locale.ROOT, " %-14s", d > nGioco ? "-" : String.format(Locale.ROOT, "%.1f%%", atteso[d] * 100)));
        }
        sb.append("\n");
    }

    /** Tabella per ruota + media su tutte le ruote rispetto al caso. */
    private static void verificaConMedia(StringBuilder sb, int[][] hit, int[] proveRuota, double[] atteso, int nGioco, int wo) {
        final List<String> ruote = Extraction.ALL_RUOTE;
        tabella(sb, hit, proveRuota, atteso, nGioco, wo, false);
        sb.append("\nMedia su tutte le ruote rispetto al caso:\n");
        for (int d = 2; d <= Math.min(5, nGioco); d++) {
            double somma = 0;
            int conDati = 0;
            for (int w = 0; w < ruote.size(); w++) {
                if (proveRuota[w] == 0) continue;
                somma += hit[w][d] * 100.0 / proveRuota[w];
                conDati++;
            }
            double media = conDati > 0 ? somma / conDati : 0;
            sb.append(String.format(Locale.ROOT, "  %-9s media %.2f%%  caso %.2f%%  differenza %+.2f punti\n",
                    TIPO[d], media, atteso[d] * 100, media - atteso[d] * 100));
        }
    }

    private static String costruisciReport(Risultato r, String titolo, String ruota, List<Integer> spie, int posizione,
                                           boolean tutteInsieme, int y0, int y1, int colpi, int nGioco,
                                           List<Extraction> seq, List<Integer> aperti) {
        final List<String> ruote = Extraction.ALL_RUOTE;
        final int wo = ruote.indexOf(ruota);
        StringBuilder sb = new StringBuilder();
        sb.append("* ").append(titolo).append("\n").append(LINEA).append("\n");
        sb.append("Ruota di origine: ").append(nomeRuota(ruota)).append(" - Periodo: ").append(y0).append("-").append(y1)
                .append(" - ").append(colpi).append(" colpi - numeri da giocare: ").append(nGioco).append("\n");
        StringBuilder sp = new StringBuilder();
        for (int i = 0; i < spie.size(); i++) sp.append(i > 0 ? " - " : "").append(nn(spie.get(i)));
        if (spie.size() == 1) {
            sb.append("Spia: ").append(sp).append(posizione > 0 ? " in " + posizione + "ª posizione" : " (qualsiasi posizione)").append("\n");
        } else {
            sb.append("Spie: ").append(sp).append(tutteInsieme ? " (tutte insieme nella stessa estrazione)" : " (almeno una)").append("\n");
        }
        sb.append("Estrazioni della ruota nel periodo: ").append(r.estrazioniRuota).append("\n");
        sb.append("Uscite che hanno attivato la spia: ").append(r.presenzeSpia)
                .append(" - cicli completi analizzati: ").append(r.eventi).append("\n");
        if (spie.size() == 1) {
            sb.append("Uscite della spia per posizione:");
            for (int p = 1; p <= 5; p++) sb.append("  ").append(p).append("ª: ").append(r.perPosizione[p]);
            sb.append("\n");
        }
        sb.append("\n");

        if (r.eventi == 0) {
            sb.append("Nessun ciclo completo nel periodo scelto: ");
            if (r.presenzeSpia == 0) {
                sb.append("la spia non è mai uscita");
                if (posizione > 0) sb.append(" in ").append(posizione).append("ª posizione");
                if (spie.size() > 1 && tutteInsieme) sb.append(" con tutte le spie nella stessa estrazione (prova \"")
                        .append(ALMENO_UNA).append("\" o meno spie)");
                sb.append(".\n");
            } else {
                sb.append("le uscite della spia sono troppo recenti per completare ").append(colpi).append(" colpi.\n");
            }
            return sb.toString();
        }

        // 1) numeri da giocare
        sb.append("NUMERI DA GIOCARE (dalla ruota di origine ").append(ruota).append(")\n").append(TRATTI).append("\n");
        StringBuilder gioco = new StringBuilder();
        for (int i = 0; i < r.gioco.size(); i++) {
            Numero x = r.gioco.get(i);
            gioco.append(i > 0 ? " - " : "").append(nn(x.numero));
            sb.append(String.format(Locale.ROOT, "%02d. %s  cicli %d/%d (%.1f%%)  uscite totali %d  rispetto al caso %.2fx\n",
                    i + 1, nn(x.numero), x.cicli, r.eventi, x.percentuale, x.frequenza, x.lift));
        }
        sb.append("\nGIOCA: ").append(gioco).append("  su ").append(nomeRuota(ruota)).append(" (e verifica sulle altre ruote)\n");

        // cicli ancora aperti
        if (!aperti.isEmpty()) {
            sb.append("\nSPIA ATTIVA: cicli ancora aperti a fine periodo\n").append(TRATTI).append("\n");
            for (int k = Math.max(0, aperti.size() - 3); k < aperti.size(); k++) {
                int i = aperti.get(k);
                int passati = seq.size() - 1 - i;
                sb.append("Spia uscita ").append(seq.get(i).getShortDate()).append(" - colpi trascorsi ").append(passati)
                        .append("/").append(colpi).append(" - rimasti ").append(colpi - passati).append("\n");
            }
        }
        sb.append("\n");

        // 2) verifica su tutte le ruote (stessi numeri, stesso periodo)
        sb.append("VERIFICA SU TUTTE LE RUOTE - stessi numeri, stesso periodo\n").append(TRATTI).append("\n");
        sb.append("Cicli in cui è uscito almeno un ambo/terno/quaterna/cinquina dei numeri giocati entro ")
                .append(colpi).append(" colpi.\n");
        sb.append("Ogni ruota è valutata solo sui cicli in cui ha estrazioni (la Nazionale esiste dal 2005).\n");
        tabella(sb, r.hit, r.prove, r.atteso, nGioco, wo, true);
        sb.append("* ruota di origine: i numeri sono scelti proprio da questi dati, quindi il risultato è favorevole per costruzione.\n");
        sb.append("Le altre ruote sono un controllo indipendente.\n");
        sb.append("\nRuota che ha dato più risultati (escluse le origine):\n");
        for (int d = 2; d <= Math.min(5, nGioco); d++) {
            int best = -1;
            for (int w = 0; w < ruote.size(); w++) {
                if (w == wo || r.prove[w] == 0) continue;
                if (best < 0 || pct(r.hit, r.prove, w, d) > pct(r.hit, r.prove, best, d)) best = w;
            }
            if (best < 0 || r.hit[best][d] == 0) {
                sb.append(String.format(Locale.ROOT, "  %-9s nessun risultato sulle altre ruote\n", TIPO[d]));
            } else {
                sb.append(String.format(Locale.ROOT, "  %-9s %-16s %s\n", TIPO[d], nomeRuota(ruote.get(best)),
                        cella(r.hit[best][d], r.prove[best], d, nGioco)));
            }
        }
        sb.append("\n");

        // 3) walk-forward
        sb.append("TEST WALK-FORWARD (numeri scelti solo con i cicli precedenti)\n").append(TRATTI).append("\n");
        if (r.wfProve == 0) {
            sb.append("Non eseguibile: servono almeno ").append(MIN_STORICO + 1).append(" cicli completi (disponibili ")
                    .append(r.eventi).append(").\n");
        } else {
            sb.append("Cicli testati: ").append(r.wfProve).append(" (dal ").append(MIN_STORICO + 1).append("° in poi)\n");
            verificaConMedia(sb, r.wfHit, r.wfProveRuota, r.atteso, nGioco, wo);
        }

        // 4) due periodi
        sb.append("\nTEST SU DUE PERIODI (numeri scelti nel primo periodo, verificati nel secondo)\n").append(TRATTI).append("\n");
        if (r.ptMotivo != null || r.ptGioco.isEmpty()) {
            sb.append("Non eseguibile: ").append(r.ptMotivo != null ? r.ptMotivo : "dati insufficienti").append(".\n");
        } else {
            StringBuilder g2 = new StringBuilder();
            for (int i = 0; i < r.ptGioco.size(); i++) g2.append(i > 0 ? " - " : "").append(nn(r.ptGioco.get(i)));
            sb.append("Primo periodo: ").append(y0).append("-").append(r.ptAnno - 1).append(" (").append(r.ptAddestramento)
                    .append(" cicli) - secondo periodo: ").append(r.ptAnno).append("-").append(y1).append(" (")
                    .append(r.ptTest).append(" cicli)\n");
            sb.append("Numeri scelti nel primo periodo: ").append(g2).append("\n");
            verificaConMedia(sb, r.ptHit, r.ptProveRuota, r.atteso, nGioco, wo);
        }

        // 5) bilancio di gioco
        sb.append("\nBILANCIO DI GIOCO (").append(TIPO[r.bilTipo].toLowerCase(Locale.ROOT)).append(" su ").append(nGioco)
                .append(" numeri, walk-forward)\n").append(TRATTI).append("\n");
        if (r.bilTipo > nGioco) {
            sb.append("Non eseguibile: per giocare ").append(TIPO[r.bilTipo].toLowerCase(Locale.ROOT)).append(" servono almeno ")
                    .append(r.bilTipo).append(" numeri.\n");
        } else if (r.wfProve == 0) {
            sb.append("Non eseguibile: servono almeno ").append(MIN_STORICO + 1).append(" cicli completi.\n");
        } else {
            double comboColpo = comb(nGioco, r.bilTipo);
            sb.append(String.format(Locale.ROOT, "1 € per ognuna delle %.0f combinazioni a ogni colpo, fino a %d colpi, si smette alla prima vincita.\n",
                    comboColpo, colpi));
            sb.append(String.format(Locale.ROOT, "Premio ufficiale %s: %,.2f € per 1 € su una ruota; vincite al netto della ritenuta dell'%.0f%%.\n",
                    TIPO[r.bilTipo].toLowerCase(Locale.ROOT), QUOTA[r.bilTipo], RITENUTA * 100));
            double rtp = QUOTA[r.bilTipo] * comb(5, r.bilTipo) / comb(90, r.bilTipo) * (1 - RITENUTA) * 100;
            sb.append(String.format(Locale.ROOT, "Per puro caso si recupererebbe in media circa il %.0f%% di ciò che si spende.\n\n", rtp));
            sb.append(String.format(Locale.ROOT, "%-16s %11s %11s %11s %7s  %s\n", "Ruota", "Spesa €", "Vincite €", "Saldo €", "Resa", "Cicli vinti"));
            List<Integer> ordine = new ArrayList<>();
            for (int w = 0; w < ruote.size(); w++) if (r.bilProve[w] > 0) ordine.add(w);
            ordine.sort((x, y) -> Double.compare(r.bilVincita[y] - r.bilSpesa[y], r.bilVincita[x] - r.bilSpesa[x]));
            double totSp = 0, totVi = 0;
            int totVinti = 0, totProve = 0;
            for (int w : ordine) {
                totSp += r.bilSpesa[w]; totVi += r.bilVincita[w]; totVinti += r.bilVinti[w]; totProve += r.bilProve[w];
                sb.append(String.format(Locale.ROOT, "%-16s %11.2f %11.2f %+11.2f %6.1f%%  %d/%d\n",
                        nomeRuota(ruote.get(w)) + (w == wo ? "*" : ""), r.bilSpesa[w], r.bilVincita[w], r.bilVincita[w] - r.bilSpesa[w],
                        r.bilSpesa[w] > 0 ? r.bilVincita[w] * 100 / r.bilSpesa[w] : 0, r.bilVinti[w], r.bilProve[w]));
            }
            sb.append(String.format(Locale.ROOT, "%-16s %11.2f %11.2f %+11.2f %6.1f%%  %d/%d\n", "TOTALE", totSp, totVi, totVi - totSp,
                    totSp > 0 ? totVi * 100 / totSp : 0, totVinti, totProve));
            sb.append("Resa = vincite / spesa (100% = pareggio). Per i premi si usano le quote attuali anche per gli anni passati.\n");
        }
        sb.append("\nL'evidenziazione colora la spia (verde) e i numeri da giocare (rosso) nei colpi successivi, su tutte le ruote.\n");
        sb.append("Nota: i numeri sono ordinati con dati storici. Il walk-forward dice se funzionerebbero senza guardare\n")
                .append("il futuro; differenze piccole rispetto al caso non indicano un vantaggio reale.\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ scanner di spie

    /** Cicli minimi per valutare una spia nello scanner (walk-forward + abbastanza cicli testati). */
    public static final int MIN_CICLI_SCAN = MIN_STORICO + 15;
    private static final int RIGHE_SCAN = 20;

    public static final class RigaScan {
        public int spia, eventi;
        public int wfHit, wfProve;       // somma sulle ruote dei cicli con successo / cicli testati
        public double wfPct;
        public double zB;                // scarto dalla base senza spia, in deviazioni standard
        public double spesa, vincita, resa;
        public boolean due;              // test su due periodi eseguibile
        public int h2, n2;
        public double pct2, z2B;
        public final List<Integer> numeri = new ArrayList<>();   // numeri da giocare calcolati su tutti i cicli
        public int colpiRimasti = -1;    // >= 0: ciclo ancora aperto
    }

    public static final class RisultatoScan {
        public final List<RigaScan> righe = new ArrayList<>();   // ordinate per zB decrescente
        public int valutate, saltate, split;
        public double pCaso;             // successo di un ciclo per puro caso
        public RigaScan base;            // stessi numeri scelti dopo ogni estrazione, senza spia (null se non calcolabile)
        public double resaTotale;        // resa media di tutte le spie valutate
        public String report;

        public RigaScan migliore() { return righe.isEmpty() ? null : righe.get(0); }
    }

    private static double zScore(int hit, int prove, double p) {
        if (prove <= 0 || p <= 0 || p >= 1) return 0;
        return (hit - prove * p) / Math.sqrt(prove * p * (1 - p));
    }

    /** Walk-forward + due periodi + bilancio su un elenco di cicli (indici di partenza), con i numeri esclusi indicati. */
    private static RigaScan valuta(int[][][] ord, int wo, int[] anno, List<Integer> ev, boolean[] escludi, int colpi,
                                   int nGioco, int tipo, int split, boolean dividi) {
        final int nw = ord.length;
        int[] cicli = new int[91], freq = new int[91];
        int[][] hit = new int[nw][6];
        int[] prove = new int[nw];
        double[] spesa = new double[nw], vincita = new double[nw];
        int[] vinti = new int[nw], provaBil = new int[nw];
        for (int k = 0; k < ev.size(); k++) {
            int i = ev.get(k);
            if (k >= MIN_STORICO) {
                boolean[] g = new boolean[91];
                for (int x : primiK(cicli, freq, escludi, nGioco)) g[x] = true;
                valutaCiclo(ord, i, colpi, g, hit, prove);
                simulaGioco(ord, i, colpi, g, tipo, nGioco, spesa, vincita, vinti, provaBil);
            }
            accumula(ord, wo, i, colpi, cicli, freq);
        }
        RigaScan r = new RigaScan();
        r.eventi = ev.size();
        for (int w = 0; w < nw; w++) {
            r.wfHit += hit[w][tipo];
            r.wfProve += prove[w];
            r.spesa += spesa[w];
            r.vincita += vincita[w];
        }
        r.wfPct = r.wfProve > 0 ? r.wfHit * 100.0 / r.wfProve : 0;
        r.resa = r.spesa > 0 ? r.vincita * 100 / r.spesa : 0;
        for (int x : primiK(cicli, freq, escludi, nGioco)) r.numeri.add(x);

        if (dividi) {
            int[] c2 = new int[91], f2 = new int[91];
            int nTrain = 0;
            List<Integer> test = new ArrayList<>();
            for (int i : ev) {
                if (anno[i + colpi] < split) { accumula(ord, wo, i, colpi, c2, f2); nTrain++; }
                else if (anno[i] >= split) test.add(i);
            }
            if (nTrain >= MIN_STORICO && !test.isEmpty()) {
                boolean[] g2 = new boolean[91];
                for (int x : primiK(c2, f2, escludi, nGioco)) g2[x] = true;
                int[][] hit2 = new int[nw][6];
                int[] prove2 = new int[nw];
                for (int i : test) valutaCiclo(ord, i, colpi, g2, hit2, prove2);
                for (int w = 0; w < nw; w++) { r.h2 += hit2[w][tipo]; r.n2 += prove2[w]; }
                if (r.n2 > 0) {
                    r.due = true;
                    r.pct2 = r.h2 * 100.0 / r.n2;
                }
            }
        }
        return r;
    }

    /**
     * Prova tutte le spie (1-90, qualsiasi posizione) sulla ruota di origine e le ordina per il valore aggiunto
     * rispetto alla base senza spia nel walk-forward (verifica su tutte le ruote), con il test su due periodi
     * e il bilancio di gioco come controlli.
     */
    public static RisultatoScan scansiona(List<Extraction> archivio, String ruota, int y0, int y1, int colpi,
                                          int nGioco, int annoTest, int tipoGioco) {
        if (ruota == null || !Extraction.ALL_RUOTE.contains(ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        if (y0 > y1) throw new IllegalArgumentException("Anno iniziale maggiore dell'anno finale.");
        if (colpi < 1 || colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (nGioco < MIN_GIOCO || nGioco > MAX_GIOCO)
            throw new IllegalArgumentException("I numeri da giocare devono essere tra " + MIN_GIOCO + " e " + MAX_GIOCO + ".");
        if (tipoGioco < 2 || tipoGioco > 5) throw new IllegalArgumentException("La giocata deve essere ambo, terno, quaterna o cinquina.");
        if (tipoGioco > nGioco)
            throw new IllegalArgumentException("Per " + TIPO[tipoGioco].toLowerCase(Locale.ROOT) + " servono almeno " + tipoGioco + " numeri da giocare.");
        if (annoTest != 0 && (annoTest <= y0 || annoTest > y1))
            throw new IllegalArgumentException("L'anno di inizio del test deve essere dopo l'anno iniziale e non oltre quello finale (oppure 0).");

        List<Extraction> seq = new ArrayList<>();
        for (Extraction e : SantoSpiaCore.cronologico(archivio)) {
            int y = e.getYear();
            if (y < y0 || y > y1) continue;
            List<Integer> n = e.getNumbersForRuota(ruota);
            if (n != null && n.size() == 5) seq.add(e);
        }
        if (seq.isEmpty()) throw new IllegalArgumentException("Nessuna estrazione valida per ruota e periodo selezionati.");
        final int n = seq.size();
        final List<String> ruote = Extraction.ALL_RUOTE;
        final int nw = ruote.size();
        final int wo = ruote.indexOf(ruota);

        int[][][] ord = new int[nw][n][];
        for (int w = 0; w < nw; w++) {
            for (int j = 0; j < n; j++) {
                List<Integer> l = seq.get(j).getNumbersForRuota(ruote.get(w));
                if (l == null || l.size() != 5) continue;
                int[] a = new int[5];
                for (int i = 0; i < 5; i++) a[i] = l.get(i);
                ord[w][j] = a;
            }
        }
        int[] anno = new int[n];
        for (int j = 0; j < n; j++) anno[j] = seq.get(j).getYear();

        RisultatoScan res = new RisultatoScan();
        int split = annoTest > 0 ? annoTest : (int) Math.round(y0 + (y1 - y0 + 1) * 0.7);
        res.split = split;
        final boolean dividi = split > y0 && split <= y1;
        res.pCaso = 1 - Math.pow(1 - probEstrazione(nGioco, tipoGioco), colpi);

        // base senza spia: stessa procedura con un ciclo dopo ogni estrazione
        List<Integer> tutti = new ArrayList<>();
        for (int i = 0; i + colpi < n; i++) tutti.add(i);
        if (tutti.size() > MIN_STORICO) {
            RigaScan b = valuta(ord, wo, anno, tutti, new boolean[91], colpi, nGioco, tipoGioco, split, dividi);
            if (b.wfProve > 0) res.base = b;
        }
        final double pB = res.base != null ? res.base.wfHit / (double) res.base.wfProve : res.pCaso;
        final double pB2 = res.base != null && res.base.due ? res.base.h2 / (double) res.base.n2 : res.pCaso;

        // le 90 spie sono indipendenti: si calcolano in parallelo (i dati sono in sola lettura)
        final int split2 = split;
        final int[][][] ordF = ord;
        final int[] annoF = anno;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors())));
        List<java.util.concurrent.Future<RigaScan>> fut = new ArrayList<>();
        try {
            for (int sp0 = 1; sp0 <= 90; sp0++) {
                final int sp = sp0;
                fut.add(pool.submit(() -> {
                    List<Integer> ev = new ArrayList<>();
                    int aperto = -1;
                    for (int i = 0; i < n; i++) {
                        boolean c = false;
                        for (int x : ordF[wo][i]) if (x == sp) { c = true; break; }
                        if (!c) continue;
                        if (i + colpi < n) ev.add(i); else aperto = i;
                    }
                    if (ev.size() < MIN_CICLI_SCAN) return null;
                    boolean[] escludi = new boolean[91];
                    escludi[sp] = true;
                    RigaScan r = valuta(ordF, wo, annoF, ev, escludi, colpi, nGioco, tipoGioco, split2, dividi);
                    if (r.wfProve == 0) return null;
                    r.spia = sp;
                    r.zB = zScore(r.wfHit, r.wfProve, pB);
                    if (r.due) r.z2B = zScore(r.h2, r.n2, pB2);
                    if (aperto >= 0) r.colpiRimasti = colpi - (n - 1 - aperto);
                    return r;
                }));
            }
            double totSpesa = 0, totVincita = 0;
            for (java.util.concurrent.Future<RigaScan> f : fut) {
                RigaScan r = f.get();
                if (r == null) { res.saltate++; continue; }
                totSpesa += r.spesa;
                totVincita += r.vincita;
                res.righe.add(r);
            }
            res.resaTotale = totSpesa > 0 ? totVincita * 100 / totSpesa : 0;
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable c = ex.getCause() != null ? ex.getCause() : ex;
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            throw new IllegalStateException(c);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scansione interrotta.");
        } finally {
            pool.shutdownNow();
        }
        res.valutate = res.righe.size();
        res.righe.sort((a, b) -> {
            int c = Double.compare(b.zB, a.zB);
            if (c != 0) return c;
            c = Double.compare(b.wfPct, a.wfPct);
            return c != 0 ? c : Integer.compare(a.spia, b.spia);
        });
        res.report = reportScan(res, ruota, y0, y1, colpi, nGioco, tipoGioco, dividi, pB);
        return res;
    }

    private static String reportScan(RisultatoScan r, String ruota, int y0, int y1, int colpi, int nGioco, int tipo,
                                     boolean dividi, double pB) {
        StringBuilder sb = new StringBuilder();
        String t = TIPO[tipo].toLowerCase(Locale.ROOT);
        sb.append("* SCANNER DI SPIE - CLASSIFICA PER VALORE AGGIUNTO DELLA SPIA\n").append(LINEA).append("\n");
        sb.append("Ruota di origine: ").append(nomeRuota(ruota)).append(" - Periodo: ").append(y0).append("-").append(y1)
                .append(" - ").append(colpi).append(" colpi - ").append(nGioco).append(" numeri da giocare - sorte: ").append(t).append("\n");
        sb.append("Spie valutate: ").append(r.valutate).append(" su 90 (saltate ").append(r.saltate)
                .append(" per meno di ").append(MIN_CICLI_SCAN).append(" cicli completi)\n");
        if (dividi) sb.append("Test su due periodi: ").append(y0).append("-").append(r.split - 1).append(" / ").append(r.split).append("-").append(y1).append("\n");
        sb.append(String.format(Locale.ROOT, "Successo di un ciclo (%s su almeno una delle 11 ruote entro %d colpi): per puro caso %.1f%%",
                t, colpi, r.pCaso * 100));
        if (r.base != null) {
            sb.append(String.format(Locale.ROOT, ", senza spia %.1f%% (stessi numeri scelti dopo ogni estrazione)\n", pB * 100));
            sb.append(String.format(Locale.ROOT, "Base senza spia: resa nel bilancio di gioco %.1f%%.\n", r.base.resa));
        } else {
            sb.append("\nBase senza spia non calcolabile: il confronto è col puro caso.\n");
        }
        sb.append("Ordine: scarto z dalla base senza spia nel walk-forward (i numeri di ogni ciclo sono scelti solo con i cicli\n")
                .append("precedenti), cioè quanto la spia aggiunge rispetto a giocare gli stessi numeri senza guardarla.\n\n");
        if (r.righe.isEmpty()) {
            sb.append("Nessuna spia ha abbastanza cicli: allarga il periodo o riduci i colpi.\n");
            return sb.toString();
        }

        sb.append("CLASSIFICA (prime ").append(Math.min(RIGHE_SCAN, r.righe.size())).append(")\n").append(TRATTI).append("\n");
        sb.append(String.format(Locale.ROOT, "%3s %-5s %5s %8s %7s %7s %8s %7s  %-20s %s\n",
                "#", "Spia", "Cicli", "WF succ.", "Base", "zB", "Resa", "z2B", "Numeri da giocare", "Stato"));
        for (int i = 0; i < Math.min(RIGHE_SCAN, r.righe.size()); i++) {
            RigaScan x = r.righe.get(i);
            StringBuilder num = new StringBuilder();
            for (int j = 0; j < x.numeri.size(); j++) num.append(j > 0 ? "-" : "").append(nn(x.numeri.get(j)));
            String z2 = x.due ? String.format(Locale.ROOT, "%+7.2f", x.z2B) : "   n.d.";
            String stato = x.colpiRimasti >= 0 ? "ATTIVA (" + x.colpiRimasti + " colpi)" : "";
            sb.append(String.format(Locale.ROOT, "%3d %-5s %5d %7.1f%% %6.1f%% %+7.2f %7.1f%% %s  %-20s %s\n",
                    i + 1, nn(x.spia), x.eventi, x.wfPct, pB * 100, x.zB, x.resa, z2, num, stato));
        }

        int nA = 0, nB = 0;
        for (RigaScan x : r.righe) {
            if (x.zB >= 2) nA++;
            if (x.zB >= 2 && x.due && x.z2B >= 1.645) nB++;
        }
        sb.append("\nLETTURA ONESTA\n").append(TRATTI).append("\n");
        if (r.base != null) {
            sb.append(String.format(Locale.ROOT, "Senza spia il successo è %.1f%% contro %.1f%% del puro caso: la parte oltre il caso non dipende dalla spia.\n",
                    pB * 100, r.pCaso * 100));
        }
        sb.append(String.format(Locale.ROOT, "Spie con z >= 2 rispetto alla base: %d su %d. Per puro caso se ne attenderebbero circa %.1f (con i cicli\n",
                nA, r.valutate, r.valutate * 0.02275));
        sb.append("che si sovrappongono lo z è un po' ottimista: qualcuna in più è normale).\n");
        if (dividi) sb.append(String.format(Locale.ROOT, "Spie che reggono anche nel secondo periodo (z >= 2 e z2B >= 1,65): %d (per caso circa %.2f).\n",
                nB, r.valutate * 0.02275 * 0.05));
        sb.append(String.format(Locale.ROOT, "Resa media di tutte le spie nel bilancio di gioco: %.1f%% (pareggio = 100%%).\n", r.resaTotale));
        sb.append("Provando 90 spie, qualcuna in testa c'è per fortuna: conta se superano nettamente il caso e reggono nel secondo periodo.\n")
                .append("L'approssimazione con lo z è meno precisa per terno, quaterna e cinquina (pochi successi): usa l'ambo.\n");
        sb.append("\nPer analizzare una spia a fondo usa \"Verifica spia su tutte le ruote\" con lo stesso periodo e gli stessi colpi.\n");
        return sb.toString();
    }
}
