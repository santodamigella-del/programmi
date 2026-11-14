package com.example.lottoinjava.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.lottoinjava.model.Extraction;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LottoLocalCache {

    private static final String TAG = "LottoLocalCache";
    private static final String FILE_NAME = "lotto_archive.json";
    private static final String PREF_NAME = "lotto_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_timestamp";

    public static synchronized void saveExtractions(Context context, List<Extraction> list) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Extraction ext : list) {
                jsonArray.put(ext.toJsonObject());
            }

            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(jsonArray.toString(2));
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();

            Log.d(TAG, "Saved " + list.size() + " extractions to cache.");
        } catch (Exception e) {
            Log.e(TAG, "Error saving extractions cache", e);
        }
    }

    public static synchronized List<Extraction> loadExtractions(Context context) {
        List<Extraction> list = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILE_NAME);

        if (!file.exists()) {
            List<Extraction> initialList = getSampleHistoricalExtractions();
            saveExtractions(context, initialList);
            return initialList;
        }

        try (FileInputStream fis = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONArray jsonArray = new JSONArray(sb.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                list.add(Extraction.fromJsonObject(obj));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading cache file, creating sample data", e);
            list = getSampleHistoricalExtractions();
            saveExtractions(context, list);
        }

        // Ordine garantito: dalla più recente alla più vecchia (i motori di analisi dipendono da questo)
        list.sort((a, b) -> {
            int c = Integer.compare(b.getDateKey(), a.getDateKey());
            return c != 0 ? c : Integer.compare(b.getConcorsoNumber(), a.getConcorsoNumber());
        });
        return list;
    }


    /** Salvataggio atomico per le operazioni di ricostruzione: non nasconde gli errori al chiamante. */
    public static synchronized void saveExtractionsStrict(Context context, List<Extraction> list) throws java.io.IOException {
        JSONArray jsonArray = new JSONArray();
        try {
            for (Extraction ext : list) jsonArray.put(ext.toJsonObject());
        } catch (org.json.JSONException e) {
            throw new java.io.IOException("Impossibile serializzare l'archivio: " + e.getMessage(), e);
        }
        File target = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(temp);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(jsonArray.toString(2));
            writer.flush();
            fos.getFD().sync();
        } catch (org.json.JSONException e) {
            throw new java.io.IOException("Errore nel formato JSON: " + e.getMessage(), e);
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
    }

    /** Crea una copia di sicurezza dell'archivio locale prima di ricostruirlo o cancellarlo. */
    public static synchronized File backupArchive(Context context) throws java.io.IOException {
        File source = new File(context.getFilesDir(), FILE_NAME);
        if (!source.exists()) return null;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(new Date());
        File backup = new File(context.getFilesDir(), "lotto_archive_backup_" + stamp + ".json");
        Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    /** Svuota l'archivio dopo il backup. Scrive [] per evitare che il caricamento crei dati dimostrativi. */
    public static synchronized File clearArchive(Context context) throws java.io.IOException {
        File backup = backupArchive(context);
        File target = new File(context.getFilesDir(), FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(target);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write("[]");
            writer.flush();
        }
        return backup;
    }

    public static String getLastSyncFormatted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
        if (lastSync == 0) {
            return "Mai aggiornato";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN);
        return sdf.format(new Date(lastSync));
    }

    public static List<Extraction> getSampleHistoricalExtractions() {
        List<Extraction> list = new ArrayList<>();

        list.add(createSample(30, "Sabato 08/03/2025", 2025, new int[][]{
                {15, 28, 44, 71, 90}, // Bari
                {3, 19, 32, 60, 88},  // Cagliari
                {12, 33, 54, 76, 81}, // Firenze
                {7, 21, 49, 63, 85},  // Genova
                {1, 24, 38, 55, 79},  // Milano
                {11, 29, 42, 68, 89}, // Napoli
                {9, 18, 36, 52, 70},  // Palermo
                {4, 16, 27, 61, 83},  // Roma
                {2, 30, 48, 67, 87},  // Torino
                {14, 25, 39, 58, 77}, // Venezia
                {10, 22, 45, 64, 86}  // Nazionale
        }));

        list.add(createSample(29, "Venerdì 07/03/2025", 2025, new int[][]{
                {8, 17, 34, 59, 82},  // Bari
                {13, 26, 41, 65, 87}, // Cagliari
                {5, 22, 37, 51, 74},  // Firenze
                {10, 28, 46, 69, 90}, // Genova
                {2, 14, 31, 58, 80},  // Milano
                {16, 33, 50, 72, 86}, // Napoli
                {6, 19, 38, 60, 84},  // Palermo
                {11, 25, 43, 67, 89}, // Roma
                {1, 20, 35, 53, 78},  // Torino
                {9, 23, 47, 62, 81},  // Venezia
                {7, 18, 29, 56, 75}   // Nazionale
        }));

        list.add(createSample(28, "Giovedì 06/03/2025", 2025, new int[][]{
                {18, 27, 45, 62, 89}, // Bari
                {4, 21, 39, 57, 76},  // Cagliari
                {9, 15, 33, 68, 84},  // Firenze
                {2, 29, 41, 54, 88},  // Genova
                {12, 30, 48, 70, 85}, // Milano
                {6, 23, 37, 61, 90},  // Napoli
                {14, 28, 43, 59, 77}, // Palermo
                {8, 19, 36, 64, 82},  // Roma
                {3, 25, 40, 52, 73},  // Torino
                {11, 31, 50, 66, 81}, // Venezia
                {5, 17, 35, 58, 79}   // Nazionale
        }));

        list.add(createSample(27, "Martedì 04/03/2025", 2025, new int[][]{
                {22, 35, 49, 73, 86}, // Bari
                {7, 18, 31, 54, 79},  // Cagliari
                {14, 28, 40, 62, 88}, // Firenze
                {3, 16, 37, 58, 81},  // Genova
                {9, 24, 43, 67, 90},  // Milano
                {1, 20, 36, 52, 75},  // Napoli
                {11, 27, 46, 60, 83}, // Palermo
                {5, 19, 33, 56, 78},  // Roma
                {13, 25, 41, 69, 87}, // Torino
                {8, 21, 38, 64, 82},  // Venezia
                {10, 30, 45, 61, 84}  // Nazionale
        }));

        list.add(createSample(26, "Sabato 01/03/2025", 2025, new int[][]{
                {6, 15, 32, 58, 80},  // Bari
                {11, 24, 42, 69, 87}, // Cagliari
                {3, 20, 37, 51, 76},  // Firenze
                {13, 29, 45, 63, 89}, // Genova
                {8, 17, 34, 60, 82},  // Milano
                {4, 22, 40, 57, 85},  // Napoli
                {10, 26, 48, 66, 90}, // Palermo
                {2, 19, 35, 53, 74},  // Roma
                {16, 31, 46, 71, 88}, // Torino
                {5, 23, 39, 55, 78},  // Venezia
                {9, 27, 44, 62, 81}   // Nazionale
        }));

        return list;
    }

    private static Extraction createSample(int concorso, String date, int year, int[][] ruoteMatrix) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        List<String> names = Extraction.ALL_RUOTE;
        for (int i = 0; i < names.size() && i < ruoteMatrix.length; i++) {
            List<Integer> list = new ArrayList<>();
            for (int n : ruoteMatrix[i]) {
                list.add(n);
            }
            map.put(names.get(i), list);
        }
        return new Extraction(concorso, date, year, map);
    }
}
