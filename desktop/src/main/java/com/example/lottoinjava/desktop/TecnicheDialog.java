package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Finestra "Tecniche": tutte le tecniche dell'app, con gli stessi controlli sui parametri del main.py
 * e, per ognuna, il pulsante che colora l'archivio (spia VERDE, risultato ROSSO).
 */
final class TecnicheDialog {

    interface Host {
        List<Extraction> archive();
        void applyHighlight(HighlightSpec spec);
        void creaGiocata(String ruota, List<Integer> numeri, int colpi);
        Stage owner();
        String css();
    }

    // ------------------------------------------------------------------ elenco tecniche

    private static final String[] NOMI = {
            "🔎 Ricerca spia & resoconto esiti",
            "⭐ Previsione post-spia (TOP 5, ambi, terni)",
            "🧭 Ciclometria spia",
            "✨ Terni magici",
            "🧩 Resoconto unificato (post-spia + ciclometria)",
            "⚡ Convergenze tecniche",
            "✅ Verifica combinazioni dopo la spia",
            "🛒 Apriori / Market basket",
            "➕ Costruttore Somma Ruote",
            "➕ Costruttore Somma + Fisso",
            "📈 Previsione a 5 numeri (frequenza/ritardo)",
            "🗡️ Metodo D'Artagnan",
            "📐 Isotopia estrazionale",
            "🌀 Serie di Fibonacci",
            "☕ Metodo Cappuccino",
            "📊 Costruttore Multistatico",
            "🚀 Super previsione statistica light",
            "🧠 Previsione con algoritmi IA",
            "🕵️ Spia a tutte (TOP 5 multi-ruota)",
            "🎯 Spia a tutte — risultati sulla ruota origine",
            "🌐 Verifica spia su tutte le ruote",
            "📍 Spia in posizione (verifica su tutte)",
            "🔗 Spia multipla, fino a 5 spie (verifica su tutte)",
            "🔎 Scanner di spie (classifica di tutte le spie)",
            "🧪 Collaudo previsioni (test onesto sul tuo archivio)"
    };

    private static final String NOTA_MAIN = "✔ Calcolo allineato al main.py (confrontato su archivio reale: risultati identici).";
    private static final String NOTA_MAIN_FIX = "✔ Calcolo allineato al main.py; le co-presenze usano la chiave ordinata "
            + "(il main.py le cerca con chiave non ordinata e ne perde circa la metà).";
    private static final String NOTA_JAVA = "ℹ Metodo presente solo nell'app Java: nel main.py non esiste, quindi non è confrontabile. "
            + "Usa tutto l'archivio (non il periodo).";

    private static final String NOTA_SPIA_TUTTE = "ℹ Metodo presente solo nell'app Java: i numeri da giocare sono quelli che escono sulla ruota di origine dopo la spia; "
            + "vengono verificati su tutte le 11 ruote (ambo, terno, quaterna, cinquina, percentuali), con test walk-forward, test su due periodi e bilancio di gioco.";

    private static final String[] NOTE = {
            NOTA_MAIN, NOTA_MAIN, NOTA_MAIN, NOTA_MAIN_FIX, NOTA_MAIN_FIX, NOTA_MAIN_FIX,
            NOTA_MAIN, NOTA_MAIN, NOTA_MAIN, NOTA_MAIN,
            NOTA_JAVA, NOTA_JAVA, NOTA_JAVA, NOTA_JAVA, NOTA_JAVA, NOTA_JAVA,
            NOTA_MAIN, NOTA_MAIN, NOTA_JAVA, NOTA_JAVA,
            NOTA_SPIA_TUTTE, NOTA_SPIA_TUTTE, NOTA_SPIA_TUTTE,
            "ℹ Metodo presente solo nell'app Java: prova tutte le spie da 1 a 90 sulla ruota scelta e le ordina per quanto aggiungono rispetto "
            + "a giocare gli stessi numeri senza spia (walk-forward, due periodi, bilancio). Con archivi lunghi può richiedere qualche secondo.",
            "ℹ Metodo presente solo nell'app Java: mette alla prova i metodi di previsione dell'app contro il caso, usando a ogni passo solo le "
            + "estrazioni precedenti (anni = storia usata e ultimo anno valutato; test dal = primo anno valutato, 0 = ultimi 10). "
            + "Richiede da una decina di secondi a qualche minuto."
    };

    // righe del modulo visibili per ogni tecnica
    private static final int R_RUOTA = 1, R_SPIA = 2, R_ANNI = 4, R_COLPI = 8, R_MULTI = 16, R_DIM = 32,
            R_RUOTESEL = 64, R_SOMMA = 128, R_FISSO = 256, R_TARGET = 512, R_WHEELB = 1024,
            R_POSIZ = 2048, R_NGIOCO = 4096, R_MODMULTI = 8192,
            R_ANNOTEST = 16384, R_TIPOGIOCO = 32768;

    private static final int SPIA_STD = R_RUOTA | R_SPIA | R_ANNI | R_COLPI;
    private static final int[] FLAGS = {
            SPIA_STD, SPIA_STD, SPIA_STD, SPIA_STD, SPIA_STD, SPIA_STD,
            R_SPIA | R_COLPI | R_DIM | R_RUOTESEL,
            SPIA_STD,
            R_RUOTA | R_SOMMA | R_FISSO | R_TARGET | R_ANNI | R_COLPI,
            R_RUOTA | R_ANNI,
            R_RUOTA,
            R_RUOTA | R_SPIA | R_COLPI,
            R_RUOTA | R_WHEELB | R_COLPI,
            R_RUOTA,
            R_RUOTA,
            R_RUOTA | R_MULTI | R_COLPI,
            SPIA_STD, SPIA_STD | R_RUOTESEL, SPIA_STD | R_RUOTESEL, SPIA_STD | R_RUOTESEL,
            SPIA_STD | R_NGIOCO | R_ANNOTEST | R_TIPOGIOCO,
            SPIA_STD | R_POSIZ | R_NGIOCO | R_ANNOTEST | R_TIPOGIOCO,
            R_RUOTA | R_MULTI | R_MODMULTI | R_ANNI | R_COLPI | R_NGIOCO | R_ANNOTEST | R_TIPOGIOCO,
            R_RUOTA | R_ANNI | R_COLPI | R_NGIOCO | R_ANNOTEST | R_TIPOGIOCO,
            R_ANNI | R_COLPI | R_NGIOCO | R_ANNOTEST | R_TIPOGIOCO
    };

    // ------------------------------------------------------------------ interfaccia

    private final Host host;
    private final int minYear, maxYear;
    private final TecnicheRunner runner;

    private ComboBox<String> tecBox, ruotaBox, ruota2Box, targetBox, wheelBBox, dimBox, modBox, multiModBox, tipoGiocoBox;
    private Spinner<Integer> spiaSp, y0Sp, y1Sp, colpiSp, pos1Sp, pos2Sp, fissoSp, posSpiaSp, nGiocoSp, annoTestSp;
    private TextField multiField;
    private final List<CheckBox> ruoteChecks = new ArrayList<>();
    private final Map<Integer, Node> righe = new LinkedHashMap<>();
    private TextArea out;
    private Label nota, stato;
    private Button btnRun, btnHighlight, btnGiocata, btnSave;
    private ComboBox<String> presetBox;
    private HighlightSpec ultimaSpec;

    private TecnicheDialog(Host host) {
        this.host = host;
        int mn = 9999, mx = 0;
        for (Extraction e : host.archive()) {
            mn = Math.min(mn, e.getYear());
            mx = Math.max(mx, e.getYear());
        }
        if (mx == 0) { mn = 2001; mx = java.time.LocalDate.now().getYear(); }
        this.minYear = mn;
        this.maxYear = mx;
        this.runner = new TecnicheRunner(mn, mx);
    }

    static void show(Host host) {
        if (host.archive().isEmpty()) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Importa o aggiorna prima le estrazioni.", javafx.scene.control.ButtonType.OK);
            a.setTitle("Archivio vuoto");
            a.setHeaderText("Archivio vuoto");
            a.getDialogPane().getStylesheets().add(host.css());
            a.showAndWait();
            return;
        }
        new TecnicheDialog(host).build();
    }

    private Spinner<Integer> spinner(int min, int max, int val, int width) {
        Spinner<Integer> sp = new Spinner<>(min, max, val);
        sp.setEditable(true);
        sp.setPrefWidth(width);
        return sp;
    }

    private ComboBox<String> combo(List<String> items, String value) {
        ComboBox<String> c = new ComboBox<>(FXCollections.observableArrayList(items));
        c.setValue(value);
        return c;
    }

    private HBox riga(Node... nodi) {
        HBox h = new HBox(10, nodi);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private void build() {
        Label title = new Label("🔮 Tecniche");
        title.getStyleClass().add("title");

        tecBox = new ComboBox<>(FXCollections.observableArrayList(NOMI));
        tecBox.setValue(NOMI[0]);
        tecBox.setMaxWidth(Double.MAX_VALUE);
        nota = new Label(NOTE[0]);
        nota.setWrapText(true);
        nota.getStyleClass().add("hint");

        List<String> ruote = new ArrayList<>(Extraction.ALL_RUOTE);
        ruotaBox = combo(ruote, "Bari");
        ruota2Box = combo(ruote, "Milano");
        wheelBBox = combo(ruote, "Milano");
        List<String> targets = new ArrayList<>();
        targets.add("TUTTE");
        targets.addAll(ruote);
        targetBox = combo(targets, "TUTTE");
        dimBox = combo(Arrays.asList("Ambo", "Terno", "Quaterna", "Cinquina"), "Ambo");
        modBox = combo(Arrays.asList("STESSA RUOTA", "RUOTE SELEZIONATE"), "STESSA RUOTA");
        multiModBox = combo(Arrays.asList(com.example.lottoinjava.analysis.SpiaTutteEngine.TUTTE_INSIEME,
                com.example.lottoinjava.analysis.SpiaTutteEngine.ALMENO_UNA), com.example.lottoinjava.analysis.SpiaTutteEngine.TUTTE_INSIEME);

        spiaSp = spinner(1, 90, 11, 90);
        int y1def = maxYear;
        int y0def = Math.max(minYear, maxYear - 5);
        y0Sp = spinner(minYear, maxYear, y0def, 100);
        y1Sp = spinner(minYear, maxYear, y1def, 100);
        colpiSp = spinner(1, 50, 9, 80);
        posSpiaSp = spinner(1, 5, 1, 70);
        nGiocoSp = spinner(2, 10, 5, 70);
        annoTestSp = spinner(0, 2200, 0, 90);
        tipoGiocoBox = combo(Arrays.asList("Ambo", "Terno", "Quaterna", "Cinquina"), "Ambo");
        pos1Sp = spinner(1, 5, 1, 70);
        pos2Sp = spinner(1, 5, 1, 70);
        fissoSp = spinner(1, 90, 1, 80);
        multiField = new TextField("11 45");
        multiField.setPromptText("fino a 5 spie separate da spazio, es. 11 45");
        multiField.setPrefWidth(200);

        FlowPane ruoteSel = new FlowPane(10, 4);
        for (String r : ruote) {
            CheckBox cb = new CheckBox(r);
            cb.setSelected("Bari".equals(r));
            ruoteChecks.add(cb);
            ruoteSel.getChildren().add(cb);
        }

        righe.put(R_RUOTA, riga(new Label("Ruota:"), ruotaBox));
        righe.put(R_SPIA, riga(new Label("Spia (1-90):"), spiaSp));
        righe.put(R_ANNI, riga(new Label("Anno inizio:"), y0Sp, new Label("Anno fine:"), y1Sp));
        righe.put(R_COLPI, riga(new Label("Colpi (1-50):"), colpiSp));
        righe.put(R_MULTI, riga(new Label("Spie:"), multiField));
        righe.put(R_DIM, riga(new Label("Combinazione:"), dimBox, new Label("Modalità:"), modBox));
        VBox selBox = new VBox(4, new Label("Ruote selezionate:"), ruoteSel);
        righe.put(R_RUOTESEL, selBox);
        righe.put(R_SOMMA, riga(new Label("Posizione 1ª ruota (1-5):"), pos1Sp, new Label("2ª ruota:"), ruota2Box,
                new Label("Posizione (1-5):"), pos2Sp));
        righe.put(R_FISSO, riga(new Label("Fisso (1-90):"), fissoSp));
        righe.put(R_TARGET, riga(new Label("Ruota esito:"), targetBox));
        righe.put(R_WHEELB, riga(new Label("Seconda ruota:"), wheelBBox));
        righe.put(R_POSIZ, riga(new Label("Posizione della spia (1-5):"), posSpiaSp));
        righe.put(R_MODMULTI, riga(new Label("Spie multiple:"), multiModBox));
        righe.put(R_NGIOCO, riga(new Label("Numeri da giocare (2-10):"), nGiocoSp));
        righe.put(R_ANNOTEST, riga(new Label("Test: secondo periodo dall'anno (0 = auto):"), annoTestSp));
        righe.put(R_TIPOGIOCO, riga(new Label("Bilancio di gioco su:"), tipoGiocoBox));

        VBox form = new VBox(8);
        for (Node n : righe.values()) form.getChildren().add(n);

        out = new TextArea();
        out.setEditable(false);
        out.setWrapText(false);
        out.getStyleClass().add("report");
        VBox.setVgrow(out, Priority.ALWAYS);

        btnRun = new Button("▶ Esegui");
        btnRun.getStyleClass().add("button-primary");
        btnHighlight = new Button("🎨 Evidenzia nell'archivio (spia verde, risultato rosso, più usciti blu scuro)");
        btnHighlight.setDisable(true);
        btnGiocata = new Button("🎯 Crea giocata");
        btnGiocata.setDisable(true);
        btnSave = new Button("💾 Salva rapporto");
        btnSave.setDisable(true);
        Button btnCopy = new Button("📋 Copia");
        Button btnClose = new Button("Chiudi");
        stato = new Label("");
        stato.getStyleClass().add("subtitle");
        HBox bottoni = new HBox(8, btnRun, btnHighlight, btnGiocata, btnSave, btnCopy, btnClose);
        bottoni.setAlignment(Pos.CENTER_LEFT);

        presetBox = new ComboBox<>();
        presetBox.setPromptText("nessuno");
        presetBox.setPrefWidth(300);
        Button btnPresetLoad = new Button("📂 Carica");
        Button btnPresetSave = new Button("💾 Salva settaggi");
        Button btnPresetDel = new Button("🗑 Elimina");
        HBox presetRow = riga(new Label("Settaggi salvati:"), presetBox, btnPresetLoad, btnPresetSave, btnPresetDel);
        aggiornaPreset(null);

        VBox root = new VBox(10, title, tecBox, nota, presetRow, form, bottoni, stato, out);
        root.setPadding(new Insets(16));

        Stage d = new Stage();
        d.initOwner(host.owner());
        d.initModality(Modality.APPLICATION_MODAL);
        d.setTitle("Tecniche");
        Scene sc = new Scene(root, 900, 780);
        sc.getStylesheets().add(host.css());
        d.setScene(sc);

        tecBox.setOnAction(e -> aggiornaModulo());
        btnRun.setOnAction(e -> esegui());
        btnHighlight.setOnAction(e -> {
            if (ultimaSpec != null) {
                host.applyHighlight(ultimaSpec);
                d.close();
            }
        });
        btnGiocata.setOnAction(e -> {
            if (ultimaSpec != null && !ultimaSpec.numbers.isEmpty()) {
                List<Integer> n = new ArrayList<>(ultimaSpec.numbers.subList(0, Math.min(10, ultimaSpec.numbers.size())));
                String ruota = ultimaSpec.targetWheels.isEmpty() ? "Tutte" : ultimaSpec.targetWheels.get(0);
                d.close();
                host.creaGiocata(ruota, n, Math.max(1, ultimaSpec.colpi == 0 ? 9 : ultimaSpec.colpi));
            }
        });
        btnSave.setOnAction(e -> salvaRapporto(d));
        btnCopy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(out.getText());
            Clipboard.getSystemClipboard().setContent(cc);
            stato.setText("Rapporto copiato negli appunti.");
        });
        btnClose.setOnAction(e -> d.close());
        btnPresetSave.setOnAction(e -> salvaPreset(d));
        btnPresetLoad.setOnAction(e -> caricaPreset());
        btnPresetDel.setOnAction(e -> eliminaPreset(d));

        aggiornaModulo();
        d.show();
    }

    private void aggiornaModulo() {
        int idx = Math.max(0, tecBox.getSelectionModel().getSelectedIndex());
        int flags = FLAGS[idx];
        for (Map.Entry<Integer, Node> e : righe.entrySet()) {
            boolean v = (flags & e.getKey()) != 0;
            e.getValue().setVisible(v);
            e.getValue().setManaged(v);
        }
        nota.setText(NOTE[idx]);
        btnHighlight.setDisable(true);
        btnGiocata.setDisable(true);
        btnSave.setDisable(true);
        ultimaSpec = null;
        stato.setText("");
    }

    // ------------------------------------------------------------------ settaggi salvati

    private void aggiornaPreset(String seleziona) {
        List<String> nomi = new ArrayList<>();
        for (TecnichePresets.Preset p : TecnichePresets.carica()) nomi.add(p.nome);
        presetBox.setItems(FXCollections.observableArrayList(nomi));
        presetBox.setValue(seleziona != null && nomi.contains(seleziona) ? seleziona : null);
    }

    private static String testo(Spinner<Integer> sp) {
        String t = sp.getEditor().getText();
        if (t != null && t.trim().matches("\\d+")) return t.trim();
        return String.valueOf(sp.getValue());
    }

    private static void imposta(Spinner<Integer> sp, String v) {
        try {
            int n = Integer.parseInt(v.trim());
            SpinnerValueFactory.IntegerSpinnerValueFactory f =
                    (SpinnerValueFactory.IntegerSpinnerValueFactory) sp.getValueFactory();
            sp.getValueFactory().setValue(Math.max(f.getMin(), Math.min(f.getMax(), n)));
        } catch (Exception ignored) { }
    }

    private static void imposta(ComboBox<String> c, String v) {
        if (v != null && c.getItems().contains(v)) c.setValue(v);
    }

    /** Nome proposto, ad es. "Spia a tutte · spia 2 · Milano". */
    private String suggerisciNome(int idx) {
        String base = NOMI[idx].replaceAll("^[^\\p{L}]+", "");
        int par = base.indexOf(" (");
        if (par > 0) base = base.substring(0, par);
        StringBuilder sb = new StringBuilder(base);
        int flags = FLAGS[idx];
        if ((flags & R_SPIA) != 0) sb.append(" · spia ").append(testo(spiaSp));
        if ((flags & R_RUOTA) != 0) sb.append(" · ").append(ruotaBox.getValue());
        return sb.toString();
    }

    private void salvaPreset(Stage owner) {
        int idx = Math.max(0, tecBox.getSelectionModel().getSelectedIndex());
        TextInputDialog dlg = new TextInputDialog(suggerisciNome(idx));
        dlg.setTitle("Salva settaggi");
        dlg.setHeaderText("Dai un nome a questi settaggi");
        dlg.setContentText("Nome:");
        dlg.initOwner(owner);
        dlg.getDialogPane().getStylesheets().add(host.css());
        Optional<String> r = dlg.showAndWait();
        if (!r.isPresent() || r.get().trim().isEmpty()) return;
        String nome = r.get().trim();

        TecnichePresets.Preset p = new TecnichePresets.Preset(nome);
        p.valori.put("tecnica", String.valueOf(idx));
        p.valori.put("ruota", String.valueOf(ruotaBox.getValue()));
        p.valori.put("ruota2", String.valueOf(ruota2Box.getValue()));
        p.valori.put("wheelB", String.valueOf(wheelBBox.getValue()));
        p.valori.put("target", String.valueOf(targetBox.getValue()));
        p.valori.put("dim", String.valueOf(dimBox.getValue()));
        p.valori.put("modalita", String.valueOf(modBox.getValue()));
        p.valori.put("spia", testo(spiaSp));
        p.valori.put("y0", testo(y0Sp));
        p.valori.put("y1", testo(y1Sp));
        p.valori.put("colpi", testo(colpiSp));
        p.valori.put("pos1", testo(pos1Sp));
        p.valori.put("pos2", testo(pos2Sp));
        p.valori.put("fisso", testo(fissoSp));
        p.valori.put("posSpia", testo(posSpiaSp));
        p.valori.put("nGioco", testo(nGiocoSp));
        p.valori.put("annoTest", testo(annoTestSp));
        p.valori.put("tipoGioco", String.valueOf(tipoGiocoBox.getValue()));
        p.valori.put("modMulti", String.valueOf(multiModBox.getValue()));
        p.valori.put("multi", multiField.getText() == null ? "" : multiField.getText());
        StringBuilder sel = new StringBuilder();
        for (CheckBox cb : ruoteChecks) if (cb.isSelected()) sel.append(sel.length() == 0 ? "" : ",").append(cb.getText());
        p.valori.put("ruoteSel", sel.toString());
        try {
            TecnichePresets.salva(p);
            aggiornaPreset(nome);
            stato.setText("Settaggi salvati: " + nome);
        } catch (java.io.IOException ex) {
            stato.setText("Salvataggio non riuscito: " + ex.getMessage());
        }
    }

    private void caricaPreset() {
        String nome = presetBox.getValue();
        if (nome == null) { stato.setText("Scegli prima dei settaggi salvati."); return; }
        for (TecnichePresets.Preset p : TecnichePresets.carica()) {
            if (!p.nome.equals(nome)) continue;
            Map<String, String> v = p.valori;
            try {
                int idx = Integer.parseInt(v.getOrDefault("tecnica", "0"));
                if (idx >= 0 && idx < NOMI.length) tecBox.setValue(NOMI[idx]);
            } catch (NumberFormatException ignored) { }
            imposta(ruotaBox, v.get("ruota"));
            imposta(ruota2Box, v.get("ruota2"));
            imposta(wheelBBox, v.get("wheelB"));
            imposta(targetBox, v.get("target"));
            imposta(dimBox, v.get("dim"));
            imposta(modBox, v.get("modalita"));
            imposta(spiaSp, v.getOrDefault("spia", "11"));
            imposta(y0Sp, v.getOrDefault("y0", String.valueOf(minYear)));
            imposta(y1Sp, v.getOrDefault("y1", String.valueOf(maxYear)));
            imposta(colpiSp, v.getOrDefault("colpi", "9"));
            imposta(pos1Sp, v.getOrDefault("pos1", "1"));
            imposta(pos2Sp, v.getOrDefault("pos2", "1"));
            imposta(fissoSp, v.getOrDefault("fisso", "1"));
            imposta(posSpiaSp, v.getOrDefault("posSpia", "1"));
            imposta(nGiocoSp, v.getOrDefault("nGioco", "5"));
            imposta(annoTestSp, v.getOrDefault("annoTest", "0"));
            imposta(tipoGiocoBox, v.get("tipoGioco"));
            imposta(multiModBox, v.get("modMulti"));
            if (v.containsKey("multi")) multiField.setText(v.get("multi"));
            List<String> scelte = Arrays.asList(v.getOrDefault("ruoteSel", "").split(","));
            for (CheckBox cb : ruoteChecks) cb.setSelected(scelte.contains(cb.getText()));
            aggiornaModulo();
            stato.setText("Caricati i settaggi: " + nome + ". Premi Esegui per calcolare.");
            return;
        }
        stato.setText("Settaggi non trovati: " + nome);
    }

    private void eliminaPreset(Stage owner) {
        String nome = presetBox.getValue();
        if (nome == null) { stato.setText("Scegli prima dei settaggi salvati."); return; }
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Eliminare i settaggi «" + nome + "»?", javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        a.initOwner(owner);
        a.getDialogPane().getStylesheets().add(host.css());
        Optional<javafx.scene.control.ButtonType> r = a.showAndWait();
        if (r.isPresent() && r.get() == javafx.scene.control.ButtonType.OK) {
            try {
                TecnichePresets.elimina(nome);
                aggiornaPreset(null);
                stato.setText("Settaggi eliminati: " + nome);
            } catch (java.io.IOException ex) {
                stato.setText("Eliminazione non riuscita: " + ex.getMessage());
            }
        }
    }

    private void salvaRapporto(Stage owner) {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Salva il rapporto");
        String nome = NOMI[Math.max(0, tecBox.getSelectionModel().getSelectedIndex())]
                .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        fc.setInitialFileName("rapporto_" + nome + "_" + java.time.LocalDate.now() + ".txt");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Testo", "*.txt"));
        java.io.File f = fc.showSaveDialog(owner);
        if (f == null) return;
        try {
            java.nio.file.Files.writeString(f.toPath(), out.getText(), java.nio.charset.StandardCharsets.UTF_8);
            stato.setText("Rapporto salvato: " + f.getName());
        } catch (java.io.IOException ex) {
            stato.setText("Salvataggio non riuscito: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ lettura parametri (controlli come nel main.py)

    private static int leggi(Spinner<Integer> sp, String etichetta) {
        String t = sp.getEditor().getText() == null ? "" : sp.getEditor().getText().trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valore non valido per \"" + etichetta + "\".");
        }
    }

    private TecnicheRunner.In leggiParametri(int idx) {
        int flags = FLAGS[idx];
        TecnicheRunner.In in = new TecnicheRunner.In();
        if ((flags & R_RUOTA) != 0) in.ruota = ruotaBox.getValue();
        if ((flags & R_SPIA) != 0) in.spia = leggi(spiaSp, "Spia");
        if ((flags & R_ANNI) != 0) { in.y0 = leggi(y0Sp, "Anno inizio"); in.y1 = leggi(y1Sp, "Anno fine"); }
        else { in.y0 = minYear; in.y1 = maxYear; }
        if ((flags & R_COLPI) != 0) in.colpi = leggi(colpiSp, "Colpi");
        if ((flags & R_DIM) != 0) {
            in.dim = dimBox.getSelectionModel().getSelectedIndex() + 2;
            in.modalita = modBox.getValue();
        }
        if ((flags & R_RUOTESEL) != 0) {
            for (CheckBox cb : ruoteChecks) if (cb.isSelected()) in.ruoteSel.add(cb.getText());
        }
        if ((flags & R_SOMMA) != 0) {
            in.ruota2 = ruota2Box.getValue();
            in.pos1 = leggi(pos1Sp, "Posizione 1ª ruota");
            in.pos2 = leggi(pos2Sp, "Posizione 2ª ruota");
        }
        if ((flags & R_FISSO) != 0) in.fisso = leggi(fissoSp, "Fisso");
        if ((flags & R_TARGET) != 0) in.target = targetBox.getValue();
        if ((flags & R_WHEELB) != 0) in.wheelB = wheelBBox.getValue();
        if ((flags & R_POSIZ) != 0) in.posSpia = leggi(posSpiaSp, "Posizione della spia");
        if ((flags & R_MODMULTI) != 0) in.modalita = multiModBox.getValue();
        if ((flags & R_NGIOCO) != 0) in.nGioco = leggi(nGiocoSp, "Numeri da giocare");
        if ((flags & R_ANNOTEST) != 0) in.annoTest = leggi(annoTestSp, "Anno del test");
        if ((flags & R_TIPOGIOCO) != 0) in.tipoGioco = tipoGiocoBox.getSelectionModel().getSelectedIndex() + 2;
        if ((flags & R_MULTI) != 0) {
            for (String part : multiField.getText().trim().split("[^0-9]+")) {
                if (part.isEmpty()) continue;
                int n;
                try { n = Integer.parseInt(part); } catch (NumberFormatException ex) { n = -1; }
                if (n < 1 || n > 90) throw new IllegalArgumentException("Ogni spia deve essere tra 1 e 90.");
                if (!in.spie.contains(n)) in.spie.add(n);
            }
            if (in.spie.isEmpty()) throw new IllegalArgumentException("Inserisci almeno una spia.");
        }
        return in;
    }

    private void esegui() {
        final int idx = Math.max(0, tecBox.getSelectionModel().getSelectedIndex());
        final TecnicheRunner.In in;
        try {
            in = leggiParametri(idx);
        } catch (IllegalArgumentException ex) {
            out.setText("ERRORE\n\n" + ex.getMessage());
            stato.setText("Controlla i parametri inseriti.");
            btnHighlight.setDisable(true);
            btnGiocata.setDisable(true);
            btnSave.setDisable(true);
            return;
        }
        final List<Extraction> arch = new ArrayList<>(host.archive());
        out.setText("Calcolo in corso…");
        stato.setText("");
        btnRun.setDisable(true);
        btnHighlight.setDisable(true);
        btnGiocata.setDisable(true);
        btnSave.setDisable(true);
        ultimaSpec = null;

        new Thread(() -> {
            TecnicheRunner.Result res;
            String err = null;
            try {
                res = runner.calcola(idx, in, arch);
            } catch (IllegalArgumentException ex) {
                res = null;
                err = "ERRORE\n\n" + ex.getMessage();
            } catch (Throwable ex) {
                res = null;
                err = "ERRORE durante il calcolo\n\n" + ex;
            }
            final TecnicheRunner.Result fr = res;
            final String fe = err;
            Platform.runLater(() -> {
                btnRun.setDisable(false);
                if (fr == null) {
                    out.setText(fe);
                    stato.setText("Calcolo non eseguito.");
                } else {
                    out.setText(fr.text);
                    // Il report viene condiviso con Gemini Chat per permettere spiegazioni basate sul risultato reale.
                    ChatAssistantDialog.registraRisultatoTecnica(NOMI[idx], fr.text);
                    out.positionCaret(0);
                    ultimaSpec = fr.spec;
                    btnHighlight.setDisable(fr.spec == null);
                    btnGiocata.setDisable(fr.spec == null || fr.spec.numbers.isEmpty());
                    btnSave.setDisable(false);
                    stato.setText(fr.spec == null ? "" : "Pronto: usa il pulsante per colorare l'archivio.");
                    NeonAssistant.show(host.owner(), "Ho completato l'analisi della tecnica selezionata. I risultati sono pronti nella finestra.", host.css());
                }
            });
        }, "tecnica").start();
    }

}
