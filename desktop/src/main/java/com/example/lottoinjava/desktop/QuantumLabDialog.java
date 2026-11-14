package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.*;

/** Laboratorio sperimentale a ispirazione quantistica: i punteggi sono euristiche, non probabilità di vincita. */
final class QuantumLabDialog {
    private QuantumLabDialog() {}
    static void show(Stage owner, List<Extraction> archive) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.initModality(Modality.NONE);
        stage.setTitle("LottoInJava · Laboratorio Quantistico (sperimentale)");
        ComboBox<String> game = new ComboBox<>(FXCollections.observableArrayList("Lotto")); game.setValue("Lotto");
        ComboBox<String> wheel = new ComboBox<>(FXCollections.observableArrayList("Tutte", "Bari", "Cagliari", "Firenze", "Genova", "Milano", "Napoli", "Palermo", "Roma", "Torino", "Venezia", "Nazionale")); wheel.setValue("Tutte");
        Spinner<Integer> window = new Spinner<>(120, 10000, 120); window.setEditable(true);
        Spinner<Integer> count = new Spinner<>(1, 90, 10); count.setEditable(true);
        Slider statWeight = new Slider(0,100,50); statWeight.setShowTickLabels(true); statWeight.setMajorTickUnit(25); statWeight.setSnapToTicks(true);
        Slider interferenceWeight = new Slider(0,100,50); interferenceWeight.setShowTickLabels(true); interferenceWeight.setMajorTickUnit(25); interferenceWeight.setSnapToTicks(true);
        ComboBox<String> phase = new ComboBox<>(FXCollections.observableArrayList("Fasi dinamiche (deterministiche)", "Fasi uniformi storiche")); phase.setValue("Fasi dinamiche (deterministiche)");
        TextArea report = new TextArea(); report.setEditable(false); report.setWrapText(true); report.setPrefRowCount(24);
        Label weights = new Label(); Runnable updateWeights = () -> weights.setText(String.format(Locale.ITALY,"Peso statistico: %.0f%% · Peso interferenza: %.0f%% (normalizzati al calcolo)",statWeight.getValue(),interferenceWeight.getValue()));
        statWeight.valueProperty().addListener((o,a,b)->updateWeights.run()); interferenceWeight.valueProperty().addListener((o,a,b)->updateWeights.run()); updateWeights.run();
        Button run = new Button("▶ Analizza e valida"); run.getStyleClass().add("button-primary");
        Button walkForward = new Button("📊 Esegui walk-forward + baseline casuale");
        walkForward.setOnAction(e -> {
            try {
                if (window.getValue() < 1 || count.getValue() < 1) throw new IllegalArgumentException("Finestra e candidati devono essere positivi.");
                String result = QuantumWalkForward.run(new ArrayList<>(archive), wheel.getValue(), window.getValue(), count.getValue(), 20261024L, statWeight.getValue(), interferenceWeight.getValue());
                report.setText(result);
            } catch (Exception ex) { report.setText("WALK-FORWARD NON ESEGUITO\n\n" + ex.getMessage() + "\n\nNessun risultato inventato: verificare archivio e parametri."); }
        });
        run.setOnAction(e -> {
            List<Extraction> data = new ArrayList<>(archive); data.sort(Comparator.comparingInt(Extraction::getDateKey));
            List<String> issues = validate(data);
            if (!issues.isEmpty()) { report.setText("VALIDAZIONE ARCHIVIO: NON SUPERATA\n\n"+String.join("\n",issues)+"\n\nNessuna classifica calcolata. Correggere l'archivio alla fonte; il laboratorio non modifica i dati."); return; }
            String selectedWheel=wheel.getValue(); int needed=window.getValue();
            List<Extraction> usable=new ArrayList<>();
            for(Extraction x:data) if(selectedWheel.equals("Tutte") || !x.getNumbersForRuota(selectedWheel).isEmpty()) usable.add(x);
            if(usable.size()<=needed){report.setText("Archivio insufficiente per la finestra richiesta. Disponibili: "+usable.size()+" estrazioni valide; richieste almeno "+(needed+1)+" per analizzare l'ultima come target fuori campione. Nessun risultato prodotto.");return;}
            Extraction target=usable.get(usable.size()-1); List<Extraction> hist=usable.subList(usable.size()-1-needed,usable.size()-1);
            double sw=statWeight.getValue(), iw=interferenceWeight.getValue(), sum=sw+iw;
            if(sum==0){report.setText("Configurazione non valida: entrambi i pesi sono zero.");return;} sw/=sum; iw/=sum;
            List<String> wheels=selectedWheel.equals("Tutte")?Extraction.ALL_RUOTE:List.of(selectedWheel);
            StringBuilder out=new StringBuilder("LABORATORIO QUANTISTICO — ESPERIMENTO SIMULATO\n");
            out.append("Gioco: Lotto · Finestra: ").append(needed).append(" · Target escluso dallo storico: ").append(target.getShortDate()).append("\n");
            out.append(String.format(Locale.ITALY,"Pesi effettivi: statistico %.1f%%, interferenza %.1f%%\n",sw*100,iw*100));
            out.append("Modello fasi: ").append(phase.getValue()).append("\n\nATTENZIONE: ranking euristico, non previsione affidabile, probabilità calibrata o risultato reale.\n");
            int candidateCount=count.getValue();
            for(String wh:wheels){
                int available=0; for(Extraction x:hist) if(x.getNumbersForRuota(wh).size()==5) available++;
                if(available<needed){out.append("\n").append(wh).append(": dati insufficienti (validi ").append(available).append('/').append(needed).append(").\n");continue;}
                int[] freq=new int[91],recent=new int[91],delay=new int[91]; Arrays.fill(delay,needed+1);
                for(int i=0;i<hist.size();i++){List<Integer> nums=hist.get(i).getNumbersForRuota(wh);for(int n:nums){freq[n]++;delay[n]=hist.size()-i;if(i>=hist.size()-20)recent[n]++;}}
                double[] ampRe=new double[91],ampIm=new double[91],score=new double[91];
                for(int n=1;n<=90;n++){
                    double p=(freq[n]+1.0)/(needed+90.0); double theta=2*Math.PI*((recent[n]+1.0)/(22.0)+(delay[n]%17)/17.0);
                    if(phase.getValue().startsWith("Fasi dinamiche")) theta += 2*Math.PI*(freq[n]%7)/7.0;
                    double amp=Math.sqrt(p); ampRe[n]=amp*Math.cos(theta); ampIm[n]=amp*Math.sin(theta);
                }
                double norm=0;for(int n=1;n<=90;n++)norm+=ampRe[n]*ampRe[n]+ampIm[n]*ampIm[n];
                if(!Double.isFinite(norm)||norm<=0){out.append("\n").append(wh).append(": interferenza non definita; nessun output.\n");continue;}
                double maxFreq=Arrays.stream(freq).max().orElse(1), maxRecent=Arrays.stream(recent).max().orElse(1), maxDelay=Arrays.stream(delay).max().orElse(1);
                for(int n=1;n<=90;n++){
                    double statistical=.5*(freq[n]/Math.max(1,maxFreq))+.3*(recent[n]/Math.max(1,maxRecent))+.2*(delay[n]/Math.max(1,maxDelay));
                    double q=(ampRe[n]*ampRe[n]+ampIm[n]*ampIm[n])/norm;
                    score[n]=sw*statistical+iw*(q*90.0);
                }
                List<Integer> ranked=new ArrayList<>();for(int n=1;n<=90;n++)ranked.add(n);ranked.sort(Comparator.<Integer>comparingDouble(n->-score[n]).thenComparingInt(n->n));
                out.append("\nRUOTA ").append(wh).append(" — ").append(candidateCount).append(" candidati ordinati\n");
                for(int i=0;i<Math.min(candidateCount,ranked.size());i++){int n=ranked.get(i);out.append(String.format(Locale.ITALY,"%2d. %02d  score %.5f  frequenza %d  ritardo %d\n",i+1,n,score[n],freq[n],delay[n]));}
            }
            out.append("\nVALIDAZIONE WALK-FORWARD E BASELINE CASUALE\n");
            out.append("Questa schermata produce solo un ranking esplorativo sull'ultima data esclusa. Per dichiarare efficacia servono test walk-forward ripetuti, parametri congelati prima del test e confronto sulla stessa sequenza di date con campioni casuali uniformi riproducibili. Nessuna superiorità statistica viene dichiarata da questo singolo output.\n");
            out.append("Baseline teorica per 10 numeri contro 5 estratti: 10×5/90 = 0,556 hit medi per ruota/estrazione.\n");
            report.setText(out.toString());
        });

        Spinner<Integer> decade = new Spinner<>(0, 8, 3); decade.setEditable(true);
        Button numeretti = new Button("🔢 Analizza Numeretti");
        numeretti.setOnAction(e -> {
            try {
                List<Extraction> data = new ArrayList<>(archive);
                data.sort(Comparator.comparingInt(Extraction::getDateKey));
                List<String> issues = validate(data);
                if (!issues.isEmpty()) {
                    report.setText("NUMERETTI — ARCHIVIO NON VALIDATO\n\n" + String.join("\n", issues)
                        + "\n\nCorreggere i dati alla fonte; nessuna correzione automatica è stata applicata.");
                    return;
                }
                String selectedWheel = wheel.getValue();
                int win = window.getValue(), dec = decade.getValue();
                List<Extraction> usable = new ArrayList<>();
                for (Extraction x : data) {
                    if (selectedWheel.equals("Tutte")) usable.add(x);
                    else if (validNumbers(x.getNumbersForRuota(selectedWheel))) usable.add(x);
                }
                if (usable.size() <= win) {
                    report.setText("Numeretti: storico insufficiente. Servono più di " + win + " estrazioni valide.");
                    return;
                }
                // L'ultima estrazione è esclusa: si analizza soltanto lo storico precedente.
                List<Extraction> hist = usable.subList(usable.size() - 1 - win, usable.size() - 1);
                int[] freq = new int[91], co = new int[91];
                int baseStart = dec * 10;
                int low = baseStart == 0 ? 1 : baseStart;
                int high = baseStart + 9;
                int draws = 0;
                for (Extraction x : hist) {
                    List<String> wheels = selectedWheel.equals("Tutte") ? Extraction.ALL_RUOTE : List.of(selectedWheel);
                    for (String wh : wheels) {
                        List<Integer> nums = x.getNumbersForRuota(wh);
                        if (!validNumbers(nums)) continue;
                        draws++;
                        boolean hasDecade = false;
                        for (int n : nums) {
                            if (n >= low && n <= high) { freq[n]++; hasDecade = true; }
                        }
                        if (hasDecade) for (int n : nums) if (n < low || n > high) co[n]++;
                    }
                }
                List<Integer> decadeRank = new ArrayList<>();
                for (int n = low; n <= high; n++) decadeRank.add(n);
                decadeRank.sort(Comparator.<Integer>comparingInt(n -> -freq[n]).thenComparingInt(n -> n));
                List<Integer> assoc = new ArrayList<>();
                for (int n = 1; n <= 90; n++) if (n < low || n > high) assoc.add(n);
                assoc.sort(Comparator.<Integer>comparingInt(n -> -co[n]).thenComparingInt(n -> n));
                StringBuilder out = new StringBuilder("NUMERETTI — ANALISI STORICA ESPLORATIVA\n");
                out.append("Ruota: ").append(selectedWheel).append(" · decade: ").append(low).append("–").append(high)
                    .append(" · finestra richiesta: ").append(win).append(" · ultima estrazione esclusa.\n");
                out.append("Unità di conteggio: frequenze dei singoli numeri nella decade; associazioni: co-presenza nello stesso concorso/ruota quando compare almeno un numero della decade.\n\n");
                out.append("NUMERI DELLA DECADE ORDINATI PER FREQUENZA (i primi sono i candidati da evidenziare in blu nell'interfaccia):\n");
                for (int i = 0; i < decadeRank.size(); i++) {
                    int n = decadeRank.get(i);
                    out.append(String.format(Locale.ITALY, "%s%02d  frequenza=%d%n", i < 3 ? "★ " : "  ", n, freq[n]));
                }
                out.append("\nDUE ASSOCIAZIONI PIÙ FREQUENTI FUORI DECADE:\n");
                for (int i = 0; i < Math.min(2, assoc.size()); i++) {
                    int n = assoc.get(i);
                    out.append(String.format(Locale.ITALY, "%02d  co-presenze=%d%n", n, co[n]));
                }
                out.append("\nOsservazioni conteggiate: ").append(draws).append(". Le co-presenze sono associazioni descrittive, non una previsione né una garanzia di ambo.");
                out.append("\nPer valutare utilità reale, eseguire un walk-forward su date future rispetto allo storico e confrontare con una baseline casuale.");
                report.setText(out.toString());
            } catch (Exception ex) {
                report.setText("NUMERETTI NON ESEGUITO\n\n" + ex.getMessage());
            }
        });

        GridPane form=new GridPane();form.setHgap(10);form.setVgap(9);form.addRow(0,new Label("Gioco"),game,new Label("Ruota"),wheel);form.addRow(1,new Label("Finestra storica"),window,new Label("Candidati"),count);form.addRow(2,new Label("Peso statistico"),statWeight);form.addRow(3,new Label("Peso interferenza"),interferenceWeight);form.addRow(4,new Label("Modello fasi"),phase);form.add(weights,0,5,4,1);form.add(run,0,6,2,1);form.add(walkForward,2,6,2,1);
        form.addRow(7, new Label("Decade (3 = 30–39)"), decade, numeretti);
        VBox root=new VBox(12,form,report);root.setPadding(new Insets(14));VBox.setVgrow(report,Priority.ALWAYS);
        stage.setScene(new Scene(root,900,720));stage.show();
    }
    private static boolean validNumbers(List<Integer> nums) {
        return nums != null && nums.size() == 5 && new HashSet<>(nums).size() == 5
            && nums.stream().allMatch(n -> n != null && n >= 1 && n <= 90);
    }
    private static List<String> validate(List<Extraction> data){
        List<String> issues=new ArrayList<>(); if(data.isEmpty()){issues.add("Archivio vuoto.");return issues;}
        Set<Integer> dates=new HashSet<>();int previous=-1;
        for(Extraction x:data){int key=x.getDateKey();if(key<18000101){issues.add("Data non interpretabile: "+x.getDate()+" (concorso "+x.getConcorsoNumber()+").");continue;} if(!dates.add(key))issues.add("Data duplicata/confondibile: "+x.getDate()+". Verificare i duplicati prima dell'analisi."); if(key<previous)issues.add("Ordine cronologico incoerente.");previous=key;
            for(Map.Entry<String,List<Integer>> en:x.getWheels().entrySet()){List<Integer> nums=en.getValue();if(nums==null||nums.size()!=5){issues.add(x.getDate()+" / "+en.getKey()+": attesi 5 numeri.");continue;}Set<Integer> unique=new HashSet<>(nums);if(unique.size()!=5||nums.stream().anyMatch(n->n==null||n<1||n>90))issues.add(x.getDate()+" / "+en.getKey()+": numeri duplicati o fuori intervallo 1–90.");}
        }
        return issues;
    }
}
