package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConvergenzeEngine {

    public static class ConvergenceItem {
        public int number;
        public int moduleCount;
        public List<String> moduleNames = new ArrayList<>();

        public String getFormatted() {
            return String.format(Locale.ITALIAN, "Numero %02d | Convergenza in %d Moduli (%s)",
                    number, moduleCount, String.join(" + ", moduleNames));
        }
    }

    public static class ConvergenzeResult {
        public int spyNumber;
        public String targetWheel;
        public List<ConvergenceItem> topConvergentNumbers = new ArrayList<>();
        public String fullReportText;
    }

    public static ConvergenzeResult runUnifiedConvergence(List<Extraction> archive, int spyNumber, String targetWheel, int colpi) {
        ConvergenzeResult result = new ConvergenzeResult();
        result.spyNumber = spyNumber;
        result.targetWheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;
        if (archive == null || archive.isEmpty()) {
            result.fullReportText = "Archivio vuoto.";
            return result;
        }
        int y0 = 9999, y1 = 0;
        for (Extraction e : archive) { y0 = Math.min(y0, e.getYear()); y1 = Math.max(y1, e.getYear()); }
        // Calcolo allineato al main.py (costruisci_convergenze_lotto): vedi SantoSpiaCore
        SantoSpiaCore.Convergenze c;
        try {
            c = SantoSpiaCore.convergenze(archive, spyNumber, result.targetWheel, y0, y1, colpi);
        } catch (IllegalArgumentException e) {
            result.fullReportText = e.getMessage();
            return result;
        }
        StringBuilder sb = new StringBuilder("# CONVERGENZE TECNICHE - LOTTO\n");
        sb.append("Spia: ").append(String.format(Locale.ITALIAN, "%02d", spyNumber)).append(" - Ruota: ").append(result.targetWheel)
                .append(" - Periodo ").append(y0).append("-").append(y1).append(" - ").append(colpi).append(" colpi\n");
        sb.append("Moduli confrontati: POST-SPIA, CICLOMETRIA, TERNI MAGICI\n\n");
        sb.append("NUMERI IN CONVERGENZA (presenti in 2 o più moduli)\n");
        if (c.topConvergenza.isEmpty()) sb.append("Nessuna convergenza tra 2 o più moduli in questo periodo.\n");
        for (int n : c.topConvergenza) {
            ConvergenceItem item = new ConvergenceItem();
            item.number = n;
            item.moduleCount = c.conteggio.get(n);
            item.moduleNames.addAll(c.dettaglio.get(n));
            result.topConvergentNumbers.add(item);
            sb.append(item.getFormatted()).append("\n");
        }
        sb.append("\nNota: la convergenza segnala solo l'accordo tra moduli già calcolati.\nNon è una previsione e non modifica la probabilità matematica reale.");
        result.fullReportText = sb.toString();
        return result;
    }

    private static void addModuleNumbers(Map<Integer, List<String>> map, List<Integer> nums, String moduleName) {
        if (nums == null) return;
        for (int n : nums) {
            if (!map.containsKey(n)) {
                map.put(n, new ArrayList<>());
            }
            map.get(n).add(moduleName);
        }
    }

    private static List<Integer> getTopKeys(Map<Integer, Double> map, int limit, int exclude) {
        List<Integer> list = new ArrayList<>(map.keySet());
        list.remove(Integer.valueOf(exclude));
        list.sort((a, b) -> Double.compare(map.getOrDefault(b, 0.0), map.getOrDefault(a, 0.0)));
        return new ArrayList<>(list.subList(0, Math.min(limit, list.size())));
    }
}
