package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prontuario delle vincite del Lotto su ruota singola, per una puntata di 1 euro (importi LORDI).
 *
 * Regole usate (tabella pubblicata per 1 a 10 numeri giocati):
 *  - ambata (estratto semplice) 11,23 con 1 numero; con più numeri la quota scende (5,62 con 2, 3,74 con 3, ...);
 *  - estratto determinato 55; ambo 250; terno 4.500; quaterna 120.000; cinquina 6.000.000,
 *    divisi per il numero di combinazioni che i numeri giocati formano (es. 5 numeri = 10 ambi, 10 terni, 5 quaterne, 1 cinquina);
 *  - su più ruote la puntata si divide tra le ruote, quindi la vincita si divide per il numero di ruote (10 con "tutte").
 *
 * Le quote sono stime per orientarsi: l'importo ufficiale è quello pagato dalla ricevitoria.
 */
public final class ProntuarioVincite {

    private ProntuarioVincite() { }

    public enum Sorte {
        AMBATA("Ambata (estratto)", 1),
        ESTRATTO_DET("Estratto determinato", 1),
        AMBO("Ambo", 2),
        TERNO("Terno", 3),
        QUATERNA("Quaterna", 4),
        CINQUINA("Cinquina", 5);

        public final String etichetta;
        public final int numeri;
        Sorte(String etichetta, int numeri) { this.etichetta = etichetta; this.numeri = numeri; }
    }

    public static final int MAX_NUMERI = 10;

    /** Quote ambata per 1..10 numeri giocati, come pubblicate (non seguono una formula unica). */
    private static final String[] AMBATA = {"11.23", "5.62", "3.74", "2.80", "2.24", "1.87", "1.60", "1.40", "1.24", "1.12"};

    private static BigDecimal base(Sorte s) {
        switch (s) {
            case ESTRATTO_DET: return new BigDecimal("55");
            case AMBO: return new BigDecimal("250");
            case TERNO: return new BigDecimal("4500");
            case QUATERNA: return new BigDecimal("120000");
            case CINQUINA: return new BigDecimal("6000000");
            default: return new BigDecimal("11.23");
        }
    }

    /** Combinazioni C(n, k). */
    public static int combinazioni(int n, int k) {
        if (k < 0 || n < k) return 0;
        long r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return (int) r;
    }

    /**
     * Quota lorda per ogni 1 euro giocato su ruota singola, giocando {@code numeriGiocati} numeri (1..10).
     * Importo che paga UNA combinazione vincente. Null se la sorte non è giocabile con quei numeri.
     */
    public static BigDecimal quota(int numeriGiocati, Sorte s) {
        if (numeriGiocati < 1 || numeriGiocati > MAX_NUMERI || numeriGiocati < s.numeri) return null;
        if (s == Sorte.AMBATA) return new BigDecimal(AMBATA[numeriGiocati - 1]);
        int comb = combinazioni(numeriGiocati, s.numeri);
        return base(s).divide(BigDecimal.valueOf(comb), 2, RoundingMode.DOWN);
    }

    /** Imposta trattenuta: aliquota% sulla parte di vincita eccedente la franchigia. */
    public static BigDecimal imposta(BigDecimal lordo, BigDecimal aliquotaPercento, BigDecimal franchigia) {
        BigDecimal imponibile = lordo.subtract(franchigia == null ? BigDecimal.ZERO : franchigia);
        if (imponibile.signum() <= 0 || aliquotaPercento == null) return BigDecimal.ZERO;
        return imponibile.multiply(aliquotaPercento).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Vincita lorda di UNA combinazione vincente, con puntata e ruote giocate. */
    public static BigDecimal vincita(int numeriGiocati, Sorte s, BigDecimal puntata, int ruote) {
        BigDecimal q = quota(numeriGiocati, s);
        if (q == null || ruote < 1) return null;
        return q.multiply(puntata).divide(BigDecimal.valueOf(ruote), 2, RoundingMode.DOWN);
    }

    // ------------------------------------------------------------------ verifica di una giocata

    public static final class Esito {
        public final BigDecimal lordo;
        public final List<String> dettaglio;
        Esito(BigDecimal lordo, List<String> dettaglio) { this.lordo = lordo; this.dettaglio = dettaglio; }
    }

    /**
     * Controlla una giocata su una estrazione dell'archivio.
     *
     * @param ruote   ruote giocate (nomi come in Extraction.ALL_RUOTE); la puntata è divisa tra queste
     * @param numeri  da 1 a 10 numeri distinti tra 1 e 90
     * @param puntate importo totale per sorte (ambata, ambo, terno, quaterna, cinquina); l'estratto determinato
     *                richiede la posizione di uscita e non è calcolato qui
     */
    public static Esito verifica(Extraction e, List<String> ruote, List<Integer> numeri, Map<Sorte, BigDecimal> puntate) {
        Set<Integer> giocati = new LinkedHashSet<>(numeri);
        int n = giocati.size();
        List<String> righe = new ArrayList<>();
        BigDecimal totale = BigDecimal.ZERO;
        if (n < 1 || n > MAX_NUMERI || ruote.isEmpty()) {
            righe.add("Servono da 1 a 10 numeri e almeno una ruota.");
            return new Esito(totale, righe);
        }
        for (String ruota : ruote) {
            List<Integer> usciti = e.getNumbersForRuota(ruota);
            List<Integer> presi = new ArrayList<>();
            if (usciti != null) for (int x : usciti) if (giocati.contains(x)) presi.add(x);
            int h = presi.size();
            StringBuilder riga = new StringBuilder(ruota).append(": ");
            if (h == 0) { righe.add(riga.append("nessun numero uscito").toString()); continue; }
            riga.append("usciti ").append(presi);
            for (Sorte s : Sorte.values()) {
                if (s == Sorte.ESTRATTO_DET) continue;
                BigDecimal puntata = puntate.get(s);
                if (puntata == null || puntata.signum() <= 0) continue;
                int comb = combinazioni(h, s.numeri);
                if (comb == 0) continue;
                BigDecimal q = quota(n, s);
                if (q == null) continue;
                // arrotondamento (per difetto) solo alla fine, per non perdere centesimi sulle combinazioni multiple
                BigDecimal parziale = q.multiply(puntata).multiply(BigDecimal.valueOf(comb))
                        .divide(BigDecimal.valueOf(ruote.size()), 2, RoundingMode.DOWN);
                totale = totale.add(parziale);
                riga.append(" → ").append(s.etichetta).append(" ×").append(comb).append(" = ").append(euro(parziale));
            }
            righe.add(riga.toString());
        }
        return new Esito(totale, righe);
    }

    public static String euro(BigDecimal v) {
        return String.format(Locale.ITALY, "%,.2f €", v);
    }

    /** Tabella completa: righe = numeri giocati 1..10, colonne = sorti (null dove non giocabile). */
    public static Map<Integer, Map<Sorte, BigDecimal>> tabella() {
        Map<Integer, Map<Sorte, BigDecimal>> t = new java.util.LinkedHashMap<>();
        for (int n = 1; n <= MAX_NUMERI; n++) {
            Map<Sorte, BigDecimal> riga = new EnumMap<>(Sorte.class);
            for (Sorte s : Sorte.values()) riga.put(s, quota(n, s));
            t.put(n, riga);
        }
        return t;
    }
}
