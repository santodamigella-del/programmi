package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Download reale delle tabelle pubbliche delle estrazioni. Nessun dato sintetico viene creato. */
public final class OnlineArchiveUpdater {
    private static final String[] WHEELS = {
        "Bari", "Cagliari", "Firenze", "Genova", "Milano",
        "Napoli", "Palermo", "Roma", "Torino", "Venezia", "Nazionale"
    };
    private static final Pattern NUMBERS = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d/M/uuuu");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private OnlineArchiveUpdater() {}

    /**
     * Aggiornamento su più fonti (archivio ufficiale, LottoCED, Xamig, Lottologia, Numeri-Lotto
     * ed eventuali fonti extra in fonti_extra.txt) con verifica incrociata.
     * Il rapporto dettagliato è in {@link LottoSources.Outcome#report}.
     */
    public static LottoSources.Outcome downloadLatestWithReport() throws Exception {
        return LottoSources.downloadLatest();
    }

    public static List<Extraction> downloadLatest() throws Exception {
        return downloadLatestWithReport().extractions;
    }

    /** Scarica le pagine annuali disponibili e costruisce un archivio nuovo senza toccare quello locale. */
    public static List<Extraction> downloadFullArchive() throws Exception {
        int currentYear = LocalDate.now().getYear();
        Map<String, Extraction> all = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int successfulYears = 0;
        for (int year = 1939; year <= currentYear; year++) {
            int beforeYear = all.size();
            boolean yearHadData = false;
            for (int page = 1; page <= 40; page++) {
                String url = "https://www.xamig.com/lotto/" + year + "/estrazioni.php?page=" + page;
                try {
                    Document doc = get(url);
                    int beforePage = all.size();
                    parseTable(doc, all, true);
                    if (all.size() > beforePage) yearHadData = true;
                    else if (page > 1 || !yearHadData) break;
                } catch (Exception ex) {
                    if (page == 1) errors.add(year + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    break;
                }
            }
            if (yearHadData && all.size() > beforeYear) successfulYears++;
        }
        // Integrazione: Lottologia + archivio ufficiale (2001-oggi) + LottoCED dell'anno corrente.
        // Da qui la chiave è la data (come nell'archivio): una estrazione già presente viene sostituita, non duplicata.
        Map<Integer, Extraction> byDate = new LinkedHashMap<>();
        for (Extraction e : all.values()) byDate.put(e.getDateKey(), e);
        try {
            for (Extraction e : fetchLottologia(currentYear)) byDate.put(e.getDateKey(), e);
        } catch (Exception ex) {
            errors.add("Lottologia (integrazione): " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        try {
            byte[] zip = LottoSources.getBytes(com.example.lottoinjava.model.OfficialArchiveParser.URL_2001_OGGI);
            for (Extraction e : com.example.lottoinjava.model.OfficialArchiveParser.parseZip(zip).extractions) byDate.put(e.getDateKey(), e);
        } catch (Exception ex) {
            errors.add("Ufficiale ZIP (integrazione): " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        try {
            for (Extraction e : LottoSources.parseAny(LottoSources.getString(
                    "https://www.lottoced.com/lotto/archivio-estrazioni-del-lotto/?anno=" + currentYear))) byDate.put(e.getDateKey(), e);
        } catch (Exception ex) {
            errors.add("LottoCED (integrazione): " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        if (byDate.isEmpty()) {
            throw new Exception("Ricostruzione annullata: nessuna estrazione valida trovata. L'archivio locale non è stato modificato.\nDiagnostica: " + String.join("; ", errors));
        }
        List<Extraction> result = new ArrayList<>(byDate.values());
        result.sort((a,b) -> Integer.compare(b.getDateKey(), a.getDateKey()));
        return result;
    }

    static List<Extraction> fetchXamig(int year) throws Exception {
        Map<String, Extraction> unique = new LinkedHashMap<>();
        for (int page = 1; page <= 5; page++) {
            String url = "https://www.xamig.com/lotto/" + year + "/estrazioni.php?page=" + page;
            Document doc = get(url);
            int before = unique.size();
            parseTable(doc, unique, true);
            // Se la pagina non contiene righe di estrazioni, non continuare a richiedere pagine.
            if (unique.size() == before && page > 1) break;
        }
        return new ArrayList<>(unique.values());
    }

    static List<Extraction> fetchLottologia(int year) throws Exception {
        Document doc = get("https://lottologia.com/lotto/estrazioni/");
        Map<String, Extraction> unique = new LinkedHashMap<>();
        parseTable(doc, unique, false);
        List<Extraction> result = new ArrayList<>();
        for (Extraction e : unique.values()) if (e.getYear() == year) result.add(e);
        return result;
    }

    private static Document get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) LottoInJava/1.1")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
                .GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new Exception("HTTP " + response.statusCode() + " da " + url);
        return Jsoup.parse(response.body(), url);
    }

    private static void parseTable(Document doc, Map<String, Extraction> out, boolean xamig) {
        for (Element row : doc.select("table tr")) {
            Elements cells = row.select("th, td");
            // Lottologia: data + 11 ruote = 12 celle. Xamig: concorso + data + 11 ruote = 13.
            if (cells.size() < 12) continue;
            String first = cells.get(0).text().trim();
            LocalDate date = parseDate(first);
            int startWheel = 1;
            int concorso;
            if (date != null) {
                // Alcune viste di Lottologia espongono data + 11 ruote senza il numero concorso.
                concorso = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
            } else {
                date = parseDate(cells.get(1).text().trim());
                if (date == null) continue;
                try { concorso = Integer.parseInt(first.replaceAll("[^0-9]", "")); }
                catch (Exception ex) { continue; }
                startWheel = 2;
            }
            if (cells.size() < startWheel + 11) continue;
            Map<String, List<Integer>> wheels = new LinkedHashMap<>();
            boolean valid = true;
            for (int i = 0; i < 11; i++) {
                List<Integer> nums = extractNumbers(cells.get(i + startWheel).text());
                if (nums.size() != 5) { valid = false; break; }
                wheels.put(WHEELS[i], nums);
            }
            if (!valid) continue;
            String key = date.toString() + "-" + concorso;
            out.put(key, new Extraction(concorso, date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), date.getYear(), wheels));
        }
    }

    private static LocalDate parseDate(String text) {
        Matcher m = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})").matcher(text);
        if (!m.find()) return null;
        try { return LocalDate.parse(m.group(1), DATE); } catch (Exception ex) { return null; }
    }

    private static List<Integer> extractNumbers(String text) {
        List<Integer> nums = new ArrayList<>();
        Matcher m = NUMBERS.matcher(text.replace('\u00a0', ' '));
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n < 1 || n > 90 || nums.contains(n)) return List.of();
            nums.add(n);
        }
        return nums;
    }
}
