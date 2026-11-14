package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.Giocata;

import org.json.JSONArray;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Salvataggio su disco in ~/.local/share/lottoinjava/ (o $XDG_DATA_HOME/lottoinjava/).
 * Il file lotto_archive.json ha lo stesso formato di quello dell'app Android.
 */
public final class DesktopStorage {

    private static final String ARCHIVE_FILE = "lotto_archive.json";
    private static final String GIOCATE_FILE = "giocate.json";

    private DesktopStorage() { }

    public static Path dataDir() {
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = (xdg != null && !xdg.trim().isEmpty())
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("lottoinjava");
    }

    // ---------------- archivio ----------------

    public static List<Extraction> loadArchive() {
        List<Extraction> list = new ArrayList<>();
        Path file = dataDir().resolve(ARCHIVE_FILE);
        if (!Files.exists(file)) return list;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Extraction.fromJsonObject(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            System.err.println("Errore lettura archivio: " + e);
        }
        sortNewestFirst(list);
        return list;
    }

    private static final int MAX_BACKUPS = 10;

    /** Copia il file archivio attuale in backups/ (con data e ora nel nome) e tiene solo gli ultimi MAX_BACKUPS. */
    static void backupArchive() {
        try {
            Path file = dataDir().resolve(ARCHIVE_FILE);
            if (!Files.exists(file) || Files.size(file) < 10) return;
            Path dir = dataDir().resolve("backups");
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            Files.copy(file, dir.resolve("lotto_archive_backup_" + stamp + ".json"));
            List<Path> backups = new ArrayList<>();
            try (java.util.stream.Stream<Path> st = Files.list(dir)) {
                st.filter(p -> p.getFileName().toString().startsWith("lotto_archive_backup_")).forEach(backups::add);
            }
            Collections.sort(backups);
            for (int i = 0; i < backups.size() - MAX_BACKUPS; i++) Files.deleteIfExists(backups.get(i));
        } catch (Exception e) {
            System.err.println("Backup archivio non riuscito: " + e);
        }
    }

    // ---------------- impostazioni semplici (settings.properties)

    private static java.util.Properties loadSettings() {
        java.util.Properties p = new java.util.Properties();
        try {
            Path f = dataDir().resolve("settings.properties");
            if (Files.exists(f)) {
                try (java.io.Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) { p.load(r); }
            }
        } catch (Exception ignored) { }
        return p;
    }

    /** Salva una singola impostazione senza perdere le altre. */
    private static void saveSetting(String key, String value) {
        try {
            Files.createDirectories(dataDir());
            java.util.Properties p = loadSettings();
            p.setProperty(key, value);
            try (java.io.Writer w = Files.newBufferedWriter(dataDir().resolve("settings.properties"), StandardCharsets.UTF_8)) {
                p.store(w, "Archivio Lotto");
            }
        } catch (Exception e) {
            System.err.println("Impostazioni non salvate: " + e);
        }
    }

    public static boolean autoUpdateAtStart() {
        return Boolean.parseBoolean(loadSettings().getProperty("autoUpdateAtStart", "false"));
    }

    public static void setAutoUpdateAtStart(boolean value) {
        saveSetting("autoUpdateAtStart", String.valueOf(value));
    }

    /** Quanti numeri colorare in blu scuro nell'archivio: 0 = automatico, altrimenti da 1 a 5. */
    public static int bluCount() {
        try { return Math.max(0, Math.min(5, Integer.parseInt(loadSettings().getProperty("bluCount", "0").trim()))); }
        catch (Exception e) { return 0; }
    }

    public static void setBluCount(int n) {
        saveSetting("bluCount", String.valueOf(Math.max(0, Math.min(5, n))));
    }

    /** Tema scelto: "blu" (predefinito), "chiaro" o "fuoco". */
    public static String tema() {
        return loadSettings().getProperty("tema", "blu");
    }

    public static void setTema(String id) {
        saveSetting("tema", id);
    }

    // ---------------- chiave API Gemini (mai stampata nei log)

    /** Variabile d'ambiente GEMINI_API_KEY oppure file gemini_key.txt nella cartella dati. */
    public static String geminiKey() {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        try {
            Path f = dataDir().resolve("gemini_key.txt");
            if (Files.exists(f)) return Files.readString(f, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { }
        return "";
    }

    public static boolean saveGeminiKey(String key) {
        try {
            Path f = dataDir().resolve("gemini_key.txt");
            if (key == null || key.trim().isEmpty()) {
                Files.deleteIfExists(f);
                return true;
            }
            Files.createDirectories(dataDir());
            Files.writeString(f, key.trim(), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(f, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (Exception ignored) { } // file system senza permessi POSIX
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Es. "configurata (…ab12)" senza mostrare la chiave. */
    public static String geminiKeyStato() {
        String k = geminiKey();
        if (k.isEmpty()) return "non configurata";
        return "configurata (…" + k.substring(Math.max(0, k.length() - 4)) + ")";
    }

    public static void saveArchive(List<Extraction> list) throws Exception {
        backupArchive();
        Files.createDirectories(dataDir());
        JSONArray arr = new JSONArray();
        for (Extraction e : list) arr.put(e.toJsonObject());
        Files.writeString(dataDir().resolve(ARCHIVE_FILE), arr.toString(1), StandardCharsets.UTF_8);
    }

    public static List<Extraction> importCsv(Path file) throws Exception {
        try (InputStream is = Files.newInputStream(file)) {
            return CsvImport.parse(is);
        }
    }

    /** Unisce le estrazioni per numero di concorso (le nuove sostituiscono le vecchie) e ordina dalla più recente. */
    public static List<Extraction> merge(List<Extraction> existing, List<Extraction> imported) {
        Map<String, Extraction> map = new LinkedHashMap<>();
        for (Extraction e : existing) map.put(archiveKey(e), e);
        for (Extraction e : imported) {
            Extraction old = map.get(archiveKey(e));
            // Alcune fonti non danno il numero di concorso (usano aaaammgg): se in archivio c'è già
            // il numero reale, lo conserviamo e aggiorniamo solo i numeri estratti.
            if (old != null && e.getConcorsoNumber() >= 10_000_000
                    && old.getConcorsoNumber() > 0 && old.getConcorsoNumber() < 10_000_000) {
                e = new Extraction(old.getConcorsoNumber(), e.getDate(), e.getYear(), e.getWheels());
            }
            map.put(archiveKey(e), e);
        }
        List<Extraction> out = new ArrayList<>(map.values());
        sortNewestFirst(out);
        return out;
    }

    private static String archiveKey(Extraction e) {
        return String.valueOf(e.getDateKey());
    }

    public static void sortNewestFirst(List<Extraction> list) {
        Collections.sort(list, (a, b) -> {
            int c = Integer.compare(b.getDateKey(), a.getDateKey());
            return c != 0 ? c : Integer.compare(b.getConcorsoNumber(), a.getConcorsoNumber());
        });
    }

    // ---------------- giocate ----------------

    public static List<Giocata> loadGiocate() {
        List<Giocata> list = new ArrayList<>();
        Path file = dataDir().resolve(GIOCATE_FILE);
        if (!Files.exists(file)) return list;
        try {
            JSONArray arr = new JSONArray(Files.readString(file, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) list.add(Giocata.fromJson(arr.getJSONObject(i)));
        } catch (Exception e) {
            System.err.println("Errore lettura giocate: " + e);
        }
        return list;
    }

    public static void saveGiocate(List<Giocata> list) {
        try {
            Files.createDirectories(dataDir());
            JSONArray arr = new JSONArray();
            for (Giocata g : list) arr.put(g.toJson());
            Files.writeString(dataDir().resolve(GIOCATE_FILE), arr.toString(1), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Errore salvataggio giocate: " + e);
        }
    }
}
