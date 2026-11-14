package com.example.lottoinjava.desktop;

import com.example.lottoinjava.analysis.SantoSpiaCore;
import com.example.lottoinjava.model.Extraction;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/** Assistente Gemini IA: report in italiano sulla spia scelta (serve una chiave API personale). */
final class GeminiDialog {

    private GeminiDialog() { }

    private static int leggi(Spinner<Integer> sp, String nome) {
        try {
            return Integer.parseInt(sp.getEditor().getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valore non valido per \"" + nome + "\".");
        }
    }

    static void show(TecnicheDialog.Host host) {
        final List<Extraction> arch = new ArrayList<>(host.archive());
        if (arch.isEmpty()) return;
        int mn = 9999, mx = 0;
        for (Extraction e : arch) { mn = Math.min(mn, e.getYear()); mx = Math.max(mx, e.getYear()); }

        Label title = new Label("🤖 Gemini IA - Assistente previsioni Lotto");
        title.getStyleClass().add("title");
        Label chiaveStato = new Label("Chiave API Gemini: " + DesktopStorage.geminiKeyStato());
        chiaveStato.getStyleClass().add("subtitle");

        ComboBox<String> ruotaBox = new ComboBox<>(FXCollections.observableArrayList(Extraction.ALL_RUOTE));
        ruotaBox.setValue("Bari");
        Spinner<Integer> spia = new Spinner<>(1, 90, 11);
        spia.setEditable(true);
        spia.setPrefWidth(90);
        Spinner<Integer> y0 = new Spinner<>(mn, mx, Math.max(mn, mx - 5));
        y0.setEditable(true);
        y0.setPrefWidth(100);
        Spinner<Integer> y1 = new Spinner<>(mn, mx, mx);
        y1.setEditable(true);
        y1.setPrefWidth(100);
        Spinner<Integer> colpi = new Spinner<>(1, 50, 9);
        colpi.setEditable(true);
        colpi.setPrefWidth(80);

        HBox riga1 = new HBox(10, new Label("Ruota:"), ruotaBox, new Label("Spia:"), spia);
        HBox riga2 = new HBox(10, new Label("Anno inizio:"), y0, new Label("Anno fine:"), y1, new Label("Colpi:"), colpi);
        riga1.setAlignment(Pos.CENTER_LEFT);
        riga2.setAlignment(Pos.CENTER_LEFT);

        TextArea out = new TextArea();
        out.setEditable(false);
        out.setWrapText(true);
        out.getStyleClass().add("report");
        VBox.setVgrow(out, Priority.ALWAYS);

        Button btnChiave = new Button("🔑 Chiave API");
        Button btnRun = new Button("▶ Genera report");
        btnRun.getStyleClass().add("button-primary");
        Button btnCopy = new Button("📋 Copia");
        Button btnClose = new Button("Chiudi");
        HBox bottoni = new HBox(8, btnRun, btnChiave, btnCopy, btnClose);
        bottoni.setAlignment(Pos.CENTER_LEFT);
        Label nota = new Label("I dati partono dal modello post-spia del programma; Gemini scrive solo il commento. Le estrazioni restano casuali.");
        nota.setWrapText(true);
        nota.getStyleClass().add("hint");

        VBox root = new VBox(10, title, chiaveStato, riga1, riga2, bottoni, nota, out);
        root.setPadding(new Insets(16));

        Stage d = new Stage();
        d.initOwner(host.owner());
        d.initModality(Modality.APPLICATION_MODAL);
        d.setTitle("Gemini IA");
        Scene sc = new Scene(root, 820, 720);
        sc.getStylesheets().add(host.css());
        d.setScene(sc);

        btnRun.setOnAction(e -> {
            final String ruota = ruotaBox.getValue();
            final int s, a0, a1, c;
            try {
                s = leggi(spia, "Spia");
                a0 = leggi(y0, "Anno inizio");
                a1 = leggi(y1, "Anno fine");
                c = leggi(colpi, "Colpi");
                SantoSpiaCore.validaParametri(s, ruota, a0, a1, c);
                SantoSpiaCore.validaDisponibilita(arch, ruota, a0, a1);
            } catch (IllegalArgumentException ex) {
                out.setText("ERRORE\n\n" + ex.getMessage());
                return;
            }
            out.setText("Calcolo e richiesta a Gemini in corso…");
            btnRun.setDisable(true);
            new Thread(() -> {
                String testo;
                try {
                    SantoSpiaCore.Modello m = SantoSpiaCore.costruisciModello(arch, s, ruota, a0, a1, c);
                    if (m.nEventi() == 0) {
                        testo = "ERRORE\n\nNessun ciclo post-spia utile per la ruota e il periodo scelti.";
                    } else {
                        List<Integer> top = SantoSpiaCore.topPost(m, 6);
                        testo = new GeminiClient(GeminiClient.BASE_DEFAULT)
                                .consulenza("LOTTO", s, ruota, top, DesktopStorage.geminiKey());
                    }
                } catch (Throwable ex) {
                    testo = "ERRORE durante la richiesta\n\n" + ex;
                }
                final String fine = testo;
                Platform.runLater(() -> { out.setText(fine); btnRun.setDisable(false); });
            }, "gemini").start();
        });

        btnChiave.setOnAction(e -> {
            Label l = new Label("Incolla la chiave API di Google AI Studio (aistudio.google.com/app/apikey).\n"
                    + "Resta salvata solo su questo computer (file con permessi riservati). Puoi anche usare la variabile GEMINI_API_KEY.");
            l.setWrapText(true);
            PasswordField pf = new PasswordField();
            pf.setPromptText("Chiave API");
            Label esito = new Label("");
            esito.setWrapText(true);
            Button salva = new Button("Salva");
            salva.getStyleClass().add("button-primary");
            Button prova = new Button("Prova");
            Button rimuovi = new Button("Rimuovi");
            Button chiudi = new Button("Chiudi");
            HBox b = new HBox(8, salva, prova, rimuovi, chiudi);
            VBox box = new VBox(10, l, pf, b, esito);
            box.setPadding(new Insets(16));
            Stage k = new Stage();
            k.initOwner(d);
            k.initModality(Modality.APPLICATION_MODAL);
            k.setTitle("Chiave API Gemini");
            Scene ks = new Scene(box, 560, 260);
            ks.getStylesheets().add(host.css());
            k.setScene(ks);
            salva.setOnAction(ev -> {
                boolean ok = DesktopStorage.saveGeminiKey(pf.getText());
                esito.setText(ok ? "Chiave salvata." : "Salvataggio non riuscito.");
                chiaveStato.setText("Chiave API Gemini: " + DesktopStorage.geminiKeyStato());
                pf.clear();
            });
            rimuovi.setOnAction(ev -> {
                DesktopStorage.saveGeminiKey("");
                esito.setText("Chiave rimossa.");
                chiaveStato.setText("Chiave API Gemini: " + DesktopStorage.geminiKeyStato());
            });
            prova.setOnAction(ev -> {
                final String chiave = pf.getText().trim().isEmpty() ? DesktopStorage.geminiKey() : pf.getText().trim();
                esito.setText("Prova in corso…");
                new Thread(() -> {
                    GeminiClient.Esito r = new GeminiClient(GeminiClient.BASE_DEFAULT).chiama("Rispondi solo con la parola OK", chiave);
                    Platform.runLater(() -> esito.setText(r.ok ? "Gemini risponde: " + r.testo : "Prova non riuscita: " + r.testo));
                }, "gemini-test").start();
            });
            chiudi.setOnAction(ev -> k.close());
            k.show();
        });
        btnCopy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(out.getText());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        btnClose.setOnAction(e -> d.close());
        d.show();
    }
}
