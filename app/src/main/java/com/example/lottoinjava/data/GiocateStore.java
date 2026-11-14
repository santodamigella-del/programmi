package com.example.lottoinjava.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.lottoinjava.model.Giocata;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Salvataggio locale delle giocate (SharedPreferences, in JSON). */
public class GiocateStore {

    private static final String TAG = "GiocateStore";
    private static final String PREF_NAME = "lotto_giocate";
    private static final String KEY_LIST = "list";

    public static synchronized List<Giocata> load(Context context) {
        List<Giocata> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LIST, null);
        if (raw == null) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Giocata.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Errore lettura giocate", e);
        }
        return list;
    }

    public static synchronized void save(Context context, List<Giocata> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Giocata g : list) arr.put(g.toJson());
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Errore salvataggio giocate", e);
        }
    }

    public static synchronized void add(Context context, Giocata g) {
        List<Giocata> list = load(context);
        list.add(0, g); // le più recenti in alto
        save(context, list);
    }

    public static synchronized void remove(Context context, long id) {
        List<Giocata> list = load(context);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                list.remove(i);
                break;
            }
        }
        save(context, list);
    }
}
