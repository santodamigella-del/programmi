package com.example.lottoinjava.desktop;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import com.example.lottoinjava.model.Extraction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Chat laterale con l'assistente Gemini. Richiede una chiave API configurata dall'utente. */
final class ChatAssistantDialog {
    private static volatile String ultimaTecnicaNome = "Nessuna tecnica ancora eseguita";
    private static volatile String ultimoRisultatoTecnica = "Nessun risultato tecnico disponibile. Esegui prima una tecnica dalla finestra Tecniche.";

    private ChatAssistantDialog() {}

    /** Condivide con la chat l'ultimo report effettivamente calcolato dalla finestra Tecniche. */
    static void registraRisultatoTecnica(String nome, String report) {
        ultimaTecnicaNome = nome == null || nome.isBlank() ? "Tecnica" : nome;
        ultimoRisultatoTecnica = report == null || report.isBlank()
                ? "Il calcolo non ha prodotto un report testuale."
                : report.length() > 14000 ? report.substring(0, 14000) + "\n[Report abbreviato per la chat]" : report;
    }

    static void show(Stage owner, String css, List<Extraction> archive) {
        Stage win = new Stage();
        win.initOwner(owner);
        win.initModality(Modality.NONE);
        win.setTitle("LottoInJava · Chat Notte Neon");

        Label title = new Label("💜 Parla con la tua assistente");
        title.getStyleClass().add("title");
        Label status = new Label("Gemini IA · " + DesktopStorage.geminiKeyStato());
        ComboBox<String> wheelScope = new ComboBox<>(FXCollections.observableArrayList(Extraction.ALL_RUOTE));
        wheelScope.setValue("Bari");
        wheelScope.setMaxWidth(Double.MAX_VALUE);
        Label scopeLabel = new Label("Ruota da analizzare:");
        HBox scopeRow = new HBox(8, scopeLabel, wheelScope); scopeRow.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("subtitle");
        TextArea history = new TextArea("Assistente: Ciao! 💜 Posso aiutarti a leggere le statistiche di LottoInJava, spiegare le funzioni e interpretare i risultati. Le estrazioni restano casuali: non posso garantire vincite.\n\n");
        history.setEditable(false);
        history.setWrapText(true);
        history.getStyleClass().add("report");
        VBox.setVgrow(history, Priority.ALWAYS);
        TextArea input = new TextArea();
        input.setPromptText("Scrivi qui il tuo messaggio…");
        input.setWrapText(true);
        input.setPrefRowCount(3);
        Button send = new Button("Invia 💜"); send.getStyleClass().add("button-primary");
        Button key = new Button("🔑 Configura chiave");
        Button clear = new Button("Pulisci chat");
        HBox actions = new HBox(8, send, key, clear); actions.setAlignment(Pos.CENTER_LEFT);
        // Domande rapide: aiutano a iniziare senza fingere di avere già accesso ai dati delle estrazioni.
        Button qFreq = new Button("📊 Spiegami le frequenze");
        Button qDelay = new Button("⏱️ Come leggere i ritardi");
        Button qLimits = new Button("🎯 Limiti delle statistiche");
        Button qLast = new Button("🧠 Spiega ultima tecnica");
        HBox quickQuestions = new HBox(7, qFreq, qDelay, qLimits, qLast);
        quickQuestions.setAlignment(Pos.CENTER_LEFT);
        quickQuestions.setFillHeight(true);
        qFreq.setOnAction(e -> { input.setText("Analizza le frequenze reali dell'archivio per la ruota selezionata, spiega i numeri più frequenti e i limiti del campione."); input.requestFocus(); });
        qDelay.setOnAction(e -> { input.setText("Spiega i ritardi attuali calcolati sull'archivio per la ruota selezionata e come interpretarli senza considerarli previsioni."); input.requestFocus(); });
        qLimits.setOnAction(e -> { input.setText("Valuta i limiti statistici dei dati disponibili e spiega perché frequenze e ritardi non garantiscono le prossime estrazioni."); input.requestFocus(); });
        qLast.setOnAction(e -> { input.setText("Spiega in modo chiaro l'ultimo risultato tecnico calcolato: riassumi metodo, parametri, numeri principali, significato dei punteggi e limiti. Usa solo il report fornito."); input.requestFocus(); });
        Label note = new Label("Per le risposte AI serve una chiave Google Gemini. La chiave viene salvata localmente."); note.getStyleClass().add("hint"); note.setWrapText(true);
        VBox root = new VBox(10, title, status, scopeRow, quickQuestions, history, input, actions, note); root.setPadding(new Insets(14));
        Scene scene = new Scene(root, 430, 620);
        if (css != null && !css.isBlank()) scene.getStylesheets().add(css);
        win.setScene(scene);

        key.setOnAction(e -> {
            PasswordField pf = new PasswordField(); pf.setPromptText("Incolla la chiave API Gemini");
            Label info = new Label("Crea una chiave su Google AI Studio e incollala qui. Non condividerla."); info.setWrapText(true);
            Button save = new Button("Salva chiave"); save.getStyleClass().add("button-primary");
            VBox pane = new VBox(10, info, pf, save); pane.setPadding(new Insets(14));
            Stage dialog = new Stage(); dialog.initOwner(win); dialog.initModality(Modality.APPLICATION_MODAL); dialog.setTitle("Chiave API Gemini");
            dialog.setScene(new Scene(pane, 390, 170));
            if (css != null && !css.isBlank()) dialog.getScene().getStylesheets().add(css);
            save.setOnAction(ev -> { boolean ok = DesktopStorage.saveGeminiKey(pf.getText()); status.setText(ok ? "Gemini IA · chiave salvata" : "Gemini IA · salvataggio non riuscito"); pf.clear(); if (ok) dialog.close(); });
            dialog.show();
        });
        clear.setOnAction(e -> history.setText("Chat pulita. 💜\n\n"));
        send.setOnAction(e -> {
            String message = input.getText().trim();
            if (message.isEmpty()) return;
            String apiKey = DesktopStorage.geminiKey();
            if (apiKey == null || apiKey.isBlank()) {
                history.appendText("Tu: " + message + "\nAssistente: Per risponderti con l'IA, configura prima la chiave Gemini con il pulsante 🔑.\n\n");
                input.clear(); return;
            }
            history.appendText("Tu: " + message + "\nAssistente: Sto pensando…\n\n");
            input.clear(); send.setDisable(true);
            String context = history.getText();
            String statsContext = buildArchiveContext(archive, wheelScope.getValue());
            String techniqueContext = "Ultima tecnica: " + ultimaTecnicaNome + "\nREPORT TECNICO:\n" + ultimoRisultatoTecnica;
            String prompt = "Sei l'assistente statistica di LottoInJava: tono caldo, competente, chiaro e onesto. "
                    + "Devi spiegare i dati numerici inclusi nel contesto, distinguendo sempre conteggi osservati, ritardi e ipotesi. "
                    + "Non inventare estrazioni, risultati o algoritmi non descritti. Se i dati non bastano, dichiaralo. "
                    + "Quando parli di frequenza, specifica il campione; il ritardo è il numero di estrazioni osservate dall'ultima uscita nell'archivio disponibile. "
                    + "Non interpretare frequenze o ritardi come probabilità maggiori alla prossima estrazione: le estrazioni sono casuali. "
                    + "Rispondi in italiano e cita i numeri effettivi del contesto quando pertinenti. Se è presente un report tecnico, spiega esattamente il metodo e i parametri riportati; non dedurre funzionalità che il report non documenta.\nDATI STATISTICI LOCALI DELL'ARCHIVIO (ruota selezionata):\n"
                    + statsContext + "\nULTIMO RISULTATO DELLE TECNICHE (calcolato localmente nell'app):\n" + techniqueContext + "\nCONTESTO RECENTE CHAT:\n"
                    + context.substring(Math.max(0, context.length() - 7000)) + "\nNUOVO MESSAGGIO UTENTE:\n" + message;
            Thread worker = new Thread(() -> {
                GeminiClient.Esito result = new GeminiClient(GeminiClient.BASE_DEFAULT).chiama(prompt, apiKey);
                Platform.runLater(() -> {
                    String text = result.ok ? result.testo : "Non riesco a rispondere: " + result.testo;
                    history.appendText("Assistente: " + text + "\n\n"); send.setDisable(false);
                    history.positionCaret(history.getLength());
                });
            }, "lottoinjava-chat"); worker.setDaemon(true); worker.start();
        });
        // Invio rapido: Ctrl+Invio invia il messaggio senza interferire con gli a capo.
        input.setOnKeyPressed(event -> {
            if (new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN).match(event)) {
                send.fire();
                event.consume();
            }
        });
        win.show();
    }

    /** Calcola un contesto descrittivo locale dai dati effettivamente presenti nell'archivio. */
    private static String buildArchiveContext(List<Extraction> archive, String wheel) {
        if (archive == null || archive.isEmpty()) return "Archivio vuoto: nessuna statistica disponibile.";
        List<Extraction> sorted = new ArrayList<>(archive);
        sorted.sort(Comparator.comparingInt(Extraction::getDateKey));
        int[] freq = new int[91];
        int[] lastIndex = new int[91];
        java.util.Arrays.fill(lastIndex, -1);
        int draws = 0;
        String firstDate = "n/d", lastDate = "n/d";
        for (Extraction e : sorted) {
            List<Integer> nums = e.getNumbersForRuota(wheel);
            if (nums == null || nums.size() < 5) continue;
            draws++;
            if (draws == 1) firstDate = e.getShortDate();
            lastDate = e.getShortDate();
            for (Integer n : nums) {
                if (n != null && n >= 1 && n <= 90) {
                    freq[n]++;
                    lastIndex[n] = draws - 1;
                }
            }
        }
        if (draws == 0) return "Nessuna estrazione completa disponibile per la ruota " + wheel + ".";
        List<Integer> byFreq = new ArrayList<>();
        List<Integer> byDelay = new ArrayList<>();
        for (int n = 1; n <= 90; n++) { byFreq.add(n); byDelay.add(n); }
        byFreq.sort((a,b) -> freq[b] != freq[a] ? Integer.compare(freq[b], freq[a]) : Integer.compare(a,b));
        final int totalDraws = draws; // copia immutabile per l'uso nelle lambda
        byDelay.sort((a,b) -> {
            int da = lastIndex[a] < 0 ? totalDraws : totalDraws - 1 - lastIndex[a];
            int db = lastIndex[b] < 0 ? totalDraws : totalDraws - 1 - lastIndex[b];
            return da != db ? Integer.compare(db, da) : Integer.compare(a,b);
        });
        StringBuilder b = new StringBuilder();
        b.append("Ruota: ").append(wheel).append("\nEstrazioni complete nel campione: ").append(draws)
                .append("\nIntervallo archivio: ").append(firstDate).append(" - ").append(lastDate).append("\nFrequenze più alte (numero: uscite nel campione): ");
        for (int i=0;i<10;i++) { if(i>0)b.append(", "); int n=byFreq.get(i); b.append(String.format(java.util.Locale.ITALIAN, "%02d: %d",n,freq[n])); }
        b.append("\nRitardi correnti maggiori in estrazioni (numero: estrazioni trascorse dall'ultima uscita): ");
        for (int i=0;i<10;i++) { if(i>0)b.append(", "); int n=byDelay.get(i); int delay=lastIndex[n]<0?totalDraws:totalDraws-1-lastIndex[n]; b.append(String.format(java.util.Locale.ITALIAN, "%02d: %d",n,delay)); }
        b.append("\nNota: statistiche descrittive calcolate solo sui dati presenti nell'archivio locale; non sono una previsione.");
        return b.toString();
    }
}
