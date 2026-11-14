package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/** Verifica storica delle tecniche Numeretti e LAMU. */
final class NuoveTecnicheDialog {
    private NuoveTecnicheDialog() {}
    static void show(Stage owner, String css, List<Extraction> archive, Consumer<HighlightSpec> applyHighlight) {
        if (archive == null || archive.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Importa prima le estrazioni nell'archivio.").showAndWait();
            return;
        }
        List<Extraction> draws = new ArrayList<>(archive);
        draws.sort(Comparator.comparingInt(Extraction::getDateKey).thenComparingInt(Extraction::getConcorsoNumber));
        List<String> wheels = new ArrayList<>(Extraction.ALL_RUOTE);
        final String T_NUM = "Tecnica Numeretti", T_DEC = "Tecnica Decine (Numeretti inversa)",
                T_LAMU = "LAMU — Lettura Analitica delle Memorie d’Uscita", T_CIN = "LAMU Cinquina";
        ComboBox<String> technique = new ComboBox<>(FXCollections.observableArrayList(T_NUM, T_DEC, T_LAMU, T_CIN));
        technique.setValue(T_NUM);
        ComboBox<String> wheel = new ComboBox<>(FXCollections.observableArrayList(wheels));
        wheel.setValue(wheels.contains("Bari") ? "Bari" : wheels.get(0));
        CheckBox allWheels = new CheckBox("Verifica su tutte le ruote");
        Spinner<Integer> number = new Spinner<>(1, 9, 1);
        number.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9, 1));
        Spinner<Integer> limit = new Spinner<>(1, 10, 10);
        Spinner<Integer> recent = new Spinner<>(20, Math.max(20, draws.size()), Math.min(Math.max(20, draws.size()), 200));
        TextArea report = new TextArea();
        report.setEditable(false); report.setWrapText(false); report.setPrefRowCount(25);
        Label hint = new Label("Numeretti: spia 1→10–19, 2→20–29, …, 9→90.");
        technique.setOnAction(e -> {
            String t = technique.getValue();
            hint.setText(t.equals(T_NUM) ? "Numeretti: spia 1→10–19, 2→20–29, …, 9→90."
                    : t.equals(T_DEC) ? "Decine (inversa): spia 10→1–10, 20→11–20, …, 90→81–90 (decine naturali)."
                    : t.equals(T_CIN) ? "LAMU Cinquina: il numero indicato si abbina a 4 fissi scelti dall'analisi storica sulle ruote selezionate."
                    : "LAMU confronta frequenze, ritardi e intervalli tra le uscite nell'archivio selezionato.");
            int current = number.getValue() == null ? 1 : number.getValue();
            SpinnerValueFactory.IntegerSpinnerValueFactory f;
            if (t.equals(T_NUM)) f = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9, Math.min(9, Math.max(1, current)));
            else if (t.equals(T_DEC)) { int d = Math.max(1, Math.min(9, current >= 10 ? current / 10 : current)); f = new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 90, d * 10, 10); }
            else f = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 90, Math.min(90, Math.max(1, current)));
            number.setValueFactory(f);
        });
        Button run = new Button("▶ Analizza");
        Button save = new Button("💾 Salva report");
        Button highlight = new Button("🎨 Evidenzia nell'archivio (spia verde, risultato rosso, più usciti blu scuro)");
        highlight.setDisable(true);
        final HighlightSpec[] lastSpec = new HighlightSpec[1];
        run.setOnAction(e -> {
            try {
                String t = technique.getValue();
                int n = number.getValue(), horizon = limit.getValue();
                int countDraws = Math.min(recent.getValue(), draws.size());
                List<Extraction> sample = draws.subList(draws.size()-countDraws, draws.size());
                List<String> targets = allWheels.isSelected() ? wheels : List.of(wheel.getValue());
                StringBuilder out = new StringBuilder("LottoInJava — ").append(t).append("\nArchivio analizzato: ").append(sample.size()).append(" estrazioni\n");
                List<Integer> cinquina; List<Integer> results; List<Integer> spies = List.of(n); int colpi = horizon;
                if (t.equals(T_NUM) || t.equals(T_DEC)) {
                    int lo = t.equals(T_NUM) ? n * 10 : n - 9;
                    int hi = t.equals(T_NUM) ? Math.min(90, n * 10 + 9) : n;
                    results = new ArrayList<>(); for (int x = lo; x <= hi; x++) results.add(x);
                    out.append(t.equals(T_NUM) ? "Numeretto spia: " : "Decina spia: ").append(n).append(" | intervallo da giocare: ").append(lo).append("–").append(hi)
                       .append(" | finestra: ").append(horizon).append(" estrazioni successive\n\n");
                    int[] assoc = new int[91];
                    int tot=0, wO=0, wA=0, wAny=0;
                    StringBuilder sO = new StringBuilder("━━ RESOCONTO RUOTA DI ORIGINE (esito sulla stessa ruota della spia) ━━\n");
                    StringBuilder sA = new StringBuilder("━━ RESOCONTO ALTRE RUOTE (esito su una qualsiasi ruota diversa da quella della spia) ━━\n");
                    for (String target: targets) {
                        int opp=0, pO=0, pA=0, pAny=0;
                        for (int i=0;i<sample.size()-1;i++) {
                            List<Integer> nums=sample.get(i).getNumbersForRuota(target);
                            if (nums == null || !nums.contains(n)) continue;
                            opp++;
                            boolean hO=false, hA=false;
                            for(int j=i+1;j<Math.min(sample.size(),i+1+horizon);j++) {
                                for (String w : wheels) {
                                    List<Integer> next=sample.get(j).getNumbersForRuota(w);
                                    if (next==null) continue;
                                    for (int x : next) if (x>=lo && x<=hi) {
                                        assoc[x]++;
                                        if (w.equalsIgnoreCase(target)) hO=true; else hA=true;
                                    }
                                }
                            }
                            if(hO) pO++; if(hA) pA++; if(hO||hA) pAny++;
                        }
                        sO.append(line(target, opp, pO));
                        sA.append(line(target, opp, pA));
                        tot+=opp; wO+=pO; wA+=pA; wAny+=pAny;
                    }
                    out.append(sO).append(total(tot, wO)).append("\n");
                    out.append(sA).append(total(tot, wA)).append("\n");
                    out.append("Esito complessivo (origine o altre ruote): ").append(total(tot, wAny)).append("\n");
                    cinquina = topFive(assoc, results, -1);
                    out.append("🌌 CINQUINA DEI NUMERI PIÙ ASSOCIATI (blu notte): ").append(cinquina).append("\n   ").append(counts(assoc, cinquina)).append("\n\n");
                    out.append("Definizione: positivo se almeno un numero dell'intervallo esce entro la finestra scelta. I dati sono storici, non previsioni garantite.");
                } else {
                    // LAMU / LAMU Cinquina: associazione = numeri usciti nella stessa estrazione insieme a n
                    int[] assocO = new int[91], assocA = new int[91];
                    int presO = 0, presA = 0;
                    for (Extraction ex : sample) for (String target : targets) {
                        List<Integer> nums = ex.getNumbersForRuota(target);
                        if (nums == null || !nums.contains(n)) continue;
                        presO++;
                        for (int x : nums) if (x>=1 && x<=90 && x!=n) assocO[x]++;
                        for (String w : wheels) {
                            if (w.equalsIgnoreCase(target)) continue;
                            List<Integer> o = ex.getNumbersForRuota(w);
                            if (o == null) continue;
                            presA++;
                            for (int x : o) if (x>=1 && x<=90 && x!=n) assocA[x]++;
                        }
                    }
                    int[] assocAll = new int[91]; for (int x=1;x<=90;x++) assocAll[x]=assocO[x]+assocA[x];
                    if (t.equals(T_CIN)) {
                        int[] freq = new int[91];
                        for (Extraction ex : sample) for (String target : targets) {
                            List<Integer> nums = ex.getNumbersForRuota(target);
                            if (nums != null) for (int x : nums) if (x >= 1 && x <= 90 && x != n) freq[x]++;
                        }
                        List<Integer> combo = new ArrayList<>(); combo.add(n); combo.addAll(topFive(freq, null, n).subList(0, Math.min(4, topFive(freq, null, n).size())));
                        results = combo;
                        out.append("LAMU CINQUINA — numero generato + 4 fissi selezionati per frequenza storica\n");
                        out.append("Ruote analizzate: ").append(String.join(", ", targets)).append("\nCinquina: ").append(combo).append("\n\n");
                        int[] hO = new int[6], hA = new int[6]; int evO=0, evA=0;
                        for (Extraction ex : sample) for (String w : wheels) {
                            List<Integer> nums=ex.getNumbersForRuota(w);
                            if(nums==null) continue;
                            int d=0; for(int x:combo) if(nums.contains(x)) d++;
                            if (targets.stream().anyMatch(w::equalsIgnoreCase)) { hO[d]++; evO++; } else { hA[d]++; evA++; }
                        }
                        out.append("━━ RESOCONTO RUOTA/E DI ORIGINE ━━\n").append(sorti(hO)).append("Estrazioni esaminate: ").append(evO).append("\n\n");
                        out.append("━━ RESOCONTO ALTRE RUOTE ━━\n").append(sorti(hA)).append("Estrazioni esaminate: ").append(evA).append("\n\n");
                        colpi = 0; spies = List.of();
                    } else {
                        out.append("LAMU — confronto descrittivo di frequenze e intervalli tra uscite\n\n");
                        for(String target: targets) {
                            int[] freq=new int[91]; List<Integer> positions=new ArrayList<>();
                            for(int i=0;i<sample.size();i++){
                                List<Integer> nums=sample.get(i).getNumbersForRuota(target);
                                if(nums!=null) for(int x:nums) if(x>=1&&x<=90) freq[x]++;
                                if(nums!=null && nums.contains(n)) positions.add(i);
                            }
                            int max=Arrays.stream(freq,1,91).max().orElse(0);
                            int min=Arrays.stream(freq,1,91).min().orElse(0);
                            List<Integer> hot=new ArrayList<>(), cold=new ArrayList<>();
                            for(int x=1;x<=90;x++){if(freq[x]==max)hot.add(x);if(freq[x]==min)cold.add(x);}
                            int last=positions.isEmpty()?-1:positions.get(positions.size()-1);
                            int delay=last<0?sample.size():sample.size()-1-last;
                            List<Integer> gaps=new ArrayList<>();
                            for(int i=1;i<positions.size();i++)gaps.add(positions.get(i)-positions.get(i-1));
                            double avg=gaps.stream().mapToInt(Integer::intValue).average().orElse(0);
                            out.append("RUOTA ").append(target).append("\n");
                            out.append("Più frequenti (").append(max).append(" uscite): ").append(hot).append("\n");
                            out.append("Meno frequenti (").append(min).append(" uscite): ").append(cold).append("\n");
                            out.append("Numero ").append(n).append(": frequenza=").append(freq[n]).append(", ritardo corrente=").append(delay).append(" estrazioni, intervallo medio=").append(String.format(Locale.ITALIAN,"%.2f",avg)).append(" estrazioni\n\n");
                        }
                        results = new ArrayList<>();
                        colpi = 0;
                    }
                    List<Integer> cO = topFive(assocO, null, n), cA = topFive(assocA, null, n);
                    out.append("━━ ASSOCIAZIONI SULLA RUOTA DI ORIGINE (").append(presO).append(" presenze di ").append(n).append(") ━━\n")
                       .append("Più associati: ").append(cO).append("  ").append(counts(assocO, cO)).append("\n\n");
                    out.append("━━ ASSOCIAZIONI SULLE ALTRE RUOTE (stessa estrazione, ").append(presA).append(" ruote esaminate) ━━\n")
                       .append("Più associati: ").append(cA).append("  ").append(counts(assocA, cA)).append("\n\n");
                    cinquina = topFive(assocAll, null, n);
                    if (results.isEmpty()) results = new ArrayList<>(cinquina);
                    out.append("🌌 CINQUINA DEI NUMERI PIÙ ASSOCIATI A ").append(n).append(" (blu notte): ").append(cinquina).append("\n\n");
                    out.append("LAMU descrive frequenze, ritardi e associazioni osservati nell'archivio; non implica che un numero sia più probabile alla prossima estrazione.");
                }
                report.setText(out.toString());
                int yearFrom = sample.stream().mapToInt(Extraction::getYear).min().orElse(1900);
                int yearTo = sample.stream().mapToInt(Extraction::getYear).max().orElse(2100);
                List<Integer> spyList = spies;
                if (t.equals(T_LAMU)) spyList = List.of(n); // spia verde, risultati nella stessa estrazione
                lastSpec[0] = new HighlightSpec(t, spyList, targets, results, targets, colpi, yearFrom, yearTo).withCinquina(cinquina);
                highlight.setDisable(false);
            } catch(Exception ex) { report.setText("Errore nell'analisi: "+ex.getMessage()); highlight.setDisable(true); }
        });
        highlight.setOnAction(e -> {
            if (lastSpec[0] != null && applyHighlight != null) {
                applyHighlight.accept(lastSpec[0]);
                ((Stage) highlight.getScene().getWindow()).close();
            }
        });
        save.setOnAction(e -> {
            FileChooser fc=new FileChooser(); fc.setTitle("Salva report"); fc.setInitialFileName("report_nuove_tecniche.txt");
            var f=fc.showSaveDialog(owner); if(f!=null) try { java.nio.file.Files.writeString(f.toPath(),report.getText(),java.nio.charset.StandardCharsets.UTF_8); }
            catch(Exception ex){new Alert(Alert.AlertType.ERROR,"Salvataggio fallito: "+ex.getMessage()).showAndWait();}
        });
        HBox params=new HBox(10,new Label("Tecnica:"),technique,new Label("Ruota:"),wheel,allWheels);
        HBox params2=new HBox(10,new Label("Spia (Numeretti 1–9 / Decine 10–90 / LAMU 1–90):"),number,new Label("Colpi successivi:"),limit,new Label("Ultime estrazioni:"),recent);
        VBox root=new VBox(10,new Label("💜 Nuove tecniche — Notte Neon"),hint,params,params2,new HBox(8,run,highlight,save),report);
        root.setPadding(new Insets(16)); VBox.setVgrow(report,Priority.ALWAYS);
        Stage stage=new Stage(); stage.initOwner(owner); stage.initModality(Modality.APPLICATION_MODAL); stage.setTitle("Numeretti, Decine & LAMU");
        Scene scene=new Scene(root,900,680); if(css!=null&&!css.isBlank()) try{scene.getStylesheets().add(css);}catch(Exception ignored){}
        stage.setScene(scene); stage.show();
    }

    private static String line(String w, int opp, int pos) {
        int neg = opp - pos;
        return w + ": occasioni=" + opp + ", positivi=" + pos + " (" + pct(pos, opp) + "%), negativi=" + neg + " (" + pct(neg, opp) + "%)\n";
    }
    private static String total(int opp, int pos) {
        return "Totale occasioni: " + opp + " | positivi: " + pos + " | negativi: " + (opp - pos) + " | percentuale positiva: " + pct(pos, opp) + "%\n";
    }
    private static String pct(int a, int b) { return String.format(Locale.ITALIAN, "%.2f", b == 0 ? 0 : 100.0 * a / b); }
    private static String sorti(int[] h) {
        return "Ambo: " + (h[2]+h[3]+h[4]+h[5]) + " | Terno: " + (h[3]+h[4]+h[5]) + " | Quaterna: " + (h[4]+h[5]) + " | Cinquina: " + h[5] + "\n";
    }
    /** I 5 numeri con il conteggio più alto (tra 'pool' se indicato), escluso 'exclude'. */
    private static List<Integer> topFive(int[] c, List<Integer> pool, int exclude) {
        List<Integer> cand = new ArrayList<>();
        for (int x = 1; x <= 90; x++) if (x != exclude && (pool == null || pool.contains(x)) && c[x] > 0) cand.add(x);
        cand.sort((a, b) -> c[b] != c[a] ? Integer.compare(c[b], c[a]) : Integer.compare(a, b));
        return new ArrayList<>(cand.subList(0, Math.min(5, cand.size())));
    }
    private static String counts(int[] c, List<Integer> xs) {
        return xs.stream().map(x -> x + "=" + c[x] + "×").collect(Collectors.joining(", "));
    }
}
