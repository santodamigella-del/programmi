package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Porting di calcola_super_previsione_lotto_light() e calcola_previsione_algoritmi_ia() (gioco LOTTO) del main.py.
 * I numeri e i testi sono gli stessi del programma Python; in coda alla previsione IA è aggiunta una nota sul
 * significato degli indici (non cambia alcun calcolo).
 */
public final class PrevisioniIa {

    private PrevisioniIa() { }

    public static final class Previsione {
        public String errore;
        public String testo;
        public int capogioco;
        public List<Integer> abbinamenti = new ArrayList<>();
        public List<Integer> topNumeri = new ArrayList<>();
    }

    private static final String LINEA = "============================================================";
    private static final String TRATTI = "------------------------------------------------------------";

    // ------------------------------------------------------------------ utilità numeriche (come Python)

    /** Formato di Python f"{x:.Nf}": arrotondamento half-even sul valore binario esatto. */
    static String fmt(double x, int decimali) {
        return new BigDecimal(x).setScale(decimali, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** Python round(x, n). */
    static double round(double x, int n) {
        return new BigDecimal(x).setScale(n, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static String nn(int n) { return String.format("%02d", n); }

    private static String dataIt(int key) {
        return String.format("%02d/%02d/%04d", key % 100, (key / 100) % 100, key / 10000);
    }

    private static List<Extraction> seqRuota(List<Extraction> archive, String ruota, int y0, int y1) {
        List<Extraction> seq = new ArrayList<>();
        for (Extraction e : SantoSpiaCore.cronologico(archive)) {
            int y = e.getYear();
            if (y < y0 || y > y1) continue;
            List<Integer> n = e.getNumbersForRuota(ruota);
            if (n != null && n.size() == 5) seq.add(e);
        }
        return seq;
    }

    private static boolean contiene(List<Integer> l, int x) { return l.contains(x); }

    private static final class Ambo {
        final int a, b;
        Ambo(int a, int b) { this.a = a; this.b = b; }
    }

    private static List<Map.Entry<Long, Integer>> piuComuni(Map<Long, Integer> m, int n) {
        List<Map.Entry<Long, Integer>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, (x, y) -> Integer.compare(y.getValue(), x.getValue())); // stabile: pareggi per ordine di inserimento
        return new ArrayList<>(l.subList(0, Math.min(n, l.size())));
    }

    private static int[] ritardi(List<Extraction> seq, String ruota) {
        int total = seq.size();
        int[] rit = new int[91];
        for (int n = 1; n <= 90; n++) {
            int r = total;
            for (int i = total - 1; i >= 0; i--) {
                if (seq.get(i).getNumbersForRuota(ruota).contains(n)) { r = (total - 1) - i; break; }
            }
            rit[n] = r;
        }
        return rit;
    }

    // ------------------------------------------------------------------ super previsione light

    public static Previsione superPrevisioneLight(List<Extraction> archive, int spia, String ruota, int colpi, int y0, int y1) {
        Previsione out = new Previsione();
        List<Extraction> seq = seqRuota(archive, ruota, y0, y1);
        int total = seq.size();
        if (total < 5) { out.errore = "Archivio insufficiente per la ruota selezionata."; return out; }

        List<Integer> indiciSpia = new ArrayList<>();
        for (int i = 0; i < total; i++) if (contiene(seq.get(i).getNumbersForRuota(ruota), spia)) indiciSpia.add(i);
        if (indiciSpia.isEmpty()) {
            out.errore = "Spia " + nn(spia) + " mai uscita su " + ruota + " tra il " + y0 + " e il " + y1 + ".";
            return out;
        }

        int[] freq = new int[91];
        Map<Long, Integer> ambi = new LinkedHashMap<>();
        for (int idx : indiciSpia) {
            int fine = Math.min(idx + 1 + colpi, total);
            for (int sub = idx + 1; sub < fine; sub++) {
                List<Integer> cinq = seq.get(sub).getNumbersForRuota(ruota);
                for (int n : cinq) freq[n]++;
                for (int a = 0; a < cinq.size(); a++) {
                    for (int b = a + 1; b < cinq.size(); b++) {
                        int lo = Math.min(cinq.get(a), cinq.get(b)), hi = Math.max(cinq.get(a), cinq.get(b));
                        ambi.merge((long) lo * 100 + hi, 1, Integer::sum);
                    }
                }
            }
        }
        int[] rit = ritardi(seq, ruota);
        int maxF = 1; boolean any = false;
        for (int n = 1; n <= 90; n++) if (freq[n] > 0) { maxF = any ? Math.max(maxF, freq[n]) : freq[n]; any = true; }
        int maxR = 1;
        for (int n = 1; n <= 90; n++) maxR = n == 1 ? rit[n] : Math.max(maxR, rit[n]);

        final double[] punt = new double[91];
        for (int n = 1; n <= 90; n++) {
            double fs = maxF > 0 ? (double) freq[n] / maxF : 0.0;
            double rs = maxR > 0 ? (double) rit[n] / maxR : 0.0;
            punt[n] = round((fs * 0.60) + (rs * 0.40), 3);
        }
        List<Integer> cl = new ArrayList<>();
        for (int n = 1; n <= 90; n++) cl.add(n);
        Collections.sort(cl, (a, b) -> Double.compare(punt[b], punt[a])); // stabile: pareggi per numero crescente
        int capo = cl.get(0);
        double scoreCg = punt[capo];
        List<Integer> abb = new ArrayList<>(cl.subList(1, 5));

        StringBuilder sb = new StringBuilder();
        sb.append(LINEA).append("\n").append("SUPER PREVISIONE STATISTICA LIGHT (LOTTO)").append("\n").append(LINEA).append("\n");
        sb.append("Ruota: ").append(ruota).append(" | Spia: ").append(nn(spia)).append(" | Colpi: ").append(colpi).append("\n");
        sb.append("Periodo: ").append(dataIt(seq.get(0).getDateKey())).append(" -> ").append(dataIt(seq.get(total - 1).getDateKey()))
                .append(" (").append(total).append(" concorsi)\n");
        sb.append("Presenze spia: ").append(indiciSpia.size()).append(" volte\n").append(TRATTI).append("\n");
        sb.append("AMBATA CAPOGIOCO: ").append(nn(capo)).append(" (Indice resa: ").append(fmt(scoreCg, 3)).append(")\n");
        sb.append("  • Frequenza post-spia : ").append(freq[capo]).append(" volte\n");
        sb.append("  • Ritardo attuale     : ").append(rit[capo]).append(" concorsi\n\n");
        StringBuilder abbStr = new StringBuilder();
        for (int i = 0; i < abb.size(); i++) abbStr.append(i > 0 ? ", " : "").append(nn(abb.get(i)));
        sb.append("ABBINAMENTI PER AMBO: ").append(abbStr).append("\n\nAMBI SECCHI PRONTI:\n");
        for (int a : abb) sb.append("  • ").append(nn(capo)).append(" - ").append(nn(a)).append("\n");
        sb.append("\nTOP 3 AMBI STORICI POST-SPIA (").append(nn(spia)).append("):");
        for (Map.Entry<Long, Integer> e : piuComuni(ambi, 3)) {
            sb.append("\n  • Ambo ").append(nn((int) (e.getKey() / 100))).append(" - ").append(nn((int) (e.getKey() % 100)))
                    .append(" (uscito ").append(e.getValue()).append(" volte)");
        }
        out.testo = sb.toString();
        out.capogioco = capo;
        out.abbinamenti = abb;
        out.topNumeri.add(capo);
        out.topNumeri.addAll(abb);
        return out;
    }

    // ------------------------------------------------------------------ previsione con algoritmi IA

    public static Previsione previsioneAlgoritmiIa(List<Extraction> archive, int spia, int y0, int y1, int colpi, String ruota) {
        Previsione out = new Previsione();
        List<Extraction> seq = seqRuota(archive, ruota, y0, y1);
        double pSingle = 5.0 / 90.0, pPair = 10.0 / 4005.0;
        int nTot = seq.size();
        if (nTot < 5) { out.errore = "Archivio LOTTO insufficiente nell'intervallo " + y0 + "-" + y1 + "."; return out; }

        List<Integer> indici = new ArrayList<>();
        for (int i = 0; i < nTot; i++) if (contiene(seq.get(i).getNumbersForRuota(ruota), spia) && i + colpi < nTot) indici.add(i);
        int nSpia = indici.size();
        if (nSpia < 1) {
            out.errore = "Spia " + nn(spia) + " mai presente nell'archivio selezionato (" + y0 + "-" + y1 + ").";
            return out;
        }
        double pBaseSingle = 1.0 - Math.pow(1.0 - pSingle, colpi);

        int[] freq = new int[91];
        double[] decayW = new double[91];
        Map<Long, Integer> ambi = new LinkedHashMap<>();
        for (int idx : indici) {
            boolean[] visti = new boolean[91];
            for (int dt = 1; dt <= colpi; dt++) {
                if (idx + dt < nTot) {
                    TreeSet<Integer> nums = new TreeSet<>(seq.get(idx + dt).getNumbersForRuota(ruota));
                    double decay = Math.pow(0.89, dt - 1);
                    for (int num : nums) {
                        if (num != spia) {
                            freq[num]++;
                            if (!visti[num]) { decayW[num] += decay; visti[num] = true; }
                        }
                    }
                    List<Integer> l = new ArrayList<>(nums);
                    l.remove(Integer.valueOf(spia));
                    for (int i1 = 0; i1 < l.size(); i1++)
                        for (int i2 = i1 + 1; i2 < l.size(); i2++)
                            ambi.merge((long) l.get(i1) * 100 + l.get(i2), 1, Integer::sum);
                }
            }
        }
        int[] rit = ritardi(seq, ruota);
        int maxF = 1; boolean any = false;
        for (int n = 1; n <= 90; n++) if (freq[n] > 0) { maxF = any ? Math.max(maxF, freq[n]) : freq[n]; any = true; }
        int maxR = 1;
        for (int n = 1; n <= 90; n++) maxR = n == 1 ? rit[n] : Math.max(maxR, rit[n]);

        final double[] score = new double[91];
        double[] confPct = new double[91], lift = new double[91], chi2 = new double[91];
        List<Integer> cl = new ArrayList<>();
        for (int num = 1; num <= 90; num++) {
            if (num == spia) continue;
            int c = freq[num];
            double conf = nSpia > 0 ? c / (double) nSpia : 0.0;
            double lf = pBaseSingle > 0 ? conf / pBaseSingle : 0.0;
            double exp = nSpia * pBaseSingle;
            double ch = exp > 0 ? Math.pow(c - exp, 2) / exp : 0.0;
            double fNorm = maxF > 0 ? c / (double) maxF : 0.0;
            double rNorm = maxR > 0 ? rit[num] / (double) maxR : 0.0;
            double light = (fNorm * 0.60) + (rNorm * 0.40);
            double ens = (conf * 3.5) + (lf * 2.0) + (light * 2.5) + (Math.sqrt(ch) * 1.2) + (decayW[num] / Math.max(1, nSpia));
            score[num] = ens;
            confPct[num] = Math.min(99.8, (ens / 10.0) * 100.0);
            lift[num] = lf;
            chi2[num] = ch;
            cl.add(num);
        }
        Collections.sort(cl, (a, b) -> Double.compare(-score[a], -score[b])); // stabile
        int capo = cl.get(0);
        List<Integer> abb = new ArrayList<>(cl.subList(1, 5));
        List<Integer> oro = new ArrayList<>(cl.subList(0, 5));

        StringBuilder sb = new StringBuilder();
        sb.append(LINEA).append("\nPREVISIONE CON ALGORITMI IA (NEURAL ENSEMBLE)\n").append(LINEA).append("\n");
        sb.append("Gioco: LOTTO | Ruota: ").append(ruota).append(" | Spia: ").append(nn(spia)).append(" | Colpi: ").append(colpi).append("\n");
        sb.append("Archivio: ").append(nSpia).append(" uscite della spia in ").append(nTot).append(" concorsi\n");
        sb.append("Incrocio IA: LSTM Sequence + Apriori Rules + Chi-Square + Weighted Score\n").append(TRATTI).append("\n");
        sb.append("AMBATA CAPOGIOCO IA: ").append(nn(capo)).append("  (Affidabilità IA: ").append(fmt(confPct[capo], 1)).append("%)\n");
        sb.append("   • Freq post-spia : ").append(freq[capo]).append(" volte | Ritardo attuale: ").append(rit[capo]).append(" concorsi\n");
        sb.append("   • Correlazione Lift: ").append(fmt(lift[capo], 2)).append(" | Test Chi-Quadrato: ").append(fmt(chi2[capo], 2)).append("\n\n");
        StringBuilder abbStr = new StringBuilder();
        for (int i = 0; i < abb.size(); i++) abbStr.append(i > 0 ? ", " : "").append(nn(abb.get(i)));
        sb.append("ABBINAMENTI PER AMBO IA: ").append(abbStr).append("\n\nAMBI SECCHI PRONTI IA:\n");
        for (int a : abb) sb.append("   • ").append(nn(capo)).append(" - ").append(nn(a)).append("  (Conf. Ambo: ").append(fmt(confPct[a], 1)).append("%)\n");
        StringBuilder oroStr = new StringBuilder();
        for (int i = 0; i < oro.size(); i++) oroStr.append(i > 0 ? " - " : "").append(nn(oro.get(i)));
        sb.append("\nCINQUINA D'ORO IA: ").append(oroStr).append("\n\n").append(TRATTI).append("\nTOP AMBI FREQUENT PATTERN IA\n").append(TRATTI);
        for (Map.Entry<Long, Integer> e : piuComuni(ambi, 3)) {
            double confPair = nSpia > 0 ? (e.getValue() / (double) nSpia) * 100.0 : 0.0;
            sb.append("\n   • Ambo ").append(nn((int) (e.getKey() / 100))).append(" - ").append(nn((int) (e.getKey() % 100)))
                    .append(" | Uscito ").append(e.getValue()).append("/").append(nSpia).append(" volte (").append(fmt(confPair, 1)).append("%)");
        }
        out.testo = sb.toString();
        out.capogioco = capo;
        out.abbinamenti = abb;
        out.topNumeri = oro;
        return out;
    }

    /** Nota da mostrare accanto alla previsione IA (non fa parte del calcolo del main.py). */
    public static final String NOTA_IA =
            "\n\nNota: \"Neural ensemble\" e \"Affidabilità IA\" sono nomi del programma: il calcolo è una formula che combina frequenza, "
                    + "ritardo, lift e chi-quadrato (nessuna rete neurale). L'\"affidabilità\" è un indice relativo, non una probabilità: "
                    + "ogni numero ha comunque la stessa probabilità di uscire.";
}
