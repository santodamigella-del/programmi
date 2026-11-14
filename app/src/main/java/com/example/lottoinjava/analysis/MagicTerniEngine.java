package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MagicTerniEngine {

    public static class MagicTerno {
        public List<Integer> numbers; // 3 numbers
        public double magicScore;
        public int frequenza;
        public int cicli;
        public double lift;
        public double stabilita;

        public MagicTerno(List<Integer> numbers, double magicScore, int frequenza, int cicli, double lift, double stabilita) {
            this.numbers = numbers;
            this.magicScore = magicScore;
            this.frequenza = frequenza;
            this.cicli = cicli;
            this.lift = lift;
            this.stabilita = stabilita;
        }

        public String getFormatted() {
            return String.format(Locale.ITALIAN, "Terno (%02d - %02d - %02d) | Magic Score: %.1f | Co-presenze: %d | Lift: %.2fx",
                    numbers.get(0), numbers.get(1), numbers.get(2), magicScore, frequenza, lift);
        }
    }

    public static List<MagicTerno> generateMagicTerni(List<Extraction> archive, int spyNumber, String targetWheel, int colpi, int quantita) {
        List<MagicTerno> out = new ArrayList<>();
        if (archive == null || archive.isEmpty()) return out;
        String wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;
        int y0 = 9999, y1 = 0;
        for (Extraction e : archive) { y0 = Math.min(y0, e.getYear()); y1 = Math.max(y1, e.getYear()); }
        // Calcolo allineato al main.py (genera_terni_magici_lotto): vedi SantoSpiaCore
        SantoSpiaCore.Modello m = SantoSpiaCore.costruisciModello(archive, spyNumber, wheel, y0, y1, colpi);
        if (m.nEventi() == 0) return out;
        for (SantoSpiaCore.TernoMagico t : SantoSpiaCore.generaTerniMagici(m, quantita, 18, 2)) {
            List<Integer> nums = new ArrayList<>();
            for (int n : t.numeri) nums.add(n);
            out.add(new MagicTerno(nums, t.score, t.frequenza, t.cicli, t.lift, t.stabilita));
        }
        return out;
    }
}
