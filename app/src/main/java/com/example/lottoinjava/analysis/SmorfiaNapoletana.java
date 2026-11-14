package com.example.lottoinjava.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SmorfiaNapoletana {

    public static final Map<Integer, String> SMORFIA_MAP = new LinkedHashMap<>();
    public static final Map<String, Integer> ALIAS_MAP = new HashMap<>();

    static {
        SMORFIA_MAP.put(1, "Italia / Il Sole");
        SMORFIA_MAP.put(2, "La Bambina / L'Acqua");
        SMORFIA_MAP.put(3, "La Gatta");
        SMORFIA_MAP.put(4, "Il Maiale");
        SMORFIA_MAP.put(5, "La Mano / La Mamma");
        SMORFIA_MAP.put(6, "Il Padre / Il Cane");
        SMORFIA_MAP.put(7, "Il Vaso / Le Donne");
        SMORFIA_MAP.put(8, "La Madonna / Il Fuoco");
        SMORFIA_MAP.put(9, "La Figliolanza / La Festa");
        SMORFIA_MAP.put(10, "I Fagioli");
        SMORFIA_MAP.put(11, "I Topi / I Topolini");
        SMORFIA_MAP.put(12, "I Soldi / Il Soldato");
        SMORFIA_MAP.put(13, "Sant'Antonio");
        SMORFIA_MAP.put(14, "L'Ubriaco");
        SMORFIA_MAP.put(15, "Il Ragazzo");
        SMORFIA_MAP.put(16, "La Fortuna / Il Culo");
        SMORFIA_MAP.put(17, "La Sfortuna / Il Sogno");
        SMORFIA_MAP.put(18, "Il Sangue / Il Diavolo");
        SMORFIA_MAP.put(19, "La Risata");
        SMORFIA_MAP.put(20, "La Festa");
        SMORFIA_MAP.put(21, "La Donna Nuda");
        SMORFIA_MAP.put(22, "Il Pazzo");
        SMORFIA_MAP.put(23, "Lo Scemo");
        SMORFIA_MAP.put(24, "Le Guardie / Il Cavallo");
        SMORFIA_MAP.put(25, "Il Natale");
        SMORFIA_MAP.put(26, "Sant'Anna");
        SMORFIA_MAP.put(27, "Il Pitale");
        SMORFIA_MAP.put(28, "Il Seno");
        SMORFIA_MAP.put(29, "Il Padre dei bambini");
        SMORFIA_MAP.put(30, "Le Palle");
        SMORFIA_MAP.put(31, "Il Padrone di casa");
        SMORFIA_MAP.put(32, "Il Capitone / Il Serpente");
        SMORFIA_MAP.put(33, "Gli Anni di Cristo");
        SMORFIA_MAP.put(34, "La Testa");
        SMORFIA_MAP.put(35, "L'Uccellino");
        SMORFIA_MAP.put(36, "Le Nacchere");
        SMORFIA_MAP.put(37, "Il Monaco");
        SMORFIA_MAP.put(38, "Le Mazzate");
        SMORFIA_MAP.put(39, "Il Cappio / La Corda");
        SMORFIA_MAP.put(40, "La Paposcia");
        SMORFIA_MAP.put(41, "Il Coltello");
        SMORFIA_MAP.put(42, "Il Caffè");
        SMORFIA_MAP.put(43, "La Donna al balcone");
        SMORFIA_MAP.put(44, "La Cancellata / Il Carcere");
        SMORFIA_MAP.put(45, "Il Vino Buono");
        SMORFIA_MAP.put(46, "I Denari");
        SMORFIA_MAP.put(47, "Il Morto");
        SMORFIA_MAP.put(48, "Il Morto che parla");
        SMORFIA_MAP.put(49, "La Carne");
        SMORFIA_MAP.put(50, "Il Pane");
        SMORFIA_MAP.put(51, "Il Giardino");
        SMORFIA_MAP.put(52, "La Mamma");
        SMORFIA_MAP.put(53, "Il Vecchio");
        SMORFIA_MAP.put(54, "Il Cappello");
        SMORFIA_MAP.put(55, "La Musica");
        SMORFIA_MAP.put(56, "La Caduta");
        SMORFIA_MAP.put(57, "Il Gobbo / La Scuola");
        SMORFIA_MAP.put(58, "Il Fagotto");
        SMORFIA_MAP.put(59, "I Peli");
        SMORFIA_MAP.put(60, "Il Lamento");
        SMORFIA_MAP.put(61, "Il Cacciatore");
        SMORFIA_MAP.put(62, "La Sposa / Il Matrimonio");
        SMORFIA_MAP.put(63, "La Sposa");
        SMORFIA_MAP.put(64, "Il Frac");
        SMORFIA_MAP.put(65, "Il Pianto / Le Lacrime");
        SMORFIA_MAP.put(66, "Le Due Zitelle");
        SMORFIA_MAP.put(67, "La Casa Vecchia");
        SMORFIA_MAP.put(68, "La Zuppa Cotta");
        SMORFIA_MAP.put(69, "Il Sottosopra");
        SMORFIA_MAP.put(70, "Il Palazzo / La Casa");
        SMORFIA_MAP.put(71, "L'Uomo Indegno");
        SMORFIA_MAP.put(72, "La Fortuna Grande");
        SMORFIA_MAP.put(73, "L'Ospedale");
        SMORFIA_MAP.put(74, "La Grotta");
        SMORFIA_MAP.put(75, "Pulcinella");
        SMORFIA_MAP.put(76, "La Fontana");
        SMORFIA_MAP.put(77, "I Diavoli");
        SMORFIA_MAP.put(78, "La Bella Figliola");
        SMORFIA_MAP.put(79, "Il Ladro");
        SMORFIA_MAP.put(80, "La Bocca");
        SMORFIA_MAP.put(81, "I Fiori");
        SMORFIA_MAP.put(82, "La Tavola Imbandita");
        SMORFIA_MAP.put(83, "Il Maltempo");
        SMORFIA_MAP.put(84, "La Chiesa");
        SMORFIA_MAP.put(85, "Le Anime del Purgatorio");
        SMORFIA_MAP.put(86, "La Bottega");
        SMORFIA_MAP.put(87, "I Pidocchi");
        SMORFIA_MAP.put(88, "I Caciocavalli");
        SMORFIA_MAP.put(89, "La Vecchia");
        SMORFIA_MAP.put(90, "La Paura");

        // Keywords mapping
        addKeywords(1, "italia", "sole", "dio");
        addKeywords(2, "bambina", "bimba", "acqua", "mare");
        addKeywords(3, "gatta", "gatto");
        addKeywords(4, "maiale", "porco");
        addKeywords(5, "mano", "mamma", "madre");
        addKeywords(6, "padre", "papa", "cane", "cagnolino");
        addKeywords(7, "donna", "donne", "vaso");
        addKeywords(8, "madonna", "fuoco");
        addKeywords(9, "figli", "festa");
        addKeywords(10, "fagioli", "fagiolo");
        addKeywords(11, "topo", "topi", "topolino");
        addKeywords(12, "soldati", "soldato", "soldi", "denaro", "monete");
        addKeywords(13, "antonio", "sant'antonio");
        addKeywords(14, "ubriaco", "vino");
        addKeywords(15, "ragazzo", "giovane");
        addKeywords(16, "fortuna", "culo");
        addKeywords(17, "sogno", "sfortuna");
        addKeywords(18, "sangue", "diavolo");
        addKeywords(19, "risata", "ridere");
        addKeywords(20, "festa", "feste");
        addKeywords(21, "nuda", "donna nuda");
        addKeywords(22, "pazzo", "follia");
        addKeywords(24, "guardia", "polizia", "cavallo");
        addKeywords(25, "natale");
        addKeywords(32, "serpente", "capitone", "anguilla");
        addKeywords(34, "testa", "capo");
        addKeywords(35, "uccello", "uccellino");
        addKeywords(41, "coltello", "lama");
        addKeywords(42, "caffe");
        addKeywords(45, "vino");
        addKeywords(46, "soldi", "denaro");
        addKeywords(47, "morto", "morta", "defunto", "cadavere");
        addKeywords(48, "parla", "parlare", "morto che parla");
        addKeywords(50, "pane");
        addKeywords(52, "mamma", "madre");
        addKeywords(62, "sposa", "matrimonio", "sposo");
        addKeywords(65, "pianto", "lacrime", "piangere");
        addKeywords(70, "palazzo", "casa");
        addKeywords(73, "ospedale", "dottore", "infermiere");
        addKeywords(79, "ladro", "furto");
        addKeywords(81, "fiori", "fiore");
        addKeywords(84, "chiesa");
        addKeywords(90, "paura", "spavento", "terrore");
    }

    private static void addKeywords(int number, String... keywords) {
        for (String k : keywords) {
            ALIAS_MAP.put(k.toLowerCase(Locale.ITALIAN), number);
        }
    }

    public static class SmorfiaMatch {
        public int number;
        public String meaning;
        public String matchedKeyword;

        public SmorfiaMatch(int number, String meaning, String matchedKeyword) {
            this.number = number;
            this.meaning = meaning;
            this.matchedKeyword = matchedKeyword;
        }
    }

    public static List<SmorfiaMatch> parseDreamText(String text) {
        List<SmorfiaMatch> matches = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return matches;
        }

        String cleanText = text.toLowerCase(Locale.ITALIAN).replaceAll("[^a-z0-9àèéìòù' ]", " ");
        String[] words = cleanText.split("\\s+");

        List<Integer> addedNumbers = new ArrayList<>();

        for (String word : words) {
            word = word.trim();
            if (word.length() > 2 && ALIAS_MAP.containsKey(word)) {
                int num = ALIAS_MAP.get(word);
                if (!addedNumbers.contains(num)) {
                    addedNumbers.add(num);
                    matches.add(new SmorfiaMatch(num, SMORFIA_MAP.getOrDefault(num, "Simbolo Smorfia"), word));
                }
            }
        }

        // If fewer than 5 numbers were found, add default harmonic symbols
        if (matches.isEmpty()) {
            int hashVal = Math.abs(text.hashCode());
            for (int i = 1; i <= 5; i++) {
                int num = ((hashVal * i) % 90) + 1;
                if (!addedNumbers.contains(num)) {
                    addedNumbers.add(num);
                    matches.add(new SmorfiaMatch(num, SMORFIA_MAP.getOrDefault(num, "Simbolo Smorfia"), "simbolo del sogno"));
                }
            }
        }

        return matches;
    }
}
