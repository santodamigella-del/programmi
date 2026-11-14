package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Porting in Java dei calcoli di "Santo Spia Evolutions" (main.py) per il Lotto:
 * modello post-spia, punteggi, ambi/terni di previsione, ciclometria, terni magici, incrocio,
 * convergenze, verifica combinazioni dopo la spia, Apriori, Somma Ruote e Somma + Fisso.
 *
 * Codice puro Java (nessuna dipendenza Android): usato da app Android e versione desktop.
 * I controlli sui parametri (spia 1-90, colpi 1-50, anno iniziale <= finale, ruota valida)
 * sono quelli del main.py e lanciano IllegalArgumentException con lo stesso messaggio.
 */
public final class SantoSpiaCore {

    private SantoSpiaCore() { }

    // ===================================================================== controlli (come nel main.py)

    public static void validaParametri(int spia, String ruota, int annoInizio, int annoFine, int colpi) {
        if (spia < 1 || spia > 90) throw new IllegalArgumentException("La spia deve essere tra 1 e 90.");
        if (annoInizio > annoFine) throw new IllegalArgumentException("Anno iniziale maggiore dell'anno finale.");
        if (colpi < 1 || colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (ruota == null || !Extraction.ALL_RUOTE.contains(ruota)) {
            throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        }
    }

    /** Controlli per i metodi che non usano il periodo (stessi messaggi del main.py). */
    public static void validaSpiaColpiRuota(int spia, String ruota, int colpi) {
        if (spia < 1 || spia > 90) throw new IllegalArgumentException("La spia deve essere tra 1 e 90.");
        if (colpi < 1 || colpi > 50) throw new IllegalArgumentException("I colpi devono essere tra 1 e 50.");
        if (ruota == null || !Extraction.ALL_RUOTE.contains(ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
    }

    /** "Nessuna estrazione valida per ruota e periodo selezionati." come nel resoconto del main.py. */
    public static void validaDisponibilita(List<Extraction> archive, String ruota, int y0, int y1) {
        if (estrazioniRuota(archive, ruota, y0, y1).isEmpty()) {
            throw new IllegalArgumentException("Nessuna estrazione valida per ruota e periodo selezionati.");
        }
    }

    // ===================================================================== dati di base

    /** Estrazioni dalla più vecchia alla più recente (una per data). */
    public static List<Extraction> cronologico(List<Extraction> archive) {
        List<Extraction> sorted = new ArrayList<>(archive == null ? new ArrayList<Extraction>() : archive);
        Collections.sort(sorted, (a, b) -> {
            int c = Integer.compare(a.getDateKey(), b.getDateKey());
            return c != 0 ? c : Integer.compare(a.getConcorsoNumber(), b.getConcorsoNumber());
        });
        return sorted;
    }

    /** estrazioni_ruota() del main.py: estrazioni di una ruota nel periodo, in ordine cronologico. */
    public static List<int[]> estrazioniRuota(List<Extraction> archive, String ruota, int y0, int y1) {
        List<int[]> out = new ArrayList<>();
        for (Extraction e : cronologico(archive)) {
            int y = e.getYear();
            if (y < y0 || y > y1) continue;
            List<Integer> nums = e.getNumbersForRuota(ruota);
            if (nums == null || nums.size() != 5) continue;
            int[] d = new int[5];
            for (int i = 0; i < 5; i++) d[i] = nums.get(i);
            out.add(d);
        }
        return out;
    }

    private static boolean contiene(int[] d, int x) {
        for (int v : d) if (v == x) return true;
        return false;
    }

    // ===================================================================== modello post-spia + punteggi

    public static final class Punteggio {
        public int numero, frequenza, cicli;
        public double mediaPosizione, punteggio;
    }

    public static final class Modello {
        public int spia, colpi;
        public List<Integer> indiciEventi = new ArrayList<>();
        public List<List<int[]>> successive = new ArrayList<>();  // per evento: i 'colpi' draw successivi
        public int[] freq = new int[91];                          // include la spia, come analizza_numero_spia
        public int[] cicli = new int[91];
        public List<List<Integer>> posizioni = new ArrayList<>(); // indice 0..90
        public Map<Integer, Punteggio> punteggi = new LinkedHashMap<>();
        public int nEventi() { return successive.size(); }
    }

    /** analizza_numero_spia() + calcola_punteggi() del main.py. */
    public static Modello costruisciModello(List<Extraction> archive, int spia, String ruota, int y0, int y1, int colpi) {
        List<int[]> estr = estrazioniRuota(archive, ruota, y0, y1);
        Modello m = new Modello();
        m.spia = spia;
        m.colpi = colpi;
        for (int i = 0; i <= 90; i++) m.posizioni.add(new ArrayList<Integer>());

        for (int i = 0; i < estr.size(); i++) {
            if (!contiene(estr.get(i), spia)) continue;
            if (i + colpi >= estr.size()) continue; // len(successive) < colpi
            List<int[]> succ = new ArrayList<>(estr.subList(i + 1, i + 1 + colpi));
            Set<Integer> ciclo = new HashSet<>();
            int[] prima = new int[91];
            for (int p = 0; p < succ.size(); p++) {
                for (int n : succ.get(p)) {
                    m.freq[n]++;
                    ciclo.add(n);
                    if (prima[n] == 0) prima[n] = p + 1;
                }
            }
            for (int n = 1; n <= 90; n++) {
                if (ciclo.contains(n)) {
                    m.cicli[n]++;
                    m.posizioni.get(n).add(prima[n]);
                }
            }
            m.indiciEventi.add(i);
            m.successive.add(succ);
        }

        if (m.nEventi() > 0) {
            int maxF = 1;
            boolean any = false;
            for (int n = 1; n <= 90; n++) {
                if (m.freq[n] > 0) { maxF = any ? Math.max(maxF, m.freq[n]) : m.freq[n]; any = true; }
            }
            for (int n = 1; n <= 90; n++) {
                if (n == spia || m.freq[n] == 0) continue;
                double sf = ((double) m.freq[n] / maxF) * 40.0;
                double sc = ((double) m.cicli[n] / m.nEventi()) * 40.0;
                List<Integer> pos = m.posizioni.get(n);
                double media = 0.0, sp = 0.0;
                if (!pos.isEmpty()) {
                    int s = 0;
                    for (int v : pos) s += v;
                    media = (double) s / pos.size();
                    sp = ((colpi + 1 - media) / colpi) * 20.0;
                }
                Punteggio p = new Punteggio();
                p.numero = n;
                p.frequenza = m.freq[n];
                p.cicli = m.cicli[n];
                p.mediaPosizione = media;
                p.punteggio = sf + sc + sp;
                m.punteggi.put(n, p);
            }
        }
        return m;
    }

    private static List<Punteggio> classifica(Modello m) {
        List<Punteggio> l = new ArrayList<>(m.punteggi.values());
        Collections.sort(l, (a, b) -> {
            int c = Double.compare(b.punteggio, a.punteggio);
            if (c != 0) return c;
            c = Integer.compare(b.frequenza, a.frequenza);
            return c != 0 ? c : Integer.compare(a.numero, b.numero);
        });
        return l;
    }

    /** Classifica completa dei numeri per punteggio (come ordina il main.py). */
    public static List<Punteggio> classificaPunteggi(Modello m) {
        return classifica(m);
    }

    /** I primi N numeri per punteggio (come top_post del main.py). */
    public static List<Integer> topPost(Modello m, int n) {
        List<Integer> out = new ArrayList<>();
        for (Punteggio p : classifica(m)) {
            if (out.size() >= n) break;
            out.add(p.numero);
        }
        return out;
    }

    // ===================================================================== ambi / terni di previsione

    public static final class Combinazione {
        public int[] numeri;
        public double score;
        public int coPresenze;
    }

    /** Confronto lessicografico tra due array di interi (come il confronto tra tuple in Python). */
    static int cmp(int[] a, int[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        }
        return Integer.compare(a.length, b.length);
    }

    private static long chiave(int[] c) {
        long k = 0;
        for (int v : c) k = k * 91 + v;
        return k;
    }

    private static void combinazioni(int[] base, int k, int start, int[] cur, int depth, List<int[]> out) {
        if (depth == k) { out.add(cur.clone()); return; }
        for (int i = start; i < base.length; i++) {
            cur[depth] = base[i];
            combinazioni(base, k, i + 1, cur, depth + 1, out);
        }
    }

    public static List<int[]> combinazioni(int[] base, int k) {
        List<int[]> out = new ArrayList<>();
        if (k <= base.length) combinazioni(base, k, 0, new int[k], 0, out);
        return out;
    }

    /**
     * genera_combinazioni_previsione_lotto(): ambi/terni tra i 18 numeri migliori.
     * NOTA: nel main.py le co-presenze vengono cercate con una chiave ordinata per punteggio invece che
     * numericamente; qui la chiave è sempre ordinata (versione corretta).
     */
    public static List<Combinazione> generaCombinazioni(Modello m, int dimensione, int quantita) {
        List<Integer> cand = new ArrayList<>();
        for (Punteggio p : classifica(m)) {
            if (p.numero != m.spia) cand.add(p.numero);
            if (cand.size() >= 18) break;
        }
        List<Combinazione> out = new ArrayList<>();
        if (cand.size() < dimensione) return out;
        Set<Integer> insieme = new HashSet<>(cand);

        Map<Long, Integer> co = new HashMap<>();
        for (List<int[]> ev : m.successive) {
            for (int[] draw : ev) {
                TreeSet<Integer> pres = new TreeSet<>();
                for (int n : draw) if (insieme.contains(n)) pres.add(n);
                if (pres.size() >= dimensione) {
                    int[] arr = new int[pres.size()];
                    int i = 0;
                    for (int v : pres) arr[i++] = v;
                    for (int[] c : combinazioni(arr, dimensione)) co.merge(chiave(c), 1, Integer::sum);
                }
            }
        }
        int[] candArr = new int[cand.size()];
        for (int i = 0; i < candArr.length; i++) candArr[i] = cand.get(i);
        for (int[] comb : combinazioni(candArr, dimensione)) {
            double base = 0;
            for (int n : comb) {
                Punteggio p = m.punteggi.get(n);
                base += p == null ? 0 : p.punteggio;
            }
            base = base / dimensione;
            int[] sorted = comb.clone();
            Arrays.sort(sorted);
            int c = co.getOrDefault(chiave(sorted), 0);
            Combinazione r = new Combinazione();
            r.numeri = comb;
            r.score = base + Math.min(10.0, c * 0.35);
            r.coPresenze = c;
            out.add(r);
        }
        Collections.sort(out, (a, b) -> {
            int x = Double.compare(b.score, a.score);
            if (x != 0) return x;
            x = Integer.compare(b.coPresenze, a.coPresenze);
            return x != 0 ? x : cmp(a.numeri, b.numeri);
        });
        return new ArrayList<>(out.subList(0, Math.min(quantita, out.size())));
    }

    // ===================================================================== ciclometria

    public static int distanzaCiclometrica(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, 90 - d);
    }

    public static final class CicloNumero {
        public int numero, distanza, frequenza;
        public double presenza, lift, stabilita, posizioneMedia, score;
    }

    public static final class Ciclometria {
        public int eventi;
        public List<CicloNumero> risultati = new ArrayList<>();
        public List<Integer> top(int n) {
            List<Integer> out = new ArrayList<>();
            for (CicloNumero c : risultati) { if (out.size() >= n) break; out.add(c.numero); }
            return out;
        }
    }

    /** analizza_ciclometria_spia() del main.py (null se non ci sono eventi). */
    public static Ciclometria analizzaCiclometria(List<Extraction> archive, int spia, String ruota, int y0, int y1, int colpi) {
        List<int[]> estr = estrazioniRuota(archive, ruota, y0, y1);
        List<Integer> eventi = new ArrayList<>();
        for (int i = 0; i < estr.size(); i++) {
            if (contiene(estr.get(i), spia) && i + colpi < estr.size()) eventi.add(i);
        }
        if (eventi.isEmpty()) return null;

        int[] freq = new int[91];
        Map<Integer, Integer> distFreq = new HashMap<>();
        List<List<Integer>> pos = new ArrayList<>();
        for (int i = 0; i <= 90; i++) pos.add(new ArrayList<Integer>());
        for (int i : eventi) {
            int[] primi = new int[91];
            for (int k = 1; k <= colpi; k++) {
                for (int n : estr.get(i + k)) {
                    if (n == spia) continue;
                    int d = distanzaCiclometrica(spia, n);
                    freq[n]++;
                    distFreq.merge(d, 1, Integer::sum);
                    if (primi[n] == 0) primi[n] = k;
                }
            }
            for (int n = 1; n <= 90; n++) if (primi[n] > 0) pos.get(n).add(primi[n]);
        }
        double atteso = ((double) eventi.size() * colpi * 5) / 90.0;
        int maxF = 1;
        boolean any = false;
        for (int n = 1; n <= 90; n++) if (freq[n] > 0) { maxF = any ? Math.max(maxF, freq[n]) : freq[n]; any = true; }
        int maxD = 1;
        boolean anyD = false;
        for (int v : distFreq.values()) { maxD = anyD ? Math.max(maxD, v) : v; anyD = true; }

        Ciclometria r = new Ciclometria();
        r.eventi = eventi.size();
        for (int n = 1; n <= 90; n++) {
            if (n == spia) continue;
            int d = distanzaCiclometrica(spia, n);
            int f = freq[n];
            double pres = (double) f / maxF;
            double anello = (double) distFreq.getOrDefault(d, 0) / maxD;
            List<Integer> ks = pos.get(n);
            double media = 0.0, stab = 0.0;
            if (!ks.isEmpty()) {
                int s = 0;
                for (int v : ks) s += v;
                media = (double) s / ks.size();
                double var = 0;
                for (int v : ks) var += (v - media) * (v - media);
                var = var / ks.size();
                stab = 1.0 / (1.0 + Math.sqrt(var));
            }
            double lift = atteso != 0 ? f / atteso : 0.0;
            double liftScore = Math.min(lift, 3.0) / 3.0;
            double score = 100 * (0.45 * pres + 0.30 * anello + 0.15 * stab + 0.10 * liftScore);
            CicloNumero c = new CicloNumero();
            c.numero = n; c.distanza = d; c.frequenza = f;
            c.presenza = (double) f / eventi.size() * 100; c.lift = lift;
            c.stabilita = stab * 100; c.posizioneMedia = media; c.score = score;
            r.risultati.add(c);
        }
        Collections.sort(r.risultati, (a, b) -> {
            int x = Double.compare(b.score, a.score);
            if (x != 0) return x;
            x = Integer.compare(b.frequenza, a.frequenza);
            if (x != 0) return x;
            x = Integer.compare(a.distanza, b.distanza);
            return x != 0 ? x : Integer.compare(a.numero, b.numero);
        });
        return r;
    }

    // ===================================================================== terni magici

    public static final class TernoMagico {
        public int[] numeri;
        public double score, lift, stabilita, scoreNum;
        public int frequenza, cicli;
    }

    private static double comb90_3() { return 117480.0; } // math.comb(90, 3)

    /**
     * genera_terni_magici_lotto() del main.py. Come per gli ambi/terni di previsione, la ricerca delle
     * co-presenze usa qui la chiave numericamente ordinata (nel main.py è ordinata per punteggio).
     */
    public static List<TernoMagico> generaTerniMagici(Modello m, int quantita, int candidatiN, int maxPresenzeNumero) {
        List<Integer> cand = new ArrayList<>();
        for (Punteggio p : classifica(m)) {
            if (p.numero != m.spia) cand.add(p.numero);
            if (cand.size() >= candidatiN) break;
        }
        List<TernoMagico> vuoto = new ArrayList<>();
        if (cand.size() < 3 || m.nEventi() == 0) return vuoto;
        int nEventi = m.nEventi();
        int colpi = m.successive.get(0).size();
        if (colpi <= 0) return vuoto;
        Set<Integer> insieme = new HashSet<>(cand);

        Map<Long, Integer> comboFreq = new HashMap<>(), comboCicli = new HashMap<>(), prima = new HashMap<>(), seconda = new HashMap<>();
        int meta = Math.max(1, nEventi / 2);
        int nPrima = Math.min(meta, nEventi), nSeconda = nEventi - nPrima;

        for (int idx = 0; idx < nEventi; idx++) {
            Set<Long> viste = new HashSet<>();
            for (int[] draw : m.successive.get(idx)) {
                TreeSet<Integer> pres = new TreeSet<>();
                for (int n : draw) if (insieme.contains(n)) pres.add(n);
                if (pres.size() < 3) continue;
                int[] arr = new int[pres.size()];
                int i = 0;
                for (int v : pres) arr[i++] = v;
                for (int[] c : combinazioni(arr, 3)) {
                    long k = chiave(c);
                    comboFreq.merge(k, 1, Integer::sum);
                    viste.add(k);
                }
            }
            for (long k : viste) {
                comboCicli.merge(k, 1, Integer::sum);
                (idx < meta ? prima : seconda).merge(k, 1, Integer::sum);
            }
        }

        double maxScoreNum = 0.0;
        boolean anyS = false;
        for (Punteggio p : m.punteggi.values()) { maxScoreNum = anyS ? Math.max(maxScoreNum, p.punteggio) : p.punteggio; anyS = true; }
        if (!anyS || maxScoreNum == 0.0) maxScoreNum = 1.0;
        int maxCicli = 1; boolean aC = false;
        for (int v : comboCicli.values()) { maxCicli = aC ? Math.max(maxCicli, v) : v; aC = true; }
        int maxFreq = 1; boolean aF = false;
        for (int v : comboFreq.values()) { maxFreq = aF ? Math.max(maxFreq, v) : v; aF = true; }
        double expected = (double) nEventi * colpi / comb90_3();

        int[] candArr = new int[cand.size()];
        for (int i = 0; i < candArr.length; i++) candArr[i] = cand.get(i);
        List<TernoMagico> risultati = new ArrayList<>();
        for (int[] comb : combinazioni(candArr, 3)) {
            int[] sorted = comb.clone();
            Arrays.sort(sorted);
            long k = chiave(sorted);
            int freq = comboFreq.getOrDefault(k, 0);
            int cic = comboCicli.getOrDefault(k, 0);
            double sn = 0;
            for (int n : comb) { Punteggio p = m.punteggi.get(n); sn += p == null ? 0.0 : p.punteggio; }
            double scoreNum = Math.min(1.0, (sn / 3.0) / maxScoreNum);
            double scoreCicli = (double) cic / maxCicli;
            double scoreCo = (double) freq / maxFreq;
            double rate1 = nPrima > 0 ? (double) prima.getOrDefault(k, 0) / nPrima : 0.0;
            double rate2 = nSeconda > 0 ? (double) seconda.getOrDefault(k, 0) / nSeconda : 0.0;
            double stab = (rate1 == 0.0 || rate2 == 0.0) ? 0.0 : Math.min(rate1, rate2) / Math.max(rate1, rate2);
            double lift = expected > 0 ? freq / expected : 0.0;
            double liftScore = Math.min(lift, 5.0) / 5.0;
            double score = 100.0 * (0.35 * scoreNum + 0.25 * scoreCicli + 0.20 * scoreCo + 0.10 * stab + 0.10 * liftScore);
            TernoMagico t = new TernoMagico();
            t.numeri = comb; t.score = score; t.frequenza = freq; t.cicli = cic;
            t.lift = lift; t.stabilita = stab; t.scoreNum = scoreNum * 100.0;
            risultati.add(t);
        }
        Collections.sort(risultati, (a, b) -> {
            int x = Double.compare(b.score, a.score);
            if (x != 0) return x;
            x = Integer.compare(b.frequenza, a.frequenza);
            if (x != 0) return x;
            x = Integer.compare(b.cicli, a.cicli);
            return x != 0 ? x : cmp(a.numeri, b.numeri);
        });

        int limite = Math.max(1, maxPresenzeNumero);
        Map<Integer, Integer> uso = new HashMap<>();
        List<TernoMagico> sel = new ArrayList<>();
        for (TernoMagico t : risultati) {
            if (sel.size() >= quantita) break;
            boolean ok = true;
            for (int n : t.numeri) if (uso.getOrDefault(n, 0) >= limite) { ok = false; break; }
            if (ok) {
                sel.add(t);
                for (int n : t.numeri) uso.merge(n, 1, Integer::sum);
            }
        }
        if (sel.size() < quantita) {
            Set<TernoMagico> gia = new HashSet<>(sel);
            for (TernoMagico t : risultati) {
                if (sel.size() >= quantita) break;
                if (!gia.contains(t)) { sel.add(t); gia.add(t); }
            }
        }
        return new ArrayList<>(sel.subList(0, Math.min(quantita, sel.size())));
    }

    // ===================================================================== resoconto unificato / convergenze

    public static final class Resoconto {
        public Modello modello;
        public List<Integer> topPost = new ArrayList<>();   // 10
        public List<Integer> topCiclo = new ArrayList<>();  // 10
        public List<Integer> topIncrocio = new ArrayList<>(); // 5
        public List<TernoMagico> terni = new ArrayList<>();
        public Ciclometria ciclo;
    }

    /** costruisci_resoconto_unificato_lotto() (parte di calcolo; i backtest a campione casuale non sono inclusi). */
    public static Resoconto resocontoUnificato(List<Extraction> archive, int spia, String ruota, int y0, int y1, int colpi) {
        validaParametri(spia, ruota, y0, y1, colpi);
        Modello m = costruisciModello(archive, spia, ruota, y0, y1, colpi);
        if (m.nEventi() == 0) throw new IllegalArgumentException("Nessun ciclo post-spia utile per la ruota e il periodo scelti.");
        Resoconto r = new Resoconto();
        r.modello = m;
        r.topPost = topPost(m, 10);
        r.ciclo = analizzaCiclometria(archive, spia, ruota, y0, y1, colpi);
        r.topCiclo = r.ciclo != null ? r.ciclo.top(10) : new ArrayList<Integer>();
        try { r.terni = generaTerniMagici(m, 10, 18, 2); } catch (RuntimeException e) { r.terni = new ArrayList<>(); }

        Set<Integer> pool = new TreeSet<>(r.topPost);
        pool.addAll(r.topCiclo);
        List<double[]> inc = new ArrayList<>(); // {finale, concordanza, numero}
        for (int n : pool) {
            Punteggio p = m.punteggi.get(n);
            double ps = p == null ? 0 : p.punteggio;
            double cs = 0;
            if (r.ciclo != null) for (CicloNumero c : r.ciclo.risultati) if (c.numero == n) { cs = c.score; break; }
            int conc = (r.topPost.contains(n) ? 1 : 0) + (r.topCiclo.contains(n) ? 1 : 0);
            inc.add(new double[]{conc * 25 + ps * .25 + cs * .25, conc, n});
        }
        Collections.sort(inc, (a, b) -> {
            int x = Double.compare(b[0], a[0]);
            if (x != 0) return x;
            x = Double.compare(b[1], a[1]);
            return x != 0 ? x : Double.compare(a[2], b[2]);
        });
        for (int i = 0; i < Math.min(5, inc.size()); i++) r.topIncrocio.add((int) inc.get(i)[2]);
        return r;
    }

    public static final class Convergenze {
        public Map<Integer, Integer> conteggio = new HashMap<>();
        public Map<Integer, List<String>> dettaglio = new HashMap<>();
        public List<Integer> topConvergenza = new ArrayList<>();
        public int totaleModuli;
    }

    /** costruisci_convergenze_lotto(): numeri presenti in 2 o più moduli (post-spia, ciclometria, terni magici). */
    public static Convergenze convergenze(List<Extraction> archive, int spia, String ruota, int y0, int y1, int colpi) {
        Resoconto base = resocontoUnificato(archive, spia, ruota, y0, y1, colpi);
        Set<Integer> numeriTerni = new TreeSet<>();
        for (TernoMagico t : base.terni) for (int n : t.numeri) numeriTerni.add(n);
        String[] nomi = {"POST-SPIA", "CICLOMETRIA", "TERNI MAGICI"};
        List<Set<Integer>> moduli = new ArrayList<>();
        moduli.add(new TreeSet<>(base.topPost));
        moduli.add(new TreeSet<>(base.topCiclo));
        moduli.add(numeriTerni);

        Convergenze c = new Convergenze();
        c.totaleModuli = moduli.size();
        for (int i = 0; i < moduli.size(); i++) {
            for (int n : moduli.get(i)) {
                if (n == spia) continue;
                c.conteggio.merge(n, 1, Integer::sum);
                c.dettaglio.computeIfAbsent(n, k -> new ArrayList<>()).add(nomi[i]);
            }
        }
        List<Map.Entry<Integer, Integer>> righe = new ArrayList<>(c.conteggio.entrySet());
        Collections.sort(righe, (a, b) -> {
            int x = Integer.compare(b.getValue(), a.getValue());
            return x != 0 ? x : Integer.compare(a.getKey(), b.getKey());
        });
        for (Map.Entry<Integer, Integer> e : righe) {
            if (e.getValue() >= 2 && c.topConvergenza.size() < 10) c.topConvergenza.add(e.getKey());
        }
        return c;
    }

    // ===================================================================== verifica combinazioni dopo la spia (v34)

    public static final class RigaVerifica {
        public int[] combo;
        public int cicli, frequenza;
    }

    public static final class Verifica {
        public int eventi;
        public List<RigaVerifica> top = new ArrayList<>();
        public double pSingolo, pOrizzonte;
    }

    /** v34_lotto_verifica_spia(): ruote selezionate per NOME; modalita = "STESSA RUOTA" oppure "RUOTE SELEZIONATE". */
    public static Verifica verificaSpia(List<Extraction> archive, int spia, List<String> ruote, int colpi,
                                        int dimensione, String modalita) {
        if (dimensione < 2 || dimensione > 5)
            throw new IllegalArgumentException("La combinazione deve essere ambo, terno, quaterna o cinquina.");
        if (ruote == null || ruote.isEmpty()) throw new IllegalArgumentException("Seleziona almeno una ruota.");
        List<Extraction> date = cronologico(archive);
        Verifica v = new Verifica();
        Map<Long, Integer> freq = new HashMap<>(), cicli = new HashMap<>();
        Map<Long, int[]> combos = new HashMap<>();
        for (int idx = 0; idx < date.size(); idx++) {
            Extraction giorno = date.get(idx);
            for (String ruotaSpia : ruote) {
                List<Integer> nums = giorno.getNumbersForRuota(ruotaSpia);
                if (nums == null || !nums.contains(spia)) continue;
                v.eventi++;
                List<String> target = "STESSA RUOTA".equals(modalita) ? Arrays.asList(ruotaSpia) : ruote;
                Set<Long> cicloCombos = new HashSet<>();
                for (int step = 1; step <= colpi; step++) {
                    int pos = idx + step;
                    if (pos >= date.size()) break;
                    Extraction futuro = date.get(pos);
                    for (String codice : target) {
                        List<Integer> fn = futuro.getNumbersForRuota(codice);
                        if (fn == null || fn.size() < dimensione) continue;
                        TreeSet<Integer> set = new TreeSet<>(fn);
                        int[] arr = new int[set.size()];
                        int i = 0;
                        for (int x : set) arr[i++] = x;
                        for (int[] c : combinazioni(arr, dimensione)) {
                            long k = chiave(c);
                            freq.merge(k, 1, Integer::sum);
                            combos.putIfAbsent(k, c);
                            cicloCombos.add(k);
                        }
                    }
                }
                for (long k : cicloCombos) cicli.merge(k, 1, Integer::sum);
            }
        }
        List<Long> keys = new ArrayList<>(freq.keySet());
        Collections.sort(keys, (a, b) -> {
            int x = Integer.compare(cicli.getOrDefault(b, 0), cicli.getOrDefault(a, 0));
            if (x != 0) return x;
            x = Integer.compare(freq.get(b), freq.get(a));
            return x != 0 ? x : cmp(combos.get(a), combos.get(b));
        });
        for (int i = 0; i < Math.min(20, keys.size()); i++) {
            RigaVerifica r = new RigaVerifica();
            r.combo = combos.get(keys.get(i));
            r.cicli = cicli.getOrDefault(keys.get(i), 0);
            r.frequenza = freq.get(keys.get(i));
            v.top.add(r);
        }
        double comb = 1;
        for (int i = 0; i < dimensione; i++) comb = comb * (90 - i) / (i + 1);
        v.pSingolo = 1.0 / Math.round(comb);
        v.pOrizzonte = 1 - Math.pow(1 - v.pSingolo, colpi);
        return v;
    }

    // ===================================================================== Apriori / market basket

    public static final class AprioriNumero {
        public int numero, count;
        public double support, confidence, lift, chi2, pVal, score;
        public String esito;
    }

    public static final class AprioriAmbo {
        public int a, b, count;
        public double confidence, lift, chi2, pVal;
    }

    public static final class Apriori {
        public String errore;
        public int nSpia;
        public List<AprioriNumero> singoli = new ArrayList<>();
        public List<AprioriAmbo> ambi = new ArrayList<>();
        public List<Integer> topNumeri = new ArrayList<>();
    }

    /** analizza_ia_market_basket_apriori() (gioco LOTTO). */
    public static Apriori apriori(List<Extraction> archive, int spia, String ruota, int y0, int y1, int colpi) {
        Apriori res = new Apriori();
        List<int[]> estr = new ArrayList<>();
        for (Extraction e : cronologico(archive)) {
            int y = e.getYear();
            if (y < y0 || y > y1) continue;
            List<Integer> nums = e.getNumbersForRuota(ruota);
            if (nums == null || nums.isEmpty()) continue;
            int[] d = new int[nums.size()];
            for (int i = 0; i < d.length; i++) d[i] = nums.get(i);
            estr.add(d);
        }
        double pSingle = 5.0 / 90.0, pPair = 10.0 / 4005.0;
        int nTot = estr.size();
        if (nTot < 5) { res.errore = "Archivio insufficiente nell'intervallo temporale."; return res; }
        List<Integer> indici = new ArrayList<>();
        for (int i = 0; i < nTot; i++) if (contiene(estr.get(i), spia) && i + colpi < nTot) indici.add(i);
        int nSpia = indici.size();
        if (nSpia < 1) {
            res.errore = String.format("Nessuna presenza del numero spia %02d nell'intervallo selezionato (%d-%d).", spia, y0, y1);
            return res;
        }
        res.nSpia = nSpia;
        double pBaseSingle = 1.0 - Math.pow(1.0 - pSingle, colpi);
        double pBasePair = 1.0 - Math.pow(1.0 - pPair, colpi);

        int[] count = new int[91];
        double[] weighted = new double[91];
        Map<Long, Integer> pairCounts = new LinkedHashMap<>();
        for (int idx : indici) {
            Set<Integer> visti = new HashSet<>();
            for (int dt = 1; dt <= colpi; dt++) {
                if (idx + dt < nTot) {
                    TreeSet<Integer> nums = new TreeSet<>();
                    for (int n : estr.get(idx + dt)) nums.add(n);
                    double decay = Math.pow(0.88, dt - 1);
                    for (int n : nums) {
                        if (n != spia && !visti.contains(n)) {
                            count[n]++;
                            weighted[n] += decay;
                            visti.add(n);
                        }
                    }
                    List<Integer> l = new ArrayList<>(nums);
                    l.remove(Integer.valueOf(spia));
                    for (int i1 = 0; i1 < l.size(); i1++) {
                        for (int i2 = i1 + 1; i2 < l.size(); i2++) {
                            pairCounts.merge((long) l.get(i1) * 100 + l.get(i2), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        List<AprioriNumero> singles = new ArrayList<>();
        for (int num = 1; num <= 90; num++) {
            if (num == spia) continue;
            int c = count[num];
            double conf = nSpia > 0 ? c / (double) nSpia : 0.0;
            double supp = nTot > 0 ? c / (double) nTot : 0.0;
            double lift = pBaseSingle > 0 ? conf / pBaseSingle : 0.0;
            double expected = nSpia * pBaseSingle;
            double chi2 = expected > 0 ? Math.pow(c - expected, 2) / expected : 0.0;
            double pVal = Math.exp(-chi2 / 2.0);
            double score = conf * lift * (1.0 + 0.25 * chi2) * (weighted[num] / Math.max(1, nSpia));
            AprioriNumero a = new AprioriNumero();
            a.numero = num; a.count = c; a.support = supp; a.confidence = conf; a.lift = lift;
            a.chi2 = chi2; a.pVal = pVal; a.score = score;
            if (pVal < 0.05 && lift > 1.15) a.esito = "CONFERMATO IA/STATISTICA";
            else if (lift > 1.10) a.esito = "PROBABILE PATTERN";
            else a.esito = "FLUTTUAZIONE CASUALE";
            singles.add(a);
        }
        Collections.sort(singles, (a, b) -> Double.compare(b.score, a.score)); // stabile, come Python
        for (int i = 0; i < Math.min(10, singles.size()); i++) {
            res.singoli.add(singles.get(i));
            res.topNumeri.add(singles.get(i).numero);
        }
        List<AprioriAmbo> pairs = new ArrayList<>();
        int soglia = Math.max(1, (int) (nSpia * 0.12));
        for (Map.Entry<Long, Integer> e : pairCounts.entrySet()) {
            int c = e.getValue();
            if (c >= soglia) {
                double conf = nSpia > 0 ? c / (double) nSpia : 0.0;
                double lift = pBasePair > 0 ? conf / pBasePair : 0.0;
                double expected = nSpia * pBasePair;
                double chi2 = expected > 0 ? Math.pow(c - expected, 2) / expected : 0.0;
                AprioriAmbo p = new AprioriAmbo();
                p.a = (int) (e.getKey() / 100); p.b = (int) (e.getKey() % 100);
                p.count = c; p.confidence = conf; p.lift = lift; p.chi2 = chi2; p.pVal = Math.exp(-chi2 / 2.0);
                pairs.add(p);
            }
        }
        Collections.sort(pairs, (x, y) -> {
            int c = Double.compare(y.confidence, x.confidence);
            return c != 0 ? c : Double.compare(y.lift, x.lift);
        });
        for (int i = 0; i < Math.min(8, pairs.size()); i++) res.ambi.add(pairs.get(i));
        return res;
    }

    // ===================================================================== costruttore Somma Ruote

    public static final class SommaRuote {
        public int eventi;
        public List<int[]> topSingoli = new ArrayList<>(); // {numero, conteggio}
        public List<int[]> topAmbi = new ArrayList<>();    // {a, b, conteggio}
        public List<Integer> topNumeri = new ArrayList<>();
    }

    /** calcola_costruttore_somma_ruote(): ruoteTarget = nome ruota oppure "TUTTE". */
    public static SommaRuote sommaRuote(List<Extraction> archive, String ruota1, int pos1, String ruota2, int pos2,
                                        int fisso, String ruotaTarget, int colpi, int y0, int y1) {
        List<Extraction> date = new ArrayList<>();
        for (Extraction e : cronologico(archive)) {
            int y = e.getYear();
            if (y >= y0 && y <= y1) date.add(e);
        }
        SommaRuote r = new SommaRuote();
        Map<Integer, Integer> fs = new LinkedHashMap<>();
        Map<Long, Integer> fa = new LinkedHashMap<>();
        for (int idx = 0; idx < date.size(); idx++) {
            List<Integer> n1 = date.get(idx).getNumbersForRuota(ruota1);
            List<Integer> n2 = date.get(idx).getNumbersForRuota(ruota2);
            if (n1 == null || n2 == null || n1.size() < pos1 || n2.size() < pos2) continue;
            r.eventi++;
            Set<Integer> visti = new HashSet<>();
            for (int dt = 1; dt <= colpi; dt++) {
                if (idx + dt >= date.size()) continue;
                Extraction target = date.get(idx + dt);
                List<Integer> tn = new ArrayList<>();
                if ("TUTTE".equals(ruotaTarget)) {
                    Set<Integer> all = new java.util.LinkedHashSet<Integer>();
                    for (String w : Extraction.ALL_RUOTE) {
                        List<Integer> x = target.getNumbersForRuota(w);
                        if (x != null) all.addAll(x);
                    }
                    tn.addAll(all);
                } else {
                    List<Integer> x = target.getNumbersForRuota(ruotaTarget);
                    if (x != null) tn.addAll(x);
                }
                for (int num : tn) {
                    if (!visti.contains(num)) { fs.merge(num, 1, Integer::sum); visti.add(num); }
                }
                List<Integer> st = new ArrayList<>(tn);
                Collections.sort(st);
                for (int i1 = 0; i1 < st.size(); i1++)
                    for (int i2 = i1 + 1; i2 < st.size(); i2++)
                        fa.merge((long) st.get(i1) * 100 + st.get(i2), 1, Integer::sum);
            }
        }
        List<Map.Entry<Integer, Integer>> s = new ArrayList<>(fs.entrySet());
        if ("TUTTE".equals(ruotaTarget)) {
            // main.py itera su un 'set' Python (ordine arbitrario) per i pareggi: qui i pareggi vanno per numero crescente
            Collections.sort(s, (a, b) -> {
                int x = Integer.compare(b.getValue(), a.getValue());
                return x != 0 ? x : Integer.compare(a.getKey(), b.getKey());
            });
        } else {
            Collections.sort(s, (a, b) -> Integer.compare(b.getValue(), a.getValue())); // stabile, come Python
        }
        for (int i = 0; i < Math.min(10, s.size()); i++) {
            r.topSingoli.add(new int[]{s.get(i).getKey(), s.get(i).getValue()});
            r.topNumeri.add(s.get(i).getKey());
        }
        List<Map.Entry<Long, Integer>> a = new ArrayList<>(fa.entrySet());
        Collections.sort(a, (x, y) -> Integer.compare(y.getValue(), x.getValue()));
        for (int i = 0; i < Math.min(8, a.size()); i++) {
            r.topAmbi.add(new int[]{(int) (a.get(i).getKey() / 100), (int) (a.get(i).getKey() % 100), a.get(i).getValue()});
        }
        return r;
    }

    // ===================================================================== costruttore Somma + Fisso (v38)

    public static final class SommaFisso {
        public int bestFisso, hits, prove, somma, numeroBase, eventiBase;
        public double percentuale;
        public int[] ultimaEstrazione;
        public List<Integer> compagni = new ArrayList<>();
        public List<Integer> previsione = new ArrayList<>();
        public Map<Integer, Integer> coUscite = new HashMap<>();
    }

    /** Calcolo del costruttore "Somma + Fisso" (_v38_costruttore_somma_fisso). */
    public static SommaFisso sommaFisso(List<Extraction> archive, String ruota, int y0, int y1) {
        if (y0 > y1) throw new IllegalArgumentException("L'anno iniziale non puo' superare quello finale.");
        if (ruota == null || !Extraction.ALL_RUOTE.contains(ruota)) throw new IllegalArgumentException("Ruota Lotto non riconosciuta.");
        List<int[]> estr = estrazioniRuota(archive, ruota, y0, y1);
        if (estr.size() < 2) throw new IllegalArgumentException("Servono almeno due estrazioni valide nel periodo selezionato.");

        int bestF = 0, bestHits = -1, bestProve = 0;
        double bestPct = -1;
        for (int fisso = 1; fisso <= 90; fisso++) {
            int hits = 0, prove = 0;
            for (int i = 0; i < estr.size() - 1; i++) {
                int somma = 0;
                for (int v : estr.get(i)) somma += v;
                int cand = ((somma + fisso - 1) % 90) + 1;
                prove++;
                if (contiene(estr.get(i + 1), cand)) hits++;
            }
            double pct = prove > 0 ? (double) hits / prove * 100.0 : 0.0;
            // max con chiave (percentuale, hits, -fisso): a parità vince il fisso più piccolo
            boolean meglio = pct > bestPct || (pct == bestPct && hits > bestHits);
            if (meglio) { bestF = fisso; bestPct = pct; bestHits = hits; bestProve = prove; }
        }
        SommaFisso r = new SommaFisso();
        r.bestFisso = bestF; r.hits = bestHits; r.prove = bestProve; r.percentuale = bestPct;
        int[] ultima = estr.get(estr.size() - 1);
        r.ultimaEstrazione = ultima;
        int somma = 0;
        for (int v : ultima) somma += v;
        r.somma = somma;
        r.numeroBase = ((somma + bestF - 1) % 90) + 1;

        int[] co = new int[91];
        for (int[] d : estr) {
            if (contiene(d, r.numeroBase)) {
                r.eventiBase++;
                for (int n : d) if (n != r.numeroBase) co[n]++;
            }
        }
        List<Integer> cand = new ArrayList<>();
        for (int n = 1; n <= 90; n++) if (co[n] > 0) cand.add(n);
        Collections.sort(cand, (a, b) -> {
            int x = Integer.compare(co[b], co[a]);
            return x != 0 ? x : Integer.compare(a, b);
        });
        for (int i = 0; i < Math.min(4, cand.size()); i++) r.compagni.add(cand.get(i));
        if (r.compagni.size() < 4) {
            for (int n = 1; n <= 90 && r.compagni.size() < 4; n++) {
                if (n != r.numeroBase && !r.compagni.contains(n)) r.compagni.add(n);
            }
        }
        for (int n : r.compagni) r.coUscite.put(n, co[n]);
        r.previsione.add(r.numeroBase);
        r.previsione.addAll(r.compagni);
        return r;
    }
}
