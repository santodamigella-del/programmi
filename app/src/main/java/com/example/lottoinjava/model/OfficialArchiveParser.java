package com.example.lottoinjava.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Legge l'archivio storico ufficiale del Lotto (file di testo dentro uno ZIP: una riga per ruota,
 * con data, ruota e 5 numeri). Il parser è volutamente tollerante: accetta separatori diversi
 * (tab, spazio, ; , |), date AAAA/MM/GG, AAAA-MM-GG, GG/MM/AAAA o AAAAMMGG, ruota come sigla (BA, NZ...)
 * o come nome (Bari, Nazionale...), e ignora eventuali colonne di testo in più (es. il nome della città).
 * Codice puro Java: usato sia dall'app Android sia dalla versione desktop.
 */
public final class OfficialArchiveParser {

    /** Archivio ufficiale (sito Lotto Italia / Brightstar): estrazioni dal 2001 a oggi. */
    public static final String URL_2001_OGGI =
            "https://www.brightstarlottery.it/STORICO_ESTRAZIONI_LOTTO/storico01-oggi.zip";

    public static final class Parsed {
        public final List<Extraction> extractions;
        public final int rowsRecognised;
        public final String preview; // prime righe del file, utili per capire un formato non riconosciuto

        Parsed(List<Extraction> extractions, int rowsRecognised, String preview) {
            this.extractions = extractions;
            this.rowsRecognised = rowsRecognised;
            this.preview = preview;
        }
    }

    private static final Pattern SPLIT = Pattern.compile("[\\s;,|]+");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$");
    private static final Pattern COMPACT = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY);
    private static final int MAX_BYTES = 60_000_000;

    private static final Map<String, String> WHEEL_ALIASES = new HashMap<>();

    static {
        String[][] pairs = {
                {"ba", "Bari"}, {"ca", "Cagliari"}, {"fi", "Firenze"}, {"ge", "Genova"},
                {"mi", "Milano"}, {"na", "Napoli"}, {"pa", "Palermo"}, {"rm", "Roma"},
                {"to", "Torino"}, {"ve", "Venezia"}, {"nz", "Nazionale"}, {"naz", "Nazionale"}
        };
        for (String[] p : pairs) WHEEL_ALIASES.put(p[0], p[1]);
        for (String r : Extraction.ALL_RUOTE) WHEEL_ALIASES.put(r.toLowerCase(Locale.ROOT), r);
    }

    private OfficialArchiveParser() { }

    /** Legge uno ZIP (byte) e analizza tutti i file di testo contenuti. */
    public static Parsed parseZip(byte[] zipBytes) throws IOException {
        StringBuilder all = new StringBuilder();
        int total = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("File ZIP troppo grande.");
                    bos.write(buf, 0, n);
                }
                all.append(new String(bos.toByteArray(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return parse(all.toString());
    }

    public static Parsed parse(String text) {
        Map<LocalDate, Map<String, List<Integer>>> byDate = new HashMap<>();
        int recognised = 0;
        StringBuilder preview = new StringBuilder();
        int previewLines = 0;

        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (previewLines < 3) {
                preview.append(line.length() > 100 ? line.substring(0, 100) : line).append("  ⏎  ");
                previewLines++;
            }

            String[] tok = SPLIT.split(line);
            LocalDate date = null;
            int dateIdx = -1;
            for (int i = 0; i < tok.length; i++) {
                LocalDate d = parseDateToken(tok[i]);
                if (d != null) {
                    date = d;
                    dateIdx = i;
                    break;
                }
            }
            if (date == null) continue;

            String wheel = null;
            int wheelIdx = -1;
            for (int i = dateIdx + 1; i < tok.length; i++) {
                String w = WHEEL_ALIASES.get(tok[i].toLowerCase(Locale.ROOT));
                if (w != null) {
                    wheel = w;
                    wheelIdx = i;
                    break;
                }
            }
            if (wheel == null) continue;

            List<Integer> nums = new ArrayList<>();
            for (int i = wheelIdx + 1; i < tok.length && nums.size() < 5; i++) {
                Integer n = parseNumber(tok[i]);
                if (n != null) nums.add(n); // le colonne non numeriche (es. nome città) vengono saltate
            }
            if (nums.size() != 5 || new HashSet<>(nums).size() != 5) continue;

            Map<String, List<Integer>> wheels = byDate.get(date);
            if (wheels == null) {
                wheels = new HashMap<>();
                byDate.put(date, wheels);
            }
            wheels.put(wheel, nums);
            recognised++;
        }

        List<LocalDate> dates = new ArrayList<>(byDate.keySet());
        Collections.sort(dates, Collections.reverseOrder());
        List<Extraction> out = new ArrayList<>();
        for (LocalDate d : dates) {
            Map<String, List<Integer>> w = byDate.get(d);
            Map<String, List<Integer>> ordered = new LinkedHashMap<>();
            for (String ruota : Extraction.ALL_RUOTE) {
                if (w.containsKey(ruota)) ordered.put(ruota, w.get(ruota));
            }
            int concorso = d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
            out.add(new Extraction(concorso, d.format(OUT), d.getYear(), ordered));
        }
        return new Parsed(out, recognised, preview.toString().trim());
    }

    private static LocalDate parseDateToken(String t) {
        try {
            Matcher m = YMD.matcher(t);
            if (m.matches()) return build(m.group(1), m.group(2), m.group(3));
            m = DMY.matcher(t);
            if (m.matches()) return build(m.group(3), m.group(2), m.group(1));
            m = COMPACT.matcher(t);
            if (m.matches()) return build(m.group(1), m.group(2), m.group(3));
        } catch (Exception ignored) { }
        return null;
    }

    private static LocalDate build(String y, String m, String d) {
        int year = Integer.parseInt(y);
        if (year < 1800 || year > 2200) return null;
        return LocalDate.of(year, Integer.parseInt(m), Integer.parseInt(d)); // lancia se non valida
    }

    private static Integer parseNumber(String t) {
        if (t.length() < 1 || t.length() > 2) return null;
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) return null;
        }
        int n = Integer.parseInt(t);
        return (n >= 1 && n <= 90) ? n : null;
    }
}
