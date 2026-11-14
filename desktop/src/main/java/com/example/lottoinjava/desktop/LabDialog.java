package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.*;

/** Laboratorio walk-forward: frequenze e ritardatari confrontati con una baseline casuale. */
final class LabDialog {
    private static final List<String> WINDOWS = List.of("Ultime 100", "Ultime 300", "Ultime 500", "Tutto lo storico");
    private LabDialog() {}
    static void show(Stage owner, List<Extraction> archive) {
        Stage stage = new Stage(); stage.initOwner(owner); stage.initModality(Modality.NONE);
        stage.setTitle("LottoInJava · Laboratorio delle tecniche");
        ComboBox<String> wheel = new ComboBox<>(FXCollections.observableArrayList(Extraction.ALL_RUOTE)); wheel.setValue(Extraction.ALL_RUOTE.get(0));
        ComboBox<String> window = new ComboBox<>(FXCollections.observableArrayList(WINDOWS)); window.setValue("Ultime 300");
        ComboBox<String> strategy = new ComboBox<>(FXCollections.observableArrayList("Confronta tutte", "Più frequenti", "Più in ritardo", "Frequenza recente (20)", "Meno frequenti")); strategy.setValue("Confronta tutte");
        Label status = new Label("Test walk-forward: a ogni passo la tecnica vede solo le estrazioni precedenti."); status.setWrapText(true);
        TextArea report = new TextArea(); report.setEditable(false); report.setWrapText(true); report.setPrefRowCount(7);
        CategoryAxis x = new CategoryAxis(); x.setLabel("Metodo"); NumberAxis y = new NumberAxis(); y.setLabel("Hit medi per estrazione (cinquina)");
        BarChart<String, Number> chart = new BarChart<>(x,y); chart.setTitle("Walk-forward: tecniche contro baseline casuale"); chart.setLegendVisible(true); chart.setAnimated(false);
        Button run = new Button("▶ Esegui confronto"); run.getStyleClass().add("button-primary");
        Runnable execute = () -> {
            List<Extraction> data = new ArrayList<>(archive); data.sort(Comparator.comparingInt(Extraction::getDateKey));
            int requested = window.getValue().startsWith("Ultime ") ? Integer.parseInt(window.getValue().substring(7)) : data.size();
            if (data.size() > requested) data = new ArrayList<>(data.subList(data.size()-requested,data.size()));
            int n = data.size();
            if (n < 35) { report.setText("Archivio insufficiente: servono almeno 35 estrazioni per un confronto walk-forward."); chart.getData().clear(); return; }
            int testStart = Math.max(25, (int)Math.floor(n*0.7));
            String[] allMethods = {"Più frequenti", "Più in ritardo", "Frequenza recente (20)", "Meno frequenti"};
            List<String> methods = new ArrayList<>();
            if (strategy.getValue().equals("Confronta tutte")) methods.addAll(Arrays.asList(allMethods));
            else methods.add(strategy.getValue());
            Map<String,Double> totals = new LinkedHashMap<>(); for(String m:allMethods) totals.put(m,0.0); totals.put("Casuale",0.0);
            Map<String,List<Double>> paired = new LinkedHashMap<>(); for(String m:allMethods) paired.put(m,new ArrayList<>());
            int tests=0; Random rng = new Random(20261022L);
            for(int i=testStart;i<n;i++) {
                int[] freq=new int[91], recent=new int[91], delay=new int[91]; Arrays.fill(delay, i+1);
                for(int j=0;j<i;j++) for(int num:data.get(j).getNumbersForRuota(wheel.getValue())) if(num>=1&&num<=90){freq[num]++;delay[num]=i-j;if(j>=Math.max(0,i-20))recent[num]++;}
                Map<String,Integer> hitsThisDraw = new HashMap<>();
                for(String method:allMethods) {
                    List<Integer> ranking=new ArrayList<>(); for(int num=1;num<=90;num++) ranking.add(num);
                    switch(method) {
                        case "Più frequenti": ranking.sort(Comparator.<Integer>comparingInt(a->-freq[a]).thenComparingInt(a->a)); break;
                        case "Più in ritardo": ranking.sort(Comparator.<Integer>comparingInt(a->-delay[a]).thenComparingInt(a->a)); break;
                        case "Frequenza recente (20)": ranking.sort(Comparator.<Integer>comparingInt(a->-recent[a]).thenComparingInt(a->a)); break;
                        default: ranking.sort(Comparator.<Integer>comparingInt(a->freq[a]).thenComparingInt(a->a));
                    }
                    Set<Integer> picks=new HashSet<>(ranking.subList(0,5));
                    int hits=0; for(int num:data.get(i).getNumbersForRuota(wheel.getValue())) if(picks.contains(num)) hits++;
                    hitsThisDraw.put(method,hits); totals.put(method, totals.get(method)+hits);
                }
                Set<Integer> randomPick=new HashSet<>(); while(randomPick.size()<5) randomPick.add(rng.nextInt(90)+1);
                int randomHits=0; for(int num:data.get(i).getNumbersForRuota(wheel.getValue())) if(randomPick.contains(num)) randomHits++;
                totals.put("Casuale",totals.get("Casuale")+randomHits);
                for(String method:allMethods) paired.get(method).add((double)hitsThisDraw.get(method)-randomHits);
                tests++;
            }
            chart.getData().clear(); XYChart.Series<String,Number> series=new XYChart.Series<>();
            StringBuilder text=new StringBuilder("LABORATORIO DELLE TECNICHE · WALK-FORWARD\nRuota: ").append(wheel.getValue()).append(" · estrazioni nella finestra: ").append(n).append(" · test fuori campione: ").append(tests).append("\nFinestra di test: ultimo 30% circa; ogni scelta usa solo estrazioni precedenti.\n\n");
            double randomAvg=totals.get("Casuale")/tests;
            for(String method:methods) {
                double avg=totals.get(method)/tests, delta=avg-randomAvg;
                series.getData().add(new XYChart.Data<>(method,avg));
                double[] ci = bootstrapCI(paired.get(method), 1200, 20261022L + method.hashCode());
                double pValue = signFlipP(paired.get(method), 2000, 20261022L ^ method.hashCode());
                text.append(String.format(Locale.ITALY,"%-25s media hit %.4f · delta vs casuale %+.4f · IC95%% delta [%+.4f; %+.4f] · p≈%.4f\n",method,avg,delta,ci[0],ci[1],pValue));
            }
            series.getData().add(new XYChart.Data<>("Casuale",randomAvg)); chart.getData().add(series);
            text.append(String.format(Locale.ITALY,"\nBaseline casuale riproducibile: %.4f hit medi per cinquina.",randomAvg));
            text.append("\n\nInterpretazione: delta positivo/negativo descrive soltanto questo campione. Le tecniche sono confrontate sulle stesse estrazioni, ma i pareggi sono risolti in ordine numerico e il campione non garantisce vantaggi futuri. Il Lotto resta casuale; non è una raccomandazione di gioco.");
            report.setText(text.toString());
        };
        run.setOnAction(e->execute.run());
        HBox controls=new HBox(10,new Label("Ruota:"),wheel,new Label("Periodo:"),window,new Label("Vista:"),strategy,run); controls.setAlignment(Pos.CENTER_LEFT); controls.setPadding(new Insets(10));
        VBox top=new VBox(6,new Label("LABORATORIO DELLE TECNICHE · WALK-FORWARD E BASELINE CASUALE"),controls,status); top.setPadding(new Insets(10));
        BorderPane root=new BorderPane(); root.setTop(top); VBox center=new VBox(8,chart,report); center.setPadding(new Insets(10)); VBox.setVgrow(chart,javafx.scene.layout.Priority.ALWAYS); root.setCenter(center);
        Scene scene = new Scene(root,1000,740); scene.getStylesheets().add(ThemeManager.cssUrl()); stage.setScene(scene); stage.show(); execute.run();
    }

    /** Bootstrap percentile appaiato: ricampiona le differenze per estrazione. */
    private static double[] bootstrapCI(List<Double> values, int iterations, long seed) {
        if (values == null || values.isEmpty()) return new double[]{Double.NaN, Double.NaN};
        Random r = new Random(seed); double[] means = new double[iterations]; int n = values.size();
        for (int b=0;b<iterations;b++) { double sum=0; for(int i=0;i<n;i++) sum += values.get(r.nextInt(n)); means[b]=sum/n; }
        Arrays.sort(means); return new double[]{means[(int)Math.floor(iterations*0.025)], means[Math.min(iterations-1,(int)Math.ceil(iterations*0.975)-1)]};
    }

    /** Test appaiato di randomizzazione con inversione casuale dei segni delle differenze. */
    private static double signFlipP(List<Double> values, int iterations, long seed) {
        if (values == null || values.isEmpty()) return Double.NaN;
        double observed=0; for(double v:values) observed+=v; observed/=values.size();
        Random r=new Random(seed); int extreme=0; int n=values.size();
        for(int b=0;b<iterations;b++){ double sum=0; for(double v:values) sum += r.nextBoolean()?v:-v; double simulated=sum/n; if(Math.abs(simulated)>=Math.abs(observed)) extreme++; }
        return (extreme+1.0)/(iterations+1.0);
    }

}
