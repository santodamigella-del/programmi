package com.example.lottoinjava.desktop;

import com.example.lottoinjava.analysis.ConvergenzeEngine;
import com.example.lottoinjava.analysis.FibonacciLottoEngine;
import com.example.lottoinjava.analysis.GiocataChecker;
import com.example.lottoinjava.analysis.IsotopiaEngine;
import com.example.lottoinjava.analysis.MetodoCappuccinoEngine;
import com.example.lottoinjava.analysis.MetodoDartagnanEngine;
import com.example.lottoinjava.analysis.MultistaticoEngine;
import com.example.lottoinjava.analysis.RitardatariEngine;
import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.Giocata;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class DesktopApp extends Application {

    private Stage stage;
    private List<Extraction> all = new ArrayList<>();

    private ListView<Extraction> listView;
    private TextField searchField;
    private ComboBox<String> ruotaBox;
    private Label countLabel;
    // Evidenziazione attiva nell'archivio: spia VERDE, risultato ROSSO (vedi HighlightSpec).
    private HighlightSpec highlight = null;
    private CheckBox onlyHighlighted;

    // ------------------------------------------------------------------ avvio

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        all = DesktopStorage.loadArchive();

        Label title = new Label("Archivio Estrazioni del Lotto");
        title.getStyleClass().add("title");
        countLabel = new Label();
        countLabel.getStyleClass().add("subtitle");
        VBox header = new VBox(2, title, countLabel);

        Button btnUpdate = button("🌐 Aggiorna online", true, this::updateOnline);
        Button btnRebuild = button("♻ Ricostruisci archivio Lotto", false, this::rebuildOnlineArchive);
        Button btnSuper = button("🎱 SuperEnalotto", false, () -> SuperEnalottoDialog.show(stage, css()));
        Button btnGuide = button("📖 Guida completa", false, this::openCompleteGuide);
        Button btnAssist = button("💜 Assistente", true, () -> NeonAssistant.show(stage, "Ciao! Sono la tua assistente Notte Neon. Quando un calcolo termina correttamente, ti mostrerò un messaggio con i risultati.", css()));
        Button btnChat = button("💬 Chat con assistente", true, () -> ChatAssistantDialog.show(stage, css(), all));
        Button btnBrowse = button("📚 Sfoglia archivio", false, () -> ArchiveBrowserDialog.show(stage, css(), all, highlight));
        Button btnStats = button("📊 Caldi/Freddi Lotto", false, () -> NumberStatsDialog.showLotto(stage, all));
        Button btnLab = button("🧪 Laboratorio tecniche", true, () -> LabDialog.show(stage, all));
        Button btnQuantum = button("⚛ Laboratorio Quantistico", true, () -> QuantumLabDialog.show(stage, all));
        Button btnImport = button("📂 Importa CSV", false, this::importCsv);
        Button btnRit = button("⏳ Ritardatari", false, this::showRitardatari);
        Button btnGiocate = button("🎯 Le mie giocate", false, this::showGiocate);
        Button btnTec = button("🔮 Tecniche", false, this::showTecniche);
        Button btnNewTech = button("💜 Numeretti & LAMU", true, () -> NuoveTecnicheDialog.show(stage, css(), all, this::applyHighlight));
        Button btnGemini = button("🤖 Gemini", false, () -> GeminiDialog.show(host()));
        Button btnSmorfia = button("💭 Smorfia", false, () -> SmorfiaDialog.show(host()));
        Button btnQuadro = button("🧮 Quadro estrazionale", false, () -> QuadroEstrazionaleDialog.show(stage, css(), all, highlight));
        Button btnProntuario = button("💰 Prontuario vincite", false, () -> ProntuarioDialog.show(stage, css(), all));
        Button btnTema = button("", false, null);
        Runnable aggiornaTema = () -> btnTema.setText("🎨 Tema: " + ThemeManager.corrente().icona + " " + ThemeManager.corrente().nome);
        aggiornaTema.run();
        btnTema.setOnAction(e -> {
            ThemeManager.cambia();
            aggiornaTema.run();
            // Il tema vale subito per la finestra principale e per tutte le finestre aperte dopo.
            stage.getScene().getStylesheets().setAll(css());
        });
        Button btnClearHighlight = button("✖ Togli evidenziazione", false, () -> {
            highlight = null;
            onlyHighlighted.setSelected(false);
            refresh();
        });
        onlyHighlighted = new CheckBox("Solo date evidenziate");
        onlyHighlighted.setOnAction(e -> refresh());
        ComboBox<String> bluBox = new ComboBox<>(FXCollections.observableArrayList(
                "Blu: auto", "Blu: 1", "Blu: 2", "Blu: 3", "Blu: 4", "Blu: 5"));
        bluBox.setValue(DesktopStorage.bluCount() == 0 ? "Blu: auto" : "Blu: " + DesktopStorage.bluCount());
        bluBox.setOnAction(e -> {
            String v = bluBox.getValue();
            int n = (v == null || v.endsWith("auto")) ? 0 : Integer.parseInt(v.substring(v.length() - 1));
            DesktopStorage.setBluCount(n);
            if (highlight != null) { highlight.prepare(all); refresh(); }
        });
        CheckBox autoUpdate = new CheckBox("Aggiorna all'avvio");
        autoUpdate.setSelected(DesktopStorage.autoUpdateAtStart());
        autoUpdate.setOnAction(e -> DesktopStorage.setAutoUpdateAtStart(autoUpdate.isSelected()));
        FlowPane toolbar = new FlowPane(6, 6, btnUpdate, btnRebuild, btnImport, btnRit, btnGiocate, btnTec, btnNewTech, btnGemini, btnSmorfia, btnClearHighlight, onlyHighlighted, bluBox, autoUpdate);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPrefWrapLength(960);
        FlowPane toolbar2 = new FlowPane(6, 6, btnSuper, btnBrowse, btnStats, btnLab, btnQuantum, btnGuide, btnAssist, btnChat, btnQuadro, btnProntuario, btnTema);
        toolbar2.setAlignment(Pos.CENTER_LEFT);
        toolbar2.setPrefWrapLength(960);

        searchField = new TextField();
        searchField.setPromptText("Cerca per data, concorso o numero (es. 90, BARI, 08/03/2025)…");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, o, n) -> refresh());

        List<String> ruoteOpts = new ArrayList<>();
        ruoteOpts.add("Tutte le ruote");
        ruoteOpts.addAll(Extraction.ALL_RUOTE);
        ruotaBox = new ComboBox<>(FXCollections.observableArrayList(ruoteOpts));
        ruotaBox.setValue("Tutte le ruote");
        ruotaBox.setOnAction(e -> refresh());
        HBox searchRow = new HBox(8, searchField, ruotaBox);

        VBox top = new VBox(7, header, toolbar, toolbar2, searchRow);
        top.getStyleClass().add("app-header");
        top.setPadding(new Insets(10, 12, 7, 12));

        listView = new ListView<>();
        listView.setCellFactory(lv -> new ListCell<Extraction>() {
            @Override
            protected void updateItem(Extraction item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    setOnMouseClicked(null);
                } else {
                    setGraphic(buildCard(item));
                    setOnMouseClicked(ev -> showDetail(item));
                }
            }
        });
        BorderPane.setMargin(listView, new Insets(0, 12, 12, 12));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(listView);

        Scene scene = new Scene(root, 1000, 780);
        scene.getStylesheets().add(css());
        stage.setTitle("Archivio Lotto");
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/lottoinjava.png")));
        } catch (Exception ignored) { }
        stage.setScene(scene);
        stage.show();

        refresh();
        if (all.isEmpty()) {
            info("Archivio vuoto",
                    "Non ci sono ancora estrazioni.\n\nPremi \"Importa CSV\" e scegli il tuo file storico delle estrazioni "
                            + "(oppure copia il file lotto_archive.json dell'app Android in:\n"
                            + DesktopStorage.dataDir() + ").");
        }
        checkGiocateWins();
        if (DesktopStorage.autoUpdateAtStart() && !all.isEmpty()) {
            Platform.runLater(() -> updateOnline(true));
        }
    }

    // ------------------------------------------------------------------ elenco

    private void refresh() {
        String q = searchField.getText() == null ? "" : searchField.getText();
        String ruota = selectedRuota();
        List<Extraction> filtered = new ArrayList<>();
        for (Extraction e : all) {
            if (!e.matchesSearch(q)) continue;
            if (highlight != null && onlyHighlighted != null && onlyHighlighted.isSelected()
                    && !highlight.isVisibleDate(e.getDateKey())) continue;
            if (ruota != null) {
                List<Integer> nums = e.getNumbersForRuota(ruota);
                if (nums == null || nums.isEmpty()) continue;
            }
            filtered.add(e);
        }
        listView.getItems().setAll(filtered);
        countLabel.setText("Estrazioni in archivio: " + all.size()
                + (filtered.size() != all.size() ? "  ·  mostrate: " + filtered.size() : "")
                + (highlight != null ? "\n🎨 " + highlight.summary() : ""));
    }

    private String selectedRuota() {
        String v = ruotaBox.getValue();
        return (v == null || v.startsWith("Tutte")) ? null : v;
    }

    private Node buildCard(Extraction ext) {
        Label date = new Label(ext.getShortDate().toUpperCase(Locale.ITALY));
        date.getStyleClass().add("card-date");

        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.getChildren().add(date);

        String filter = selectedRuota();
        List<String> ruote = new ArrayList<>();
        if (filter != null) {
            ruote.add(filter);
        } else if (highlight != null) {
            ruote.addAll(highlight.wheelsToShow());
        } else {
            ruote.addAll(Arrays.asList("Bari", "Milano", "Roma", "Nazionale"));
        }
        for (String ruota : ruote) {
            List<Integer> nums = ext.getNumbersForRuota(ruota);
            if (nums != null && nums.size() >= 5) {
                card.getChildren().add(wheelRow(ruota, nums, ext));
            }
        }
        Label hint = new Label(filter != null
                ? "Clic per vedere tutte le ruote del " + ext.getShortDate()
                : "Clic per vedere tutte le 11 ruote");
        hint.getStyleClass().add("hint");
        card.getChildren().add(hint);
        return card;
    }

    private HBox wheelRow(String ruota, List<Integer> nums, Extraction extraction) {
        Label name = new Label(ruota.toUpperCase());
        name.setMinWidth(96);
        name.getStyleClass().add("wheel-name");
        if ("Nazionale".equalsIgnoreCase(ruota)) name.getStyleClass().add("wheel-nazionale");
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(name);
        for (int n : nums) {
            Label b = ball(n, false);
            if (extraction != null && highlight != null) {
                int key = extraction.getDateKey();
                if (highlight.isSpyBall(ruota, n, key)) {
                    b.getStyleClass().add("ball-spy");       // spia: verde
                } else if (highlight.isCinquinaBall(ruota, n, key)) {
                    b.getStyleClass().add("ball-cinquina");  // cinquina più associata: blu notte
                } else if (highlight.isResultBall(ruota, n, key)) {
                    b.getStyleClass().add("ball-result");    // risultato: rosso
                }
            }
            row.getChildren().add(b);
        }
        return row;
    }

    private Label ball(int number, boolean hit) {
        Label l = new Label(String.valueOf(number));
        l.setMinSize(36, 36);
        l.setPrefSize(36, 36);
        l.setMaxSize(36, 36);
        l.getStyleClass().add("ball");
        if (hit) l.getStyleClass().add("ball-hit");
        return l;
    }

    private void showDetail(Extraction ext) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        Label t = new Label("Estrazione completa");
        t.getStyleClass().add("title");
        Label s = new Label(ext.getLongDate());
        s.getStyleClass().add("subtitle");
        box.getChildren().addAll(t, s);
        for (String ruota : Extraction.ALL_RUOTE) {
            List<Integer> nums = ext.getNumbersForRuota(ruota);
            if (nums != null && nums.size() >= 5) box.getChildren().add(wheelRow(ruota, nums, ext));
        }
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        dialog("Estrazione del " + ext.getShortDate(), sp, 560, 640).show();
    }

    // ------------------------------------------------------------------ aggiornamento online

    private void updateOnline() {
        updateOnline(false);
    }

    /** silent = aggiornamento automatico all'avvio: niente finestre, solo il riepilogo in alto. */
    private void updateOnline(boolean silent) {
        Alert progress = new Alert(Alert.AlertType.INFORMATION);
        progress.setTitle("Aggiornamento archivio");
        progress.setHeaderText("Download estrazioni in corso…");
        progress.setContentText("Lettura dell'archivio online. L'operazione può richiedere alcuni secondi.");
        progress.getButtonTypes().setAll(ButtonType.CANCEL);
        progress.initOwner(stage);
        if (!silent) progress.show();
        else countLabel.setText(countLabel.getText() + "\n🌐 Aggiornamento automatico in corso…");

        Thread worker = new Thread(() -> {
            try {
                LottoSources.Outcome outcome = OnlineArchiveUpdater.downloadLatestWithReport();
                List<Extraction> downloaded = outcome.extractions;
                if (downloaded.isEmpty()) throw new IllegalStateException("Le fonti non hanno restituito estrazioni valide.");
                List<Extraction> existing = DesktopStorage.loadArchive();
                final String localLast = existing.isEmpty() ? "archivio vuoto" : existing.get(0).getDate();
                List<Extraction> merged = DesktopStorage.merge(existing, downloaded);
                DesktopStorage.saveArchive(merged);
                int added = Math.max(0, merged.size() - existing.size());
                Platform.runLater(() -> {
                    progress.close();
                    all = merged;
                    refresh();
                    if (silent) {
                        countLabel.setText(countLabel.getText() + "\n🌐 Aggiornato all'avvio: " + added + " nuove estrazioni (ultima "
                                + merged.get(0).getDate() + ", fonti attive " + outcome.sourcesOk + "/" + outcome.sourcesTotal + ").");
                    } else {
                        info("Aggiornamento completato",
                                "Ultima estrazione prima dell'aggiornamento: " + localLast + "\n"
                                + "Ultima estrazione ora in archivio: " + merged.get(0).getDate() + "\n"
                                + "Estrazioni scaricate e validate: " + downloaded.size() + "\n"
                                + "Nuove estrazioni aggiunte: " + added + "\n"
                                + "Totale archivio: " + merged.size() + "\n\n"
                                + outcome.report + "\n\n"
                                + "Prima di ogni salvataggio viene creata una copia in backups/.\n"
                                + "Altre fonti: aggiungi righe Nome|URL in " + DesktopStorage.dataDir().resolve("fonti_extra.txt") + " (usa {anno} nell'URL se la pagina è annuale).");
                    }
                    checkGiocateWins();
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    progress.close();
                    if (silent) {
                        countLabel.setText(countLabel.getText() + "\n🌐 Aggiornamento automatico non riuscito (puoi riprovare con il pulsante).");
                    } else {
                        info("Aggiornamento non riuscito",
                                "L'archivio locale non è stato modificato.\n\n"
                                + ex + "\n\nPuoi riprovare o importare un CSV.");
                    }
                });
            }
        }, "lotto-online-update");
        worker.setDaemon(true);
        worker.start();
    }

    /** Ricostruisce da zero l'archivio Lotto usando le pagine annuali online. Il file locale resta intatto finché il download non è validato. */
    private void rebuildOnlineArchive() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Verrà scaricato l'archivio storico disponibile da Xamig (anni 1939–oggi). Solo dopo aver trovato estrazioni valide verrà salvato un backup e sostituito l'archivio Lotto attuale. Se il download fallisce, l'archivio attuale resterà intatto. L'operazione può richiedere diversi minuti.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Ricostruisci archivio Lotto");
        confirm.setHeaderText("Confermi la ricostruzione completa?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        Alert progress = new Alert(Alert.AlertType.INFORMATION);
        progress.setTitle("Ricostruzione archivio Lotto");
        progress.setHeaderText("Download storico in corso…");
        progress.setContentText("Sto controllando le pagine annuali. Non chiudere il programma.");
        progress.getButtonTypes().clear();
        progress.initOwner(stage);
        progress.show();
        Thread worker = new Thread(() -> {
            try {
                List<Extraction> rebuilt = OnlineArchiveUpdater.downloadFullArchive();
                if (rebuilt.isEmpty()) throw new IllegalStateException("Nessuna estrazione valida: sostituzione annullata.");
                DesktopStorage.saveArchive(rebuilt); // backup automatico prima della sostituzione
                Platform.runLater(() -> {
                    progress.close(); all = rebuilt; highlight = null;
                    if (onlyHighlighted != null) onlyHighlighted.setSelected(false);
                    refresh();
                    info("Ricostruzione completata", "Archivio Lotto ricostruito dalle pagine annuali online.\nEstrazioni valide: " + rebuilt.size() + "\nPrima estrazione in archivio: " + rebuilt.get(rebuilt.size()-1).getDate() + "\nUltima estrazione in archivio: " + rebuilt.get(0).getDate() + "\n\nÈ stato creato un backup dell'archivio precedente.");
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> { progress.close(); info("Ricostruzione non riuscita", "L'archivio locale non è stato sostituito.\n\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage()); });
            }
        }, "lotto-full-archive-rebuild");
        worker.setDaemon(true); worker.start();
    }

    // ------------------------------------------------------------------ import CSV

    private void importCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Scegli il file CSV delle estrazioni");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV / testo", "*.csv", "*.txt", "*.tsv"),
                new FileChooser.ExtensionFilter("Tutti i file", "*.*"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;

        countLabel.setText("Importazione in corso…");
        new Thread(() -> {
            try {
                List<Extraction> imported = DesktopStorage.importCsv(f.toPath());
                List<Extraction> merged = DesktopStorage.merge(all, imported);
                DesktopStorage.saveArchive(merged);
                Platform.runLater(() -> {
                    all = merged;
                    refresh();
                    info("Importazione completata", "Estrazioni lette dal file: " + imported.size()
                            + "\nTotale in archivio: " + merged.size());
                    checkGiocateWins();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    refresh();
                    info("Errore di importazione", String.valueOf(ex.getMessage()));
                });
            }
        }, "csv-import").start();
    }

    // ------------------------------------------------------------------ ritardatari

    private void showRitardatari() {
        if (all.isEmpty()) {
            info("Archivio vuoto", "Importa prima le estrazioni.");
            return;
        }
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(Extraction.ALL_RUOTE));
        String pre = selectedRuota();
        box.setValue(pre != null ? pre : "Bari");

        Label sub = new Label();
        sub.getStyleClass().add("subtitle");
        VBox rows = new VBox(6);
        ScrollPane sp = new ScrollPane(rows);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);

        Label tip = new Label("Clic su un numero per creare una giocata.");
        tip.getStyleClass().add("hint");

        Label title = new Label("⏳ Ritardatari");
        title.getStyleClass().add("title");
        VBox root = new VBox(8, title, new HBox(10, new Label("Ruota:"), box), sub, sp, tip);
        root.setPadding(new Insets(16));

        Stage d = dialog("Ritardatari", root, 560, 700);

        Runnable render = () -> {
            String ruota = box.getValue();
            RitardatariEngine.Result res = RitardatariEngine.compute(all, ruota);
            sub.setText("Ruota di " + ruota.toUpperCase() + " · " + res.drawsConsidered + " estrazioni analizzate · primi 20");
            rows.getChildren().clear();
            int shown = Math.min(20, res.entries.size());
            for (int i = 0; i < shown; i++) {
                RitardatariEngine.Entry e = res.entries.get(i);
                boolean near = e.maxDelay > 0 && e.delay >= 0.8 * e.maxDelay;

                Label info = new Label("Ritardo " + e.delay + (near ? " 🔥" : "") + "  ·  record " + e.maxDelay);
                info.getStyleClass().add(near ? "hot-text" : "strong");
                ProgressBar bar = new ProgressBar(Math.min(1.0, (double) e.delay / Math.max(1, e.maxDelay)));
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(8);
                if (near) bar.getStyleClass().add("hot");
                VBox mid = new VBox(4, info, bar);
                HBox.setHgrow(mid, Priority.ALWAYS);
                Label hits = new Label(e.hits + "×");
                hits.getStyleClass().add("hint");

                HBox row = new HBox(10, ball(e.number, false), mid, hits);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("row-clickable");
                final int number = e.number;
                row.setOnMouseClicked(ev -> {
                    d.close();
                    showNuovaGiocata(ruota, number, null);
                });
                rows.getChildren().add(row);
            }
        };
        box.setOnAction(e -> render.run());
        render.run();
        d.show();
    }

    // ------------------------------------------------------------------ giocate

    private static String formatDateKey(int key) {
        return String.format(Locale.ITALY, "%02d/%02d/%04d", key % 100, (key / 100) % 100, key / 10000);
    }

    private void checkGiocateWins() {
        if (all.isEmpty()) return;
        List<Giocata> list = DesktopStorage.loadGiocate();
        StringBuilder sb = new StringBuilder();
        int wins = 0;
        for (Giocata g : list) {
            if (g.notified) continue;
            GiocataChecker.Result r = GiocataChecker.check(g, all);
            if (r.status == GiocataChecker.Status.VINTA) {
                g.notified = true;
                wins++;
                sb.append("• ").append(g.getTipoName()).append(" ").append(g.numeri)
                        .append(" su ").append(r.ruotaVincente.toUpperCase())
                        .append(" al colpo ").append(r.colpoVincente)
                        .append(" (").append(r.estrazioneVincente.getShortDate()).append(")\n")
                        .append("   usciti: ").append(r.numeriUsciti).append("\n");
            }
        }
        if (wins == 0) return;
        DesktopStorage.saveGiocate(list);
        info(wins == 1 ? "🎉 Giocata vincente!" : "🎉 " + wins + " giocate vincenti!", sb.toString().trim());
    }

    private void showGiocate() {
        Label title = new Label("🎯 Le mie giocate");
        title.getStyleClass().add("title");
        Label sub = new Label();
        sub.getStyleClass().add("subtitle");
        VBox rows = new VBox(8);
        ScrollPane sp = new ScrollPane(rows);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);

        Runnable[] refresh = new Runnable[1];
        Button btnNew = button("➕ Nuova giocata", true, () -> showNuovaGiocata(null, null, () -> refresh[0].run()));
        VBox root = new VBox(8, title, sub, sp, btnNew);
        root.setPadding(new Insets(16));
        Stage d = dialog("Le mie giocate", root, 580, 680);

        refresh[0] = () -> {
            rows.getChildren().clear();
            List<Giocata> list = DesktopStorage.loadGiocate();
            sub.setText(list.isEmpty()
                    ? "Nessuna giocata salvata. Premi \"Nuova giocata\" per iniziare."
                    : list.size() + (list.size() == 1 ? " giocata salvata" : " giocate salvate"));
            for (Giocata g : list) rows.getChildren().add(giocataCard(g, refresh[0]));
        };
        refresh[0].run();
        d.show();
    }

    private Node giocataCard(Giocata g, Runnable onChanged) {
        GiocataChecker.Result r = GiocataChecker.check(g, all);
        String ruotaLabel = Giocata.TUTTE.equalsIgnoreCase(g.ruota) ? "TUTTE LE RUOTE" : g.ruota.toUpperCase();

        Label head = new Label(ruotaLabel + " · " + g.getTipoName() + " · " + g.colpi + (g.colpi == 1 ? " colpo" : " colpi"));
        head.getStyleClass().add("card-date");
        HBox.setHgrow(head, Priority.ALWAYS);
        head.setMaxWidth(Double.MAX_VALUE);
        Button del = button("🗑", false, () -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    g.getTipoName() + " " + g.numeri + " su " + ruotaLabel, ButtonType.OK, ButtonType.CANCEL);
            a.setTitle("Eliminare la giocata?");
            a.setHeaderText("Eliminare la giocata?");
            a.getDialogPane().getStylesheets().add(css());
            Optional<ButtonType> res = a.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                List<Giocata> list = DesktopStorage.loadGiocate();
                list.removeIf(x -> x.id == g.id);
                DesktopStorage.saveGiocate(list);
                onChanged.run();
            }
        });
        HBox header = new HBox(8, head, del);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox balls = new HBox(6);
        for (int n : g.numeri) {
            balls.getChildren().add(ball(n, r.status == GiocataChecker.Status.VINTA && r.numeriUsciti.contains(n)));
        }

        Label status = new Label();
        status.setWrapText(true);
        switch (r.status) {
            case VINTA:
                status.setText("🎉 VINTA al colpo " + r.colpoVincente + " su " + r.ruotaVincente.toUpperCase()
                        + " — " + r.estrazioneVincente.getShortDate());
                status.getStyleClass().add("status-win");
                break;
            case SCADUTA:
                status.setText("⌛ Scaduta: " + g.colpi + " colpi senza vincita (dal " + formatDateKey(g.fromKey) + ")");
                status.getStyleClass().add("status-lost");
                break;
            default:
                status.setText("⏳ In corso: " + r.colpiEffettuati + " colpi su " + g.colpi
                        + " (dal " + formatDateKey(g.fromKey) + ")");
                status.getStyleClass().add("hint");
                break;
        }

        VBox card = new VBox(6, header, balls, status);
        card.getStyleClass().add("card");
        return card;
    }

    private void showNuovaGiocata(String preRuota, Integer preNumero, Runnable onSaved) {
        List<Integer> pre = new ArrayList<>();
        if (preNumero != null) pre.add(preNumero);
        showNuovaGiocata(preRuota, pre, 9, onSaved);
    }

    /** Apre "Nuova giocata" già compilata (ruota, numeri e colpi). */
    private void showNuovaGiocata(String preRuota, List<Integer> preNumeri, int preColpi, Runnable onSaved) {
        int latestKey = 0;
        for (Extraction e : all) latestKey = Math.max(latestKey, e.getDateKey());
        final int fromKey = latestKey > 0 ? latestKey
                : LocalDate.now().getYear() * 10000 + LocalDate.now().getMonthValue() * 100 + LocalDate.now().getDayOfMonth();

        Label title = new Label("➕ Nuova giocata");
        title.getStyleClass().add("title");
        Label infoLabel = new Label(latestKey > 0
                ? "Verrà controllata dalle estrazioni successive al " + formatDateKey(latestKey)
                : "Verrà controllata dalle prossime estrazioni");
        infoLabel.getStyleClass().add("subtitle");

        List<String> ruoteOpts = new ArrayList<>();
        ruoteOpts.add(Giocata.TUTTE);
        ruoteOpts.addAll(Extraction.ALL_RUOTE);
        ComboBox<String> ruotaBox = new ComboBox<>(FXCollections.observableArrayList(ruoteOpts));
        ruotaBox.setValue(preRuota != null ? preRuota : Giocata.TUTTE);

        StringBuilder preTesto = new StringBuilder();
        for (int n : preNumeri) preTesto.append(preTesto.length() > 0 ? " " : "").append(n);
        TextField numeri = new TextField(preTesto.toString());
        numeri.setPromptText("es. 12 45 67 (da 1 a 10 numeri)");

        ComboBox<String> tipoBox = new ComboBox<>(FXCollections.observableArrayList(
                Giocata.TIPI[1], Giocata.TIPI[2], Giocata.TIPI[3], Giocata.TIPI[4], Giocata.TIPI[5]));
        tipoBox.setValue(preNumeri.size() == 1 ? Giocata.TIPI[1] : Giocata.TIPI[2]);

        Spinner<Integer> colpi = new Spinner<>(1, 99, Math.max(1, Math.min(99, preColpi)));
        colpi.setEditable(true);

        VBox form = new VBox(6,
                new Label("Ruota:"), ruotaBox,
                new Label("Numeri:"), numeri,
                new Label("Vince se escono almeno:"), tipoBox,
                new Label("Colpi (estrazioni in cui resta valida):"), colpi);

        Button save = button("Salva", true, null);
        Button cancel = button("Annulla", false, null);
        HBox buttons = new HBox(8, cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, title, infoLabel, form, buttons);
        root.setPadding(new Insets(16));
        Stage d = dialog("Nuova giocata", root, 460, 520);

        cancel.setOnAction(e -> d.close());
        save.setOnAction(e -> {
            List<Integer> nums = new ArrayList<>();
            for (String part : numeri.getText().trim().split("[^0-9]+")) {
                if (part.isEmpty()) continue;
                int n;
                try {
                    n = Integer.parseInt(part);
                } catch (NumberFormatException ex) {
                    n = -1;
                }
                if (n < 1 || n > 90) {
                    info("Numeri non validi", "I numeri devono essere compresi tra 1 e 90.");
                    return;
                }
                if (!nums.contains(n)) nums.add(n);
            }
            int hits = tipoBox.getSelectionModel().getSelectedIndex() + 1;
            if (nums.isEmpty() || nums.size() > 10) {
                info("Numeri non validi", "Inserisci da 1 a 10 numeri.");
                return;
            }
            if (nums.size() < hits) {
                info("Numeri insufficienti", "Per " + Giocata.TIPI[hits] + " servono almeno " + hits + " numeri.");
                return;
            }
            int nColpi = 9;
            try {
                nColpi = Math.max(1, Math.min(99, Integer.parseInt(colpi.getEditor().getText().trim())));
            } catch (NumberFormatException ignored) { }

            Giocata g = new Giocata(System.currentTimeMillis(), ruotaBox.getValue(), nums, hits, nColpi, fromKey, false);
            List<Giocata> list = DesktopStorage.loadGiocate();
            list.add(0, g);
            DesktopStorage.saveGiocate(list);
            d.close();
            if (onSaved != null) onSaved.run();
        });
        d.show();
    }

    // ------------------------------------------------------------------ tecniche

    private TecnicheDialog.Host host() {
        return new TecnicheDialog.Host() {
            @Override public List<Extraction> archive() { return all; }
            @Override public void applyHighlight(HighlightSpec spec) { DesktopApp.this.applyHighlight(spec); }
            @Override public void creaGiocata(String ruota, List<Integer> numeri, int colpi) {
                showNuovaGiocata(ruota, numeri, colpi, null);
            }
            @Override public Stage owner() { return stage; }
            @Override public String css() { return DesktopApp.this.css(); }
        };
    }

    private void showTecniche() {
        TecnicheDialog.show(host());
    }

    /** Apre la guida in background per non bloccare il thread dell'interfaccia JavaFX. */
    private void openCompleteGuide() {
        Thread opener = new Thread(() -> {
            try (InputStream in = getClass().getResourceAsStream("/docs/guida_completa_lottoinjava.html")) {
                if (in == null) throw new java.io.IOException("Guida HTML non inclusa nel pacchetto.");
                Path guide = Files.createTempFile("lottoinjava-guida-completa-", ".html");
                Files.copy(in, guide, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new java.io.IOException("Il sistema non supporta l'apertura automatica del browser.");
                Desktop.getDesktop().browse(guide.toUri());
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.INFORMATION,
                            "Non riesco ad aprire automaticamente il browser.\nLa guida è inclusa nel progetto in desktop/docs/guida_completa_lottoinjava.html e desktop/docs/guida_completa_lottoinjava.pdf.\n\nDettaglio: " + ex.getMessage(), ButtonType.OK);
                    a.initOwner(stage); a.setHeaderText("Guida completa LottoInJava"); a.showAndWait();
                });
            }
        }, "lottoinjava-open-guide");
        opener.setDaemon(true);
        opener.start();
    }

    /** Colora l'archivio: spia in verde e risultato in rosso, per verificare a mano la tecnica. */
    private void applyHighlight(HighlightSpec spec) {
        spec.prepare(all);
        highlight = spec;
        // Una previsione può ricomparire su ruote diverse da quella d'origine:
        // quando si attiva l'evidenziazione mostriamo sempre tutte le ruote,
        // altrimenti il filtro della ruota-spia nasconderebbe le ricorrenze rosse.
        ruotaBox.setValue("Tutte le ruote");
        refresh();
        listView.scrollTo(0);
        if (spec.windowKeys.isEmpty() && spec.spyKeys.isEmpty()) {
            info("Niente da evidenziare",
                    "Nel periodo scelto non ci sono estrazioni che corrispondono alla spia o al risultato.");
        }
    }

    private String css() {
        return ThemeManager.cssUrl();
    }

    private Button button(String text, boolean primary, Runnable action) {
        Button b = new Button(text);
        if (primary) b.getStyleClass().add("button-primary");
        if (action != null) b.setOnAction(e -> action.run());
        return b;
    }

    private Stage dialog(String title, Region content, double w, double h) {
        Stage d = new Stage();
        d.initOwner(stage);
        d.initModality(Modality.APPLICATION_MODAL);
        d.setTitle(title);
        Scene sc = new Scene(content, w, h);
        sc.getStylesheets().add(css());
        d.setScene(sc);
        return d;
    }

    private void info(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(title);
        a.getDialogPane().getStylesheets().add(css());
        a.setResizable(true);
        a.getDialogPane().setMinWidth(640);
        a.showAndWait();
    }
}
