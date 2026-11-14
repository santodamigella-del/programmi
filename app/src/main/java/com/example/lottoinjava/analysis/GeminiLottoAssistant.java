package com.example.lottoinjava.analysis;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeminiLottoAssistant {

    public interface GeminiCallback {
        void onSuccess(String aiResponse, List<Integer> suggestedNumbers);
        void onError(String errorMsg);
    }

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    /**
     * Interprets a dream description using Gemini AI, translating symbols into 5 Lotto numbers (1-90)
     */
    public static void interpretDreamWithGemini(String apiKey, String dreamText, GeminiCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (callback != null) callback.onError("Inserisci una chiave API Gemini valida.");
            return;
        }

        String prompt = "Sei un esperto cabalista ed estrattore di numeri del Lotto esperto di Smorfia Napoletana.\n" +
                "Analizza questo sogno: '" + dreamText + "'.\n" +
                "1. Estrai esattamente 5 numeri compresi tra 1 e 90 associati ai simboli del sogno.\n" +
                "2. Spiega brevemente il significato cabalistico di ciascun numero.\n" +
                "3. Suggerisci la ruota consigliata e scrivi i 5 numeri finali in questo formato alla fine del testo: NUMERI: [X, Y, Z, A, B]";

        callGeminiApi(apiKey, prompt, callback);
    }

    /**
     * Generates a smart AI strategy report for prediction numbers
     */
    public static void generateStrategyReportWithGemini(String apiKey, String wheel, List<Integer> numbers, GeminiCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (callback != null) callback.onError("Inserisci una chiave API Gemini valida.");
            return;
        }

        String prompt = String.format(Locale.ITALIAN,
                "Sei un assistente AI specializzato in analisi statistica del Lotto italiano.\n" +
                        "Analizza la cinquina proposta per la ruota di %s: %s.\n" +
                        "Fornisci una sintesi strategica motivata sui ritardi, frequenze e convergenze consigliando la combinazione per Ambo e Terno.",
                wheel.toUpperCase(), numbers.toString());

        callGeminiApi(apiKey, prompt, callback);
    }

    private static void callGeminiApi(String apiKey, String prompt, GeminiCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                URL url = new URL(GEMINI_API_URL + "?key=" + apiKey.trim());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                // Build JSON payload for Gemini API
                JSONObject textObj = new JSONObject();
                textObj.put("text", prompt);

                JSONArray partsArray = new JSONArray();
                partsArray.put(textObj);

                JSONObject contentObj = new JSONObject();
                contentObj.put("parts", partsArray);

                JSONArray contentsArray = new JSONArray();
                contentsArray.put(contentObj);

                JSONObject payload = new JSONObject();
                payload.put("contents", contentsArray);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder responseSb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        responseSb.append(line);
                    }

                    JSONObject responseJson = new JSONObject(responseSb.toString());
                    JSONArray candidates = responseJson.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                        if (content != null) {
                            JSONArray parts = content.optJSONArray("parts");
                            if (parts != null && parts.length() > 0) {
                                String textResult = parts.getJSONObject(0).optString("text", "");

                                // Extract 5 suggested numbers if present in format NUMERI: [x, y, z...]
                                List<Integer> extractedNums = parseNumbersFromText(textResult);

                                mainHandler.post(() -> {
                                    if (callback != null) callback.onSuccess(textResult, extractedNums);
                                });
                                return;
                            }
                        }
                    }
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Risposta da Gemini non valida o vuota.");
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Errore Server Gemini (Codice " + code + "). Controlla la tua API Key.");
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Errore di connessione a Gemini API: " + e.getLocalizedMessage());
                });
            }
        }).start();
    }

    private static List<Integer> parseNumbersFromText(String text) {
        List<Integer> list = new ArrayList<>();
        if (text == null) return list;

        try {
            int idx = text.indexOf("NUMERI:");
            if (idx != -1) {
                String sub = text.substring(idx);
                int startBracket = sub.indexOf('[');
                int endBracket = sub.indexOf(']');
                if (startBracket != -1 && endBracket != -1) {
                    String numStr = sub.substring(startBracket + 1, endBracket);
                    String[] parts = numStr.split(",");
                    for (String p : parts) {
                        try {
                            int n = Integer.parseInt(p.trim());
                            if (n >= 1 && n <= 90 && !list.contains(n)) {
                                list.add(n);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}
