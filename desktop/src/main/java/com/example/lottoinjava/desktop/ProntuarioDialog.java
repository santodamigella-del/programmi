package com.example.lottoinjava.desktop;

import com.example.lottoinjava.desktop.ProntuarioVincite.Sorte;
import com.example.lottoinjava.model.Extraction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prontuario vincite del Lotto: tabella delle quote, calcolatore e controllo di una giocata
 * sulle estrazioni dell'archivio. Gli importi sono stime lorde/nette: vale quanto paga la ricevitoria.
 */
public final class ProntuarioDialog {

    private ProntuarioDialog() { }

    public static void show(Stage owner, String css, List<Extraction> source) {
        List<Extraction> archive = new ArrayList<>(source == null ? List.<Extraction>of() : source);
        archive.sort(Comparator.comparingInt(Extraction::getDateKey).reversed());

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("💰 Prontuario vincite Lotto");

        Label title = new Label("💰 Prontuario vincite");
        title.getStyleClass().add("title");
        Label sub = new Label("Importi per ruota singola e puntata di 1 €. Sono stime: l'importo ufficiale è quello pagato dalla ricevitoria.");
        sub.getStyleClass().add("subtitle");
        sub.setWrapText(true);

        // ---- imposta (comune a Calcola e Controlla)
        TextField aliquota = new TextField("8");
        aliquota.setPrefColumnCount(4);
        TextField franchigia = new TextField("0");
        franchigia.setPrefColumnCount(5);
        Label taxNote = new Label("Imposta modificabile: le fonti non concordano (8% su tutta la vincita, oppure 8% solo sulla parte oltre 500 €; "
                + "dal 2026 sono in corso modifiche). Controlla le regole in vigore con la ricevitoria.");
        taxNote.setWrapText(true);
        HBox taxRow = new HBox(8, new Label("Imposta %"), aliquota, new Label("Esente fino a (€)"), franchigia);
        taxRow.setAlignment(Pos.CENTER_LEFT);
        VBox taxBox = new VBox(4, taxRow, taxNote);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(tabQuote());
        tabs.getTabs().add(tabCalcola(aliquota, franchigia));
        tabs.getTabs().add(tabControlla(archive, aliquota, franchigia));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox root = new VBox(10, title, sub, tabs, taxBox);
        root.setPadding(new Insets(14));
        Scene scene = new Scene(root, 880, 700);
        scene.getStylesheets().add(css);
        stage.setScene(scene);
        stage.show();
    }

    // ------------------------------------------------------------------ tab 1: quote

    private static Tab tabQuote() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(6);
        g.setPadding(new Insets(10));
        Sorte[] sorti = Sorte.values();
        g.add(bold("Numeri giocati"), 0, 0);
        for (int c = 0; c < sorti.length; c++) g.add(bold(sorti[c].etichetta), c + 1, 0);
        Map<Integer, Map<Sorte, BigDecimal>> t = ProntuarioVincite.tabella();
        for (int n = 1; n <= ProntuarioVincite.MAX_NUMERI; n++) {
            g.add(new Label(String.valueOf(n)), 0, n);
            for (int c = 0; c < sorti.length; c++) {
                BigDecimal q = t.get(n).get(sorti[c]);
                g.add(new Label(q == null ? "—" : ProntuarioVincite.euro(q)), c + 1, n);
            }
        }
        Label nota = new Label("Importi LORDI per ogni combinazione vincente, con 1 € su una ruota.\n"
                + "• Giocando più numeri la puntata si divide tra le combinazioni (5 numeri = 10 ambi, 10 terni, 5 quaterne, 1 cinquina).\n"
                + "• Su più ruote la vincita si divide per il numero di ruote (10 con \"tutte\", la Nazionale è a parte).\n"
                + "• Si possono giocare al massimo 10 numeri.");
        nota.setWrapText(true);
        VBox box = new VBox(12, g, nota);
        box.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        Tab tab = new Tab("📋 Quote", sp);
        return tab;
    }

    // ------------------------------------------------------------------ tab 2: calcolatore

    private static Tab tabCalcola(TextField aliquota, TextField franchigia) {
        ComboBox<Integer> numeri = new ComboBox<>();
        for (int i = 1; i <= ProntuarioVincite.MAX_NUMERI; i++) numeri.getItems().add(i);
        numeri.setValue(2);
        ComboBox<String> ruote = new ComboBox<>();
        for (int i = 1; i <= 10; i++) ruote.getItems().add(i == 10 ? "Tutte le ruote (10)" : i + (i == 1 ? " ruota" : " ruote"));
        ruote.setValue("1 ruota");
        TextField puntata = new TextField("1");
        puntata.setPrefColumnCount(6);
        VBox out = new VBox(6);

        Runnable calc = () -> {
            out.getChildren().clear();
            BigDecimal p = money(puntata.getText());
            BigDecimal aliq = money(aliquota.getText());
            BigDecimal fr = money(franchigia.getText());
            if (p == null || p.signum() <= 0 || aliq == null || fr == null) {
                out.getChildren().add(new Label("Controlla i valori inseriti (puntata, imposta, esenzione)."));
                return;
            }
            int n = numeri.getValue();
            int r = ruote.getSelectionModel().getSelectedIndex() + 1;
            out.getChildren().add(bold("Se esce UNA combinazione vincente (puntata " + ProntuarioVincite.euro(p) + " su ogni sorte):"));
            for (Sorte s : Sorte.values()) {
                BigDecimal v = ProntuarioVincite.vincita(n, s, p, r);
                if (v == null) continue;
                BigDecimal tax = ProntuarioVincite.imposta(v, aliq, fr);
                out.getChildren().add(new Label(String.format("%s — lordo %s · imposta %s · netto %s",
                        s.etichetta, ProntuarioVincite.euro(v), ProntuarioVincite.euro(tax), ProntuarioVincite.euro(v.subtract(tax)))));
            }
        };
        numeri.setOnAction(e -> calc.run());
        ruote.setOnAction(e -> calc.run());
        puntata.setOnAction(e -> calc.run());
        Button go = new Button("Calcola");
        go.getStyleClass().add("button-primary");
        go.setOnAction(e -> calc.run());
        calc.run();

        HBox form = new HBox(10, new Label("Numeri giocati"), numeri, new Label("Ruote"), ruote, new Label("Puntata € per sorte"), puntata, go);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(4, 0, 4, 0));
        VBox box = new VBox(12, form, out);
        box.setPadding(new Insets(10));
        return new Tab("🧮 Calcola", box);
    }

    // ------------------------------------------------------------------ tab 3: controlla una giocata

    private static Tab tabControlla(List<Extraction> archive, TextField aliquota, TextField franchigia) {
        TextField numeri = new TextField();
        numeri.setPromptText("es. 12 45 67 (da 1 a 10 numeri)");
        numeri.setPrefColumnCount(22);

        FlowPane ruoteBox = new FlowPane(10, 6);
        List<CheckBox> checks = new ArrayList<>();
        for (String r : Extraction.ALL_RUOTE) {
            CheckBox cb = new CheckBox(r);
            checks.add(cb);
            ruoteBox.getChildren().add(cb);
        }
        checks.get(0).setSelected(true);
        Button tutte = new Button("Tutte (10)");
        tutte.setOnAction(e -> {
            for (CheckBox cb : checks) cb.setSelected(!cb.getText().equalsIgnoreCase("Nazionale"));
        });
        ruoteBox.getChildren().add(tutte);

        Map<Sorte, TextField> puntate = new EnumMap<>(Sorte.class);
        HBox stakes = new HBox(10);
        stakes.setAlignment(Pos.CENTER_LEFT);
        for (Sorte s : Sorte.values()) {
            if (s == Sorte.ESTRATTO_DET) continue;
            TextField tf = new TextField(s == Sorte.AMBO ? "1" : "");
            tf.setPrefColumnCount(4);
            puntate.put(s, tf);
            stakes.getChildren().addAll(new Label(s.etichetta.replace(" (estratto)", "")), tf);
        }

        ComboBox<String> date = new ComboBox<>();
        for (Extraction e : archive) date.getItems().add(e.getShortDate());
        if (!date.getItems().isEmpty()) date.getSelectionModel().select(0);

        TextArea result = new TextArea();
        result.setEditable(false);
        result.setWrapText(true);
        VBox.setVgrow(result, Priority.ALWAYS);

        Button check = new Button("Controlla la giocata");
        check.getStyleClass().add("button-primary");
        check.setOnAction(e -> {
            if (archive.isEmpty()) { result.setText("L'archivio Lotto è vuoto: importa o aggiorna le estrazioni."); return; }
            List<Integer> nums = numbers(numeri.getText());
            if (nums == null || nums.isEmpty() || nums.size() > ProntuarioVincite.MAX_NUMERI) {
                result.setText("Inserisci da 1 a 10 numeri distinti tra 1 e 90.");
                return;
            }
            List<String> ruote = new ArrayList<>();
            for (CheckBox cb : checks) if (cb.isSelected()) ruote.add(cb.getText());
            if (ruote.isEmpty()) { result.setText("Seleziona almeno una ruota."); return; }
            Map<Sorte, BigDecimal> stake = new EnumMap<>(Sorte.class);
            for (Map.Entry<Sorte, TextField> en : puntate.entrySet()) {
                String txt = en.getValue().getText().trim();
                if (txt.isEmpty()) continue;
                BigDecimal v = money(txt);
                if (v == null || v.signum() < 0) { result.setText("Importo non valido per " + en.getKey().etichetta + "."); return; }
                stake.put(en.getKey(), v);
            }
            if (stake.isEmpty()) { result.setText("Indica l'importo su almeno una sorte."); return; }
            BigDecimal aliq = money(aliquota.getText());
            BigDecimal fr = money(franchigia.getText());
            if (aliq == null || fr == null) { result.setText("Controlla imposta ed esenzione."); return; }

            int idx = Math.max(0, date.getSelectionModel().getSelectedIndex());
            Extraction ex = archive.get(idx);
            ProntuarioVincite.Esito es = ProntuarioVincite.verifica(ex, ruote, nums, stake);
            BigDecimal tax = ProntuarioVincite.imposta(es.lordo, aliq, fr);
            StringBuilder sb = new StringBuilder();
            sb.append("Estrazione del ").append(ex.getShortDate()).append(" — numeri giocati: ").append(nums)
              .append(" su ").append(ruote.size() == 1 ? ruote.get(0) : ruote.size() + " ruote").append("\n\n");
            for (String line : es.dettaglio) sb.append(line).append('\n');
            sb.append('\n');
            if (es.lordo.signum() == 0) {
                sb.append("Nessuna vincita su questa estrazione.");
            } else {
                sb.append("Vincita lorda:  ").append(ProntuarioVincite.euro(es.lordo)).append('\n')
                  .append("Imposta:        ").append(ProntuarioVincite.euro(tax)).append('\n')
                  .append("Netto stimato:  ").append(ProntuarioVincite.euro(es.lordo.subtract(tax)));
            }
            result.setText(sb.toString());
        });

        Label help = new Label("Scegli i numeri, le ruote e quanto hai puntato su ogni sorte, poi la data dell'estrazione da verificare "
                + "(di solito l'ultima). L'estratto determinato non è incluso perché richiede anche la posizione di uscita.");
        help.setWrapText(true);
        VBox box = new VBox(10, help,
                row("Numeri", numeri), new Label("Ruote"), ruoteBox,
                new Label("Puntata totale per sorte (€) — si divide tra le ruote scelte"), stakes,
                row("Estrazione", date), check, result);
        box.setPadding(new Insets(10));
        return new Tab("🎯 Controlla giocata", box);
    }

    // ------------------------------------------------------------------ utilità

    private static HBox row(String label, javafx.scene.Node n) {
        HBox h = new HBox(10, new Label(label), n);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static Label bold(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    /** "12,50" o "12.50" -> BigDecimal; null se non valido. */
    private static BigDecimal money(String s) {
        if (s == null) return null;
        try { return new BigDecimal(s.trim().replace(',', '.')); } catch (Exception e) { return null; }
    }

    /** Numeri 1-90 distinti separati da spazi, virgole o trattini; null se qualcosa non va. */
    private static List<Integer> numbers(String text) {
        Set<Integer> set = new LinkedHashSet<>();
        if (text == null) return null;
        for (String t : text.trim().split("[^0-9]+")) {
            if (t.isEmpty()) continue;
            int n;
            try { n = Integer.parseInt(t); } catch (Exception e) { return null; }
            if (n < 1 || n > 90 || !set.add(n)) return null;
        }
        return new ArrayList<>(set);
    }
}
