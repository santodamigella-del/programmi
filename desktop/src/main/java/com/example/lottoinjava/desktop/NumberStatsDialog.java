package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Grafici frequenze calde/fredde calcolati dall'archivio locale selezionato. */
final class NumberStatsDialog {
    private static final List<String> WINDOWS = List.of("Ultime 50", "Ultime 100", "Ultime 300", "Ultime 500", "Tutte");
    private NumberStatsDialog() { }

    static void showLotto(Stage owner, List<Extraction> archive) {
        List<String> scopes = new ArrayList<>();
        scopes.add("Tutte le ruote");
        scopes.addAll(Extraction.ALL_RUOTE);
        show(owner, "Lotto · Numeri caldi e freddi", scopes, scope -> {
            List<Extraction> ordered = new ArrayList<>(archive);
            ordered.sort(Comparator.comparingInt(Extraction::getDateKey));
            List<int[]> result = new ArrayList<>();
            for (Extraction e : ordered) {
                if ("Tutte le ruote".equals(scope)) {
                    List<Integer> all = new ArrayList<>();
                    for (String wheel : Extraction.ALL_RUOTE) all.addAll(e.getNumbersForRuota(wheel));
                    result.add(all.stream().mapToInt(Integer::intValue).toArray());
                } else {
                    result.add(e.getNumbersForRuota(scope).stream().mapToInt(Integer::intValue).toArray());
                }
            }
            return result;
        }, 90, "Ruota / aggregazione");
    }

    static void showSuper(Stage owner, List<int[]> archive) {
        List<int[]> ordered = new ArrayList<>(archive);
        show(owner, "SuperEnalotto · Numeri caldi e freddi", List.of("SuperEnalotto"), ignored -> ordered, 90, "Archivio");
    }

    private static void show(Stage owner, String title, List<String> scopes,
                             Function<String, List<int[]>> dataProvider, int maxNumber, String scopeLabel) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(title);

        ComboBox<String> scope = new ComboBox<>(FXCollections.observableArrayList(scopes));
        scope.setValue(scopes.get(0));
        ComboBox<String> window = new ComboBox<>(FXCollections.observableArrayList(WINDOWS));
        window.setValue("Ultime 300");
        Label summary = new Label();
        summary.setWrapText(true);
        TabPane charts = new TabPane();
        Tab hotTab = new Tab("🔥 Numeri caldi", new VBox()); hotTab.setClosable(false);
        Tab coldTab = new Tab("❄ Numeri freddi", new VBox()); coldTab.setClosable(false);
        charts.getTabs().addAll(hotTab, coldTab);
        HBox controls = new HBox(10, new Label(scopeLabel + ":"), scope, new Label("Finestra:"), window);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(8));
        VBox top = new VBox(6, new Label("FREQUENZE STORICHE · CALDI E FREDDI"), controls, summary);
        top.setPadding(new Insets(12));
        BorderPane root = new BorderPane(charts);
        root.setTop(top);
        root.setPadding(new Insets(8));

        Runnable refresh = () -> {
            List<int[]> data = dataProvider.apply(scope.getValue());
            int totalDraws = data.size();
            int requested = windowSize(window.getValue(), totalDraws);
            int from = Math.max(0, totalDraws - requested);
            int used = totalDraws - from;
            int[] freq = new int[maxNumber + 1];
            for (int i = from; i < totalDraws; i++) {
                for (int n : data.get(i)) if (n >= 1 && n <= maxNumber) freq[n]++;
            }
            List<Integer> numbers = new ArrayList<>();
            for (int n = 1; n <= maxNumber; n++) numbers.add(n);
            List<Integer> hot = new ArrayList<>(numbers);
            hot.sort(Comparator.<Integer>comparingInt(n -> -freq[n]).thenComparingInt(n -> n));
            List<Integer> cold = new ArrayList<>(numbers);
            cold.sort(Comparator.<Integer>comparingInt(n -> freq[n]).thenComparingInt(n -> n));
            hot = new ArrayList<>(hot.subList(0, Math.min(10, hot.size())));
            cold = new ArrayList<>(cold.subList(0, Math.min(10, cold.size())));
            summary.setText("Estrazioni analizzate: " + used + " su " + totalDraws
                    + " · numeri/estrazione: " + ("Lotto · Numeri caldi e freddi".equals(title) ? ("Tutte le ruote".equals(scope.getValue()) ? "fino a 55" : "5") : "6")
                    + "\nCaldi = frequenza più alta nella finestra; freddi = frequenza più bassa. A parità di frequenza, ordine numerico crescente.");
            ((VBox) hotTab.getContent()).getChildren().setAll(makeChart("TOP 10 · PIÙ FREQUENTI", hot, freq, "Frequenze"));
            ((VBox) coldTab.getContent()).getChildren().setAll(makeChart("TOP 10 · MENO FREQUENTI", cold, freq, "Frequenze"));
        };
        scope.setOnAction(e -> refresh.run());
        window.setOnAction(e -> refresh.run());
        refresh.run();
        Scene scene = new Scene(root, 980, 650);
        scene.getStylesheets().add(ThemeManager.cssUrl());
        stage.setScene(scene);
        stage.show();
    }

    private static int windowSize(String selected, int total) {
        if (selected == null || selected.equals("Tutte")) return total;
        try { return Math.min(total, Integer.parseInt(selected.replace("Ultime ", ""))); }
        catch (Exception ignored) { return Math.min(total, 300); }
    }

    private static BarChart<String, Number> makeChart(String title, List<Integer> values, int[] freq, String yLabel) {
        CategoryAxis x = new CategoryAxis(); x.setLabel("Numero");
        NumberAxis y = new NumberAxis(); y.setLabel(yLabel); y.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(title); chart.setLegendVisible(false); chart.setAnimated(false);
        chart.setCategoryGap(8); chart.setBarGap(2); chart.setMaxHeight(Double.MAX_VALUE);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int n : values) series.getData().add(new XYChart.Data<>(String.format(Locale.ROOT, "%02d", n), freq[n]));
        chart.getData().add(series);
        return chart;
    }
}
