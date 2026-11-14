package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Archivio navigabile Lotto con ricerca dei numeri sulle altre ruote. */
public final class ArchiveBrowserDialog {
    private ArchiveBrowserDialog() {}

    public static void show(Stage owner, String css, List<Extraction> source) { show(owner, css, source, null); }

    public static void show(Stage owner, String css, List<Extraction> source, HighlightSpec prediction) {
        if (source == null || source.isEmpty()) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, "L'archivio Lotto è vuoto. Importa prima le estrazioni.");
            a.initOwner(owner); a.showAndWait(); return;
        }
        List<Extraction> archive = new ArrayList<>(source);
        archive.sort(Comparator.comparingInt(Extraction::getDateKey));
        Stage stage = new Stage(); stage.initOwner(owner); stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Archivio Lotto — previsioni colorate e navigazione");

        Label date = new Label(); date.getStyleClass().add("title");
        Label contest = new Label(); contest.getStyleClass().add("subtitle");
        ComboBox<String> wheel = new ComboBox<>(); wheel.getItems().addAll(Extraction.ALL_RUOTE); wheel.setValue("Bari");
        VBox draw = new VBox(9); draw.setPadding(new Insets(14));
        Label resultsTitle = new Label("Verifica previsioni e ricorrenze"); resultsTitle.getStyleClass().add("subtitle");
        Label predictionHint = new Label(prediction == null ? "Nessuna previsione attiva: apri Tecniche e calcola una previsione per colorarne i numeri qui." : "Previsione attiva: " + prediction.title + " — verde = spia; rosso = numeri selezionati dalla previsione su tutte le ruote nelle date previste.");
        predictionHint.setWrapText(true);
        VBox results = new VBox(6);
        ScrollPane resultScroll = new ScrollPane(results); resultScroll.setFitToWidth(true); resultScroll.setPrefHeight(220);
        final int[] index = {archive.size() - 1};
        final Set<String> sourceHighlights = new HashSet<>();
        final Set<String> recurrenceHighlights = new HashSet<>();

        Runnable render = () -> {
            Extraction e = archive.get(index[0]); date.setText(e.getLongDate());
            contest.setText("Estrazione " + (e.getConcorsoNumber() > 0 ? "n. " + e.getConcorsoNumber() : "") + "  ·  " + (index[0] + 1) + " di " + archive.size());
            draw.getChildren().clear(); draw.getChildren().addAll(date, contest);
            for (String r : Extraction.ALL_RUOTE) {
                List<Integer> nums = e.getNumbersForRuota(r);
                if (nums == null || nums.isEmpty()) continue;
                Label name = new Label(r.toUpperCase()); name.setMinWidth(95); name.getStyleClass().add("wheel-name");
                HBox row = new HBox(7); row.setAlignment(Pos.CENTER_LEFT); row.getChildren().add(name);
                for (int n : nums) {
                    Label ball = new Label(String.format("%02d", n));
                    String key = index[0] + "|" + r.toLowerCase() + "|" + n;
                    ball.getStyleClass().add("ball");
                    if (prediction != null && prediction.isSpyBall(r, n, e.getDateKey())) ball.getStyleClass().add("ball-spy");
                    else if (prediction != null && prediction.isCinquinaBall(r, n, e.getDateKey())) ball.getStyleClass().add("ball-cinquina");
                    else if (prediction != null && prediction.isResultBall(r, n, e.getDateKey())) ball.getStyleClass().add("ball-result");
                    else if (sourceHighlights.contains(key)) ball.getStyleClass().add("ball-spy");
                    else if (recurrenceHighlights.contains(key)) ball.getStyleClass().add("ball-result");
                    ball.setMinSize(36,36); ball.setPrefSize(36,36); ball.setMaxSize(36,36); row.getChildren().add(ball);
                }
                draw.getChildren().add(row);
            }
        };
        Button prev = new Button("← Precedente"); Button next = new Button("Successiva →");
        prev.setOnAction(ev -> { if (index[0] > 0) index[0]--; render.run(); });
        next.setOnAction(ev -> { if (index[0] < archive.size()-1) index[0]++; render.run(); });
        Button search = new Button("🔎 Cerca questi numeri sulle altre ruote"); search.getStyleClass().add("button-primary");
        search.setOnAction(ev -> {
            Extraction selected = archive.get(index[0]); String origin = wheel.getValue();
            List<Integer> nums = selected.getNumbersForRuota(origin);
            results.getChildren().clear();
            sourceHighlights.clear(); recurrenceHighlights.clear();
            if (nums == null || nums.isEmpty()) { results.getChildren().add(new Label("Nessun numero disponibile sulla ruota " + origin + " in questa estrazione.")); return; }
            for (int n : nums) sourceHighlights.add(index[0] + "|" + origin.toLowerCase() + "|" + n);
            Label scope = new Label("Numeri di " + origin + " del " + selected.getShortDate() + ": " + nums + ". Verde = numeri di partenza; rosso = ricorrenze sulle altre ruote nelle estrazioni successive."); scope.setWrapText(true); results.getChildren().add(scope);
            int matches = 0;
            for (int i = index[0] + 1; i < archive.size(); i++) {
                Extraction future = archive.get(i);
                for (String other : Extraction.ALL_RUOTE) {
                    if (other.equalsIgnoreCase(origin)) continue;
                    List<Integer> otherNums = future.getNumbersForRuota(other);
                    if (otherNums == null || otherNums.isEmpty()) continue;
                    TreeSet<Integer> hit = new TreeSet<>(otherNums); hit.retainAll(nums);
                    if (!hit.isEmpty()) {
                        for (int n : hit) recurrenceHighlights.add(i + "|" + other.toLowerCase() + "|" + n);
                        Label line = new Label(future.getShortDate() + "  ·  " + other + "  ·  usciti: " + hit);
                        line.setWrapText(true); results.getChildren().add(line); matches++;
                    }
                }
            }
            if (matches == 0) results.getChildren().add(new Label("Nessuna ricorrenza trovata nelle estrazioni successive presenti in archivio."));
            else results.getChildren().add(1, new Label("Trovate " + matches + " corrispondenze ruota/data."));
            render.run();
        });
        HBox nav = new HBox(8, prev, next); nav.setAlignment(Pos.CENTER);
        HBox searchRow = new HBox(8, new Label("Ruota di partenza:"), wheel, search); searchRow.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(search, Priority.ALWAYS);
        VBox content = new VBox(12, nav, predictionHint, draw, searchRow, resultsTitle, resultScroll); content.setPadding(new Insets(14));
        BorderPane root = new BorderPane(content);
        Scene scene = new Scene(root, 780, 760); if (css != null && !css.isBlank()) scene.getStylesheets().add(css);
        stage.setScene(scene); render.run(); stage.show();
    }
}
