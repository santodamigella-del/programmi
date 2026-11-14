package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Button;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Quadro estrazionale: due viste dell'archivio.
 *  - Quadro ruote × date: le ultime estrazioni in tabella, con gli stessi colori dell'archivio
 *    (spia verde, risultato rosso, più usciti blu scuro).
 *  - Tabellone 1–90: mappa di calore per frequenza o ritardo, con i 5 numeri in testa evidenziati.
 * Sono dati storici descrittivi: non cambiano la probabilità delle estrazioni future.
 */
final class QuadroEstrazionaleDialog {

    private static final String TUTTE = "Tutte le ruote";
    private static final String MODO_TECNICA = "Evidenziazione tecnica";
    private static final String MODO_TOP = "Più usciti (blu scuro)";
    private static final String MODO_NESSUNO = "Nessun colore";

    private QuadroEstrazionaleDialog() { }

    static void show(Stage owner, String css, List<Extraction> source, HighlightSpec highlight) {
        if (source == null || source.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "L'archivio Lotto è vuoto. Importa o aggiorna prima le estrazioni.");
            a.initOwner(owner);
            a.showAndWait();
            return;
        }
        List<Extraction> archive = new ArrayList<>(source);
        archive.sort(Comparator.comparingInt(Extraction::getDateKey)); // cronologico: la più recente in fondo

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Quadro estrazionale");

        TabPane tabs = new TabPane();
        Tab t1 = new Tab("🧮 Quadro ruote × date", quadroTab(archive, highlight));
        t1.setClosable(false);
        Tab t2 = new Tab("🔢 Tabellone 1–90", tabelloneTab(archive));
        t2.setClosable(false);
        tabs.getTabs().addAll(t1, t2);

        Scene scene = new Scene(new BorderPane(tabs), 1180, 760);
        if (css != null && !css.isBlank()) scene.getStylesheets().add(css);
        stage.setScene(scene);
        stage.show();
    }

    // ------------------------------------------------------------------ quadro ruote × date

    private static Node quadroTab(List<Extraction> archive, HighlightSpec hl) {
        List<String> scopes = new ArrayList<>();
        scopes.add(TUTTE);
        scopes.addAll(Extraction.ALL_RUOTE);
        ComboBox<String> ruota = new ComboBox<>(FXCollections.observableArrayList(scopes));
        ruota.setValue(TUTTE);
        ComboBox<String> righe = new ComboBox<>(FXCollections.observableArrayList(
                "Ultime 10", "Ultime 20", "Ultime 30", "Ultime 50", "Ultime 100"));
        righe.setValue("Ultime 20");
        List<String> modi = hl != null
                ? Arrays.asList(MODO_TECNICA, MODO_TOP, MODO_NESSUNO)
                : Arrays.asList(MODO_TOP, MODO_NESSUNO);
        ComboBox<String> modo = new ComboBox<>(FXCollections.observableArrayList(modi));
        modo.setValue(modi.get(0));
        CheckBox solo = new CheckBox("Solo date evidenziate");
        solo.setDisable(hl == null);

        Label info = new Label();
        info.setWrapText(true);
        info.getStyleClass().add("hint");
        Label stato = new Label();
        stato.getStyleClass().add("subtitle");
        final List<Extraction> ultimeRighe = new ArrayList<>();
        final List<String> ultimeColonne = new ArrayList<>();

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setPannable(true);

        Runnable render = () -> {
            grid.getChildren().clear();
            List<String> cols = TUTTE.equals(ruota.getValue())
                    ? new ArrayList<>(Extraction.ALL_RUOTE)
                    : Collections.singletonList(ruota.getValue());

            List<Extraction> pool = new ArrayList<>();
            for (Extraction e : archive) {
                if (!solo.isSelected() || hl == null || hl.isVisibleDate(e.getDateKey())) pool.add(e);
            }
            int n = Integer.parseInt(righe.getValue().replace("Ultime ", ""));
            List<Extraction> shown = new ArrayList<>(pool.subList(Math.max(0, pool.size() - n), pool.size()));
            Collections.reverse(shown); // la più recente in alto

            int[] freq = new int[91];
            for (Extraction e : shown) {
                for (String w : cols) {
                    List<Integer> nums = e.getNumbersForRuota(w);
                    if (nums == null) continue;
                    for (int x : nums) if (x >= 1 && x <= 90) freq[x]++;
                }
            }
            int quantiBlu = HighlightSpec.quantiBlu(5);
            List<Integer> top = topN(freq, quantiBlu);
            ultimeRighe.clear();
            ultimeRighe.addAll(shown);
            ultimeColonne.clear();
            ultimeColonne.addAll(cols);
            boolean usaTecnica = hl != null && MODO_TECNICA.equals(modo.getValue());
            boolean usaTop = MODO_TOP.equals(modo.getValue());

            grid.add(intestazione("DATA"), 0, 0);
            for (int c = 0; c < cols.size(); c++) grid.add(intestazione(cols.get(c).toUpperCase()), c + 1, 0);
            for (int r = 0; r < shown.size(); r++) {
                Extraction e = shown.get(r);
                Label d = new Label(e.getShortDate());
                d.getStyleClass().add("card-date");
                grid.add(d, 0, r + 1);
                for (int c = 0; c < cols.size(); c++) {
                    String w = cols.get(c);
                    List<Integer> nums = e.getNumbersForRuota(w);
                    HBox cell = new HBox(3);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    if (nums != null) {
                        for (int x : nums) {
                            String classe = null;
                            if (usaTecnica) {
                                int key = e.getDateKey();
                                if (hl.isSpyBall(w, x, key)) classe = "ball-spy";
                                else if (hl.isCinquinaBall(w, x, key)) classe = "ball-cinquina";
                                else if (hl.isResultBall(w, x, key)) classe = "ball-result";
                            } else if (usaTop && top.contains(x)) {
                                classe = "ball-cinquina";
                            }
                            cell.getChildren().add(pallina(x, classe));
                        }
                    }
                    grid.add(cell, c + 1, r + 1);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Estrazioni mostrate: ").append(shown.size()).append(" · colonne: ").append(cols.size());
            if (usaTecnica) {
                sb.append("\nSpia verde, risultato rosso, più usciti della tecnica blu scuro: «").append(hl.title).append("».");
            } else if (usaTop) {
                sb.append("\nIn blu scuro i ").append(quantiBlu).append(" numeri più usciti in questo quadro: ").append(top);
            }
            sb.append("\nDati storici descrittivi: non indicano numeri più probabili alla prossima estrazione.");
            info.setText(sb.toString());
        };
        ruota.setOnAction(e -> render.run());
        righe.setOnAction(e -> render.run());
        modo.setOnAction(e -> render.run());
        solo.setOnAction(e -> render.run());
        render.run();

        Button btnPng = new Button("🖼 Salva PNG");
        btnPng.setOnAction(e -> stato.setText(salvaPng(grid, "quadro_estrazionale")));
        Button btnCsv = new Button("📄 Salva CSV");
        btnCsv.setOnAction(e -> stato.setText(salvaCsv(grid, "quadro_estrazionale", () -> csvQuadro(ultimeRighe, ultimeColonne))));
        HBox controls = new HBox(10, new Label("Ruota:"), ruota, new Label("Righe:"), righe,
                new Label("Colori:"), modo, solo, btnPng, btnCsv);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox topBox = new VBox(8, controls, info, stato);
        topBox.setPadding(new Insets(12));
        BorderPane root = new BorderPane(scroll);
        root.setTop(topBox);
        return root;
    }

    // ------------------------------------------------------------------ tabellone 1-90

    private static Node tabelloneTab(List<Extraction> archive) {
        List<String> scopes = new ArrayList<>();
        scopes.add(TUTTE);
        scopes.addAll(Extraction.ALL_RUOTE);
        ComboBox<String> ruota = new ComboBox<>(FXCollections.observableArrayList(scopes));
        ruota.setValue(TUTTE);
        ComboBox<String> finestra = new ComboBox<>(FXCollections.observableArrayList(
                "Ultime 50", "Ultime 100", "Ultime 300", "Ultime 500", "Tutte"));
        finestra.setValue("Ultime 300");
        ComboBox<String> modo = new ComboBox<>(FXCollections.observableArrayList("Frequenza", "Ritardo"));
        modo.setValue("Frequenza");

        Label info = new Label();
        info.setWrapText(true);
        info.getStyleClass().add("hint");
        Label stato = new Label();
        stato.getStyleClass().add("subtitle");
        final int[][] ultimo = new int[2][];
        Label dettaglio = new Label("Clicca un numero per vedere uscite, ritardo e ultime date.");
        dettaglio.setWrapText(true);
        dettaglio.getStyleClass().add("strong");

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setPadding(new Insets(6));

        Runnable render = () -> {
            grid.getChildren().clear();
            List<String> cols = TUTTE.equals(ruota.getValue())
                    ? new ArrayList<>(Extraction.ALL_RUOTE)
                    : Collections.singletonList(ruota.getValue());
            int total = archive.size();
            int richieste = "Tutte".equals(finestra.getValue()) ? total
                    : Integer.parseInt(finestra.getValue().replace("Ultime ", ""));
            final List<Extraction> win = archive.subList(Math.max(0, total - Math.min(richieste, total)), total);

            final int[] freq = new int[91];
            final int[] ritardo = new int[91];
            Arrays.fill(ritardo, win.size()); // mai uscito nella finestra: ritardo = tutta la finestra
            for (int i = 0; i < win.size(); i++) {
                boolean[] presente = new boolean[91];
                for (String w : cols) {
                    List<Integer> nums = win.get(i).getNumbersForRuota(w);
                    if (nums == null) continue;
                    for (int x : nums) if (x >= 1 && x <= 90) { freq[x]++; presente[x] = true; }
                }
                for (int x = 1; x <= 90; x++) if (presente[x]) ritardo[x] = win.size() - 1 - i;
            }

            final boolean isRitardo = "Ritardo".equals(modo.getValue());
            final int[] valore = isRitardo ? ritardo : freq;
            int max = 1;
            for (int x = 1; x <= 90; x++) max = Math.max(max, valore[x]);
            int quantiBlu = HighlightSpec.quantiBlu(5);
            List<Integer> top = topN(valore, quantiBlu);
            ultimo[0] = freq;
            ultimo[1] = ritardo;

            for (int x = 1; x <= 90; x++) {
                final int numero = x;
                VBox cell = new VBox(0);
                cell.setAlignment(Pos.CENTER);
                cell.setMinSize(84, 54);
                cell.setPrefSize(84, 54);
                cell.setMaxSize(84, 54);
                Label num = new Label(String.format(Locale.ROOT, "%02d", x));
                num.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
                Label sub = new Label(isRitardo ? valore[x] + " est." : valore[x] + "×");
                sub.setStyle("-fx-font-size: 10px;");
                cell.getChildren().addAll(num, sub);

                String base = "-fx-background-radius: 10; -fx-border-radius: 10; -fx-cursor: hand;";
                if (top.contains(x)) {
                    // Frequenza: i più usciti in blu scuro; ritardo: i più in ritardo in rosso.
                    cell.getStyleClass().add(isRitardo ? "ball-result" : "ball-cinquina");
                    cell.setStyle(base);
                } else {
                    double ratio = valore[x] / (double) max;
                    String rgb = isRitardo ? "239,68,68" : "251,191,36";
                    cell.setStyle(base + " -fx-border-color: -c-border; -fx-background-color: rgba("
                            + rgb + "," + String.format(Locale.ROOT, "%.2f", 0.06 + 0.64 * ratio) + ");");
                }
                cell.setOnMouseClicked(ev -> dettaglio.setText(descrivi(numero, win, cols, freq, ritardo)));
                grid.add(cell, (x - 1) % 10, (x - 1) / 10);
            }
            info.setText("Estrazioni analizzate: " + win.size() + " su " + total
                    + " · " + (isRitardo
                        ? "ritardo = estrazioni trascorse dall'ultima uscita; in rosso i " + quantiBlu + " più in ritardo."
                        : "frequenza = quante volte è uscito; in blu scuro i " + quantiBlu + " più usciti.")
                    + "\nDati storici descrittivi: un numero in ritardo non è più probabile.");
        };
        ruota.setOnAction(e -> render.run());
        finestra.setOnAction(e -> render.run());
        modo.setOnAction(e -> render.run());
        render.run();

        Button btnPng = new Button("🖼 Salva PNG");
        btnPng.setOnAction(e -> stato.setText(salvaPng(grid, "tabellone_1_90")));
        Button btnCsv = new Button("📄 Salva CSV");
        btnCsv.setOnAction(e -> stato.setText(salvaCsv(grid, "tabellone_1_90", () -> csvTabellone(ultimo[0], ultimo[1]))));
        HBox controls = new HBox(10, new Label("Ruota:"), ruota, new Label("Finestra:"), finestra,
                new Label("Mostra:"), modo, btnPng, btnCsv);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, controls, info, stato, grid, dettaglio);
        content.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // ------------------------------------------------------------------ utilità

    private static String descrivi(int numero, List<Extraction> win, List<String> cols, int[] freq, int[] ritardo) {
        StringBuilder sb = new StringBuilder("Numero " + String.format(Locale.ROOT, "%02d", numero));
        sb.append(" · uscite nel periodo: ").append(freq[numero]);
        sb.append(" · ritardo attuale: ").append(ritardo[numero]).append(" estrazioni");
        List<String> ultime = new ArrayList<>();
        for (int i = win.size() - 1; i >= 0 && ultime.size() < 6; i--) {
            List<String> ruote = new ArrayList<>();
            for (String w : cols) {
                List<Integer> nums = win.get(i).getNumbersForRuota(w);
                if (nums != null && nums.contains(numero)) ruote.add(w);
            }
            if (!ruote.isEmpty()) ultime.add(win.get(i).getShortDate() + " (" + String.join(", ", ruote) + ")");
        }
        if (ultime.isEmpty()) sb.append("\nNon è uscito nel periodo scelto.");
        else sb.append("\nUltime uscite: ").append(String.join("; ", ultime));
        return sb.toString();
    }

    /** I primi n numeri per valore (a parità, il più piccolo); esclude chi ha valore 0. */
    private static List<Integer> topN(int[] valore, int n) {
        List<Integer> numeri = new ArrayList<>();
        for (int x = 1; x <= 90; x++) numeri.add(x);
        numeri.sort((a, b) -> valore[b] != valore[a] ? Integer.compare(valore[b], valore[a]) : Integer.compare(a, b));
        List<Integer> top = new ArrayList<>();
        for (int x : numeri) {
            if (top.size() >= n || valore[x] <= 0) break;
            top.add(x);
        }
        return top;
    }

    // ------------------------------------------------------------------ esportazione

    private static String csvQuadro(List<Extraction> righe, List<String> colonne) {
        StringBuilder sb = new StringBuilder("data;ruota;n1;n2;n3;n4;n5\n");
        for (Extraction e : righe) {
            for (String w : colonne) {
                List<Integer> nums = e.getNumbersForRuota(w);
                if (nums == null) continue;
                sb.append(e.getShortDate()).append(';').append(w);
                for (int x : nums) sb.append(';').append(x);
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String csvTabellone(int[] freq, int[] ritardo) {
        StringBuilder sb = new StringBuilder("numero;frequenza;ritardo\n");
        if (freq == null || ritardo == null) return sb.toString();
        for (int x = 1; x <= 90; x++) sb.append(x).append(';').append(freq[x]).append(';').append(ritardo[x]).append('\n');
        return sb.toString();
    }

    private static String salvaCsv(Node nodo, String nome, java.util.function.Supplier<String> contenuto) {
        Window w = nodo.getScene() == null ? null : nodo.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Salva il CSV");
        fc.setInitialFileName(nome + "_" + LocalDate.now() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showSaveDialog(w);
        if (f == null) return "";
        try {
            Files.writeString(f.toPath(), contenuto.get(), StandardCharsets.UTF_8);
            return "CSV salvato: " + f.getName();
        } catch (Exception ex) {
            return "Salvataggio non riuscito: " + ex.getMessage();
        }
    }

    /** Salva l'intero quadro (anche la parte fuori dalla finestra) come immagine PNG nei colori del tema. */
    private static String salvaPng(Node nodo, String nome) {
        Window w = nodo.getScene() == null ? null : nodo.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Salva l'immagine");
        fc.setInitialFileName(nome + "_" + LocalDate.now() + ".png");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Immagine PNG", "*.png"));
        File f = fc.showSaveDialog(w);
        if (f == null) return "";
        try {
            double larg = nodo.getBoundsInLocal().getWidth();
            double alt = nodo.getBoundsInLocal().getHeight();
            double scala = (larg * 2 > 8000 || alt * 2 > 8000) ? 1 : 2; // oltre ~8000 px la grafica non regge
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.web(ThemeManager.sfondoHex()));
            sp.setTransform(Transform.scale(scala, scala));
            WritableImage img = nodo.snapshot(sp, null);
            int iw = (int) img.getWidth(), ih = (int) img.getHeight();
            PixelReader pr = img.getPixelReader();
            BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) bi.setRGB(x, y, pr.getArgb(x, y));
            javax.imageio.ImageIO.write(bi, "png", f);
            return "Immagine salvata: " + f.getName();
        } catch (Exception ex) {
            return "Salvataggio non riuscito: " + ex.getMessage();
        }
    }

    private static Label intestazione(String testo) {
        Label l = new Label(testo);
        l.getStyleClass().add("wheel-name");
        return l;
    }

    private static Label pallina(int numero, String classe) {
        Label l = new Label(String.format(Locale.ROOT, "%02d", numero));
        l.getStyleClass().add("ball");
        if (classe != null) l.getStyleClass().add(classe);
        l.setMinSize(26, 26);
        l.setPrefSize(26, 26);
        l.setMaxSize(26, 26);
        l.setStyle("-fx-background-radius: 13; -fx-border-radius: 13; -fx-font-size: 11px;");
        return l;
    }
}
