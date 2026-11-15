# Archivio Estrazioni del Lotto

Applicazione desktop per Linux per consultare l'archivio delle estrazioni del Lotto e del SuperEnalotto e usare strumenti di analisi statistica.

## Schermata

Aggiungi qui uno screenshot dell'applicazione, per esempio in `desktop/docs/screenshot-archivio-lotto.png`, poi inserisci nel README:

```markdown
![Archivio Estrazioni del Lotto](desktop/docs/screenshot-archivio-lotto.png)
```

## Linux: scaricare e avviare

È disponibile un'AppImage per Linux x86_64 nella cartella [`desktop`](desktop/). Scarica `LottoInJava-1.0-x86_64.AppImage`, poi da terminale, nella cartella in cui l'hai scaricata, esegui:

```bash
chmod +x LottoInJava-1.0-x86_64.AppImage
./LottoInJava-1.0-x86_64.AppImage
```

Se l'avvio tramite FUSE non è supportato, prova:

```bash
./LottoInJava-1.0-x86_64.AppImage --appimage-extract-and-run
```

## Creare l'AppImage

Il progetto include lo script [`desktop/build-appimage.sh`](desktop/build-appimage.sh). Su EndeavourOS/Arch, installa i requisiti e avvia lo script dalla cartella del repository:

```bash
sudo pacman -S jdk21-openjdk maven curl fuse2 gtk3
cd desktop
./build-appimage.sh
```

Lo script compila l'applicazione e crea `LottoInJava-1.0-x86_64.AppImage` nella cartella `desktop`. Serve un JDK completo, non solo il JRE. In alternativa, se lo script non è eseguibile, avvialo con `bash build-appimage.sh`.

## Informazioni

Per dettagli sull'app desktop, i dati e le funzionalità consulta il [README della versione desktop](desktop/README.md).

Le analisi e le statistiche descrivono i dati storici e non prevedono né garantiscono vincite.
