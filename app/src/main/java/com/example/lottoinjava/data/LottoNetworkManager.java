package com.example.lottoinjava.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.OfficialArchiveParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LottoNetworkManager {

    private static final String TAG = "LottoNetworkManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Online sources list including Xaming and Lottologia
    public static final String[] SOURCES = {
            "Xaming (xaming.it)",
            "Lottologia (lottologia.com)",
            "ADM / Gioco del Lotto (adm.gov.it)",
            "Importazione File CSV Locale"
    };

    public interface NetworkCallback {
        void onSuccess(List<Extraction> extractions, String message);
        void onError(String error);
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        }
        return false;
    }

    public static void updateArchiveFromInternet(Context context, NetworkCallback callback) {
        executor.execute(() -> {
            if (!isNetworkAvailable(context)) {
                mainHandler.post(() -> callback.onError("Connessione Internet non disponibile. Verifica la tua rete."));
                return;
            }

            try {
                // Fetch online extractions from remote sources (Xaming / Lottologia / Live Feeds)
                List<Extraction> remoteExtractions = fetchFromRemoteServer();

                if (!remoteExtractions.isEmpty()) {
                    List<Extraction> existing = LottoLocalCache.loadExtractions(context);
                    List<Extraction> merged = mergeExtractions(existing, remoteExtractions);

                    LottoLocalCache.saveExtractions(context, merged);

                    int addedCount = merged.size() - existing.size();
                    Extraction onlineLast = remoteExtractions.get(0);
                    for (Extraction e : remoteExtractions) if (e.getDateKey() > onlineLast.getDateKey()) onlineLast = e;
                    Extraction localLast = existing.isEmpty() ? null : existing.get(0);
                    for (Extraction e : existing) if (localLast == null || e.getDateKey() > localLast.getDateKey()) localLast = e;
                    String localDate = localLast == null ? "archivio vuoto" : localLast.getShortDate();
                    String onlineDate = onlineLast.getShortDate();
                    String msg = "Fonte ufficiale: ultima estrazione " + onlineDate + ". Archivio locale: " + localDate + ". " +
                            (addedCount > 0 ? "Aggiunte " + addedCount + " estrazioni." :
                            (localLast != null && localLast.getDateKey() >= onlineLast.getDateKey() ?
                            "Nessuna estrazione nuova dalla fonte." : "ATTENZIONE: la fonte non ha aggiunto dati, controllare il formato dell'archivio."));

                    mainHandler.post(() -> callback.onSuccess(merged, msg));
                } else {
                    mainHandler.post(() -> callback.onError("Il server non ha restituito estrazioni valide."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Network update error", e);
                mainHandler.post(() -> callback.onError("Errore durante l'aggiornamento: " + e.getLocalizedMessage()));
            }
        });
    }

    /** Ricostruisce da zero l'archivio dopo aver verificato che la fonte restituisca dati validi. */
    public static void rebuildArchiveFromInternet(Context context, NetworkCallback callback) {
        executor.execute(() -> {
            if (!isNetworkAvailable(context)) {
                mainHandler.post(() -> callback.onError("Connessione Internet non disponibile. Archivio non modificato."));
                return;
            }
            try {
                List<Extraction> downloaded = fetchFromRemoteServer();
                if (downloaded.isEmpty()) throw new Exception("La fonte ufficiale non ha restituito estrazioni valide.");
                downloaded.sort((a, b) -> Integer.compare(b.getDateKey(), a.getDateKey()));
                Extraction newest = downloaded.get(0);
                if (newest.getWheels() == null || newest.getWheels().size() < 10) {
                    throw new Exception("Archivio scaricato ma incompleto: l'ultima data " + newest.getShortDate() +
                            " contiene solo " + (newest.getWheels() == null ? 0 : newest.getWheels().size()) +
                            " ruote. Archivio locale lasciato invariato.");
                }
                LottoLocalCache.backupArchive(context);
                LottoLocalCache.saveExtractionsStrict(context, downloaded);
                String msg = "Ricostruzione completata: " + downloaded.size() + " estrazioni. Ultima data: " + newest.getShortDate() +
                        ". Fonte: archivio ufficiale Brightstar. Verifica dati: " + newest.getWheels().size() + "/11 ruote.";
                mainHandler.post(() -> callback.onSuccess(downloaded, msg));
            } catch (Exception e) {
                Log.e(TAG, "Archive rebuild error", e);
                mainHandler.post(() -> callback.onError("Ricostruzione non completata; archivio precedente conservato. Dettaglio: " +
                        (e.getLocalizedMessage() == null ? e.getClass().getSimpleName() : e.getLocalizedMessage())));
            }
        });
    }

    /** Scarica l'archivio ufficiale (ZIP dal 2001 a oggi). Nessun dato viene inventato: se qualcosa va storto, lancia un errore. */
    private static List<Extraction> fetchFromRemoteServer() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(OfficialArchiveParser.URL_2001_OGGI).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LottoInJava/1.1");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("Il server ha risposto HTTP " + code + ".");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            OfficialArchiveParser.Parsed parsed = OfficialArchiveParser.parseZip(bos.toByteArray());
            if (parsed.extractions.isEmpty()) {
                throw new Exception("File scaricato ma formato non riconosciuto. Prime righe: " + parsed.preview);
            }
            return parsed.extractions;
        } finally {
            conn.disconnect();
        }
    }

    public static List<Extraction> mergeExtractions(List<Extraction> existing, List<Extraction> newExtractions) {
        Map<Integer, Extraction> map = new LinkedHashMap<>();

        for (Extraction e : existing) {
            map.put(e.getDateKey(), e);
        }
        for (Extraction e : newExtractions) {
            map.put(e.getDateKey(), e); // i dati nuovi sostituiscono quelli con la stessa data
        }

        List<Extraction> result = new ArrayList<>(map.values());
        result.sort((a, b) -> {
            int c = Integer.compare(b.getDateKey(), a.getDateKey());
            return c != 0 ? c : Integer.compare(b.getConcorsoNumber(), a.getConcorsoNumber());
        });
        return result;
    }
}
