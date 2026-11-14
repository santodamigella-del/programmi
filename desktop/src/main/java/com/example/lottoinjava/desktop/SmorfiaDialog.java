package com.example.lottoinjava.desktop;

import com.example.lottoinjava.analysis.SmorfiaCore;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

/** Smorfia napoletana: scrivi il sogno e ottieni i numeri del prontuario (nessuna statistica). */
final class SmorfiaDialog {

    private SmorfiaDialog() { }

    static void show(TecnicheDialog.Host host) {
        Label title = new Label("💭 Smorfia - Spia sogno");
        title.getStyleClass().add("title");
        Label sub = new Label("Scrivi il sogno: il prontuario offline cerca i simboli associati.");
        sub.getStyleClass().add("subtitle");

        TextArea sogno = new TextArea();
        sogno.setPromptText("Es.: Ho sognato un cane, il mare e una persona morta...");
        sogno.setWrapText(true);
        sogno.setPrefRowCount(4);

        TextArea out = new TextArea();
        out.setEditable(false);
        out.setWrapText(true);
        out.getStyleClass().add("report");
        VBox.setVgrow(out, Priority.ALWAYS);

        final List<Integer>[] numeri = new List[]{new ArrayList<Integer>()};
        Button btnInterpreta = new Button("▶ Interpreta");
        btnInterpreta.getStyleClass().add("button-primary");
        Button btnPulisci = new Button("Pulisci");
        Button btnGiocata = new Button("🎯 Crea giocata");
        btnGiocata.setDisable(true);
        Button btnCopy = new Button("📋 Copia");
        Button btnClose = new Button("Chiudi");
        HBox bottoni = new HBox(8, btnInterpreta, btnPulisci, btnGiocata, btnCopy, btnClose);
        bottoni.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, title, sub, sogno, bottoni, out);
        root.setPadding(new Insets(16));

        Stage d = new Stage();
        d.initOwner(host.owner());
        d.initModality(Modality.APPLICATION_MODAL);
        d.setTitle("Smorfia");
        Scene sc = new Scene(root, 760, 700);
        sc.getStylesheets().add(host.css());
        d.setScene(sc);

        btnInterpreta.setOnAction(e -> {
            String testo = sogno.getText() == null ? "" : sogno.getText().trim();
            if (testo.isEmpty()) {
                out.setText("Inserisci prima un sogno o un evento.");
                btnGiocata.setDisable(true);
                return;
            }
            List<SmorfiaCore.Risultato> r = SmorfiaCore.interpreta(testo, 12);
            out.setText(SmorfiaCore.rapporto(testo, r));
            numeri[0] = new ArrayList<>();
            for (SmorfiaCore.Risultato x : r) numeri[0].add(x.numero);
            btnGiocata.setDisable(numeri[0].isEmpty());
        });
        btnPulisci.setOnAction(e -> { sogno.clear(); out.clear(); btnGiocata.setDisable(true); });
        btnGiocata.setOnAction(e -> {
            if (!numeri[0].isEmpty()) {
                List<Integer> n = new ArrayList<>(numeri[0].subList(0, Math.min(10, numeri[0].size())));
                d.close();
                host.creaGiocata("Tutte", n, 9);
            }
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
