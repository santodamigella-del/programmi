package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.OfficialArchiveParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aggiornamento del Lotto da PIÙ fonti web, con verifica incrociata.
 *
 * Formato dichiarato nel progetto (Extraction): numero concorso, data "dd/MM/yyyy",
 * 11 ruote nell'ordine Extraction.ALL_RUOTE, 5 numeri distinti 1-90 per ruota.
 * Nessun dato viene inventato: una estrazione è accettata solo se completa e valida.
 *
 * Fonti predefinite: archivio ufficiale (ZIP), LottoCED, Xamig, Lottologia, Numeri-Lotto.
 * Altre fonti si aggiungono senza ricompilare nel file "fonti_extra.txt" della cartella dati
 * (una riga per fonte: Nome|URL, con {anno} facoltativo; righe con # ignorate).
 */
public final class LottoSources {

    private LottoSources() { }

    // ------------------------------------------------------------------ risultato

    public static final class Outcome {
        public final List<Extraction> extractions;
        public final String report;
        public final int sourcesOk;
        public final int sourcesTotal;
        public final LocalDate latest;

        Outcome(List<Extraction> extractions, String report, int ok, int total, LocalDate latest) {
            this.extractions = extractions;
            this.report = report;
            this.sourcesOk = ok;
            this.sourcesTotal = total;
            this.latest = latest;
        }
    }

    @FunctionalInterface
    interface Fetcher { List<Extraction> fetch() throws Exception; }

    static final class Source {
        final String name;
        final int priority; // più basso = più affidabile
        final Fetcher fetcher;
        Source(String name, int priority, Fetcher fetcher) {
            this.name = name; this.priority = priority; this.fetcher = fetcher;
        }
    }

    static final class Result {
        Source src;
        List<Extraction> data = new ArrayList<>();
        String error;
        long ms;
    }

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    // ------------------------------------------------------------------ API

    /** Interroga tutte le fonti in parallelo e restituisce le estrazioni consolidate + il rapporto. */
    public static Outcome downloadLatest() throws Exception {
        LocalDate today = LocalDate.now();
        List<Source> sources = buildSources(today);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, sources.size()), r -> {
            Thread t = new Thread(r, "lotto-source");
            t.setDaemon(true);
            return t;
        });
        List<Future<Result>> futures = new ArrayList<>();
        for (Source s : sources) {
            futures.add(pool.submit(() -> {
                Result r = new Result();
                r.src = s;
                long t0 = System.currentTimeMillis();
                try {
                    r.data = s.fetcher.fetch();
                } catch (Throwable ex) {
                    String m = ex.getMessage();
                    r.error = ex.getClass().getSimpleName() + (m == null ? "" : ": " + m);
                }
                r.ms = System.currentTimeMillis() - t0;
                return r;
            }));
        }
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Result r;
            try {
                r = futures.get(i).get(120, TimeUnit.SECONDS);
            } catch (Exception ex) {
                futures.get(i).cancel(true);
                r = new Result();
                r.src = sources.get(i);
                r.error = "nessuna risposta entro 120 secondi";
            }
            results.add(r);
        }
        pool.shutdownNow();
        return consolidate(results, today);
    }

    // ------------------------------------------------------------------ elenco fonti

    private static List<Source> buildSources(LocalDate today) {
        final int year = today.getYear();
        // a inizio anno controlla anche l'anno precedente
        final int[] years = today.getMonthValue() <= 2 ? new int[]{year, year - 1} : new int[]{year};

        List<Source> list = new ArrayList<>();

        list.add(new Source("Ufficiale (Brightstar ZIP)", 1, () -> {
            byte[] zip = getBytes(OfficialArchiveParser.URL_2001_OGGI);
            OfficialArchiveParser.Parsed p = OfficialArchiveParser.parseZip(zip);
            if (p.extractions.isEmpty()) throw new Exception("ZIP scaricato ma formato non riconosciuto: " + p.preview);
            return p.extractions;
        }));

        list.add(new Source("LottoCED", 2, () -> {
            List<Extraction> all = new ArrayList<>();
            for (int y : years)
                all.addAll(pageParse("LottoCED_" + y, "https://www.lottoced.com/lotto/archivio-estrazioni-del-lotto/?anno=" + y));
            return all;
        }));

        list.add(new Source("Xamig", 3, () -> {
            List<Extraction> all = new ArrayList<>();
            for (int y : years) {
                final int yy = y;
                all.addAll(withFallback(() -> OnlineArchiveUpdater.fetchXamig(yy),
                        "Xamig_" + y, "https://www.xamig.com/lotto/" + y + "/estrazioni.php?page=1"));
            }
            return all;
        }));

        list.add(new Source("Lottologia", 4, () -> withFallback(() -> OnlineArchiveUpdater.fetchLottologia(year),
                "Lottologia", "https://lottologia.com/lotto/estrazioni/")));

        list.add(new Source("Lottologia TXT", 5, () -> {
            List<Extraction> all = new ArrayList<>();
            for (int y : years)
                all.addAll(pageParse("LottologiaTXT_" + y, "https://www.lottologia.com/lotto/archivio-estrazioni/?as=TXT&year=" + y));
            return all;
        }));

        list.add(new Source("Numeri-Lotto", 6, () -> {
            List<Extraction> all = new ArrayList<>();
            for (int y : years)
                all.addAll(pageParse("NumeriLotto_" + y, "https://numeri-lotto.com/estrazioni/lotto-archivio-" + y + "/"));
            try { all.addAll(parseAny(getString("https://numeri-lotto.com/estrazioni-lotto-oggi/"))); }
            catch (Exception ignored) { }
            return all;
        }));

        list.add(new Source("QuiFinanza (mese)", 7, () -> pageParse("QuiFinanza",
                "https://quifinanza.it/info-utili/estrazioni-e-lotterie/estrazioni-del-lotto-di-questo-mese-tutti-i-numeri-vincenti/594894/")));

        // fonti aggiunte dall'utente
        int prio = 20;
        for (String[] extra : loadExtraSources()) {
            final String name = extra[0], url = extra[1];
            list.add(new Source(name, prio++, () -> {
                List<Extraction> all = new ArrayList<>();
                if (url.contains("{anno}")) {
                    for (int y : years) all.addAll(pageParse(name + "_" + y, url.replace("{anno}", String.valueOf(y))));
                } else {
                    all.addAll(pageParse(name, url));
                }
                return all;
            }));
        }
        return list;
    }

    private static List<String[]> loadExtraSources() {
        List<String[]> out = new ArrayList<>();
        try {
            Path f = DesktopStorage.dataDir().resolve("fonti_extra.txt");
            if (!Files.exists(f)) return out;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int bar = line.indexOf('|');
                if (bar <= 0) continue;
                String name = line.substring(0, bar).trim();
                String url = line.substring(bar + 1).trim();
                if (!name.isEmpty() && url.startsWith("http")) out.add(new String[]{name, url});
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ------------------------------------------------------------------ consolidamento

    private static final class Vote {
        final Result res;
        final Extraction ex;
        Vote(Result res, Extraction ex) { this.res = res; this.ex = ex; }
    }

    static Outcome consolidate(List<Result> results, LocalDate today) throws Exception {
        final int windowStart = (today.getYear() - 1) * 10000 + 101; // da gennaio dell'anno scorso
        final DateTimeFormatterHolder fmt = new DateTimeFormatterHolder();

        // dateKey -> firma numeri -> voti
        Map<Integer, Map<String, List<Vote>>> byDate = new TreeMap<>();
        int ok = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("FONTI CONSULTATE (").append(results.size()).append(")\n");

        for (Result r : results) {
            if (r.error != null) {
                sb.append("✖ ").append(r.src.name).append(" — ").append(r.error).append('\n');
                continue;
            }
            int valid = 0;
            int latestKey = 0;
            Set<Integer> seen = new HashSet<>();
            for (Extraction e : r.data) {
                int key = e.getDateKey();
                if (key < windowStart || !isValid(e)) continue;
                if (!seen.add(key)) continue; // una sola volta per fonte e data
                valid++;
                if (key > latestKey) latestKey = key;
                byDate.computeIfAbsent(key, k -> new LinkedHashMap<>())
                      .computeIfAbsent(signature(e), k -> new ArrayList<>())
                      .add(new Vote(r, e));
            }
            if (valid == 0) {
                sb.append("✖ ").append(r.src.name)
                  .append(" — pagina raggiunta ma nessuna estrazione valida riconosciuta (struttura cambiata?)\n");
                continue;
            }
            ok++;
            sb.append("✔ ").append(r.src.name).append(" — ").append(valid)
              .append(" estrazioni, ultima ").append(fmt.key(latestKey))
              .append(" (").append(String.format(Locale.ROOT, "%.1f", r.ms / 1000.0)).append(" s)\n");
        }

        if (byDate.isEmpty()) {
            throw new Exception("Aggiornamento Lotto fallito: nessuna fonte ha restituito estrazioni valide.\n\n" + sb);
        }

        // voto di maggioranza per ogni data
        List<Extraction> chosen = new ArrayList<>();
        int multi = 0, single = 0, conflicts = 0;
        List<String> conflictLines = new ArrayList<>();
        Map<Integer, Integer> votesPerDate = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, List<Vote>>> d : byDate.entrySet()) {
            Map<String, List<Vote>> variants = d.getValue();
            String best = null;
            int bestVotes = -1, bestPrio = Integer.MAX_VALUE;
            for (Map.Entry<String, List<Vote>> v : variants.entrySet()) {
                int n = v.getValue().size();
                int prio = Integer.MAX_VALUE;
                for (Vote x : v.getValue()) prio = Math.min(prio, x.res.src.priority);
                if (n > bestVotes || (n == bestVotes && prio < bestPrio)) {
                    best = v.getKey(); bestVotes = n; bestPrio = prio;
                }
            }
            List<Vote> winners = variants.get(best);
            votesPerDate.put(d.getKey(), winners.size());
            if (winners.size() >= 2) multi++; else single++;
            if (variants.size() > 1) {
                conflicts++;
                if (conflictLines.size() < 8) {
                    StringBuilder c = new StringBuilder("  • ").append(fmt.key(d.getKey())).append(": ");
                    boolean first = true;
                    for (Map.Entry<String, List<Vote>> v : variants.entrySet()) {
                        if (!first) c.append("  ≠  ");
                        first = false;
                        c.append(names(v.getValue()));
                        if (v.getKey().equals(best)) c.append(" [adottata]");
                    }
                    conflictLines.add(c.toString());
                }
            }
            // concorso reale se una fonte concorde lo fornisce, altrimenti aaaammgg
            Extraction base = winners.get(0).ex;
            int concorso = base.getConcorsoNumber();
            for (Vote w : winners) {
                int c = w.ex.getConcorsoNumber();
                if (c > 0 && c < 10_000_000) { concorso = c; break; }
            }
            chosen.add(new Extraction(concorso, base.getDate(), base.getYear(), base.getWheels()));
        }
        chosen.sort((a, b) -> Integer.compare(b.getDateKey(), a.getDateKey()));

        int newestKey = chosen.get(0).getDateKey();
        sb.append("\nVERIFICA INCROCIATA\n");
        sb.append("Ultima estrazione trovata: ").append(fmt.key(newestKey))
          .append(" — confermata da ").append(votesPerDate.get(newestKey)).append(" fonte/i su ").append(ok).append(" attive.\n");

        // ultime 3 date
        sb.append("Ultime estrazioni: ");
        for (int i = 0; i < Math.min(3, chosen.size()); i++) {
            int k = chosen.get(i).getDateKey();
            if (i > 0) sb.append(" · ");
            sb.append(fmt.key(k)).append(" (").append(votesPerDate.get(k)).append("/").append(ok).append(")");
        }
        sb.append('\n');

        // fonti in ritardo
        List<String> late = new ArrayList<>();
        for (Result r : results) {
            if (r.error != null) continue;
            Integer rLatest = null;
            for (Extraction e : r.data) {
                int key = e.getDateKey();
                if (key >= windowStart && isValid(e) && (rLatest == null || key > rLatest)) rLatest = key;
            }
            if (rLatest != null && rLatest < newestKey)
                late.add(r.src.name + " (ultima " + fmt.key(rLatest) + ")");
        }
        if (!late.isEmpty()) sb.append("Fonti in ritardo: ").append(String.join(", ", late)).append('\n');

        sb.append("Date confermate da almeno 2 fonti: ").append(multi)
          .append(" · da una sola fonte: ").append(single)
          .append(" · con numeri discordanti: ").append(conflicts).append('\n');
        if (conflicts > 0) {
            sb.append("In caso di discordanza vince la versione con più fonti concordi (a parità, la fonte più affidabile):\n");
            for (String c : conflictLines) sb.append(c).append('\n');
        }
        if (ok == 1) {
            sb.append("\nATTENZIONE: è stata raggiunta una sola fonte, quindi i dati non sono stati verificati.\n");
        }

        LocalDate latest = LocalDate.of(newestKey / 10000, (newestKey / 100) % 100, newestKey % 100);
        return new Outcome(chosen, sb.toString().trim(), ok, results.size(), latest);
    }

    private static String names(List<Vote> votes) {
        List<String> n = new ArrayList<>();
        for (Vote v : votes) n.add(v.res.src.name);
        return String.join("+", n);
    }

    /** Formatta aaaammgg come dd/MM/yyyy. */
    private static final class DateTimeFormatterHolder {
        String key(int k) {
            return String.format(Locale.ROOT, "%02d/%02d/%04d", k % 100, (k / 100) % 100, k / 10000);
        }
    }

    private static String signature(Extraction e) {
        StringBuilder s = new StringBuilder();
        for (String ruota : Extraction.ALL_RUOTE) {
            List<Integer> n = e.getNumbersForRuota(ruota);
            s.append(ruota.charAt(0)).append(ruota.charAt(1)).append(n).append(';');
        }
        return s.toString();
    }

    // ------------------------------------------------------------------ validazione (formato del progetto)

    static boolean isValid(Extraction e) {
        if (e == null || e.getWheels() == null) return false;
        int key = e.getDateKey();
        if (key < 19390101) return false;
        LocalDate d;
        try { d = LocalDate.of(key / 10000, (key / 100) % 100, key % 100); }
        catch (Exception ex) { return false; }
        if (d.isAfter(LocalDate.now().plusDays(1))) return false;
        int need = d.getYear() >= 2010 ? 11 : 10;
        int count = 0;
        for (String ruota : Extraction.ALL_RUOTE) {
            List<Integer> n = e.getWheels().get(ruota);
            if (n == null) continue;
            if (n.size() != 5 || new HashSet<>(n).size() != 5) return false;
            for (int x : n) if (x < 1 || x > 90) return false;
            count++;
        }
        return count >= need;
    }

    private static Extraction build(LocalDate d, Map<String, List<Integer>> wheels, int concorso) {
        if (d == null) return null;
        Map<String, List<Integer>> ordered = new LinkedHashMap<>();
        for (String ruota : Extraction.ALL_RUOTE) {
            if (wheels.containsKey(ruota)) ordered.put(ruota, wheels.get(ruota));
        }
        int c = concorso > 0 ? concorso : d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        Extraction e = new Extraction(c, d.format(OUT), d.getYear(), ordered);
        return isValid(e) ? e : null;
    }


    // ------------------------------------------------------------------ conversione + diagnostica

    /** Scarica la pagina, la converte nel formato del progetto; se non riconosce nulla spiega perché e salva una copia. */
    static List<Extraction> pageParse(String label, String url) throws Exception {
        String html = getString(url);
        List<Extraction> r = parseAny(html);
        if (r.isEmpty()) throw new Exception(diagnose(label, html));
        return r;
    }

    /** Prima il lettore dedicato; se fallisce o non trova nulla, il convertitore generico sulla pagina. */
    static List<Extraction> withFallback(Fetcher primary, String label, String fallbackUrl) throws Exception {
        String first;
        try {
            List<Extraction> r = primary.fetch();
            if (!r.isEmpty()) return r;
            first = "lettore dedicato: nessun dato";
        } catch (Exception ex) {
            first = "lettore dedicato: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " " + ex.getMessage());
        }
        try {
            return pageParse(label, fallbackUrl);
        } catch (Exception ex) {
            throw new Exception(first + " | convertitore generico: " + ex.getMessage());
        }
    }

    static String diagnose(String label, String html) {
        int tables = 0;
        Matcher m = Pattern.compile("(?i)<table\\b").matcher(html);
        while (m.find()) tables++;
        String hint;
        if (html.length() < 5000) hint = "pagina quasi vuota (blocco anti-bot o contenuto caricato da JavaScript)";
        else if (tables == 0 && !Pattern.compile("(?i)\\b(bari|cagliari|napoli)\\b").matcher(html).find())
            hint = "i numeri non sono nell'HTML (probabilmente caricati da JavaScript)";
        else hint = "dati presenti ma con struttura non riconosciuta (" + tables + " tabelle)";
        String where = "";
        try {
            Path dir = DesktopStorage.dataDir().resolve("diagnostica");
            Files.createDirectories(dir);
            Path f = dir.resolve(label.replaceAll("[^A-Za-z0-9_.-]", "_") + ".html");
            Files.write(f, html.getBytes(StandardCharsets.UTF_8));
            where = "; copia della pagina salvata in " + f;
        } catch (Exception ignored) { }
        return "nessuna estrazione riconosciuta: " + hint + ", " + (html.length() / 1024) + " KB" + where;
    }

    // ------------------------------------------------------------------ rete

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) LottoInJava/1.2")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.5")
                .GET().build();
    }

    static String getString(String url) throws Exception {
        HttpResponse<byte[]> r = CLIENT.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() < 200 || r.statusCode() >= 300) throw new Exception("HTTP " + r.statusCode() + " da " + url);
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    static byte[] getBytes(String url) throws Exception {
        HttpResponse<byte[]> r = CLIENT.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() < 200 || r.statusCode() >= 300) throw new Exception("HTTP " + r.statusCode() + " da " + url);
        return r.body();
    }

    // ------------------------------------------------------------------ parser HTML (solo JDK)

    /** Prova i tre layout noti e unisce i risultati per data. */
    static List<Extraction> parseAny(String html) {
        Map<Integer, Extraction> out = new LinkedHashMap<>();
        for (List<Extraction> part : Arrays.asList(parseVertical(html), parseHorizontal(html), parseTransposed(html), parseText(html), parseLines(html))) {
            for (Extraction e : part) out.putIfAbsent(e.getDateKey(), e);
        }
        return new ArrayList<>(out.values());
    }

    private static final String[] MESI = {"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
            "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"};
    private static final Pattern D_LONG = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*(?:°|º)?\\s+(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern D_SLASH = Pattern.compile("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})(?!\\d)");
    private static final Pattern D_YMD = Pattern.compile("(?<!\\d)(\\d{4})[/.](\\d{1,2})[/.](\\d{1,2})(?!\\d)");
    private static final Pattern D_ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern NUM = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)");
    private static final Pattern WHEEL = Pattern.compile(
            "^\\s*(bari|cagliari|firenze|genova|milano|napoli|palermo|roma|torino|venezia|nazionale)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String[] SIGLE = {"BA", "CA", "FI", "GE", "MI", "NA", "PA", "RO", "TO", "VE", "NZ"};
    private static final Pattern SIGLA = Pattern.compile("^\\s*(BA|CA|FI|GE|MI|NA|PA|RO|TO|VE|NZ)(?=[\\s:;.,|\\-–]|$)");

    private static String sigla2Name(String sg) {
        for (int i = 0; i < SIGLE.length; i++) if (SIGLE[i].equals(sg)) return Extraction.ALL_RUOTE.get(i);
        return null;
    }

    /** {nome ruota, resto della riga} se la riga inizia con nome o sigla di una ruota; altrimenti null. */
    private static String[] splitWheel(String line) {
        Matcher m = WHEEL.matcher(line);
        if (m.find()) {
            String w = m.group(1).toLowerCase(Locale.ROOT);
            return new String[]{Character.toUpperCase(w.charAt(0)) + w.substring(1), line.substring(m.end())};
        }
        m = SIGLA.matcher(line);
        if (m.find()) return new String[]{sigla2Name(m.group(1)), line.substring(m.end())};
        return null;
    }

    static LocalDate findDate(String s) {
        if (s == null) return null;
        try {
            Matcher m = D_LONG.matcher(s);
            if (m.find()) {
                String mese = m.group(2).toLowerCase(Locale.ITALY);
                int mi = Arrays.asList(MESI).indexOf(mese) + 1;
                if (mi > 0) return LocalDate.of(Integer.parseInt(m.group(3)), mi, Integer.parseInt(m.group(1)));
            }
            m = D_SLASH.matcher(s);
            if (m.find()) return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            m = D_ISO.matcher(s);
            if (m.find()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            m = D_YMD.matcher(s);
            if (m.find()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception ignored) { }
        return null;
    }

    private static String wheelName(String cell) {
        if (cell == null) return null;
        String[] sp = splitWheel(cell);
        return sp == null ? null : sp[0];
    }

    /** Esattamente 5 numeri distinti 1-90 nel testo (si ignora quanto segue una parentesi, es. "(oro 48)"). */
    private static List<Integer> fiveNumbers(String text) {
        int par = text.indexOf('(');
        if (par >= 0) text = text.substring(0, par);
        List<Integer> nums = new ArrayList<>();
        Matcher m = NUM.matcher(text);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n < 1 || n > 90 || nums.contains(n)) return null;
            nums.add(n);
        }
        return nums.size() == 5 ? nums : null;
    }

    // --- utilità HTML

    private static String stripNoise(String html) {
        return html.replaceAll("(?is)<script\\b.*?</script>", " ")
                   .replaceAll("(?is)<style\\b.*?</style>", " ")
                   .replaceAll("(?s)<!--.*?-->", " ");
    }

    private static String unescape(String s) {
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&deg;", "°");
        Matcher m = Pattern.compile("&#(\\d+);").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep;
            try { rep = String.valueOf((char) Integer.parseInt(m.group(1))); } catch (Exception ex) { rep = " "; }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString().replace('\u00a0', ' ');
    }

    private static String plain(String fragment) {
        return unescape(fragment.replaceAll("(?s)<[^>]*>", " ")).replaceAll("\\s+", " ").trim();
    }

    private static final Pattern TABLE = Pattern.compile("(?is)<table\\b.*?</table>");
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b.*?</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<t[hd]\\b[^>]*>(.*?)</t[hd]>");

    private static List<List<List<String>>> tables(String html) {
        List<List<List<String>>> out = new ArrayList<>();
        String clean = stripNoise(html);
        Matcher tm = TABLE.matcher(clean);
        while (tm.find()) {
            List<List<String>> rows = new ArrayList<>();
            Matcher rm = ROW.matcher(tm.group());
            while (rm.find()) {
                List<String> cells = new ArrayList<>();
                Matcher cm = CELL.matcher(rm.group());
                while (cm.find()) cells.add(plain(cm.group(1)));
                if (!cells.isEmpty()) rows.add(cells);
            }
            if (!rows.isEmpty()) {
                // La data può stare nel titolo subito prima della tabella: la aggiungo come ULTIMA riga,
                // così una data presente dentro la tabella ha sempre la precedenza.
                String ctx = plain(clean.substring(Math.max(0, tm.start() - 600), tm.start()));
                if (ctx.length() > 200) ctx = ctx.substring(ctx.length() - 200);
                if (findDate(ctx) != null) rows.add(new ArrayList<>(List.of("§ctx " + ctx)));
                out.add(rows);
            }
        }
        return out;
    }

    /**
     * Layout "a blocchi" (LottoCED): ogni tabella = una estrazione; intestazione con la data,
     * poi una riga per ruota (nome + 5 numeri).
     */
    static List<Extraction> parseVertical(String html) {
        List<Extraction> out = new ArrayList<>();
        for (List<List<String>> rows : tables(html)) {
            LocalDate date = null;
            Map<String, List<Integer>> wheels = new LinkedHashMap<>();
            for (List<String> row : rows) {
                String w = wheelName(row.get(0));
                if (w == null) {
                    if (date == null) date = findDate(String.join(" ", row));
                    continue;
                }
                List<Integer> nums = fiveNumbers(String.join(" ", row.subList(1, row.size())));
                if (nums != null) wheels.put(w, nums);
            }
            Extraction e = build(date, wheels, 0);
            if (e != null) out.add(e);
        }
        return out;
    }

    /** Layout "a righe" (Xamig/Lottologia): una riga = [concorso] + data + 11 ruote (55 numeri). */
    static List<Extraction> parseHorizontal(String html) {
        List<Extraction> out = new ArrayList<>();
        for (List<List<String>> rows : tables(html)) {
            for (List<String> row : rows) {
                if (row.size() < 3) continue;
                int di = -1;
                LocalDate date = null;
                for (int i = 0; i < Math.min(3, row.size()); i++) {
                    date = findDate(row.get(i));
                    if (date != null) { di = i; break; }
                }
                if (date == null) continue;
                int concorso = 0;
                if (di > 0 && row.get(0).matches("\\d{1,6}")) concorso = Integer.parseInt(row.get(0));
                List<Integer> all = new ArrayList<>();
                Matcher m = NUM.matcher(String.join(" ", row.subList(di + 1, row.size())));
                while (m.find()) all.add(Integer.parseInt(m.group(1)));
                if (all.size() != 55) continue;
                Map<String, List<Integer>> wheels = new LinkedHashMap<>();
                boolean okRow = true;
                for (int i = 0; i < 11 && okRow; i++) {
                    List<Integer> five = new ArrayList<>(all.subList(i * 5, i * 5 + 5));
                    if (new HashSet<>(five).size() != 5) okRow = false;
                    wheels.put(Extraction.ALL_RUOTE.get(i), five);
                }
                if (!okRow) continue;
                Extraction e = build(date, wheels, concorso);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    /**
     * Ripiego sul testo: cerca una data e poi righe "RUOTA n n n n n". Una estrazione è emessa
     * solo quando le 11 ruote sono complete; se compare un'altra data a metà, il blocco incompleto è scartato.
     */
    private static String toText(String html) {
        String t = stripNoise(html)
                .replaceAll("(?i)</(tr|p|div|li|h[1-6]|table|section|article|ul|ol)>|<br\\s*/?>", "\n")
                .replaceAll("(?i)</t[dh]>", " ");
        return unescape(t.replaceAll("(?s)<[^>]*>", " "));
    }

    /** Righe singole "data [concorso] BA n n n n n CA ... " oppure "data + 55 numeri" (file TXT/CSV). */
    static List<Extraction> parseLines(String html) {
        List<Extraction> out = new ArrayList<>();
        for (String raw : toText(html).split("\\r?\\n")) {
            LocalDate d = findDate(raw);
            if (d == null) continue;
            String rest = D_LONG.matcher(raw).replaceAll(" ");
            rest = D_SLASH.matcher(rest).replaceAll(" ");
            rest = D_YMD.matcher(rest).replaceAll(" ");
            rest = D_ISO.matcher(rest).replaceAll(" ");
            String[] tok = rest.trim().split("[^A-Za-z0-9]+");
            Map<String, List<Integer>> wheels = new LinkedHashMap<>();
            for (int i = 0; i < tok.length; i++) {
                String name = sigla2Name(tok[i]);
                if (name == null) continue;
                List<Integer> five = new ArrayList<>();
                for (int j = i + 1; j < tok.length && five.size() < 5 && tok[j].matches("\\d{1,2}"); j++)
                    five.add(Integer.parseInt(tok[j]));
                if (five.size() == 5 && new HashSet<>(five).size() == 5) wheels.put(name, five);
            }
            if (wheels.size() < 10) { // nessuna sigla: data + 55 numeri
                wheels.clear();
                List<Integer> all = new ArrayList<>();
                for (String t : tok) if (t.matches("\\d{1,2}")) all.add(Integer.parseInt(t));
                if (all.size() == 56) all.remove(0); // numero di concorso a una o due cifre
                if (all.size() == 55) {
                    for (int i = 0; i < 11; i++) {
                        List<Integer> five = new ArrayList<>(all.subList(i * 5, i * 5 + 5));
                        if (new HashSet<>(five).size() == 5) wheels.put(Extraction.ALL_RUOTE.get(i), five);
                    }
                }
            }
            Extraction e = build(d, wheels, 0);
            if (e != null) out.add(e);
        }
        return out;
    }

    /** Tabella "ruote in colonna": intestazione con i nomi delle ruote e 5 righe (una per posizione estratta). */
    static List<Extraction> parseTransposed(String html) {
        List<Extraction> out = new ArrayList<>();
        for (List<List<String>> rows : tables(html)) {
            int head = -1;
            for (int i = 0; i < rows.size(); i++) {
                int n = 0;
                for (String c : rows.get(i)) if (wheelName(c) != null) n++;
                if (n >= 10) { head = i; break; }
            }
            if (head < 0) continue;
            List<String> header = rows.get(head);
            LocalDate date = null;
            for (List<String> r : rows) { date = findDate(String.join(" ", r)); if (date != null) break; }
            Map<String, List<Integer>> wheels = new LinkedHashMap<>();
            for (int i = head + 1; i < rows.size(); i++) {
                List<String> r = rows.get(i);
                int off = r.size() - header.size(); // eventuale cella iniziale con "1° estratto"
                for (int c = 0; c < header.size(); c++) {
                    String w = wheelName(header.get(c));
                    int idx = c + Math.max(0, off);
                    if (w == null || idx >= r.size() || !r.get(idx).matches("\\d{1,2}")) continue;
                    wheels.computeIfAbsent(w, k -> new ArrayList<>()).add(Integer.parseInt(r.get(idx)));
                }
            }
            wheels.values().removeIf(l -> l.size() != 5);
            Extraction e = build(date, wheels, 0);
            if (e != null) out.add(e);
        }
        return out;
    }

    static List<Extraction> parseText(String html) {
        String t = toText(html);
        List<Extraction> out = new ArrayList<>();
        LocalDate current = null;
        Map<String, List<Integer>> wheels = new LinkedHashMap<>();
        for (String raw : t.split("\\r?\\n")) {
            String line = raw.replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) continue;
            String[] sp = splitWheel(line);
            if (sp != null) {
                if (current == null) continue;
                String w = sp[0];
                List<Integer> nums = fiveNumbers(sp[1]);
                if (nums != null) wheels.put(w, nums);
                if (wheels.size() == 11) {
                    Extraction e = build(current, wheels, 0);
                    if (e != null) out.add(e);
                    wheels = new LinkedHashMap<>();
                    current = null;
                }
                continue;
            }
            LocalDate d = findDate(line);
            if (d != null) {
                if (!d.equals(current)) wheels = new LinkedHashMap<>();
                current = d;
            }
        }
        return out;
    }
}
