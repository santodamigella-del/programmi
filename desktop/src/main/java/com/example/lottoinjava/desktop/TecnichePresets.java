package com.example.lottoinjava.desktop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Settaggi salvati delle tecniche (es. "Spia a tutte · spia 2 · Milano"): nome + valori dei controlli.
 * Sono scritti nella cartella dati dell'app, nel file tecniche_salvate.properties.
 */
final class TecnichePresets {

    static final class Preset {
        final String nome;
        final Map<String, String> valori = new LinkedHashMap<>();
        Preset(String nome) { this.nome = nome; }
    }

    private TecnichePresets() { }

    private static Path file() {
        return DesktopStorage.dataDir().resolve("tecniche_salvate.properties");
    }

    static List<Preset> carica() {
        List<Preset> out = new ArrayList<>();
        Path f = file();
        if (!Files.exists(f)) return out;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return out;
        }
        for (int i = 0; i < 10000; i++) {
            String nome = p.getProperty(i + ".nome");
            if (nome == null) {
                if (i > 0 && p.getProperty((i + 1) + ".nome") == null) break;
                continue;
            }
            Preset pr = new Preset(nome);
            String prefisso = i + ".v.";
            for (String k : p.stringPropertyNames()) {
                if (k.startsWith(prefisso)) pr.valori.put(k.substring(prefisso.length()), p.getProperty(k));
            }
            out.add(pr);
        }
        return out;
    }

    /** Salva il preset; se esiste già uno con lo stesso nome (senza distinguere maiuscole) lo sostituisce. */
    static void salva(Preset nuovo) throws IOException {
        List<Preset> tutti = carica();
        boolean trovato = false;
        for (int i = 0; i < tutti.size(); i++) {
            if (tutti.get(i).nome.equalsIgnoreCase(nuovo.nome)) {
                tutti.set(i, nuovo);
                trovato = true;
            }
        }
        if (!trovato) tutti.add(nuovo);
        scrivi(tutti);
    }

    static void elimina(String nome) throws IOException {
        List<Preset> tutti = carica();
        tutti.removeIf(p -> p.nome.equalsIgnoreCase(nome));
        scrivi(tutti);
    }

    private static void scrivi(List<Preset> tutti) throws IOException {
        Files.createDirectories(DesktopStorage.dataDir());
        Properties p = new Properties();
        for (int i = 0; i < tutti.size(); i++) {
            p.setProperty(i + ".nome", tutti.get(i).nome);
            for (Map.Entry<String, String> e : tutti.get(i).valori.entrySet()) {
                p.setProperty(i + ".v." + e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "Settaggi salvati delle tecniche");
        }
    }
}
