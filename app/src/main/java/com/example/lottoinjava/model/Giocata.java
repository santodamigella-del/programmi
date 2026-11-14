package com.example.lottoinjava.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Una giocata salvata dall'utente (ruota, numeri, tipo, colpi). */
public class Giocata {

    /** "Tutte" = le 10 ruote classiche (Nazionale esclusa, come al Lotto). */
    public static final String TUTTE = "Tutte";
    public static final String[] TIPI = {"", "Estratto", "Ambo", "Terno", "Quaterna", "Cinquina"};

    public final long id;
    public final String ruota;
    public final List<Integer> numeri;
    public final int hitsNeeded; // 1=Estratto ... 5=Cinquina
    public final int colpi;      // numero di estrazioni in cui la giocata resta valida
    public final int fromKey;    // aaaammgg: si controllano le estrazioni SUCCESSIVE a questa data
    public boolean notified;     // vincita già segnalata all'utente

    public Giocata(long id, String ruota, List<Integer> numeri, int hitsNeeded, int colpi, int fromKey, boolean notified) {
        this.id = id;
        this.ruota = ruota;
        this.numeri = numeri;
        this.hitsNeeded = hitsNeeded;
        this.colpi = colpi;
        this.fromKey = fromKey;
        this.notified = notified;
    }

    public String getTipoName() {
        return TIPI[Math.max(1, Math.min(5, hitsNeeded))];
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("ruota", ruota);
        JSONArray arr = new JSONArray();
        for (int n : numeri) arr.put(n);
        o.put("numeri", arr);
        o.put("hits", hitsNeeded);
        o.put("colpi", colpi);
        o.put("from", fromKey);
        o.put("notified", notified);
        return o;
    }

    public static Giocata fromJson(JSONObject o) throws JSONException {
        List<Integer> nums = new ArrayList<>();
        JSONArray arr = o.getJSONArray("numeri");
        for (int i = 0; i < arr.length(); i++) nums.add(arr.getInt(i));
        return new Giocata(
                o.getLong("id"),
                o.optString("ruota", TUTTE),
                nums,
                o.optInt("hits", 1),
                o.optInt("colpi", 9),
                o.optInt("from", 0),
                o.optBoolean("notified", false));
    }
}
