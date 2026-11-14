package com.example.lottoinjava.desktop;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestisce i temi dell'app: Blu scuro (predefinito), Chiaro, Rosso fuoco e Vampiro.
 * Il file style.css usa solo variabili colore (-c-...) definite nel blocco @THEME-BEGIN/@THEME-END:
 * per Chiaro e Rosso fuoco il blocco viene sostituito e il CSS risultante è salvato nella cartella dati.
 * Le palline dell'archivio (spia verde, risultato rosso, più usciti blu scuro) restano uguali in tutti i temi.
 */
final class ThemeManager {

    enum Tema {
        BLU("blu", "Blu scuro", "🌙"),
        CHIARO("chiaro", "Chiaro", "☀️"),
        FUOCO("fuoco", "Rosso fuoco", "🔥"),
        VAMPIRO("vampiro", "Vampiro", "🧛");

        final String id, nome, icona;
        Tema(String id, String nome, String icona) { this.id = id; this.nome = nome; this.icona = icona; }

        Tema prossimo() { return values()[(ordinal() + 1) % values().length]; }

        static Tema daId(String id) {
            for (Tema t : values()) if (t.id.equals(id)) return t;
            return BLU;
        }
    }

    private static final String BEGIN = "/* @THEME-BEGIN */";
    private static final String END = "/* @THEME-END */";

    private static Tema corrente = null;
    private static final Map<Tema, String> cache = new HashMap<>();

    private ThemeManager() {}

    static Tema corrente() {
        if (corrente == null) corrente = Tema.daId(DesktopStorage.tema());
        return corrente;
    }

    /** Passa al tema successivo e lo ricorda per il prossimo avvio. */
    static Tema cambia() {
        corrente = corrente().prossimo();
        DesktopStorage.setTema(corrente.id);
        return corrente;
    }

    /** URL del foglio di stile del tema corrente (da usare in scene.getStylesheets()). */
    static String cssUrl() {
        Tema t = corrente();
        String url = cache.get(t);
        if (url != null) return url;
        String resource = ThemeManager.class.getResource("/style.css").toExternalForm();
        if (t == Tema.BLU) { cache.put(t, resource); return resource; }
        try {
            String base;
            try (InputStream in = ThemeManager.class.getResourceAsStream("/style.css")) {
                base = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int a = base.indexOf(BEGIN), b = base.indexOf(END);
            if (a < 0 || b < a) throw new IllegalStateException("blocco tema non trovato");
            String css = base.substring(0, a) + blocco(t) + base.substring(b + END.length());
            Path dir = DesktopStorage.dataDir().resolve("themes");
            Files.createDirectories(dir);
            Path file = dir.resolve("style-" + t.id + ".css");
            Files.writeString(file, css, StandardCharsets.UTF_8);
            url = file.toUri().toString();
        } catch (Exception ex) {
            System.err.println("Tema " + t.nome + " non applicato: " + ex);
            url = resource;
        }
        cache.put(t, url);
        return url;
    }

    private static String blocco(Tema t) {
        String[][] p = t == Tema.CHIARO ? CHIARO : (t == Tema.VAMPIRO ? VAMPIRO : FUOCO);
        StringBuilder sb = new StringBuilder(".root {\n");
        for (String[] kv : p) sb.append("    ").append(kv[0]).append(": ").append(kv[1]).append(";\n");
        return sb.append("}\n").toString();
    }

    private static final String[][] CHIARO = {
        {"-fx-base", "#E2E8F0"}, {"-fx-background", "#F1F5F9"}, {"-fx-control-inner-background", "#FFFFFF"},
        {"-c-bg", "#F1F5F9"}, {"-c-panel", "#FFFFFF"}, {"-c-panel2", "#FFFFFF"},
        {"-c-border", "#CBD5E1"}, {"-c-border2", "#94A3B8"},
        {"-c-text", "#0F172A"}, {"-c-muted", "#475569"},
        {"-c-title", "#B45309"}, {"-c-accent", "#B45309"}, {"-c-accent2", "#D97706"},
        {"-c-header-a", "#FFFFFF"}, {"-c-header-b", "#DBEAFE"}, {"-c-bar", "#2563EB"},
        {"-c-sel", "#FDE68A"}, {"-c-sel-text", "#0F172A"},
        {"-c-popup-bg", "#FFFFFF"}, {"-c-popup-hover", "#E2E8F0"},
        {"-c-win", "#047857"}, {"-c-lost", "#B91C1C"},
        {"-c-prim-a", "#6D28D9"}, {"-c-prim-b", "#2563EB"}, {"-c-prim-border", "#6D28D9"},
        {"-c-prim-hover", "#7C3AED"}, {"-c-prim-hover-border", "#4C1D95"},
        {"-c-toast-a", "#F3E8FF"}, {"-c-toast-b", "#E0E7FF"}, {"-c-toast-border", "#A855F7"}, {"-c-toast-text", "#581C87"},
        {"-c-err-bg", "#FFE4E6"}, {"-c-err-border", "#E11D48"}, {"-c-err-text", "#9F1239"},
        {"-c-pop-a", "#F5F3FF"}, {"-c-pop-b", "#EDE9FE"}, {"-c-asst-title", "#A21CAF"}
    };

    /** Colore di sfondo del tema corrente (per le immagini salvate). */
    static String sfondoHex() {
        switch (corrente()) {
            case CHIARO: return "#F1F5F9";
            case FUOCO: return "#2A0E06";
            case VAMPIRO: return "#1A0505";
            default: return "#0F172A";
        }
    }

    private static final String[][] VAMPIRO = {
        {"-fx-base", "#2D0A0A"}, {"-fx-background", "#1A0505"}, {"-fx-control-inner-background", "#2D0A0A"},
        {"-c-bg", "#1A0505"}, {"-c-panel", "#2D0A0A"}, {"-c-panel2", "#220707"},
        {"-c-border", "#7F1D1D"}, {"-c-border2", "#B91C1C"},
        {"-c-text", "#FFF7ED"}, {"-c-muted", "#FDBA74"},
        {"-c-title", "#FB923C"}, {"-c-accent", "#FBBF24"}, {"-c-accent2", "#F97316"},
        {"-c-header-a", "#2A0707"}, {"-c-header-b", "#7F1D1D"}, {"-c-bar", "#F97316"},
        {"-c-sel", "#B45309"}, {"-c-sel-text", "#FFFFFF"},
        {"-c-popup-bg", "#220707"}, {"-c-popup-hover", "#7F1D1D"},
        {"-c-win", "#34D399"}, {"-c-lost", "#FCA5A5"},
        {"-c-prim-a", "#DC2626"}, {"-c-prim-b", "#F97316"}, {"-c-prim-border", "#FDBA74"},
        {"-c-prim-hover", "#EF4444"}, {"-c-prim-hover-border", "#FCD34D"},
        {"-c-toast-a", "#450A0A"}, {"-c-toast-b", "#7C2D12"}, {"-c-toast-border", "#F97316"}, {"-c-toast-text", "#FFEDD5"},
        {"-c-err-bg", "#450A0A"}, {"-c-err-border", "#FCA5A5"}, {"-c-err-text", "#FECACA"},
        {"-c-pop-a", "#2A0606"}, {"-c-pop-b", "#4A0E0E"}, {"-c-asst-title", "#FB923C"}
    };

    private static final String[][] FUOCO = {
        {"-fx-base", "#5A1A08"}, {"-fx-background", "#2A0E06"}, {"-fx-control-inner-background", "#4A1607"},
        {"-c-bg", "#2A0E06"}, {"-c-panel", "#4A1607"}, {"-c-panel2", "#3A1105"},
        {"-c-border", "#C2410C"}, {"-c-border2", "#EA580C"},
        {"-c-text", "#FFF7ED"}, {"-c-muted", "#FED7AA"},
        {"-c-title", "#FDBA74"}, {"-c-accent", "#FDE047"}, {"-c-accent2", "#F97316"},
        {"-c-header-a", "#7C1D12"}, {"-c-header-b", "#EA580C"}, {"-c-bar", "#F59E0B"},
        {"-c-sel", "#C2410C"}, {"-c-sel-text", "#FFFFFF"},
        {"-c-popup-bg", "#3A1105"}, {"-c-popup-hover", "#9A3412"},
        {"-c-win", "#4ADE80"}, {"-c-lost", "#FECACA"},
        {"-c-prim-a", "#DC2626"}, {"-c-prim-b", "#F59E0B"}, {"-c-prim-border", "#FDBA74"},
        {"-c-prim-hover", "#EF4444"}, {"-c-prim-hover-border", "#FDE047"},
        {"-c-toast-a", "#7C2D12"}, {"-c-toast-b", "#B91C1C"}, {"-c-toast-border", "#FB923C"}, {"-c-toast-text", "#FFEDD5"},
        {"-c-err-bg", "#7F1D1D"}, {"-c-err-border", "#FECACA"}, {"-c-err-text", "#FEE2E2"},
        {"-c-pop-a", "#5A1A08"}, {"-c-pop-b", "#9A3412"}, {"-c-asst-title", "#FDBA74"}
    };
}
