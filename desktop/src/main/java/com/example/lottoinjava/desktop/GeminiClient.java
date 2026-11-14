package com.example.lottoinjava.desktop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assistente Gemini (stessa logica del main.py): elenca i modelli "flash" disponibili, prova i primi 5
 * (con tre nomi di riserva) e, se Gemini non risponde, restituisce un riepilogo locale.
 */
final class GeminiClient {

    static final String BASE_DEFAULT = "https://generativelanguage.googleapis.com/v1beta";
    private static final String[] MODELLI_FALLBACK = {"gemini-flash-latest", "gemini-2.5-flash", "gemini-3-flash-preview"};

    static final class Esito {
        final boolean ok;
        final String testo;
        Esito(boolean ok, String testo) { this.ok = ok; this.testo = testo; }
    }

    private final String base;
    private HttpClient http;

    GeminiClient(String base) { this.base = base; }

    private HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        return http;
    }

    private static String erroreHttp(int code, String body) {
        try {
            Map<String, Object> o = MiniJson.obj(MiniJson.parse(body));
            String m = MiniJson.str(MiniJson.obj(o.get("error")).get("message"), null);
            if (m != null) return m;
        } catch (RuntimeException ignored) { }
        return "HTTP " + code;
    }

    /** Nomi dei modelli utilizzabili oggi, stabili prima delle anteprime. Lancia HttpFallita se la chiave non va. */
    private List<String> modelliDisponibili(String key) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/models?pageSize=200"))
                .timeout(Duration.ofSeconds(20)).header("x-goog-api-key", key).GET().build();
        HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 400) throw new HttpFallita(resp.statusCode(), erroreHttp(resp.statusCode(), resp.body()));
        List<String> nomi = new ArrayList<>();
        for (Object o : MiniJson.arr(MiniJson.obj(MiniJson.parse(resp.body())).get("models"))) {
            Map<String, Object> m = MiniJson.obj(o);
            String nome = MiniJson.str(m.get("name"), "").replace("models/", "");
            if (!MiniJson.arr(m.get("supportedGenerationMethods")).contains("generateContent")) continue;
            if (!nome.contains("flash")) continue;
            boolean escluso = false;
            for (String x : new String[]{"image", "tts", "live", "audio", "robotics", "embedding", "thinking", "exp"}) {
                if (nome.contains(x)) { escluso = true; break; }
            }
            if (!escluso) nomi.add(nome);
        }
        Collections.sort(nomi, (a, b) -> {
            int c = Boolean.compare(a.contains("preview"), b.contains("preview"));
            if (c != 0) return c;
            c = Boolean.compare(a.contains("lite"), b.contains("lite"));
            return c != 0 ? c : a.compareTo(b);
        });
        return nomi;
    }

    private static final class HttpFallita extends Exception {
        final int code;
        HttpFallita(int code, String msg) { super(msg); this.code = code; }
    }

    Esito chiama(String prompt, String apiKey) {
        String key = String.valueOf(apiKey).trim().replaceAll("^[\"']+|[\"']+$", "").trim();
        if (key.isEmpty()) return new Esito(false, "Chiave API vuota.");

        List<String> modelli = new ArrayList<>();
        try {
            modelli = modelliDisponibili(key);
        } catch (HttpFallita he) {
            if (he.code == 400 || he.code == 401 || he.code == 403) {
                return new Esito(false, "Chiave non accettata da Google: " + he.getMessage());
            }
        } catch (Exception ignored) { }
        for (String m : MODELLI_FALLBACK) if (!modelli.contains(m)) modelli.add(m);

        String payload = "{\"contents\":[{\"parts\":[{\"text\":" + MiniJson.quote(prompt) + "}]}]}";
        String ultimoErrore = "Impossibile contattare i server Google Gemini.";
        for (int k = 0; k < Math.min(5, modelli.size()); k++) {
            String modello = modelli.get(k);
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/models/" + modello + ":generateContent"))
                        .timeout(Duration.ofSeconds(40))
                        .header("Content-Type", "application/json").header("x-goog-api-key", key)
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
                HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 400) {
                    ultimoErrore = "Google (" + modello + "): " + erroreHttp(resp.statusCode(), resp.body());
                    if (resp.statusCode() == 400 || resp.statusCode() == 401 || resp.statusCode() == 403) break; // chiave/permessi
                    continue; // 404 (modello ritirato), 429 (quota), 5xx: prossimo modello
                }
                Map<String, Object> res = MiniJson.obj(MiniJson.parse(resp.body()));
                List<Object> cands = MiniJson.arr(res.get("candidates"));
                if (!cands.isEmpty()) {
                    Map<String, Object> c0 = MiniJson.obj(cands.get(0));
                    StringBuilder testo = new StringBuilder();
                    for (Object p : MiniJson.arr(MiniJson.obj(c0.get("content")).get("parts"))) testo.append(MiniJson.str(MiniJson.obj(p).get("text"), ""));
                    String t = testo.toString().trim();
                    if (!t.isEmpty()) return new Esito(true, t);
                    ultimoErrore = "Google (" + modello + "): risposta vuota (motivo: " + MiniJson.str(c0.get("finishReason"), "?") + ")";
                } else {
                    String blocco = MiniJson.str(MiniJson.obj(res.get("promptFeedback")).get("blockReason"), "nessun candidato");
                    ultimoErrore = "Google (" + modello + "): nessuna risposta (" + blocco + ")";
                }
            } catch (Exception e) {
                ultimoErrore = "Errore connessione: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                String up = String.valueOf(e).toUpperCase(Locale.ROOT);
                if (up.contains("CERTIFICATE") || up.contains("SSL") || up.contains("HANDSHAKE")) {
                    ultimoErrore += " (ricostruisci l'AppImage con ./build-appimage.sh: servono i moduli java.naming e jdk.crypto.ec)";
                    break;
                }
            }
        }
        return new Esito(false, ultimoErrore);
    }

    /** genera_consulenza_gemini_ia() del main.py: report Gemini oppure riepilogo locale con il motivo. */
    String consulenza(String gioco, int spia, String ruotaNome, List<Integer> top, String apiKey) {
        String ora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        StringBuilder topStr = new StringBuilder();
        for (int i = 0; i < Math.min(6, top.size()); i++) topStr.append(i > 0 ? " - " : "").append(String.format("%02d", top.get(i)));
        int capo = top.isEmpty() ? 17 : top.get(0);
        List<Integer> abb = top.size() > 1 ? new ArrayList<>(top.subList(1, Math.min(5, top.size()))) : java.util.Arrays.asList(90, 8, 42, 55);

        String errore;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String prompt = "Sei l'Assistente IA di SANTO SPIA EVOLUTIONS per il gioco del " + gioco + ".\n"
                    + "Parametri attuali:\n"
                    + "- Numero Spia Selezionato: " + String.format("%02d", spia) + "\n"
                    + "- Ruota/Ambito: " + ruotaNome + "\n"
                    + "- Numeri statistici in evidenza: " + topStr + "\n"
                    + "- Capogioco statistico: " + String.format("%02d", capo) + "\n"
                    + "Scrivi in italiano un report breve e chiaro: 1) valutazione della spia; "
                    + "2) numeri e abbinamenti suggeriti dai dati; 3) consiglio di gioco responsabile. "
                    + "Ricorda che le estrazioni sono casuali e nessuna previsione e' certa.";
            Esito e = chiama(prompt, apiKey);
            if (e.ok && !e.testo.isEmpty()) {
                String linea = "============================================================";
                return linea + "\nGEMINI IA ONLINE - REPORT " + gioco + "\n" + linea + "\n" + e.testo + "\n" + linea;
            }
            errore = e.testo;
        } else {
            errore = "Nessuna chiave API configurata (usa 'Chiave API').";
        }
        StringBuilder ab = new StringBuilder();
        for (int i = 0; i < abb.size(); i++) ab.append(i > 0 ? ", " : "").append(String.format("%02d", abb.get(i)));
        String linea = "============================================================";
        String tratti = "------------------------------------------------------------";
        return linea + "\nGEMINI NON DISPONIBILE - RIEPILOGO LOCALE\n" + linea + "\n"
                + "Motivo: " + errore + "\n" + tratti + "\n"
                + "Spia " + String.format("%02d", spia) + " (" + ruotaNome + ") - ore " + ora + "\n"
                + "  Numero in evidenza : " + String.format("%02d", capo) + "\n"
                + "  Abbinamenti        : " + ab + "\n"
                + "  Numeri statistici  : " + topStr + "\n" + linea;
    }
}
