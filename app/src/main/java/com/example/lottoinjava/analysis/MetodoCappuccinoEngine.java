package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MetodoCappuccinoEngine {

    public static class CappuccinoResult {
        public String wheel;
        public int number1;
        public int number2;
        public int closureNumber;
        public int diametralNumber;
        public List<Integer> cinquinaCappuccino = new ArrayList<>();
        public String reportText;
    }

    /**
     * Calculates Metodo Cappuccino based on cyclometric closure and diametral harmonics
     */
    public static CappuccinoResult calcolaMetodoCappuccino(List<Extraction> archive, String targetWheel) {
        CappuccinoResult result = new CappuccinoResult();
        result.wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;

        if (archive == null || archive.isEmpty()) {
            result.reportText = "Archivio vuoto.";
            return result;
        }

        Extraction lastExt = archive.get(0);
        List<Integer> nums = lastExt.getNumbersForRuota(result.wheel);

        if (nums == null || nums.size() < 2) {
            result.reportText = "Numeri insufficienti nell'ultima estrazione.";
            return result;
        }

        int n1 = nums.get(0);
        int n2 = nums.get(1);

        int closure = (90 - (n1 + n2) % 90);
        if (closure <= 0) closure += 90;

        int diam = CyclometryAnalyzer.getDiametral(closure);
        int comp = CyclometryAnalyzer.getComplementary(closure);

        List<Integer> cinquina = new ArrayList<>();
        cinquina.add(n1);
        if (!cinquina.contains(n2)) cinquina.add(n2);
        if (!cinquina.contains(closure)) cinquina.add(closure);
        if (!cinquina.contains(diam)) cinquina.add(diam);
        if (!cinquina.contains(comp)) cinquina.add(comp);

        while (cinquina.size() < 5) {
            int extra = (cinquina.get(cinquina.size() - 1) + 15 - 1) % 90 + 1;
            if (!cinquina.contains(extra)) cinquina.add(extra);
        }

        Collections.sort(cinquina);
        result.number1 = n1;
        result.number2 = n2;
        result.closureNumber = closure;
        result.diametralNumber = diam;
        result.cinquinaCappuccino = cinquina;

        StringBuilder sb = new StringBuilder();
        sb.append("☕ METODO CAPPUCCINO CICLOMETRICO\n");
        sb.append("====================================================\n");
        sb.append(String.format(Locale.ITALIAN, "Ruota: %s | Estrazione del %s (Concorso N° %s)\n",
                result.wheel.toUpperCase(), lastExt.getDate(), lastExt.getConcorsoNumber()));
        sb.append(String.format(Locale.ITALIAN, "• Numeri di Base: %02d e %02d\n", n1, n2));
        sb.append(String.format(Locale.ITALIAN, "• Chiusura Cappuccino (90 - Somma): %02d\n", closure));
        sb.append(String.format(Locale.ITALIAN, "• Diametrale Cappuccino (+45): %02d\n\n", diam));

        sb.append("CINQUINA CAPPUCCINO CONSIGLIATA PER AMBO E TERNO:\n");
        sb.append(String.format(Locale.ITALIAN, "★ %02d - %02d - %02d - %02d - %02d\n",
                cinquina.get(0), cinquina.get(1), cinquina.get(2), cinquina.get(3), cinquina.get(4)));

        result.reportText = sb.toString();
        return result;
    }
}
