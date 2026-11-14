package com.example.lottoinjava.desktop;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.awt.Desktop;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Modulo SuperEnalotto desktop: archivio separato, analisi post-spia, ciclometria e motore statistico. */
final class SuperEnalottoDialog {
    private static final Path ARCHIVE = DesktopStorage.dataDir().resolve("superenalotto_archive.csv");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] URLS = {
            "https://www.estrazioni.it/index.php?formato=csv&p=download&tipo=superenalotto",
            "https://raw.githubusercontent.com/luigimassa/superenalotto-archivio/main/superenalotto.csv"
    };
    private final Stage owner;
    private final String css;
    private Stage dialog;
    private List<Draw> draws = new ArrayList<>();
    private Spinner<Integer> spy, yearFrom, yearTo, shots;
    private TextArea output;
    private Label status;
    private Label assistantStatus;
    private ListView<Draw> archiveView;
    private TextField archiveSearch;
    private CheckBox highlightArchive;
    private HBox highlightLegend;
    private Set<Integer> highlightedCandidates = new HashSet<>();
    private Set<String> spyDrawKeys = new HashSet<>();
    private Set<String> resultDrawKeys = new HashSet<>();

    private SuperEnalottoDialog(Stage owner, String css) { this.owner = owner; this.css = css; }
    static void show(Stage owner, String css) { new SuperEnalottoDialog(owner, css).open(); }

    private void open() {
        draws = loadArchive();
        dialog = new Stage(); dialog.initOwner(owner); dialog.initModality(Modality.NONE);
        dialog.setTitle("LottoInJava · SuperEnalotto");
        Label title = new Label("SUPERENALOTTO · ARCHIVIO E TECNICHE"); title.getStyleClass().add("title");
        status = new Label(); status.getStyleClass().add("subtitle"); updateStatus();
        Button importBtn = new Button("Importa CSV (sostituisce archivio)"); importBtn.setOnAction(e -> importCsv());
        Button onlineBtn = new Button("Aggiorna online"); onlineBtn.getStyleClass().add("button-primary"); onlineBtn.setOnAction(e -> updateOnline());
        Button resetBtn = new Button("Reset archivio"); resetBtn.setOnAction(e -> resetArchive());
        Button guideBtn = new Button("Guida tecniche (web)"); guideBtn.setOnAction(e -> openGuide());
        Button statsBtn = new Button("📊 Numeri caldi/freddi"); statsBtn.setOnAction(e -> showNumberStats());
        Button labBtn = new Button("🧪 Laboratorio tecniche"); labBtn.setOnAction(e -> showLaboratory());
        HBox archiveActions = new HBox(8, importBtn, onlineBtn, resetBtn, guideBtn, statsBtn, labBtn);
        spy = new Spinner<>(1,90,7); spy.setEditable(true);
        spy.valueProperty().addListener((o,old,v)->{highlightedCandidates.clear();spyDrawKeys.clear();resultDrawKeys.clear();refreshArchive();});
        int min = draws.stream().mapToInt(d -> d.date.getYear()).min().orElse(2000);
        int max = draws.stream().mapToInt(d -> d.date.getYear()).max().orElse(LocalDate.now().getYear());
        yearFrom = new Spinner<>(1900, 2200, min); yearFrom.setEditable(true);
        yearTo = new Spinner<>(1900, 2200, max); yearTo.setEditable(true);
        shots = new Spinner<>(1,50,9); shots.setEditable(true);
        GridPane params = new GridPane(); params.setHgap(10); params.setVgap(8);
        params.addRow(0,new Label("Numero spia (1–90)"),spy,new Label("Anno da"),yearFrom,new Label("Anno a"),yearTo,new Label("Colpi"),shots);
        Button analyse = new Button("Analisi post-spia"); analyse.setOnAction(e -> run("post"));
        Button forecast = new Button("Previsione statistica"); forecast.setOnAction(e -> run("forecast"));
        Button cyclo = new Button("Ciclometria spia"); cyclo.setOnAction(e -> run("cyclo"));
        Button unified = new Button("Resoconto unificato"); unified.setOnAction(e -> run("unified"));
        Button backtest = new Button("Backtest walk-forward"); backtest.setOnAction(e -> run("backtest"));
        Button compare = new Button("Confronto modelli"); compare.setOnAction(e -> run("compare"));
        Button cycloBacktest = new Button("Backtest ciclometria vs casuale"); cycloBacktest.setOnAction(e -> run("cyclobacktest"));
        Button holdout = new Button("Verifica sadica + holdout"); holdout.setOnAction(e -> run("holdout"));
        HBox methods = new HBox(8,analyse,forecast,cyclo,unified,backtest,compare);
        HBox methods2 = new HBox(8,cycloBacktest,holdout);
        output = new TextArea(); output.setEditable(false); output.setWrapText(false); output.setPromptText("I risultati delle tecniche appariranno qui…");
        archiveSearch = new TextField(); archiveSearch.setPromptText("Cerca data, concorso o numero…"); archiveSearch.textProperty().addListener((o,old,v)->refreshArchive());
        highlightArchive = new CheckBox("Colora archivio: spia verde e numeri selezionati trovati nei colpi successivi in rosso");
        highlightArchive.setSelected(true); highlightArchive.selectedProperty().addListener((o,old,v)->refreshArchive());
        Label spyLegend = new Label("● SPIA"); spyLegend.setStyle("-fx-text-fill: -c-win; -fx-font-weight: bold;");
        Label resultLegend = new Label("● RISULTATO trovato nei colpi successivi"); resultLegend.setStyle("-fx-text-fill: -c-lost; -fx-font-weight: bold;");
        highlightLegend = new HBox(14, new Label("Legenda:"), spyLegend, resultLegend); highlightLegend.setAlignment(Pos.CENTER_LEFT);
        Label archiveTitle = new Label("ARCHIVIO STORICO · estrazioni e palline"); archiveTitle.getStyleClass().add("section-title");
        archiveView = new ListView<>(); archiveView.setPlaceholder(new Label("Archivio vuoto: importa un CSV o aggiorna online."));
        archiveView.setCellFactory(lv -> new ListCell<Draw>() {
            @Override protected void updateItem(Draw item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                if (empty || item == null) { setGraphic(null); return; }
                setGraphic(buildDrawCard(item));
            }
        });
        VBox archivePane = new VBox(6,archiveTitle,archiveSearch,highlightArchive,highlightLegend,archiveView);
        VBox.setVgrow(archiveView,Priority.ALWAYS);
        Label resultsTitle = new Label("RISULTATI DELLE TECNICHE"); resultsTitle.getStyleClass().add("section-title");
        assistantStatus = new Label("♡ Assistente pronta · scegli una tecnica per iniziare");
        assistantStatus.getStyleClass().add("assistant-toast");
        assistantStatus.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.image.ImageView assistantArt = new javafx.scene.image.ImageView();
        try { assistantArt.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/assistente_notte_neon.png"))); } catch (Exception ignored) {}
        assistantArt.setFitHeight(115); assistantArt.setPreserveRatio(true); assistantArt.setSmooth(true);
        VBox resultsPane = new VBox(6,resultsTitle,assistantStatus,assistantArt,output);
        VBox.setVgrow(output,Priority.ALWAYS);
        SplitPane lower = new SplitPane(archivePane,resultsPane); lower.setOrientation(javafx.geometry.Orientation.VERTICAL); lower.setDividerPositions(0.58);
        VBox root = new VBox(10,title,status,archiveActions,params,methods,methods2,lower);
        root.setPadding(new Insets(16)); VBox.setVgrow(lower,Priority.ALWAYS);
        Scene scene = new Scene(root,1100,850); try { scene.getStylesheets().add(css); } catch(Exception ignored) {}
        dialog.setScene(scene); dialog.show(); refreshArchive();
        if (!draws.isEmpty()) output.setText(archiveSummary());
        else output.setText("Archivio SuperEnalotto vuoto. Importa un CSV storico oppure premi Aggiorna online.\n\nL'archivio SuperEnalotto è separato dall'archivio Lotto.");
    }

    private void showNumberStats() {
        List<int[]> data = new ArrayList<>();
        for (Draw draw : draws) data.add(Arrays.copyOf(draw.numbers, draw.numbers.length));
        NumberStatsDialog.showSuper(owner, data);
    }

    private void updateStatus() { if(status!=null) status.setText("Estrazioni: "+draws.size()+" · archivio separato: "+ARCHIVE+ (draws.isEmpty()?"":" · periodo "+draws.get(0).date+" – "+draws.get(draws.size()-1).date)); }
    private void importCsv() {
        FileChooser fc = new FileChooser(); fc.setTitle("Importa archivio SuperEnalotto"); fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV / testo","*.csv","*.txt","*.tsv"),new FileChooser.ExtensionFilter("Tutti i file","*.*"));
        File f=fc.showOpenDialog(dialog); if(f==null)return;
        try { List<Draw> parsed=parseCsv(Files.readString(f.toPath(),StandardCharsets.UTF_8)); if(parsed.isEmpty())throw new IllegalArgumentException("Nessuna estrazione valida trovata nel CSV.");
            backup(); writeArchive(parsed); draws=parsed; updateStatus(); refreshArchive(); output.setText("IMPORTAZIONE COMPLETATA\n\nIl CSV selezionato ha SOSTITUITO l'archivio SuperEnalotto attivo.\nNon è stato accodato al vecchio archivio e i doppioni sono stati rimossi.\n\n"+archiveSummary());
        } catch(Exception ex){ error("Importazione non riuscita",ex); }
    }
    private void updateOnline() {
        status.setText("Download archivio SuperEnalotto in corso…");
        Thread t=new Thread(()->{ Exception last=null; List<Draw> downloaded=null;
            for(String url:URLS) try { HttpClient c=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest req=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(30)).header("User-Agent","LottoInJava-Linux/1.0").GET().build();
                HttpResponse<String> r=c.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); if(r.statusCode()<200||r.statusCode()>=300)throw new IOException("HTTP "+r.statusCode());
                downloaded=parseCsv(r.body()); if(downloaded.isEmpty())throw new IOException("CSV online senza estrazioni riconosciute"); break;
            } catch(Exception ex){last=ex;}
            final List<Draw> data=downloaded; final Exception err=last;
            Platform.runLater(()->{ if(data==null){status.setText("Aggiornamento online non riuscito"); error("Aggiornamento non riuscito",err==null?new Exception("Nessuna fonte valida"):err);return;}
                Map<String,Draw> map=new LinkedHashMap<>(); for(Draw d:draws)map.put(d.key(),d); int old=map.size(); for(Draw d:data)map.put(d.key(),d); List<Draw> merged=sorted(map.values());
                try{backup();writeArchive(merged);draws=merged;updateStatus();refreshArchive();output.setText("AGGIORNAMENTO ONLINE COMPLETATO\nEstrazioni scaricate e validate: "+data.size()+"\nNuove estrazioni: "+(merged.size()-old)+"\nTotale archivio: "+merged.size()+"\n\n"+archiveSummary());}
                catch(Exception ex){error("Salvataggio non riuscito",ex);}
            });
        },"superenalotto-online-update");t.setDaemon(true);t.start();
    }
    /** Apre la guida in un thread separato: il browser non deve bloccare l'interfaccia. */
    private void openGuide() {
        Thread opener = new Thread(() -> {
            try (InputStream in = getClass().getResourceAsStream("/docs/guida_tecniche_superenalotto.html")) {
                if (in == null) throw new IOException("Guida HTML non inclusa nel pacchetto.");
                Path guide = Files.createTempFile("lottoinjava-guida-superenalotto-", ".html");
                Files.copy(in, guide, StandardCopyOption.REPLACE_EXISTING);
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IOException("Il sistema non supporta l'apertura automatica del browser.");
                Desktop.getDesktop().browse(guide.toUri());
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.INFORMATION,
                            "Non riesco ad aprire automaticamente il browser. La guida è inclusa nel progetto in desktop/docs/guida_tecniche_superenalotto.html e desktop/docs/guida_tecniche_superenalotto.pdf.\n\nDettaglio: " + ex.getMessage(), ButtonType.OK);
                    a.initOwner(dialog); a.setHeaderText("Guida alle tecniche"); a.showAndWait();
                });
            }
        }, "lottoinjava-open-superenalotto-guide");
        opener.setDaemon(true);
        opener.start();
    }

    private void resetArchive(){ Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Vuoi svuotare l'archivio SuperEnalotto attivo? Prima verrà salvata una copia di sicurezza. L'archivio Lotto non verrà toccato.",ButtonType.CANCEL,ButtonType.OK);a.initOwner(dialog);a.setHeaderText("Conferma reset archivio");
        if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return; try{backup();Files.createDirectories(ARCHIVE.getParent());Files.deleteIfExists(ARCHIVE);draws.clear();highlightedCandidates.clear();spyDrawKeys.clear();resultDrawKeys.clear();updateStatus();refreshArchive();output.clear();output.setText("Archivio SuperEnalotto azzerato. Le copie di sicurezza sono conservate nella cartella backups.");}catch(Exception ex){error("Reset non riuscito",ex);} }
    private void backup() throws IOException { if(Files.exists(ARCHIVE)&&Files.size(ARCHIVE)>0){Path dir=ARCHIVE.getParent().resolve("backups");Files.createDirectories(dir);String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));Files.copy(ARCHIVE,dir.resolve("superenalotto_backup_"+stamp+".csv"),StandardCopyOption.COPY_ATTRIBUTES);} }
    private List<Draw> loadArchive(){try{return Files.exists(ARCHIVE)?parseCsv(Files.readString(ARCHIVE,StandardCharsets.UTF_8)):new ArrayList<>();}catch(Exception ex){return new ArrayList<>();}}
    private void writeArchive(List<Draw> data)throws IOException { Files.createDirectories(ARCHIVE.getParent());Path tmp=ARCHIVE.resolveSibling(ARCHIVE.getFileName()+".tmp");StringBuilder s=new StringBuilder("DATA;CONCORSO;N1;N2;N3;N4;N5;N6;JOLLY;SUPERSTAR\n");for(Draw d:sorted(data)){s.append(d.date).append(';').append(clean(d.contest));for(int n:d.numbers)s.append(';').append(n);s.append(';').append(clean(d.jolly)).append(';').append(clean(d.superstar)).append('\n');}Files.writeString(tmp,s,StandardCharsets.UTF_8);try{Files.move(tmp,ARCHIVE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,ARCHIVE,StandardCopyOption.REPLACE_EXISTING);} }
    private static String clean(String s){return s==null?"":s.replace(';',',').replace('\n',' ');}

    private void run(String mode){ if(draws.isEmpty()){output.setText("Archivio vuoto: importa o scarica prima le estrazioni.");return;} int spia=spy.getValue(),a=yearFrom.getValue(),b=yearTo.getValue(),k=shots.getValue(); if(a>b){output.setText("Intervallo anni non valido.");return;}
        try{List<Draw> period=draws.stream().filter(d->d.date.getYear()>=a&&d.date.getYear()<=b).collect(Collectors.toList());if(period.isEmpty())throw new IllegalArgumentException("Nessuna estrazione nel periodo selezionato.");
            String report=switch(mode){case "post"->postReport(period,spia,k);case "forecast"->forecastReport(period,spia);case "cyclo"->cycloReport(period,spia,k);case "backtest"->backtestReport(period,spia);case "compare"->comparativeBacktestReport(period,spia,k);case "cyclobacktest"->cyclometryBacktestReport(period,spia,k);case "holdout"->sadicaHoldoutReport(period,spia,k);default->unifiedReport(period,spia,k);};
            output.setText(report);
            assistantStatus.setText("♡ Calcolo eseguito! Analisi completata: ecco i risultati principali. ♡");
            assistantStatus.getStyleClass().remove("assistant-error");
            NeonAssistant.show(dialog, "Ho completato l'analisi SuperEnalotto. Il rapporto dettagliato è pronto nella finestra.", css);
            prepareArchiveHighlight(period,spia,k,mode);
            refreshArchive();
        }catch(Exception ex){output.setText("ANALISI NON COMPLETATA\n\n"+ex.getMessage()); if(assistantStatus!=null){assistantStatus.setText("⚠ Analisi non completata · controlla i parametri e riprova."); if(!assistantStatus.getStyleClass().contains("assistant-error"))assistantStatus.getStyleClass().add("assistant-error");}}}

    private void prepareArchiveHighlight(List<Draw> period,int spia,int k,String mode) {
        highlightedCandidates.clear(); spyDrawKeys.clear(); resultDrawKeys.clear();
        Map<Integer,Integer> freq=new HashMap<>(),cycles=new HashMap<>(); Map<Integer,List<Integer>> pos=new HashMap<>(); int events=0;
        for(int i=0;i<period.size();i++) if(period.get(i).has(spia)) {
            spyDrawKeys.add(period.get(i).key());
            int available=Math.min(k,period.size()-i-1);
            for(int j=i+1;j<=i+available;j++) resultDrawKeys.add(period.get(j).key());
            if(available<k) continue; // ciclo aperto: si colora, ma non entra nei punteggi storici
            events++; Set<Integer> seen=new HashSet<>(); Map<Integer,Integer> firstPosition=new HashMap<>();
            for(int j=i+1;j<=i+k;j++){Draw d=period.get(j);for(int n:d.numbers){freq.merge(n,1,Integer::sum);seen.add(n);firstPosition.putIfAbsent(n,j-i);}}
            for(int n:seen){cycles.merge(n,1,Integer::sum);pos.computeIfAbsent(n,z->new ArrayList<>()).add(firstPosition.get(n));}
        }
        if(mode.equals("forecast") || mode.equals("backtest") || mode.equals("compare") || mode.equals("holdout")) {
            highlightedCandidates.addAll(topFromReportNumbers(forecastReport(period,spia),15));
        } else if(mode.equals("cyclo") || mode.equals("cyclobacktest")) {
            highlightedCandidates.addAll(topFromReportNumbers(cycloReport(period,spia,k),15));
        } else if(mode.equals("unified")) {
            highlightedCandidates.addAll(topFromReportNumbers(postReport(period,spia,k),10));
            highlightedCandidates.addAll(topFromReportNumbers(cycloReport(period,spia,k),10));
            highlightedCandidates.addAll(topFromReportNumbers(forecastReport(period,spia),15));
        } else if(events>0) {
            highlightedCandidates.addAll(rank(freq,cycles,pos,events,k,spia).stream().limit(15).collect(Collectors.toList()));
        }
    }

    private void refreshArchive() {
        if(archiveView==null)return;
        String q=archiveSearch==null||archiveSearch.getText()==null?"":archiveSearch.getText().trim().toLowerCase(Locale.ROOT);
        List<Draw> filtered=new ArrayList<>();
        for(Draw d:draws){String searchable=d.date.toString()+" "+d.contest+" "+Arrays.stream(d.numbers).mapToObj(String::valueOf).collect(Collectors.joining(" "));
            if(searchable.toLowerCase(Locale.ROOT).contains(q))filtered.add(d);}
        Collections.reverse(filtered); archiveView.getItems().setAll(filtered);
    }

    private Node buildDrawCard(Draw d) {
        VBox card=new VBox(6); card.getStyleClass().add("card");
        String label=d.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if(!d.contest.isBlank())label+="  ·  CONCORSO "+d.contest;
        Label date=new Label(label);date.getStyleClass().add("card-date");
        HBox balls=new HBox(7);balls.setAlignment(Pos.CENTER_LEFT);
        for(int n:d.numbers){Label ball=new Label(String.format(Locale.ROOT,"%02d",n));ball.setMinSize(38,38);ball.setPrefSize(38,38);ball.setMaxSize(38,38);ball.getStyleClass().add("ball");
            if(highlightArchive!=null&&highlightArchive.isSelected()){
                // Stessa convenzione cromatica dell'archivio Lotto: spia verde, risultato trovato rosso.
                if(n==spy.getValue()&&spyDrawKeys.contains(d.key()))ball.getStyleClass().add("ball-spy");
                else if(resultDrawKeys.contains(d.key())&&highlightedCandidates.contains(n))ball.getStyleClass().add("ball-result");
            }
            balls.getChildren().add(ball);
        }
        card.getChildren().addAll(date,balls);
        if(!d.jolly.isBlank()||!d.superstar.isBlank()) {Label extras=new Label("Jolly: "+(d.jolly.isBlank()?"—":d.jolly)+"   ·   SuperStar: "+(d.superstar.isBlank()?"—":d.superstar));extras.getStyleClass().add("hint");card.getChildren().add(extras);}
        return card;
    }
    private String archiveSummary(){return "ARCHIVIO SUPERENALOTTO\nEstrazioni: "+draws.size()+"\nPrima: "+draws.get(0).date+"\nUltima: "+draws.get(draws.size()-1).date+"\n\nCSV attivo: "+ARCHIVE;}
    private String postReport(List<Draw> d,int spia,int k){Map<Integer,Integer> freq=new HashMap<>(),cycles=new HashMap<>();Map<Integer,List<Integer>> pos=new HashMap<>();int events=0;
        for(int i=0;i<d.size();i++)if(d.get(i).has(spia)&&i+k<d.size()){events++;Set<Integer> seen=new HashSet<>();Map<Integer,Integer> firstPosition=new HashMap<>();for(int j=i+1;j<=i+k;j++){Draw x=d.get(j);for(int n:x.numbers){freq.merge(n,1,Integer::sum);seen.add(n);firstPosition.putIfAbsent(n,j-i);}}for(int n:seen){cycles.merge(n,1,Integer::sum);pos.computeIfAbsent(n,z->new ArrayList<>()).add(firstPosition.get(n));}}
        if(events==0)return "Nessun ciclo post-spia completo per il numero "+spia+" nel periodo.";
        final int ev=events;List<Integer> top=rank(freq,cycles,pos,ev,k,spia);StringBuilder s=new StringBuilder("ANALISI POST-SPIA\nSpia: "+fmt(spia)+" · estrazioni "+d.size()+" · cicli completi "+events+" · colpi "+k+"\n\nTOP NUMERI PER SCORE (frequenza 40%, cicli 40%, posizione 20%)\n");for(int n:top)s.append(String.format(Locale.ROOT,"%02d  freq=%d  cicli=%d/%d  pos.media=%.2f  score=%.2f%n",n,freq.getOrDefault(n,0),cycles.getOrDefault(n,0),events,avg(pos.get(n)),score(n,freq,cycles,pos,events,k)));s.append("\nNota: le statistiche storiche non aumentano la probabilità matematica dell'estrazione.");return s.toString();}
    private List<Integer> rank(Map<Integer,Integer> freq,Map<Integer,Integer> cycles,Map<Integer,List<Integer>> pos,int events,int k,int spia){double max=freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);return java.util.stream.IntStream.rangeClosed(1,90).filter(n->n!=spia&&freq.getOrDefault(n,0)>0).boxed().sorted(Comparator.<Integer>comparingDouble(n->-score(n,freq,cycles,pos,events,k)).thenComparingInt(n->-freq.getOrDefault(n,0)).thenComparingInt(n->n)).collect(Collectors.toList());}
    private double score(int n,Map<Integer,Integer> f,Map<Integer,Integer> c,Map<Integer,List<Integer>> p,int ev,int k){double max=f.values().stream().mapToInt(Integer::intValue).max().orElse(1);return f.getOrDefault(n,0)/max*40+c.getOrDefault(n,0)/(double)Math.max(1,ev)*40+(p.containsKey(n)?(k+1-avg(p.get(n)))/k*20:0);}
    private String forecastReport(List<Draw> d,int spia){if(d.size()<30)throw new IllegalArgumentException("Il motore statistico richiede almeno 30 estrazioni nel periodo.");int window=Math.min(120,Math.max(30,d.size()/5));Map<Integer,Integer> freq=new HashMap<>(),recent=new HashMap<>(),cycle=new HashMap<>(),co=new HashMap<>();int[] delay=new int[91];Arrays.fill(delay,d.size());
        for(int i=0;i<d.size();i++){Draw x=d.get(i);for(int n:x.numbers){freq.merge(n,1,Integer::sum);if(i>=d.size()-window)recent.merge(n,1,Integer::sum);delay[n]=d.size()-1-i;}}
        for(int i=0;i<d.size();i++)if(d.get(i).has(spia)){Set<Integer> seen=new HashSet<>();for(int j=i+1;j<Math.min(d.size(),i+10);j++)for(int n:d.get(j).numbers)seen.add(n);for(int n:seen)co.merge(n,1,Integer::sum);for(int j=i+1;j<Math.min(d.size(),i+10);j++)for(int n:d.get(j).numbers)cycle.merge(n,1,Integer::sum);}
        double[] fn=norm(freq),rn=norm(recent),cn=norm(cycle),sn=norm(co);int maxDelay=Arrays.stream(delay,1,91).max().orElse(1);StringBuilder s=new StringBuilder("PREVISIONE STATISTICA · finestra recente "+window+" estrazioni\nSpia "+fmt(spia)+" · periodo "+d.get(0).date+" – "+d.get(d.size()-1).date+"\n\nPesi come nel main.py: frequenza 25%, recenza 25%, presenza nei cicli 25%, co-presenza spia 15%, ritardo 10%.\nIl ritardo è informativo, non una certezza.\n\nTOP 15 SCORE\n");List<Integer> nums=new ArrayList<>();for(int n=1;n<=90;n++)nums.add(n);nums.sort(Comparator.<Integer>comparingDouble(n->-(.25*fn[n]+.25*rn[n]+.25*cn[n]+.15*sn[n]+.10*delay[n]/(double)Math.max(1,maxDelay))).thenComparingInt(n->n));for(int i=0;i<15;i++){int n=nums.get(i);double sc=(.25*fn[n]+.25*rn[n]+.25*cn[n]+.15*sn[n]+.10*delay[n]/(double)Math.max(1,maxDelay))*100;s.append(String.format(Locale.ROOT,"%02d  score %.2f · freq %d · recenti %d · cicli %d · co-spia %d · ritardo %d%n",n,sc,freq.getOrDefault(n,0),recent.getOrDefault(n,0),cycle.getOrDefault(n,0),co.getOrDefault(n,0),delay[n]));}s.append("\nPANIERE TOP 15: ").append(nums.subList(0,15).stream().map(SuperEnalottoDialog::fmt).collect(Collectors.joining(" - "))).append("\nSestine candidate: combinazioni TOP 15 ordinate per score medio (prime 10)\n");List<Integer> comboPool=new ArrayList<>(nums.subList(0,15));if(comboPool.remove(Integer.valueOf(spia)))comboPool.add(spia);List<List<Integer>> combos=new ArrayList<>();combine(comboPool,0,6,new ArrayList<>(),combos);Map<String,Integer> pair=new HashMap<>();for(Draw x:d)for(int i=0;i<6;i++)for(int j=i+1;j<6;j++)pair.merge(Math.min(x.numbers[i],x.numbers[j])+":"+Math.max(x.numbers[i],x.numbers[j]),1,Integer::sum);combos.sort(Comparator.<List<Integer>>comparingDouble(c->-forecastComboScore(c,fn,rn,cn,sn,delay,maxDelay,pair)));for(int i=0;i<Math.min(10,combos.size());i++)s.append(String.format("%02d. %s%n",i+1,combos.get(i).stream().map(SuperEnalottoDialog::fmt).collect(Collectors.joining(" "))));return s.toString();}
    private double forecastComboScore(List<Integer> combo,double[] fn,double[] recent,double[] cycle,double[] co,int[] delay,int maxDelay,Map<String,Integer> pairs) {
        double scoreNum=combo.stream().mapToDouble(n->(.25*fn[n]+.25*recent[n]+.25*cycle[n]+.15*co[n]+.10*delay[n]/(double)Math.max(1,maxDelay))*100).average().orElse(0);
        int even=0,sum=0;Set<Integer> decades=new HashSet<>();int pairScore=0;
        for(int n:combo){if(n%2==0)even++;sum+=n;decades.add((n-1)/10);}
        for(int i=0;i<combo.size();i++)for(int j=i+1;j<combo.size();j++){int a=Math.min(combo.get(i),combo.get(j)),b=Math.max(combo.get(i),combo.get(j));pairScore+=pairs.getOrDefault(a+":"+b,0);}
        double balance=(even>=2&&even<=4)?1.0:.55;
        double distribution=Math.min(1.0,decades.size()/5.0);
        double sumBonus=(sum>=120&&sum<=330)?1.0:.60;
        return scoreNum*.70+balance*10+distribution*10+sumBonus*10+Math.min(pairScore/30.0,10.0);
    }

    private String cycloReport(List<Draw> d,int spia,int k) {
        int[] freq=new int[91],distFreq=new int[46],eventsDist=new int[46];
        Map<Integer,List<Integer>> positions=new HashMap<>();
        int events=0;
        for(int i=0;i<d.size();i++) if(d.get(i).has(spia)&&i+k<d.size()) {
            events++; Set<Integer> seenD=new HashSet<>(),seenN=new HashSet<>();
            for(int j=i+1;j<=i+k;j++) {
                Draw x=d.get(j);
                for(int n:x.numbers) if(n!=spia) {
                    int dist=Math.min(Math.abs(n-spia),90-Math.abs(n-spia));
                    freq[n]++; distFreq[dist]++; seenD.add(dist);
                    if(seenN.add(n)) positions.computeIfAbsent(n,z->new ArrayList<>()).add(j-i);
                }
            }
            for(int z:seenD) eventsDist[z]++;
        }
        if(events==0) return "Dati insufficienti per la ciclometria: nessun ciclo completo.";
        List<Integer> ns=new ArrayList<>();
        for(int n=1;n<=90;n++) if(n!=spia) ns.add(n);
        final int totalEvents=events;
        ns.sort(Comparator.<Integer>comparingDouble(n->-cycloScore(n,spia,freq,distFreq,positions,totalEvents,k)).thenComparingInt(n->n));
        StringBuilder out=new StringBuilder("CICLOMETRIA SPIA · distanza circolare su 90 numeri\nSpia "+fmt(spia)+" · eventi "+events+" · colpi "+k+"\n\nTOP 15 NUMERI\n");
        for(int i=0;i<Math.min(15,ns.size());i++) {
            int n=ns.get(i),dist=Math.min(Math.abs(n-spia),90-Math.abs(n-spia));
            out.append(String.format(Locale.ROOT,"%02d · distanza %02d · frequenza %d · eventi distanza %d · score %.2f%n",n,dist,freq[n],eventsDist[dist],cycloScore(n,spia,freq,distFreq,positions,totalEvents,k)));
        }
        out.append("\nANELLI CICLOMETRICI (distanza, frequenza, presenza negli eventi e lift)\n");
        List<Integer> ds=new ArrayList<>(); for(int i=1;i<=45;i++) ds.add(i);
        ds.sort(Comparator.<Integer>comparingDouble(i->{double expected=totalEvents*k*6.0/90.0*(i<45?2:1);return -(distFreq[i]/Math.max(1.0,expected));}).thenComparingInt(i->i));
        for(int i=0;i<Math.min(15,ds.size());i++){int z=ds.get(i);double expected=events*k*6.0/90.0*(z<45?2:1);double lift=expected>0?distFreq[z]/expected:0;out.append(String.format(Locale.ROOT,"d=%02d · occorrenze %d · eventi %d · attese %.2f · lift %.3f%n",z,distFreq[z],eventsDist[z],expected,lift));}
        out.append("\nSCHEDE CICLOMETRICHE DAI TOP 10 (ordinamento statistico, non garanzia)\n");
        List<Integer> top=ns.subList(0,Math.min(10,ns.size()));
        int[] sizes={2,3,4,6},limits={6,6,5,4}; String[] labels={"Coppie","Terni","Quartine","Sestine"};
        for(int ix=0;ix<sizes.length;ix++) {
            int size=sizes[ix]; List<List<Integer>> combos=new ArrayList<>();
            if(top.size()>=size) combine(top,0,size,new ArrayList<>(),combos);
            final int eventCount=totalEvents,shotCount=k;
            combos.sort(Comparator.<List<Integer>>comparingDouble(c->-cycloComboScore(c,spia,freq,distFreq,positions,eventCount,shotCount)));
            out.append("\n").append(labels[ix]).append(" TOP ").append(Math.min(limits[ix],combos.size())).append(":\n");
            for(int j=0;j<Math.min(limits[ix],combos.size());j++) {
                List<Integer> c=combos.get(j);
                out.append(String.format(Locale.ROOT,"%02d. %s · score %.2f%n",j+1,c.stream().map(SuperEnalottoDialog::fmt).collect(Collectors.joining(" ")),cycloComboScore(c,spia,freq,distFreq,positions,eventCount,shotCount)));
            }
        }
        return out.toString();
    }

    private double cycloScore(int n,int spia,int[] freq,int[] distFreq,Map<Integer,List<Integer>> positions,int events,int k) {
        int dist=Math.min(Math.abs(n-spia),90-Math.abs(n-spia));
        int maxf=Arrays.stream(freq).max().orElse(1),maxd=Arrays.stream(distFreq).max().orElse(1);
        List<Integer> p=positions.getOrDefault(n,List.of()); double mean=avg(p);
        double variance=p.stream().mapToDouble(x->(x-mean)*(x-mean)).average().orElse(0);
        double stability=p.isEmpty()?0:1/(1+Math.sqrt(variance));
        double expected=events*k*6.0/90.0;
        double lift=expected>0?freq[n]/expected:0.0;
        // Stessa scala 0-100 e stessi pesi della ciclometria nel main.py.
        double numberStrength=freq[n]/(double)Math.max(1,maxf);
        double ringStrength=distFreq[dist]/(double)Math.max(1,maxd);
        double liftScore=Math.min(lift,3.0)/3.0;
        return 100.0*(.45*numberStrength+.30*ringStrength+.15*stability+.10*liftScore);
    }

    private double cycloComboScore(List<Integer> combo,int spia,int[] freq,int[] distFreq,Map<Integer,List<Integer>> positions,int events,int k) {
        double avgScore=combo.stream().mapToDouble(n->cycloScore(n,spia,freq,distFreq,positions,events,k)).average().orElse(0);
        Set<Integer> distances=new HashSet<>(); int min=45,max=1;
        for(int n:combo){int d=Math.min(Math.abs(n-spia),90-Math.abs(n-spia));distances.add(d);min=Math.min(min,d);max=Math.max(max,d);}
        double uniqueBonus=distances.size()/(double)combo.size()*8.0;
        double spreadBonus=Math.min((max-min)/45.0,1.0)*4.0;
        return avgScore+uniqueBonus+spreadBonus;
    }

    private String unifiedReport(List<Draw>d,int spia,int k){String a=postReport(d,spia,k),b=cycloReport(d,spia,k),c=forecastReport(d,spia);Set<Integer> post=topFromReportNumbers(a,10),cyc=topFromReportNumbers(b,10),forecast=topFromReportNumbers(c,15);Map<Integer,Integer> agree=new HashMap<>();for(int n:post)agree.merge(n,1,Integer::sum);for(int n:cyc)agree.merge(n,1,Integer::sum);for(int n:forecast)agree.merge(n,1,Integer::sum);StringBuilder s=new StringBuilder("RESOCONTO UNIFICATO SUPERENALOTTO\nI tre motori sono diagnostici, non predittori certi.\n\nINCROCIO POST-SPIA + CICLOMETRIA + STATISTICA\n");agree.entrySet().stream().sorted(Map.Entry.<Integer,Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(10).forEach(e->s.append(fmt(e.getKey())).append(" · concordanza ").append(e.getValue()).append(" motori\n"));s.append("\n").append(a).append("\n\n").append(b).append("\n\n").append(c);return s.toString();}
    private Set<Integer> topFromReportNumbers(String report,int limit){Set<Integer> out=new LinkedHashSet<>();for(String line:report.split("\\R")){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{2})\\s+(?:freq=|· distanza|score)").matcher(line);if(m.find())out.add(Integer.parseInt(m.group(1)));if(out.size()>=limit)break;}return out;}
    private String backtestReport(List<Draw>d,int spia){int min=120;if(d.size()<=min+1)throw new IllegalArgumentException("Per il backtest walk-forward servono almeno 122 estrazioni nel periodo.");List<Integer> hits=new ArrayList<>();for(int i=min;i<d.size()-1;i++)if(d.get(i).has(spia)){List<Draw> history=d.subList(0,i);String report=forecastReport(history,spia);Set<Integer> top=new HashSet<>();java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{2})  score",java.util.regex.Pattern.MULTILINE).matcher(report);while(m.find()&&top.size()<6)top.add(Integer.parseInt(m.group(1)));if(top.isEmpty())continue;int h=0;for(int n:d.get(i+1).numbers)if(top.contains(n))h++;hits.add(h);}if(hits.isEmpty())return "Nessun evento spia utile per il backtest.";double avg=hits.stream().mapToInt(Integer::intValue).average().orElse(0);int[] dist=new int[7];for(int h:hits)dist[h]++;return String.format(Locale.ROOT,"BACKTEST WALK-FORWARD · TOP 6 contro estrazione successiva%nEventi valutati: %d%nHit medi: %.3f su 6%nRiferimento casuale teorico: %.3f%n",hits.size(),avg,6.0*6/90)+"Distribuzione hit:\n"+java.util.stream.IntStream.rangeClosed(0,6).mapToObj(h->h+ " hit: "+dist[h]).collect(Collectors.joining("\n"))+"\n\nIl test usa solo lo storico precedente a ciascun evento. Non garantisce risultati futuri.";}

    /**
     * Verifica sadica con holdout: per ogni evento la previsione usa soltanto lo storico precedente;
     * il 70% degli eventi validi è usato per la valutazione iniziale e il 30% finale resta holdout.
     */
    private String sadicaHoldoutReport(List<Draw> d,int spia,int k) {
        int minimum=120;
        if(d.size()<minimum+k+8) throw new IllegalArgumentException("Per la verifica sadica servono almeno "+(minimum+k+8)+" estrazioni nel periodo.");
        List<Integer> eligible=new ArrayList<>();
        for(int i=minimum;i+k<d.size();i++) if(d.get(i).has(spia)) eligible.add(i);
        if(eligible.size()>60){List<Integer> sampled=new ArrayList<>();double step=eligible.size()/60.0;for(int j=0;j<60;j++)sampled.add(eligible.get((int)(j*step)));eligible=sampled;}
        List<Integer> modelHits=new ArrayList<>(),randomHits=new ArrayList<>();
        Random rng=new Random(71201L+spia);
        List<Integer> pool=java.util.stream.IntStream.rangeClosed(1,90).filter(n->n!=spia).boxed().collect(Collectors.toList());
        for(int i:eligible){
            List<Draw> history=d.subList(0,i);
            String report;try{report=forecastReport(history,spia);}catch(Exception ex){continue;}
            Set<Integer> top=new LinkedHashSet<>();
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{2})  score",java.util.regex.Pattern.MULTILINE).matcher(report);
            while(m.find()&&top.size()<6){int n=Integer.parseInt(m.group(1));if(n!=spia)top.add(n);}
            if(top.size()<6)continue;
            Set<Integer> actual=new HashSet<>();for(int j=i+1;j<=i+k;j++)for(int n:d.get(j).numbers)actual.add(n);
            List<Integer> shuffled=new ArrayList<>(pool);Collections.shuffle(shuffled,rng);Set<Integer> random=new HashSet<>(shuffled.subList(0,6));
            modelHits.add(intersectionSize(top,actual));randomHits.add(intersectionSize(random,actual));
        }
        int n=modelHits.size();if(n<8)return "VERIFICA SADICA + HOLDOUT\nEventi validi: "+n+"\nDati insufficienti: servono almeno 8 eventi validi per distinguere valutazione e holdout.";
        int split=Math.max(4,Math.min(n-4,(int)Math.floor(n*.70)));
        List<Double> trainDiff=new ArrayList<>(),holdDiff=new ArrayList<>();
        for(int i=0;i<n;i++){double delta=modelHits.get(i)-randomHits.get(i);(i<split?trainDiff:holdDiff).add(delta);}
        List<Integer> trainModel=modelHits.subList(0,split),trainRandom=randomHits.subList(0,split);
        List<Integer> holdModel=modelHits.subList(split,n),holdRandom=randomHits.subList(split,n);
        double trainDelta=avg(trainModel)-avg(trainRandom),holdDelta=avg(holdModel)-avg(holdRandom);
        double[] ci=bootstrapCi(holdDiff,2000,71203L);double p=pairedRandomizationP(holdDiff,71204L,4000);
        double expected=6.0*(1.0-Math.pow(84.0/90.0,k));
        String verdict=holdDelta>0&&p<0.05?"SEGNALE INTERESSANTE DA RIVERIFICARE":"NON DIMOSTRATO SULL'HOLDOUT";
        return String.format(Locale.ROOT,"VERIFICA SADICA SUPERENALOTTO · WALK-FORWARD + HOLDOUT%nSpia: %02d · colpi successivi: %d%nEventi validi appaiati: %d%n%nVALUTAZIONE INIZIALE (70%%) · eventi %d%nModello TOP 6: %.3f hit medi nell'unione dei colpi%nCasuale TOP 6: %.3f%nDelta modello − casuale: %.3f%n%nHOLDOUT FINALE (30%%) · eventi %d%nModello TOP 6: %.3f hit medi nell'unione dei colpi%nCasuale TOP 6: %.3f%nRiferimento teorico casuale per 6 numeri: %.3f%nDelta modello − casuale: %.3f%nIC bootstrap 95%% del delta holdout: [%.3f, %.3f]%nP-value randomizzazione appaiato holdout: %.4f%nVERDETTO: %s%n%nOgni previsione è stata costruita usando solo estrazioni precedenti all'evento. L'holdout è una verifica esplorativa; eventi sovrapposti e scelta della spia limitano l'interpretazione statistica. Non dimostra un vantaggio futuro.",spia,k,n,split,avg(trainModel),avg(trainRandom),trainDelta,n-split,avg(holdModel),avg(holdRandom),expected,holdDelta,ci[0],ci[1],p,verdict);
    }

    /** Walk-forward ciclometrico ispirato al main.py: ogni previsione usa solo lo storico precedente. */
    private String cyclometryBacktestReport(List<Draw> d,int spia,int k) {
        List<Integer> eligible=new ArrayList<>();
        for(int i=20;i<d.size()-k;i++) if(d.get(i).has(spia)) eligible.add(i);
        if(eligible.size()>40){List<Integer> sample=new ArrayList<>();double step=eligible.size()/40.0;for(int j=0;j<40;j++)sample.add(eligible.get((int)(j*step)));eligible=sample;}
        List<Integer> modelHits=new ArrayList<>(),randomHits=new ArrayList<>();
        Random rng=new Random(45601L);List<Integer> pool=java.util.stream.IntStream.rangeClosed(1,90).filter(n->n!=spia).boxed().collect(Collectors.toList());
        for(int i:eligible){
            List<Draw> history=d.subList(0,i);
            String report;
            try{report=cycloReport(history,spia,k);}catch(Exception ex){continue;}
            java.util.regex.Matcher evm=java.util.regex.Pattern.compile("eventi\\s+(\\d+)").matcher(report);
            if(!evm.find()||Integer.parseInt(evm.group(1))<3)continue;
            Set<Integer> top=topFromReportNumbers(report,5);if(top.size()<5)continue;
            Set<Integer> actual=new HashSet<>();for(int j=i+1;j<=i+k;j++)for(int n:d.get(j).numbers)actual.add(n);
            List<Integer> shuffled=new ArrayList<>(pool);Collections.shuffle(shuffled,rng);Set<Integer> random=new HashSet<>(shuffled.subList(0,5));
            modelHits.add(intersectionSize(top,actual));randomHits.add(intersectionSize(random,actual));
        }
        if(modelHits.isEmpty())return "Dati insufficienti per il backtest ciclometrico: servono almeno 3 cicli storici validi per evento e un periodo futuro completo.";
        double model=avg(modelHits),random=avg(randomHits),expected=5.0*(1.0-Math.pow(84.0/90.0,k));
        List<Double> diffs=new ArrayList<>();for(int i=0;i<modelHits.size();i++)diffs.add((double)(modelHits.get(i)-randomHits.get(i)));
        double[] ci=bootstrapCi(diffs,2000,45601L);double p=pairedRandomizationP(diffs,45602L,2000);
        int maxModel=modelHits.stream().mapToInt(Integer::intValue).max().orElse(0),maxRandom=randomHits.stream().mapToInt(Integer::intValue).max().orElse(0);
        return String.format(Locale.ROOT,"BACKTEST CICLOMETRIA vs CASUALE · walk-forward senza leakage%nEventi valutati: %d%nTOP 5 ciclometrico: %.3f hit medi nei successivi %d colpi%nTOP 5 casuale: %.3f hit medi nei successivi %d colpi%nRiferimento teorico casuale (unione dei colpi): %.3f%nDelta modello-casuale: %.3f%nIC bootstrap 95%%: [%.3f, %.3f]%nP-value randomizzazione appaiato: %.4f%nMassimo hit modello/casuale: %d / %d%n\nIl test misura solo lo storico scelto; non dimostra capacità predittiva futura.",modelHits.size(),model,k,random,k,expected,model-random,ci[0],ci[1],p,maxModel,maxRandom);
    }

    private String comparativeBacktestReport(List<Draw> d,int spia,int k){
        int minimum=120;
        if(d.size()<=minimum+1) throw new IllegalArgumentException("Per il confronto walk-forward servono almeno 122 estrazioni nel periodo.");
        List<Integer> baseHits=new ArrayList<>(),modelHits=new ArrayList<>(),randomHits=new ArrayList<>();
        Random rng=new Random(2609L); List<Integer> eligible=new ArrayList<>();
        for(int i=minimum;i<d.size()-1;i++) if(d.get(i).has(spia)) eligible.add(i);
        if(eligible.size()>80){List<Integer> sampled=new ArrayList<>();double step=eligible.size()/80.0;for(int j=0;j<80;j++)sampled.add(eligible.get((int)(j*step)));eligible=sampled;}
        for(int i:eligible){List<Draw> history=d.subList(0,i);Set<Integer> base=topFromReportNumbers(postReport(history,spia,Math.min(9,Math.max(1,k))),6);Set<Integer> model=new LinkedHashSet<>();java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{2})  score",java.util.regex.Pattern.MULTILINE).matcher(forecastReport(history,spia));while(m.find()&&model.size()<6)model.add(Integer.parseInt(m.group(1)));if(base.size()<6||model.size()<6)continue;Set<Integer> random=new HashSet<>();while(random.size()<6)random.add(1+rng.nextInt(90));Set<Integer> actual=Arrays.stream(d.get(i+1).numbers).boxed().collect(Collectors.toSet());baseHits.add(intersectionSize(base,actual));modelHits.add(intersectionSize(model,actual));randomHits.add(intersectionSize(random,actual));}
        if(modelHits.isEmpty())return "Nessun evento valido per il confronto walk-forward.";
        double mb=avg(baseHits),mm=avg(modelHits),mr=avg(randomHits);
        List<Double> diffRandom=new ArrayList<>(),diffBase=new ArrayList<>();
        for(int i=0;i<modelHits.size();i++){diffRandom.add((double)(modelHits.get(i)-randomHits.get(i)));diffBase.add((double)(modelHits.get(i)-baseHits.get(i)));}
        double meanRandom=avgD(diffRandom),meanBase=avgD(diffBase);
        double[] ciRandom=bootstrapCi(diffRandom,2000,2609L),ciBase=bootstrapCi(diffBase,2000,2611L);
        double pRandom=pairedRandomizationP(diffRandom,2610L,2000),pBase=pairedRandomizationP(diffBase,2612L,2000);
        return String.format(Locale.ROOT,"CONFRONTO WALK-FORWARD · nessun dato futuro usato per costruire la previsione%nEventi valutati appaiati: %d%nMedia hit TOP 6 post-spia: %.3f / 6%nMedia hit TOP 6 statistica: %.3f / 6%nMedia hit sestina casuale: %.3f / 6%nRiferimento teorico casuale: %.3f / 6%n\nSTATISTICA − CASUALE: delta %.3f · IC bootstrap 95%% [%.3f, %.3f] · p randomizzazione %.4f%nSTATISTICA − POST-SPIA: delta %.3f · IC bootstrap 95%% [%.3f, %.3f] · p randomizzazione %.4f%n\nIl confronto è esplorativo: dipendenze tra eventi, scelta della spia e molte analisi possibili limitano l'interpretazione dei p-value. Non implica che il modello migliori le probabilità future.",modelHits.size(),mb,mm,mr,6.0*6/90,meanRandom,ciRandom[0],ciRandom[1],pRandom,meanBase,ciBase[0],ciBase[1],pBase);
    }
    private static int intersectionSize(Set<Integer> a,Set<Integer> b){int n=0;for(int x:a)if(b.contains(x))n++;return n;}
    private static double avgD(List<Double> v){return v==null||v.isEmpty()?0:v.stream().mapToDouble(Double::doubleValue).average().orElse(0);}
    private static double pairedRandomizationP(List<Double> differences,long seed,int reps){if(differences.isEmpty())return 1;double observed=avgD(differences);Random r=new Random(seed);int extreme=0;for(int b=0;b<reps;b++){double sum=0;for(double v:differences)sum+=(r.nextBoolean()?v:-v);if(sum/differences.size()>=observed)extreme++;}return (extreme+1.0)/(reps+1.0);}
    private static double[] bootstrapCi(List<Double> values,int reps,long seed){if(values.isEmpty())return new double[]{0,0};Random r=new Random(seed);double[] means=new double[reps];for(int i=0;i<reps;i++){double sum=0;for(int j=0;j<values.size();j++)sum+=values.get(r.nextInt(values.size()));means[i]=sum/values.size();}Arrays.sort(means);return new double[]{means[(int)(.025*(reps-1))],means[(int)(.975*(reps-1))]};}

    private static double avg(List<Integer> v){return v==null||v.isEmpty()?0:v.stream().mapToInt(Integer::intValue).average().orElse(0);}
    private static double[] norm(Map<Integer,Integer> m){double[] x=new double[91];int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;for(int n=1;n<=90;n++){int v=m.getOrDefault(n,0);lo=Math.min(lo,v);hi=Math.max(hi,v);}if(lo==Integer.MAX_VALUE){lo=hi=0;}for(int n=1;n<=90;n++)x[n]=hi==lo?.5:(m.getOrDefault(n,0)-lo)/(double)(hi-lo);return x;}
    private static String fmt(int n){return String.format(Locale.ROOT,"%02d",n);}
    private void error(String title,Exception ex){Alert a=new Alert(Alert.AlertType.ERROR,title+"\n\n"+(ex==null?"Errore sconosciuto":ex.getMessage()),ButtonType.OK);a.initOwner(dialog);a.setHeaderText(title);a.showAndWait();}
    private static void combine(List<Integer> pool,int idx,int remaining,List<Integer> cur,List<List<Integer>> out){if(remaining==0){out.add(new ArrayList<>(cur));return;}for(int i=idx;i<=pool.size()-remaining;i++){cur.add(pool.get(i));combine(pool,i+1,remaining-1,cur,out);cur.remove(cur.size()-1);}}
    private static List<Draw> sorted(Collection<Draw> input){Map<String,Draw> unique=new LinkedHashMap<>();for(Draw d:input)unique.put(d.key(),d);List<Draw> out=new ArrayList<>(unique.values());out.sort(Comparator.comparing(x->x.date));return out;}
    private static List<Draw> parseCsv(String content)throws Exception {String[] lines=content.replace("\uFEFF","").split("\\R");if(lines.length<2)throw new IOException("CSV vuoto o senza righe dati.");char delim=lines[0].contains(";")?';':lines[0].contains("\t")?'\t':',';List<String> header=split(lines[0],delim).stream().map(SuperEnalottoDialog::norm).collect(Collectors.toList());int di=find(header,"DATA","DATE"),ci=find(header,"CONCORSO","CONCORSO N","CONCORSO NUMERO","NCONCORSO");int[] ni=new int[6];for(int i=0;i<6;i++)ni[i]=find(header,"N"+(i+1),"NUMERO"+(i+1),"NUM"+(i+1));int ji=find(header,"JOLLY"),si=find(header,"SUPERSTAR");if(di<0||Arrays.stream(ni).anyMatch(x->x<0))throw new IOException("Formato CSV non riconosciuto: servono DATA e N1,N2,N3,N4,N5,N6.");List<Draw> out=new ArrayList<>();for(int i=1;i<lines.length;i++){if(lines[i].trim().isEmpty())continue;try{List<String> row=split(lines[i],delim);LocalDate date=parseDate(value(row,di));int[] nums=new int[6];Set<Integer> seen=new HashSet<>();for(int j=0;j<6;j++){nums[j]=(int)Double.parseDouble(value(row,ni[j]).replace(',','.'));if(nums[j]<1||nums[j]>90||!seen.add(nums[j]))throw new IllegalArgumentException();}out.add(new Draw(date,value(row,ci),nums,value(row,ji),value(row,si)));}catch(Exception ignored){}}
        return sorted(out);}
    private static String value(List<String> row,int i){return i<0||i>=row.size()?"":row.get(i).trim();}
    private static int find(List<String> h,String... keys){for(String k:keys){String n=norm(k);for(int i=0;i<h.size();i++)if(h.get(i).equals(n))return i;}return -1;}
    private static String norm(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
    private static LocalDate parseDate(String s){for(DateTimeFormatter f:List.of(DateTimeFormatter.ofPattern("d/M/uuuu"),DateTimeFormatter.ofPattern("dd-MM-uuuu"),DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("d-M-uuuu")))try{return LocalDate.parse(s.trim(),f);}catch(Exception ignored){}throw new IllegalArgumentException("Data non valida: "+s);}
    private static List<String> split(String line,char delimiter){List<String> out=new ArrayList<>();StringBuilder cur=new StringBuilder();boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){cur.append('"');i++;}else quoted=!quoted;}else if(c==delimiter&&!quoted){out.add(cur.toString());cur.setLength(0);}else cur.append(c);}out.add(cur.toString());return out;}
    /** Confronto walk-forward delle strategie principali sullo storico SuperEnalotto. */
    private void showLaboratory() {
        if (draws.size() < 35) { error("Laboratorio", new IllegalStateException("Servono almeno 35 estrazioni nell'archivio SuperEnalotto.")); return; }
        List<Draw> data = new ArrayList<>(draws);
        data.sort(Comparator.comparing(d -> d.date));
        Stage lab = new Stage(); lab.initOwner(dialog); lab.initModality(Modality.NONE); lab.setTitle("LottoInJava · Laboratorio SuperEnalotto");
        ComboBox<String> window = new ComboBox<>(FXCollections.observableArrayList("Ultime 100", "Ultime 300", "Ultime 500", "Tutto lo storico")); window.setValue("Ultime 300");
        Label intro = new Label("Walk-forward: ogni sestina viene scelta usando esclusivamente concorsi precedenti."); intro.setWrapText(true);
        CategoryAxis x = new CategoryAxis(); x.setLabel("Metodo"); NumberAxis y = new NumberAxis(); y.setLabel("Hit medi per sestina");
        javafx.scene.chart.BarChart<String,Number> chart = new javafx.scene.chart.BarChart<>(x,y); chart.setTitle("Tecniche contro baseline casuale"); chart.setAnimated(false); chart.setLegendVisible(false);
        TextArea report = new TextArea(); report.setEditable(false); report.setWrapText(true);
        Button run = new Button("▶ Esegui confronto");
        Runnable execute = () -> {
            int requested = window.getValue().startsWith("Ultime ") ? Integer.parseInt(window.getValue().substring(7)) : data.size();
            List<Draw> sample = data.size() > requested ? new ArrayList<>(data.subList(data.size()-requested,data.size())) : new ArrayList<>(data);
            int n=sample.size(); if(n<35){report.setText("Archivio insufficiente per la finestra selezionata.");chart.getData().clear();return;}
            int start=Math.max(25,(int)Math.floor(n*0.7)), tests=0; String[] methods={"Più frequenti","Più in ritardo","Frequenza recente (20)"};
            Map<String,Double> totals=new LinkedHashMap<>();for(String m:methods)totals.put(m,0.0);totals.put("Casuale",0.0); Random rng=new Random(20261022L);
            for(int i=start;i<n;i++){
                int[] freq=new int[91],recent=new int[91],delay=new int[91];Arrays.fill(delay,i+1);
                for(int j=0;j<i;j++)for(int num:sample.get(j).numbers){freq[num]++;delay[num]=i-j;if(j>=Math.max(0,i-20))recent[num]++;}
                for(String m:methods){List<Integer> rank=new ArrayList<>();for(int num=1;num<=90;num++)rank.add(num);
                    if(m.equals("Più frequenti"))rank.sort(Comparator.<Integer>comparingInt(a->-freq[a]).thenComparingInt(a->a));
                    else if(m.equals("Più in ritardo"))rank.sort(Comparator.<Integer>comparingInt(a->-delay[a]).thenComparingInt(a->a));
                    else rank.sort(Comparator.<Integer>comparingInt(a->-recent[a]).thenComparingInt(a->a));
                    Set<Integer> picks=new HashSet<>(rank.subList(0,6));for(int num:sample.get(i).numbers)if(picks.contains(num))totals.put(m,totals.get(m)+1.0);
                }
                Set<Integer> randomPick=new HashSet<>();while(randomPick.size()<6)randomPick.add(rng.nextInt(90)+1);for(int num:sample.get(i).numbers)if(randomPick.contains(num))totals.put("Casuale",totals.get("Casuale")+1.0);tests++;
            }
            chart.getData().clear();XYChart.Series<String,Number> series=new XYChart.Series<>();StringBuilder sb=new StringBuilder("LABORATORIO SUPERENALOTTO · WALK-FORWARD\nConcorsi nella finestra: ").append(n).append(" · concorsi di test: ").append(tests).append("\nSestina scelta solo con lo storico precedente.\n\n");double baseline=totals.get("Casuale")/tests;
            for(String m:methods){double avg=totals.get(m)/tests;series.getData().add(new XYChart.Data<>(m,avg));sb.append(String.format(Locale.ITALY,"%-23s hit medi %.4f · delta vs casuale %+.4f\n",m,avg,avg-baseline));}
            series.getData().add(new XYChart.Data<>("Casuale",baseline));chart.getData().add(series);sb.append(String.format(Locale.ITALY,"Casuale: %.4f hit medi per sestina\n",baseline));sb.append("\nNota: risultati descrittivi del campione storico, non probabilità future. I numeri estratti sono equiprobabili secondo il modello del gioco; non è una garanzia né un consiglio di puntata.");report.setText(sb.toString());
        };
        run.setOnAction(e->execute.run()); HBox controls=new HBox(10,new Label("Periodo:"),window,run);controls.setPadding(new Insets(10));controls.setAlignment(Pos.CENTER_LEFT);
        VBox root=new VBox(8,intro,controls,chart,report);root.setPadding(new Insets(12));VBox.setVgrow(chart,Priority.ALWAYS);VBox.setVgrow(report,Priority.ALWAYS);lab.setScene(new Scene(root,950,700));lab.show();execute.run();
    }

    private static final class Draw {final LocalDate date;final String contest;final int[] numbers;final String jolly,superstar;Draw(LocalDate d,String c,int[] n,String j,String s){date=d;contest=c==null?"":c;numbers=n;jolly=j==null?"":j;superstar=s==null?"":s;}boolean has(int n){for(int x:numbers)if(x==n)return true;return false;}String key(){return date.toString();}}
}
