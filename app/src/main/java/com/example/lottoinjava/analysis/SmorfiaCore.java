package com.example.lottoinjava.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
  * Smorfia napoletana (V45.1 del main.py): prontuario offline con alias. Non applica statistiche e non modifica probabilità.
  */
public final class SmorfiaCore {

    private SmorfiaCore() { }

    public static final Map<Integer, String> PRONTUARIO = new LinkedHashMap<>();
    private static final Map<Integer, String[]> ALIAS = new LinkedHashMap<>();

    static {
        PRONTUARIO.put(1, "Italia");
        PRONTUARIO.put(2, "bambina");
        PRONTUARIO.put(3, "gatta");
        PRONTUARIO.put(4, "maiale");
        PRONTUARIO.put(5, "mano");
        PRONTUARIO.put(6, "quella che guarda a terra");
        PRONTUARIO.put(7, "vaso");
        PRONTUARIO.put(8, "Madonna");
        PRONTUARIO.put(9, "figliolanza");
        PRONTUARIO.put(10, "fagioli");
        PRONTUARIO.put(11, "topolini");
        PRONTUARIO.put(12, "soldato");
        PRONTUARIO.put(13, "Sant'Antonio");
        PRONTUARIO.put(14, "ubriaco");
        PRONTUARIO.put(15, "ragazzo");
        PRONTUARIO.put(16, "culo");
        PRONTUARIO.put(17, "disgrazia");
        PRONTUARIO.put(18, "sangue");
        PRONTUARIO.put(19, "risata");
        PRONTUARIO.put(20, "festa");
        PRONTUARIO.put(21, "donna nuda");
        PRONTUARIO.put(22, "pazzo");
        PRONTUARIO.put(23, "scemo");
        PRONTUARIO.put(24, "guardie");
        PRONTUARIO.put(25, "Natale");
        PRONTUARIO.put(26, "Sant'Anna");
        PRONTUARIO.put(27, "pitale");
        PRONTUARIO.put(28, "seni");
        PRONTUARIO.put(29, "padre dei bambini");
        PRONTUARIO.put(30, "palle del tenente");
        PRONTUARIO.put(31, "padrone di casa");
        PRONTUARIO.put(32, "capitone");
        PRONTUARIO.put(33, "anni di Cristo");
        PRONTUARIO.put(34, "testa");
        PRONTUARIO.put(35, "uccellino");
        PRONTUARIO.put(36, "nacchere");
        PRONTUARIO.put(37, "monaco");
        PRONTUARIO.put(38, "mazzate");
        PRONTUARIO.put(39, "cappio al collo");
        PRONTUARIO.put(40, "paposcia");
        PRONTUARIO.put(41, "coltello");
        PRONTUARIO.put(42, "caffe");
        PRONTUARIO.put(43, "donna al balcone");
        PRONTUARIO.put(44, "cancellate");
        PRONTUARIO.put(45, "vino buono");
        PRONTUARIO.put(46, "soldi");
        PRONTUARIO.put(47, "morto");
        PRONTUARIO.put(48, "morto che parla");
        PRONTUARIO.put(49, "carne");
        PRONTUARIO.put(50, "pane");
        PRONTUARIO.put(51, "giardino");
        PRONTUARIO.put(52, "mamma");
        PRONTUARIO.put(53, "vecchio");
        PRONTUARIO.put(54, "cappello");
        PRONTUARIO.put(55, "musica");
        PRONTUARIO.put(56, "caduta");
        PRONTUARIO.put(57, "gobbo");
        PRONTUARIO.put(58, "fagotto");
        PRONTUARIO.put(59, "peli");
        PRONTUARIO.put(60, "lamento");
        PRONTUARIO.put(61, "cacciatore");
        PRONTUARIO.put(62, "morto ammazzato");
        PRONTUARIO.put(63, "sposa");
        PRONTUARIO.put(64, "frac");
        PRONTUARIO.put(65, "pianto");
        PRONTUARIO.put(66, "due zitelle");
        PRONTUARIO.put(67, "casa vecchia");
        PRONTUARIO.put(68, "zuppa cotta");
        PRONTUARIO.put(69, "sottosopra");
        PRONTUARIO.put(70, "palazzo");
        PRONTUARIO.put(71, "uomo indegno");
        PRONTUARIO.put(72, "stupore");
        PRONTUARIO.put(73, "ospedale");
        PRONTUARIO.put(74, "grotta");
        PRONTUARIO.put(75, "Pulcinella");
        PRONTUARIO.put(76, "fontana");
        PRONTUARIO.put(77, "diavoli");
        PRONTUARIO.put(78, "bella figliola");
        PRONTUARIO.put(79, "ladro");
        PRONTUARIO.put(80, "bocca");
        PRONTUARIO.put(81, "fiori");
        PRONTUARIO.put(82, "tavola imbandita");
        PRONTUARIO.put(83, "maltempo");
        PRONTUARIO.put(84, "chiesa");
        PRONTUARIO.put(85, "anime del purgatorio");
        PRONTUARIO.put(86, "bottega");
        PRONTUARIO.put(87, "pidocchi");
        PRONTUARIO.put(88, "caciocavalli");
        PRONTUARIO.put(89, "vecchia");
        PRONTUARIO.put(90, "paura");
        ALIAS.put(1, new String[]{"italia", "italiano", "italiana"});
        ALIAS.put(2, new String[]{"bambina", "bimba", "bambino"});
        ALIAS.put(3, new String[]{"gatta", "gatto"});
        ALIAS.put(6, new String[]{"cane", "cani", "cagnolino", "cagnolina"});
        ALIAS.put(4, new String[]{"maiale", "porco", "suino"});
        ALIAS.put(5, new String[]{"mano", "mani"});
        ALIAS.put(7, new String[]{"vaso", "vasi"});
        ALIAS.put(8, new String[]{"madonna"});
        ALIAS.put(9, new String[]{"figli", "figliolanza"});
        ALIAS.put(10, new String[]{"fagiolo", "fagioli"});
        ALIAS.put(11, new String[]{"topo", "topolini", "topi"});
        ALIAS.put(12, new String[]{"soldato", "soldati"});
        ALIAS.put(13, new String[]{"sant antonio", "sant'antonio", "antonio"});
        ALIAS.put(14, new String[]{"ubriaco", "ubriaca", "ubriacatura"});
        ALIAS.put(15, new String[]{"ragazzo", "ragazza"});
        ALIAS.put(16, new String[]{"culo", "sedere"});
        ALIAS.put(17, new String[]{"disgrazia", "sfortuna"});
        ALIAS.put(18, new String[]{"sangue"});
        ALIAS.put(19, new String[]{"risata", "ridere", "riso"});
        ALIAS.put(20, new String[]{"festa", "festeggiamento"});
        ALIAS.put(21, new String[]{"donna nuda", "nuda"});
        ALIAS.put(22, new String[]{"pazzo", "pazza", "follia"});
        ALIAS.put(23, new String[]{"scemo", "sciocco"});
        ALIAS.put(24, new String[]{"guardia", "guardie", "polizia"});
        ALIAS.put(25, new String[]{"natale"});
        ALIAS.put(26, new String[]{"sant anna", "sant'anna", "anna"});
        ALIAS.put(27, new String[]{"pitale", "vaso da notte"});
        ALIAS.put(28, new String[]{"seno", "seni"});
        ALIAS.put(29, new String[]{"padre dei bambini"});
        ALIAS.put(30, new String[]{"palle del tenente"});
        ALIAS.put(31, new String[]{"padrone di casa", "proprietario"});
        ALIAS.put(32, new String[]{"capitone", "anguilla"});
        ALIAS.put(33, new String[]{"anni di cristo", "cristo", "eta di cristo"});
        ALIAS.put(34, new String[]{"testa", "capo"});
        ALIAS.put(35, new String[]{"uccellino", "uccello"});
        ALIAS.put(36, new String[]{"nacchere"});
        ALIAS.put(37, new String[]{"monaco", "frate"});
        ALIAS.put(38, new String[]{"mazzate", "mazzata", "botte"});
        ALIAS.put(39, new String[]{"cappio", "cappio al collo"});
        ALIAS.put(40, new String[]{"paposcia"});
        ALIAS.put(41, new String[]{"coltello", "coltelli"});
        ALIAS.put(42, new String[]{"caffe"});
        ALIAS.put(43, new String[]{"donna al balcone", "balcone"});
        ALIAS.put(44, new String[]{"cancellata", "cancellate"});
        ALIAS.put(45, new String[]{"vino", "vino buono"});
        ALIAS.put(46, new String[]{"soldi", "denaro", "monete"});
        ALIAS.put(47, new String[]{"morto", "morta", "cadavere", "defunto"});
        ALIAS.put(48, new String[]{"morto che parla"});
        ALIAS.put(49, new String[]{"carne"});
        ALIAS.put(50, new String[]{"pane"});
        ALIAS.put(51, new String[]{"giardino"});
        ALIAS.put(52, new String[]{"mamma", "madre"});
        ALIAS.put(53, new String[]{"vecchio", "anziano"});
        ALIAS.put(54, new String[]{"cappello"});
        ALIAS.put(55, new String[]{"musica", "canzone"});
        ALIAS.put(56, new String[]{"caduta", "cadere"});
        ALIAS.put(57, new String[]{"gobbo", "gobba"});
        ALIAS.put(58, new String[]{"fagotto"});
        ALIAS.put(59, new String[]{"peli", "pelo"});
        ALIAS.put(60, new String[]{"lamento", "lamentarsi"});
        ALIAS.put(61, new String[]{"cacciatore", "caccia"});
        ALIAS.put(62, new String[]{"morto ammazzato", "assassinato", "ucciso"});
        ALIAS.put(63, new String[]{"sposa", "sposo", "matrimonio"});
        ALIAS.put(64, new String[]{"frac"});
        ALIAS.put(65, new String[]{"pianto", "piangere", "lacrime"});
        ALIAS.put(66, new String[]{"due zitelle", "zitella"});
        ALIAS.put(67, new String[]{"casa vecchia", "vecchia casa"});
        ALIAS.put(68, new String[]{"zuppa cotta"});
        ALIAS.put(69, new String[]{"sottosopra", "capovolto", "rovesciato"});
        ALIAS.put(70, new String[]{"palazzo", "edificio"});
        ALIAS.put(71, new String[]{"uomo indegno"});
        ALIAS.put(72, new String[]{"stupore", "sorpresa", "meraviglia"});
        ALIAS.put(73, new String[]{"ospedale", "infermiere", "dottore"});
        ALIAS.put(74, new String[]{"grotta", "caverna"});
        ALIAS.put(75, new String[]{"pulcinella"});
        ALIAS.put(76, new String[]{"fontana"});
        ALIAS.put(77, new String[]{"diavoli", "diavolo"});
        ALIAS.put(78, new String[]{"bella figliola", "bella ragazza"});
        ALIAS.put(79, new String[]{"ladro", "ladra", "furto"});
        ALIAS.put(80, new String[]{"bocca", "labbra"});
        ALIAS.put(81, new String[]{"fiori", "fiore"});
        ALIAS.put(82, new String[]{"tavola imbandita", "tavola", "banchetto"});
        ALIAS.put(83, new String[]{"maltempo", "tempesta", "temporale"});
        ALIAS.put(84, new String[]{"chiesa"});
        ALIAS.put(85, new String[]{"anime del purgatorio", "purgatorio", "anime"});
        ALIAS.put(86, new String[]{"bottega", "negozio"});
        ALIAS.put(87, new String[]{"pidocchi", "pidocchio"});
        ALIAS.put(88, new String[]{"caciocavalli", "caciocavallo", "formaggio"});
        ALIAS.put(89, new String[]{"vecchia", "anziana"});
        ALIAS.put(90, new String[]{"paura", "spavento", "terrore"});
    }

    public static final class Risultato {
        public int numero;
        public String significato;
        public List<String> match = new ArrayList<>();
        public int score;
    }

    static String normalizza(String testo) {
        String t = testo == null ? "" : testo.toLowerCase(Locale.ROOT);
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD);
        t = t.replaceAll("\\p{Mn}+", "");
        t = t.replaceAll("[^a-z0-9' ]+", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static final class Candidato {
        int parole, lunghezza, numero;
        String alias;
    }

    private static List<Candidato> candidati;

    private static synchronized List<Candidato> candidati() {
        if (candidati == null) {
            List<Candidato> l = new ArrayList<>();
            for (Map.Entry<Integer, String[]> e : ALIAS.entrySet()) {
                for (String alias : e.getValue()) {
                    String a = normalizza(alias);
                    if (a.isEmpty()) continue;
                    Candidato c = new Candidato();
                    c.parole = a.split(" ").length;
                    c.lunghezza = a.length();
                    c.numero = e.getKey();
                    c.alias = a;
                    l.add(c);
                }
            }
            // come Python: sort(reverse=True) sulla tupla (parole, lunghezza, numero, alias)
            Collections.sort(l, (x, y) -> {
                int c = Integer.compare(y.parole, x.parole);
                if (c != 0) return c;
                c = Integer.compare(y.lunghezza, x.lunghezza);
                if (c != 0) return c;
                c = Integer.compare(y.numero, x.numero);
                return c != 0 ? c : y.alias.compareTo(x.alias);
            });
            candidati = l;
        }
        return candidati;
    }

    /** interpreta_sogno_smorfia() V45.1 del main.py: numeri riconosciuti, con parola trovata e punteggio indicativo. */
    public static List<Risultato> interpreta(String testo, int massimo) {
        String t = normalizza(testo);
        List<Risultato> out = new ArrayList<>();
        if (t.isEmpty()) return out;
        Map<Integer, List<String>> trovati = new LinkedHashMap<>();
        for (Candidato c : candidati()) {
            Matcher m = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(c.alias) + "(?![a-z0-9])").matcher(t);
            if (m.find()) {
                List<String> l = trovati.get(c.numero);
                if (l == null) { l = new ArrayList<>(); trovati.put(c.numero, l); }
                l.add(c.alias);
            }
        }
        for (Map.Entry<Integer, List<String>> e : trovati.entrySet()) {
            Risultato r = new Risultato();
            r.numero = e.getKey();
            r.significato = PRONTUARIO.getOrDefault(e.getKey(), "");
            r.match = new ArrayList<>(e.getValue().subList(0, Math.min(3, e.getValue().size())));
            r.score = Math.min(100, 45 + 15 * e.getValue().size());
            out.add(r);
        }
        Collections.sort(out, (a, b) -> {
            int c = Integer.compare(b.score, a.score);
            return c != 0 ? c : Integer.compare(a.numero, b.numero);
        });
        return new ArrayList<>(out.subList(0, Math.min(massimo, out.size())));
    }

    /** Rapporto come nella schermata "Spia sogno" del main.py. */
    public static String rapporto(String sogno, List<Risultato> risultati) {
        StringBuilder sb = new StringBuilder("SMORFIA SPIA SOGNO\n");
        String eq = "==========================================";
        String tr = "------------------------------------------";
        sb.append(eq).append("\nSogno: ").append(sogno).append("\n\n");
        if (risultati.isEmpty()) {
            sb.append("Nessun simbolo del prontuario riconosciuto.\n");
            sb.append("Prova con parole piu' concrete: cane, mare, morto, matrimonio, soldi, casa, paura, ecc.");
            return sb.toString();
        }
        sb.append("NUMERI SMORFIA RICONOSCIUTI\n").append(tr).append("\n");
        int i = 1;
        for (Risultato r : risultati) {
            sb.append(String.format("%02d. %02d  -  %s  -  match: %s\n", i++, r.numero, r.significato, String.join(", ", r.match)));
        }
        StringBuilder nums = new StringBuilder();
        for (int k = 0; k < Math.min(10, risultati.size()); k++) nums.append(k > 0 ? " - " : "").append(String.format("%02d", risultati.get(k).numero));
        sb.append("\nMAGIA NUMERI DEL SOGNO\n").append(tr).append("\n").append(nums).append("\n");
        if (risultati.size() >= 3) {
            sb.append("\n<> TERNO DA TRADIZIONE\n").append(tr).append("\n");
            sb.append(String.format("%02d - %02d - %02d", risultati.get(0).numero, risultati.get(1).numero, risultati.get(2).numero)).append("\n");
        }
        sb.append("\nSTATISTICA: NON APPLICATA\nQuesto modulo non modifica Magic Score, frequenze o probabilita'.");
        return sb.toString();
    }
}
