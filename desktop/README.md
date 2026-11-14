# Archivio Lotto — versione desktop (Linux / AppImage)

Stessa logica dell'app Android: le cartelle `model/` e `analysis/` di `../app` sono usate **direttamente**
(un solo codice di calcolo per telefono e PC). Qui ci sono solo l'interfaccia JavaFX e il salvataggio su disco.

## Costruire l'AppImage (EndeavourOS / Arch)

    sudo pacman -S jdk21-openjdk maven curl fuse2 gtk3
    cd desktop
    ./build-appimage.sh

Esce `LottoInJava-1.0-x86_64.AppImage`. Per avviarlo: `chmod +x LottoInJava-1.0-x86_64.AppImage && ./LottoInJava-1.0-x86_64.AppImage`
(senza `fuse2`: `./LottoInJava-1.0-x86_64.AppImage --appimage-extract-and-run`).

Per provarlo senza creare l'AppImage: `mvn -q clean package && java -jar target/lottoinjava-desktop-1.0-all.jar`

## Dati

I dati stanno in `~/.local/share/lottoinjava/`:
- `lotto_archive.json`: archivio (stesso formato dell'app Android: puoi copiare quello del telefono qui)
- `giocate.json`: le tue giocate

Al primo avvio premi **Importa CSV** e scegli il tuo file storico.


## Menu Tecniche (revisione con verifica sul main.py)

Il menu **Tecniche** contiene 16 tecniche, tutte con i controlli del main.py (spia 1-90, colpi 1-50, anno iniziale <= finale,
ruota valida, periodo con dati, almeno una ruota per la verifica, posizioni 1-5, fisso 1-90):

| Tecnica | Riferimento nel main.py |
|---|---|
| Ricerca spia & resoconto, Previsione post-spia (TOP 5, ambi, terni), Ciclometria, Terni magici, Resoconto unificato, Convergenze, Verifica combinazioni, Apriori, Somma Ruote, Somma + Fisso | sì: risultati confrontati su archivio reale (vedi sotto) |
| Previsione a 5 numeri, D'Artagnan, Isotopia, Fibonacci, Cappuccino, Multistatico | no: esistono solo nell'app Java, non sono confrontabili |

### Verifica della spia su tutte le ruote (tecniche 21-23)

Tre tecniche solo Java, che condividono il motore `SpiaTutteEngine` (modulo condiviso `app/.../analysis`).

1. **Numeri da giocare dalla ruota di origine**: a ogni uscita della spia sulla ruota scelta (solo cicli completi nel periodo) si
   guardano i successivi N colpi sulla stessa ruota; i numeri che escono in più cicli (da 2 a 10, a scelta) sono quelli da giocare.
2. **Verifica su tutte le 11 ruote**, con gli stessi numeri e gli stessi parametri: per ogni ruota, in quanti cicli è uscito almeno un
   ambo, terno, quaterna o cinquina dei numeri giocati, con le percentuali e il valore atteso dal puro caso. La ruota di origine è
   marcata con `*` (i numeri sono scelti da quei dati, quindi il risultato è favorevole per costruzione); le altre ruote sono il controllo.
   Ogni ruota è valutata solo sui cicli in cui ha estrazioni (la Nazionale esiste dal 2005), quindi con archivi lunghi (1939-oggi)
   le percentuali restano corrette.
3. **Test walk-forward**: i numeri di ogni ciclo sono scelti usando solo i cicli precedenti (dal 9° ciclo in poi) e poi verificati,
   senza dati futuri; in fondo la media su tutte le ruote confrontata con il caso.

4. **Test su due periodi**: i numeri sono scelti nel primo periodo (di default il primo 70% degli anni) e verificati nel secondo, mai
   visto; i cicli a cavallo dei due periodi sono esclusi. L'anno di inizio del secondo periodo si può fissare (0 = automatico).
5. **Bilancio di gioco** (ambo, terno, quaterna o cinquina sui numeri scelti): simula 1 € per combinazione a ogni colpo, fermandosi alla
   prima vincita, usando i numeri del walk-forward. Premi ufficiali per 1 € (estratto 11,23, ambo 250, terno 4.500, quaterna 120.000,
   cinquina 6.000.000) al netto della ritenuta dell'8%; il rapporto mostra spesa, vincite, saldo e resa per ruota e la resa teorica
   del puro caso (ambo ~57%, terno ~35%). Si usano le quote attuali anche per gli anni passati.

Il rapporto indica anche la **spia attiva** (cicli ancora aperti a fine periodo, con i colpi rimasti).

| Tecnica | Quando scatta il ciclo |
|---|---|
| Verifica spia su tutte le ruote | la spia esce sulla ruota scelta, in qualsiasi posizione |
| Spia in posizione | la spia esce sulla ruota scelta proprio nella posizione 1-5 indicata |
| Spia multipla (fino a 5 spie) | escono **tutte insieme** nella stessa estrazione, oppure **almeno una** |

L'evidenziazione colora la spia in verde e i numeri da giocare in rosso. I settaggi (posizione, modalità multipla, numeri da giocare)
si salvano con *Salva settaggi*.

### Scanner di spie (tecnica 24)

Prova tutte le spie da 1 a 90 sulla ruota di origine (qualsiasi posizione) e le ordina per il **valore aggiunto della spia**: lo scarto
(z) del walk-forward rispetto alla *base senza spia*, cioè gli stessi numeri scelti dopo ogni estrazione senza guardare alcuna spia.
Così un effetto generale (numeri frequenti sulla ruota) non viene scambiato per merito della singola spia. Per ogni spia: cicli,
successo, resa nel bilancio di gioco, z nel secondo periodo, numeri da giocare e se il ciclo è ancora aperto. Il rapporto chiude con una
"lettura onesta": quante spie con z >= 2 si attenderebbero per puro caso. Si evidenzia in archivio la prima della classifica.
Servono almeno 23 cicli completi per valutare una spia. Su un archivio 1939-oggi richiede circa un secondo.

### Collaudo previsioni (tecnica 25)

Mette alla prova i metodi di previsione dell'app contro il caso, sul tuo archivio: "Previsione a 5 numeri", "Solo spia dell'ultima
estrazione", più frequenti (ultime 30 e storico), ritardatari e un metodo casuale di controllo. A ogni passo ogni metodo usa solo le
estrazioni precedenti; le finestre di verifica non si sovrappongono (una ogni N colpi) e si controllano tutte le 11 ruote.
Misure: copertura (quanti dei K numeri escono entro i colpi), successo (ambo/terno/... dei K numeri nella stessa estrazione) e resa
nel bilancio di gioco. Un metodo "supera il caso" solo se lo z della copertura è almeno 2,33 (soglia corretta per i 5 metodi provati)
**e** il risultato si conferma in entrambe le metà del periodo di test; altrimenti è "non confermato" (probabile fortuna).
In fondo i numeri di oggi del metodo migliore, con l'etichetta onesta se non supera il caso. Parametri: anni (storia usata e ultimo
anno valutato), anno di inizio del test (0 = ultimi 10 anni), colpi, numeri e sorte. Da una decina di secondi a qualche minuto.

Ogni tecnica ha il pulsante **Evidenzia nell'archivio**: la spia diventa **verde**, i numeri del risultato **rossi** (nei colpi
successivi alla spia, oppure nella stessa estrazione per i metodi senza spia). Le previsioni attive vengono cercate su tutte le 11 ruote:
quando attivi l'evidenziazione, il filtro passa automaticamente a **Tutte le ruote** per non nascondere le ricomparse sulle altre ruote.
Anche le estrazioni disponibili dei cicli ancora aperti in fondo all'archivio vengono colorate; i cicli incompleti non entrano però
nei conteggi statistici dei cicli completi. "Solo date evidenziate" mostra solo le estrazioni coinvolte; "Togli evidenziazione" ripristina l'archivio.

### Verifica con il main.py
I calcoli Java sono in `SantoSpiaCore` (cartella `analysis`, condivisa con l'app Android) e sono stati confrontati con le
funzioni originali del main.py sullo stesso archivio, su 40 configurazioni casuali (spia, ruota, periodo, colpi) e con
l'archivio in ordine casuale: 548 controlli, tutti identici.

Differenze note:
- **Difetto del main.py**: in `genera_combinazioni_previsione_lotto` e `genera_terni_magici_lotto` le co-presenze sono contate con
  chiavi ordinate numericamente ma cercate con la combinazione in ordine di punteggio: la ricerca fallisce nella maggior parte dei
  casi. In Java la chiave è sempre ordinata. Correzione per il main.py: sostituire `combo_freq.get(comb, 0)`,
  `combo_cicli.get(comb, 0)`, `prima.get(comb, 0)`, `seconda.get(comb, 0)` e `co_counter.get(comb, 0)` con la stessa chiamata
  su `tuple(sorted(comb))`.
- Somma Ruote con ruota esito TUTTE: nei pareggi il main.py segue l'ordine (arbitrario) di un `set` Python, Java usa il numero crescente.
- I backtest con campionamento casuale del main.py (seed fisso) non sono riprodotti; il test walk-forward della ricerca spia sì.


## Previsioni, Gemini e Smorfia (versione PC)

- **Tecniche 17 e 18** (Super previsione light, Previsione con algoritmi IA): stessi numeri e stessi testi del main.py
  (`PrevisioniIa`; confronto su 40 configurazioni reali: 80 rapporti identici, compresi i messaggi di errore).
- **Smorfia** (pulsante 💭): prontuario V45.1 del main.py (alias, parole intere, accenti ignorati, nessuna statistica);
  confrontato con il Python su 16 sogni: risultati identici. "Crea giocata" porta i numeri nelle giocate.
- **Gemini** (pulsante 🤖): stessa logica del main.py (elenco modelli "flash" disponibili, fino a 5 tentativi con modelli di riserva,
  riepilogo locale se Gemini non risponde). Provato con un server simulato: chiave non valida, modello ritirato, quota, risposta vuota,
  server spento. La chiave si inserisce da "🔑 Chiave API" (file `gemini_key.txt` nella cartella dati, permessi riservati) oppure con
  la variabile `GEMINI_API_KEY`; non viene mai scritta nei rapporti. Il contatto reale con i server Google non è stato provato.

### Difetti trovati nel main.py
- `interpreta_sogno_smorfia` è definita due volte (riga 165 e riga 10415): resta attiva l'ultima, che non accetta `massimo`, quindi la
  schermata "SMORFIA - SPIA SOGNO" (riga 3806) va in errore (`unexpected keyword argument 'massimo'`). Correzione: rinominare la
  seconda in `interpreta_sogno_smorfia_ia` e aggiornare le chiamate alle righe 10505 e 10535.
- La seconda versione (quella "IA") non è il prontuario ufficiale: usa ricerca per sottostringa ("sole" trova "solenne") e, se non trova
  nulla, inventa numeri da una somma dei caratteri del testo.
- "Neural ensemble / LSTM" nella previsione IA indica una formula (frequenza, ritardo, lift, chi-quadrato), non una rete neurale;
  "Affidabilità IA" è un indice relativo, non una probabilità.

## Modulo SuperEnalotto (Linux)

Il pulsante **🎱 SuperEnalotto** apre un modulo separato dall'archivio Lotto. Supporta CSV con intestazioni `DATA`/`DATE`, `N1`…`N6` e colonne facoltative `CONCORSO`, `JOLLY`, `SUPERSTAR`.

- **Importa CSV** sostituisce l'archivio SuperEnalotto attivo, deduplica usando la data dell’estrazione come chiave primaria e conserva un backup prima della sostituzione. Non accoda il file nuovo al precedente.
- **Aggiorna online** prova le fonti CSV configurate e unisce le estrazioni per chiave senza doppioni.
- **Reset archivio** richiede conferma e crea una copia di sicurezza; l'archivio Lotto non viene toccato.
- **Tecniche**: analisi post-spia con frequenza/cicli/posizione (la posizione è la prima uscita del numero in ciascun ciclo); motore statistico con frequenza storica, recenza, presenza nei cicli, co-presenza della spia e ritardo; ciclometria su distanza circolare con punteggio normalizzato 0–100 (frequenza 45%, anello 30%, stabilità 15%, lift 10%) e schede di coppie/terni/quartine/sestine; sestine candidate TOP 15 con bonus di equilibrio pari/dispari, distribuzione per decine, fascia di somma e coppie storiche; resoconto unificato che incrocia tutti e tre i motori; backtest walk-forward, confronto appaiato tra post-spia, modello statistico e sestina casuale, e nuovo **Backtest ciclometria vs casuale** sui successivi colpi, con intervallo bootstrap e test di randomizzazione; **Verifica sadica + holdout**, che separa il 70% degli eventi per valutazione iniziale e il 30% finale per controllo indipendente.
- **Evidenziazione recente**: i colpi disponibili di un ciclo aperto vengono colorati anche quando l'archivio termina prima che il ciclo sia completo; restano esclusi dai punteggi e dal conteggio degli eventi completi.
- **Archivio/deduplicazione**: l'aggiornamento unisce le estrazioni usando la data come chiave primaria, evitando che eventuali numeri di concorso ripetuti sovrascrivano date diverse.

L'archivio viene salvato in `~/.local/share/lottoinjava/superenalotto_archive.csv` (oppure sotto `$XDG_DATA_HOME/lottoinjava/`). I backup sono in `backups/`. I risultati sono statistiche descrittive, non garanzie di vincita né modifiche alle probabilità matematiche del gioco.

#### Visualizzazione archivio SuperEnalotto
Il modulo SuperEnalotto mostra ora lo storico come schede con data/concorso e le sei palline, con ricerca per data, concorso o numero. Dopo un'analisi post-spia/statistica/ciclometrica/unificata o un backtest, la spia viene evidenziata in verde e i numeri candidati presenti nei colpi successivi vengono evidenziati in rosso, con la stessa convenzione cromatica dell'archivio Lotto. La legenda mostra esplicitamente i due colori; la casella di evidenziazione consente di attivarli/disattivarli. La spia è verde, mentre i numeri selezionati che compaiono realmente nei colpi successivi sono rossi. Anche i colpi disponibili di un ciclo aperto restano verificabili visivamente.


#### Revisione statistica SuperEnalotto (2026-10)
Nel confronto con `main.py` sono state riallineate la normalizzazione min-max, il calcolo del ritardo dall'ultima uscita, la prima posizione per numero in ciascun ciclo post-spia e la funzione di punteggio delle sestine candidate. Il confronto walk-forward usa gli stessi eventi validi per tutti i modelli, evitando disallineamenti delle serie appaiate. Nel `main.py` originale, invece, i pesi descritti nel commento come adattivi in `prepara_previsione_superenalotto` sono ancora fissi (`0.25/0.25/0.25/0.15/0.10`); il commento è più ambizioso dell'implementazione. La ciclometria Java usa ora i pesi espliciti del metodo Python e riporta il lift degli anelli. Nessuna di queste statistiche modifica le probabilità matematiche del gioco.

### Guida alle tecniche SuperEnalotto

Nel modulo desktop è disponibile il pulsante **Guida tecniche (web)**, che apre la guida HTML nel browser. I file sono inclusi anche nel progetto:

- `desktop/docs/guida_tecniche_superenalotto.html` — guida consultabile in un browser;
- `desktop/docs/guida_tecniche_superenalotto.pdf` — versione stampabile;
- `desktop/src/main/resources/docs/guida_tecniche_superenalotto.html` — risorsa caricata dal pulsante nell'app.

La guida descrive analisi post-spia, previsione statistica, ciclometria, resoconto unificato, backtest walk-forward, confronto modelli, backtest ciclometrico vs casuale, holdout e colorazione dell'archivio.

- Guida operativa completa Lotto + metodi sommativi + SuperEnalotto: `docs/guida_completa_lottoinjava.html` e `docs/guida_completa_lottoinjava.pdf`; si apre dalla home con il pulsante `Guida completa`.

## Grafici numeri caldi e freddi

- Lotto: il pulsante **Caldi/Freddi Lotto** apre grafici separati per i 10 numeri più e meno frequenti, con selezione della ruota o aggregazione di tutte le ruote.
- SuperEnalotto: il pulsante **Numeri caldi/freddi** mostra gli stessi grafici usando le sei palline principali dell'archivio SuperEnalotto.
- In entrambi i casi si può scegliere la finestra delle ultime 50, 100, 300 o 500 estrazioni oppure tutto lo storico disponibile.
- Le frequenze sono descrittive dello storico e non indicano probabilità maggiori per le estrazioni future.

## Quadro estrazionale
Pulsante **🧮 Quadro estrazionale**: scheda *Quadro ruote × date* (ultime estrazioni con i colori dell'archivio: spia verde, risultato rosso, più usciti blu scuro) e scheda *Tabellone 1–90* (frequenza o ritardo, con i 5 numeri in testa evidenziati; clic su un numero per i dettagli).

## Temi
Pulsante **🎨 Tema** nella barra: Blu scuro, Chiaro, Rosso fuoco. La scelta viene ricordata in `settings.properties`.

## Novità v18
- **Tema Vampiro** (bordeaux scuro) come quarto tema; i temi valgono anche in Caldi/Freddi e Laboratorio tecniche.
- **Blu regolabile**: il menu *Blu: auto/1/2/3/4/5* nella barra sceglie quanti numeri colorare in blu scuro nell'archivio (e nel tabellone).
- **Esporta il quadro**: *Salva PNG* e *Salva CSV* nel Quadro estrazionale e nel Tabellone.
- **Settaggi salvati** nella finestra Tecniche: *Salva settaggi / Carica / Elimina* (file `tecniche_salvate.properties` nella cartella dati).

## Android
Menu ⋮ → **🧮 Quadro estrazionale** (quadro ultime 20 estrazioni, tabellone frequenza/ritardo) e numeri più usciti in blu scuro nell'archivio evidenziato.


## Aggiornamento online su più fonti (v19)
Il pulsante **🌐 Aggiorna online** interroga in parallelo sette fonti e confronta i risultati prima di salvare:

| Fonte | Pagina |
|---|---|
| Ufficiale (Brightstar ZIP) | stesso archivio dell'app Android, priorità massima |
| LottoCED | `lottoced.com/lotto/archivio-estrazioni-del-lotto/?anno=AAAA` |
| Xamig | pagine annuali con numero di concorso |
| Lottologia | ultime estrazioni |
| Lottologia TXT | `lottologia.com/lotto/archivio-estrazioni/?as=TXT&year=AAAA` |
| Numeri-Lotto | `numeri-lotto.com/estrazioni/lotto-archivio-AAAA/` |
| QuiFinanza (mese) | estrazioni del mese corrente |

**Conversione nel formato del progetto.** Ogni pagina passa da un convertitore che riconosce cinque impaginazioni:
tabelle a blocchi (una per estrazione), tabelle a righe (data + 55 numeri), tabelle con le ruote in colonna,
testo con nome ruota (`Bari: 12 – 34 – 56 – 78 – 90`) e righe TXT con sigle (`BA 12 34 56 78 90 CA …`).
Xamig e Lottologia hanno prima il loro lettore dedicato; se non trova nulla si prova il convertitore generico.
Il risultato è sempre il formato di `Extraction`: data `gg/mm/aaaa`, 11 ruote nell'ordine Bari…Nazionale, 5 numeri distinti 1-90 per ruota.
Una estrazione incompleta, con numeri ripetuti o con data futura viene scartata: non si inventa mai nulla.

**Verifica incrociata.** Per ogni data vince la versione con più fonti concordi (a parità, la fonte più affidabile).
Il rapporto mostra quali fonti hanno risposto, l'ultima data di ciascuna, le fonti in ritardo, le date confermate da una sola fonte e le discordanze.

**Se una fonte non viene riconosciuta.** Il rapporto ne spiega il motivo (pagina vuota o bloccata, numeri caricati da JavaScript,
struttura diversa) e salva la pagina scaricata in `~/.local/share/lottoinjava/diagnostica/NomeFonte.html`:
con quel file si può scrivere il convertitore esatto per quel sito.

**Numero di concorso.** Se in archivio c'è già il numero reale viene conservato; le fonti senza concorso usano `aaaammgg`.

**Aggiungere altri siti senza ricompilare.** Crea `~/.local/share/lottoinjava/fonti_extra.txt` con una riga per sito, `Nome|URL`
(usa `{anno}` se la pagina è annuale; le righe che iniziano con `#` sono ignorate):

    # Nome|URL
    MioSito|https://example.com/lotto/archivio-{anno}/

**Ricostruisci archivio**: dopo Xamig integra Lottologia, l'archivio ufficiale e LottoCED, con chiave la data (nessun doppione).

## 💰 Prontuario vincite
Pulsante **💰 Prontuario vincite** nella seconda barra. Tre schede:
- **Quote**: tabella ufficiale per 1 € su ruota singola, da 1 a 10 numeri giocati (ambata, estratto determinato, ambo, terno, quaterna, cinquina). Importi lordi.
- **Calcola**: scegli numeri giocati, ruote e puntata e vedi lordo, imposta e netto di una combinazione vincente. Su più ruote la vincita si divide per il numero di ruote (10 con "tutte").
- **Controlla giocata**: inserisci i tuoi numeri, le ruote e quanto hai puntato su ogni sorte, scegli una estrazione dell'archivio (di solito l'ultima) e ottieni numeri usciti, sorti realizzate e vincita stimata.

L'imposta è modificabile (aliquota % ed esenzione): le fonti consultate non concordano (8% su tutta la vincita oppure 8% solo oltre 500 €) e sono in corso modifiche, quindi il valore va verificato in ricevitoria.
L'estratto determinato non è nel controllo giocata perché richiede anche la posizione di uscita. Le quote derivano dalla tabella pubblicata e il calcolo è coperto da un test che le confronta tutte e 50.
