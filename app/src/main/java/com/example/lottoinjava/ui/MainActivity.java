package com.example.lottoinjava.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.lottoinjava.R;
import com.example.lottoinjava.analysis.AprioriPatternEngine;
import com.example.lottoinjava.analysis.ConvergenzeEngine;
import com.example.lottoinjava.analysis.CostruttoreLotto;
import com.example.lottoinjava.analysis.FibonacciLottoEngine;
import com.example.lottoinjava.analysis.GeminiLottoAssistant;
import com.example.lottoinjava.analysis.IsotopiaEngine;
import com.example.lottoinjava.analysis.MagicTerniEngine;
import com.example.lottoinjava.analysis.MetodoCappuccinoEngine;
import com.example.lottoinjava.analysis.MetodoDartagnanEngine;
import com.example.lottoinjava.analysis.MultistaticoEngine;
import com.example.lottoinjava.analysis.PredictionEngine;
import com.example.lottoinjava.analysis.SmorfiaNapoletana;
import com.example.lottoinjava.analysis.SpyBacktestEngine;
import com.example.lottoinjava.analysis.GiocataChecker;
import com.example.lottoinjava.analysis.RitardatariEngine;
import com.example.lottoinjava.data.GiocateStore;
import com.example.lottoinjava.model.Giocata;
import com.example.lottoinjava.data.LottoCsvImporter;
import com.example.lottoinjava.data.LottoLocalCache;
import com.example.lottoinjava.data.LottoNetworkManager;
import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.HighlightConfig;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvTotalCount;
    private TextView tvNetworkStatus;
    private TextView tvLastSync;
    private MaterialButton btnOpen11WheelViewer;
    private MaterialButton btnGeneratePrediction;
    private MaterialButton btnUpdateInternet;
    private MaterialButton btnMyBets;
    private MaterialButton btnRitardatari;
    private EditText etSearch;
    private ImageView btnClearSearch;
    private ChipGroup chipGroupRuote;
    private MaterialCardView cardHighlightBanner;
    private TextView tvHighlightBannerText;
    private MaterialButton btnClearHighlight;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerViewExtractions;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutLoading;

    private ExtractionAdapter adapter;
    private List<Extraction> currentExtractions;
    private String currentSelectedWheel = "Bari";

    // CSV File Picker Launcher
    private final ActivityResultLauncher<String> csvPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCsvFileSelected);

    public interface TechniqueParamCallback {
        void onParamsConfirmed(int spyNumber, String wheel, int startYear, int endYear, int colpi);
    }

    public static String formatDataEstrazione(Extraction e) {
        return e.getLongDate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupRuotaChips();
        setupListeners();

        // Apply status bar padding to top AppBarLayout to fix overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBarLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        // Load cached extractions first
        loadCachedData();

        // Check network status and auto update
        updateNetworkStatusUI();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        tvLastSync = findViewById(R.id.tvLastSync);
        btnOpen11WheelViewer = findViewById(R.id.btnOpen11WheelViewer);
        btnGeneratePrediction = findViewById(R.id.btnGeneratePrediction);
        btnUpdateInternet = findViewById(R.id.btnUpdateInternet);
        btnMyBets = findViewById(R.id.btnMyBets);
        btnRitardatari = findViewById(R.id.btnRitardatari);
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        chipGroupRuote = findViewById(R.id.chipGroupRuote);
        cardHighlightBanner = findViewById(R.id.cardHighlightBanner);
        tvHighlightBannerText = findViewById(R.id.tvHighlightBannerText);
        btnClearHighlight = findViewById(R.id.btnClearHighlight);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerViewExtractions = findViewById(R.id.recyclerViewExtractions);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutLoading = findViewById(R.id.layoutLoading);
    }

    private void setupToolbar() {
        toolbar.setTitle(R.string.archive_title);
        toolbar.setSubtitle("Archivio ufficiale Lotto • Sync diretto");
        toolbar.inflateMenu(R.menu.main_menu);

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_spy_search) {
                showSpySearchDialog();
                return true;
            } else if (id == R.id.action_import_csv) {
                openCsvPicker();
                return true;
            } else if (id == R.id.action_refresh) {
                performInternetUpdate();
                return true;
            } else if (id == R.id.action_my_bets) {
                showGiocateDialog();
                return true;
            } else if (id == R.id.action_ritardatari) {
                showRitardatariDialog();
                return true;
            } else if (id == R.id.action_quadro) {
                showQuadroDialog();
                return true;
            } else if (id == R.id.action_rebuild_archive) {
                confirmRebuildArchive();
                return true;
            } else if (id == R.id.action_clear_archive) {
                confirmClearArchive();
                return true;
            } else if (id == R.id.action_sources) {
                showSourcesDialog();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        recyclerViewExtractions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExtractionAdapter(this, this::showExtractionDetailDialog);
        recyclerViewExtractions.setAdapter(adapter);
    }

    private void setupRuotaChips() {
        chipGroupRuote.removeAllViews();

        Chip allChip = new Chip(this);
        allChip.setText(R.string.filter_all);
        allChip.setCheckable(true);
        allChip.setChecked(true);
        allChip.setChipBackgroundColorResource(R.color.card_background);
        allChip.setTextColor(getResources().getColor(R.color.accent_gold, getTheme()));
        allChip.setOnClickListener(v -> {
            adapter.setRuotaFilter(null);
            currentSelectedWheel = "Bari";
        });
        chipGroupRuote.addView(allChip);

        for (String ruota : Extraction.ALL_RUOTE) {
            Chip chip = new Chip(this);
            chip.setText(ruota);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> {
                adapter.setRuotaFilter(ruota);
                currentSelectedWheel = ruota;
                updateEmptyState();
            });
            chipGroupRuote.addView(chip);
        }
    }

    private void setupListeners() {
        if (btnOpen11WheelViewer != null) {
            btnOpen11WheelViewer.setOnClickListener(v -> show11WheelArchiveViewer(0));
        }
        btnGeneratePrediction.setOnClickListener(v -> showTechniqueSelectorDialog());
        btnUpdateInternet.setOnClickListener(v -> performInternetUpdate());
        btnMyBets.setOnClickListener(v -> showGiocateDialog());
        btnRitardatari.setOnClickListener(v -> showRitardatariDialog());
        btnClearHighlight.setOnClickListener(v -> clearHighlight());

        swipeRefreshLayout.setOnRefreshListener(this::performInternetUpdate);
        swipeRefreshLayout.setColorSchemeResources(R.color.accent_gold, R.color.accent_blue);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.setSearchQuery(query);
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
    }

    private void clearHighlight() {
        adapter.setHighlightConfig(new HighlightConfig());
        cardHighlightBanner.setVisibility(View.GONE);
    }

    private void showTechniqueSelectorDialog() {
        String[] techniques = {
                "🔮 Previsione 5 Numeri (Spia + Ciclometria)",
                "🗡️ Metodo D'Artagnan (Moschettieri + Diametrale)",
                "📐 Analisi Isotopia Estrazionale (Stessa Posizione)",
                "🌀 Serie Sezione Aurea di Fibonacci",
                "☕ Metodo Cappuccino Ciclometrico",
                "✨ Terni Magici (Magic Score)",
                "🧮 Costruttore Somma + Fisso",
                "🌐 Costruttore Somma Ruote (Ruota A + Ruota B)",
                "📊 Costruttore Multistatico (Matrice Multispia)",
                "🌍 Costruttore / Verifica su TUTTE (v53p-coor)",
                "🛒 Market Basket & Apriori Pattern Mining",
                "💭 Smorfia Napoletana & Spia Sogno (con Gemini AI)",
                "⚡ Convergenze Tecniche Incrociate",
                "🕵️ Ricerca Numero Spia & Backtest (2001-2025)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Seleziona Tecnica o Costruttore Lotto")
                .setItems(techniques, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showTechniqueParamDialog("🔮 Previsione 5 Numeri", (spyNum, wheel, startYear, endYear, colpi) ->
                                    showPredictionDialog(wheel));
                            break;
                        case 1:
                            showTechniqueParamDialog("🗡️ Metodo D'Artagnan", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runDartagnanWithParams(spyNum, wheel, colpi));
                            break;
                        case 2:
                            showTechniqueParamDialog("📐 Analisi Isotopia Estrazionale", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runIsotopiaWithParams(wheel, colpi));
                            break;
                        case 3:
                            showTechniqueParamDialog("🌀 Serie Sezione Aurea di Fibonacci", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runFibonacciWithParams(wheel));
                            break;
                        case 4:
                            showTechniqueParamDialog("☕ Metodo Cappuccino Ciclometrico", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runCappuccinoWithParams(wheel));
                            break;
                        case 5:
                            showTechniqueParamDialog("✨ Terni Magici V45", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runTerniMagiciWithParams(spyNum, wheel, colpi));
                            break;
                        case 6:
                            showTechniqueParamDialog("🧮 Costruttore Somma + Fisso", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runCostruttoreSommaWithParams(wheel, colpi));
                            break;
                        case 7:
                            showTechniqueParamDialog("🌐 Costruttore Somma Ruote", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runSommaRuoteWithParams(wheel, colpi));
                            break;
                        case 8:
                            showTechniqueParamDialog("📊 Costruttore Multistatico", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runMultistaticoWithParams(spyNum, wheel, colpi));
                            break;
                        case 9:
                            showTechniqueParamDialog("🌍 Verifica su TUTTE (v53p-coor)", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runVerificaTutteWithParams(spyNum, wheel, startYear, endYear, colpi));
                            break;
                        case 10:
                            showTechniqueParamDialog("🛒 Apriori Pattern Mining", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runAprioriMiningWithParams(spyNum, wheel, colpi));
                            break;
                        case 11: showSmorfiaDialog(); break;
                        case 12:
                            showTechniqueParamDialog("⚡ Convergenze Tecniche Incrociate", (spyNum, wheel, startYear, endYear, colpi) ->
                                    runConvergenzeWithParams(spyNum, wheel, colpi));
                            break;
                        case 13: showSpySearchDialog(); break;
                    }
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void showTechniqueParamDialog(String techniqueTitle, TechniqueParamCallback callback) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_technique_params, null);

        TextView tvParamTitle = view.findViewById(R.id.tvParamTitle);
        EditText etParamSpyNumber = view.findViewById(R.id.etParamSpyNumber);
        EditText etParamStartYear = view.findViewById(R.id.etParamStartYear);
        EditText etParamEndYear = view.findViewById(R.id.etParamEndYear);
        EditText etParamColpi = view.findViewById(R.id.etParamColpi);
        ChipGroup chipGroupParamRuota = view.findViewById(R.id.chipGroupParamRuota);
        MaterialButton btnRunTechnique = view.findViewById(R.id.btnRunTechnique);
        MaterialButton btnCloseParam = view.findViewById(R.id.btnCloseParam);

        tvParamTitle.setText(techniqueTitle);

        final String[] wheelRef = {currentSelectedWheel != null ? currentSelectedWheel : "Bari"};

        chipGroupParamRuota.removeAllViews();
        for (String ruota : Extraction.ALL_RUOTE) {
            Chip chip = new Chip(this);
            chip.setText(ruota);
            chip.setCheckable(true);
            if (ruota.equalsIgnoreCase(wheelRef[0])) {
                chip.setChecked(true);
            }
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> wheelRef[0] = ruota);
            chipGroupParamRuota.addView(chip);
        }

        btnRunTechnique.setOnClickListener(v -> {
            int spyNum = parseNum(etParamSpyNumber.getText().toString(), 45);
            int startYear = parseNum(etParamStartYear.getText().toString(), 2001);
            int endYear = parseNum(etParamEndYear.getText().toString(), 2025);
            int colpi = parseNum(etParamColpi.getText().toString(), 6);

            dialog.dismiss();
            if (callback != null) {
                callback.onParamsConfirmed(spyNum, wheelRef[0], startYear, endYear, colpi);
            }
        });

        btnCloseParam.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }

    private void runDartagnanWithParams(int spyNum, String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        MetodoDartagnanEngine.DartagnanResult res = MetodoDartagnanEngine.calcolaMetodoDartagnan(currentExtractions, spyNum, wheel, colpi);

        new AlertDialog.Builder(this)
                .setTitle("🗡️ Metodo D'Artagnan")
                .setMessage(res.reportText)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, spyNum, wheel, res.quartettaDartagnan, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText(String.format(Locale.ITALIAN, "🎨 Evidenziazione Metodo D'Artagnan (Spia %d su %s) Attiva!", spyNum, wheel.toUpperCase()));
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runIsotopiaWithParams(String wheelA, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        IsotopiaEngine.IsotopiaResult res =
                IsotopiaEngine.analyzeIsotopia(currentExtractions, wheelA, "Milano", colpi);

        new AlertDialog.Builder(this)
                .setTitle("📐 Analisi Isotopia Estrazionale")
                .setMessage(res.reportText)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    if (!res.topConvergentNumbers.isEmpty()) {
                        HighlightConfig config = new HighlightConfig(true, res.topConvergentNumbers.get(0), wheelA, res.topConvergentNumbers, colpi);
                        adapter.setHighlightConfig(config);
                        cardHighlightBanner.setVisibility(View.VISIBLE);
                        tvHighlightBannerText.setText("🎨 Evidenziazione Isotopia Attiva!");
                        recyclerViewExtractions.scrollToPosition(0);
                    }
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runFibonacciWithParams(String wheel) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        FibonacciLottoEngine.FibonacciResult res =
                FibonacciLottoEngine.analyzeFibonacci(currentExtractions, wheel);

        new AlertDialog.Builder(this)
                .setTitle("🌀 Serie Sezione Aurea di Fibonacci")
                .setMessage(res.reportText)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, res.top5FibonacciNumbers.get(0), wheel, res.top5FibonacciNumbers, 6);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Numeri Fibonacci Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runCappuccinoWithParams(String wheel) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        MetodoCappuccinoEngine.CappuccinoResult res =
                MetodoCappuccinoEngine.calcolaMetodoCappuccino(currentExtractions, wheel);

        new AlertDialog.Builder(this)
                .setTitle("☕ Metodo Cappuccino Ciclometrico")
                .setMessage(res.reportText)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, res.closureNumber, wheel, res.cinquinaCappuccino, 6);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Metodo Cappuccino Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runTerniMagiciWithParams(int spyNum, String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        List<MagicTerniEngine.MagicTerno> terni = MagicTerniEngine.generateMagicTerni(currentExtractions, spyNum, wheel, colpi, 10);
        StringBuilder sb = new StringBuilder();
        sb.append("✨ TERNI MAGICI V45 - MAGIC SCORE (Spia ").append(spyNum).append(" su ").append(wheel.toUpperCase()).append(")\n\n");

        List<Integer> highlightNums = new ArrayList<>();
        for (int i = 0; i < terni.size(); i++) {
            MagicTerniEngine.MagicTerno t = terni.get(i);
            sb.append(String.format(Locale.ITALIAN, "%02d. %s\n", (i + 1), t.getFormatted()));
            if (i < 3) {
                highlightNums.addAll(t.numbers);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("✨ Terni Magici V45")
                .setMessage(sb.toString())
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, spyNum, wheel, highlightNums, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Terni Magici Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runCostruttoreSommaWithParams(String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        CostruttoreLotto.SommaFissoResult res = CostruttoreLotto.calcolaSommaFisso(currentExtractions, wheel, colpi);
        String text = res.getSummaryText(wheel.toUpperCase(), currentExtractions.get(0).getLongDate());

        new AlertDialog.Builder(this)
                .setTitle("🧮 Costruttore Somma + Fisso")
                .setMessage(text)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, res.baseNumber, wheel, res.cinquinaPrevisione, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Costruttore Somma Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runSommaRuoteWithParams(String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        CostruttoreLotto.SommaRuoteResult res = CostruttoreLotto.calcolaSommaRuote(currentExtractions, "Bari", 1, "Milano", 1, 1, wheel, colpi);
        StringBuilder sb = new StringBuilder();
        sb.append("🌐 COSTRUTTORE SOMMA RUOTE: ").append(res.ruleDescription).append("\n\n");
        sb.append("TOP NUMERI RISULTANTI POST-SOMMA:\n");

        for (int i = 0; i < res.topNumbers.size(); i++) {
            sb.append(String.format(Locale.ITALIAN, "%02d. Numero %02d\n", (i + 1), res.topNumbers.get(i)));
        }

        new AlertDialog.Builder(this)
                .setTitle("🌐 Costruttore Somma Ruote")
                .setMessage(sb.toString())
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, res.topNumbers.get(0), wheel, res.topNumbers, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Somma Ruote Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runMultistaticoWithParams(int spyNum, String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        List<Integer> spie = Arrays.asList(spyNum, (spyNum % 90) + 1);
        MultistaticoEngine.MultistaticoResult res = MultistaticoEngine.runMultistaticoAnalysis(currentExtractions, spie, wheel, colpi);

        new AlertDialog.Builder(this)
                .setTitle("📊 Costruttore Multistatico")
                .setMessage(res.reportText)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, spyNum, wheel, res.topConvergentNumbers, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Multistatico Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    /** Testo del test walk-forward (nessun dato futuro) confrontato con il puro caso. */
    private String describeWalkForward(SpyBacktestEngine.BacktestResult r) {
        if (r.totalSpyOccurrences == 0) {
            return "• Nessun ciclo post-spia completo nel periodo scelto.";
        }
        if (r.wfEvents == 0) {
            return String.format(Locale.ITALIAN,
                    "• Test walk-forward non eseguibile: servono almeno 6 cicli completi (disponibili %d).",
                    r.totalSpyOccurrences);
        }
        return String.format(Locale.ITALIAN,
                "• Test walk-forward su %d eventi: in media %.2f numeri su 5 centrati nei %d colpi\n" +
                        "• Atteso dal puro caso: %.2f (differenza %+.2f)",
                r.wfEvents, r.wfAvgHits, r.maxColpi, r.wfExpected, r.wfAvgHits - r.wfExpected);
    }

    private void runVerificaTutteWithParams(int spyNum, String wheel, int startYear, int endYear, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        SpyBacktestEngine.BacktestResult res = SpyBacktestEngine.runBacktest(currentExtractions, spyNum, wheel, startYear, endYear, colpi);

        String text = String.format(Locale.ITALIAN,
                "🌍 VERIFICA SPIA %d SU %s (%d - %d)\n" +
                        "====================================================\n" +
                        "• Spia presente %d volte; cicli post-spia completi: %d\n" +
                        "• Top abbinamenti per frequenza: %s\n\n%s",
                spyNum, wheel.toUpperCase(), startYear, endYear,
                res.spyPresences, res.totalSpyOccurrences,
                res.top5AssociatedNumbers.toString(),
                describeWalkForward(res)
        );

        new AlertDialog.Builder(this)
                .setTitle("🌍 Costruttore / Verifica su TUTTE (v53p-coor)")
                .setMessage(text)
                .setPositiveButton("Colora Archivio TUTTE", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, spyNum, "TUTTE", res.top5AssociatedNumbers, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione TUTTE le Ruote Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runAprioriMiningWithParams(int spyNum, String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        AprioriPatternEngine.AprioriResult res = AprioriPatternEngine.runAprioriMining(currentExtractions, spyNum, wheel, colpi);
        StringBuilder sb = new StringBuilder();
        sb.append("🛒 MARKET BASKET & APRIORI PATTERN MINING (Spia ").append(spyNum).append(" su ").append(wheel.toUpperCase()).append(")\n\n");
        sb.append("TOP NUMERI PATTERN:\n");

        List<Integer> topNums = new ArrayList<>();
        for (AprioriPatternEngine.AprioriItem item : res.topItems) {
            sb.append("• ").append(item.getFormatted()).append("\n");
            topNums.add(item.number);
        }

        new AlertDialog.Builder(this)
                .setTitle("🛒 Apriori Pattern Mining")
                .setMessage(sb.toString())
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, spyNum, wheel, topNums, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Apriori Mining Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void runConvergenzeWithParams(int spyNum, String wheel, int colpi) {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        ConvergenzeEngine.ConvergenzeResult res = ConvergenzeEngine.runUnifiedConvergence(currentExtractions, spyNum, wheel, colpi);

        List<Integer> convNums = new ArrayList<>();
        for (ConvergenzeEngine.ConvergenceItem item : res.topConvergentNumbers) {
            convNums.add(item.number);
        }

        new AlertDialog.Builder(this)
                .setTitle("⚡ Convergenze Tecniche Incrociate")
                .setMessage(res.fullReportText)
                .setPositiveButton("Colora Archivio", (dialog, which) -> {
                    HighlightConfig config = new HighlightConfig(true, spyNum, wheel, convNums, colpi);
                    adapter.setHighlightConfig(config);
                    cardHighlightBanner.setVisibility(View.VISIBLE);
                    tvHighlightBannerText.setText("🎨 Evidenziazione Convergenze Attiva!");
                    recyclerViewExtractions.scrollToPosition(0);
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void show11WheelArchiveViewer(int initialIndex) {
        if (currentExtractions == null || currentExtractions.isEmpty()) {
            Snackbar.make(recyclerViewExtractions, "Nessuna estrazione in archivio da visualizzare.", Snackbar.LENGTH_LONG).show();
            return;
        }

        final int[] currentIndex = {Math.max(0, Math.min(initialIndex, currentExtractions.size() - 1))};

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_archive_viewer, null);

        MaterialButton btnPrevExtraction = view.findViewById(R.id.btnPrevExtraction);
        MaterialButton btnNextExtraction = view.findViewById(R.id.btnNextExtraction);
        TextView tvViewerConcorsoBadge = view.findViewById(R.id.tvViewerConcorsoBadge);
        TextView tvViewerDateInfo = view.findViewById(R.id.tvViewerDateInfo);
        LinearLayout containerViewer11Wheels = view.findViewById(R.id.containerViewer11Wheels);
        MaterialButton btnCloseViewer = view.findViewById(R.id.btnCloseViewer);

        Runnable renderExtraction = new Runnable() {
            @Override
            public void run() {
                Extraction ext = currentExtractions.get(currentIndex[0]);
                tvViewerConcorsoBadge.setText(formatDataEstrazione(ext).toUpperCase(Locale.ITALY));
                tvViewerDateInfo.setText(String.format(Locale.ITALIAN, "Estrazione %d di %d", (currentIndex[0] + 1), currentExtractions.size()));

                btnPrevExtraction.setEnabled(currentIndex[0] < currentExtractions.size() - 1);
                btnNextExtraction.setEnabled(currentIndex[0] > 0);

                containerViewer11Wheels.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                HighlightConfig config = adapter.getHighlightConfig();

                for (String ruota : Extraction.ALL_RUOTE) {
                    List<Integer> nums = ext.getNumbersForRuota(ruota);
                    if (nums != null && nums.size() >= 5) {
                        View rowView = inflater.inflate(R.layout.item_wheel_row, containerViewer11Wheels, false);

                        TextView tvRuotaName = rowView.findViewById(R.id.tvRuotaName);
                        TextView[] ballViews = new TextView[]{
                                rowView.findViewById(R.id.tvNum1),
                                rowView.findViewById(R.id.tvNum2),
                                rowView.findViewById(R.id.tvNum3),
                                rowView.findViewById(R.id.tvNum4),
                                rowView.findViewById(R.id.tvNum5)
                        };

                        tvRuotaName.setText(ruota.toUpperCase());
                        if ("Nazionale".equalsIgnoreCase(ruota)) {
                            tvRuotaName.setBackgroundResource(R.drawable.bg_wheel_badge);
                            tvRuotaName.setTextColor(0xFFFBBF24);
                        }

                        for (int i = 0; i < 5; i++) {
                            int num = nums.get(i);
                            TextView ballView = ballViews[i];
                            ballView.setText(String.valueOf(num));

                            if (config != null && config.isActive()) {
                                boolean isTargetRuota = "TUTTE".equalsIgnoreCase(config.getTargetWheel()) || ruota.equalsIgnoreCase(config.getTargetWheel());
                                if (isTargetRuota && config.isSpyNumber(num)) {
                                    ballView.setBackgroundResource(R.drawable.bg_lotto_ball_spy);
                                    ballView.setTextColor(0xFFFFFFFF);
                                } else if (config.isBlueNumber(num)) {
                                    ballView.setBackgroundResource(R.drawable.bg_lotto_ball_blue);
                                    ballView.setTextColor(0xFFFFFFFF);
                                } else if (config.isAssociatedNumber(num)) {
                                    ballView.setBackgroundResource(R.drawable.bg_lotto_ball_hit);
                                    ballView.setTextColor(0xFFFFFFFF);
                                } else {
                                    ballView.setBackgroundResource(R.drawable.bg_lotto_ball);
                                    ballView.setTextColor(0xFF0F172A);
                                }
                            } else {
                                ballView.setBackgroundResource(R.drawable.bg_lotto_ball);
                                ballView.setTextColor(0xFF0F172A);
                            }
                        }

                        containerViewer11Wheels.addView(rowView);
                    }
                }
            }
        };

        btnPrevExtraction.setOnClickListener(v -> {
            if (currentIndex[0] < currentExtractions.size() - 1) {
                currentIndex[0]++;
                renderExtraction.run();
            }
        });

        btnNextExtraction.setOnClickListener(v -> {
            if (currentIndex[0] > 0) {
                currentIndex[0]--;
                renderExtraction.run();
            }
        });

        btnCloseViewer.setOnClickListener(v -> dialog.dismiss());

        renderExtraction.run();
        dialog.setContentView(view);
        dialog.show();
    }

    private void showSmorfiaDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_smorfia, null);

        EditText etDreamText = view.findViewById(R.id.etDreamText);
        MaterialButton btnParseDream = view.findViewById(R.id.btnParseDream);
        MaterialCardView cardDreamResult = view.findViewById(R.id.cardDreamResult);
        LinearLayout containerSmorfiaMatches = view.findViewById(R.id.containerSmorfiaMatches);
        MaterialButton btnCloseDream = view.findViewById(R.id.btnCloseDream);

        btnParseDream.setOnClickListener(v -> {
            String dream = etDreamText.getText().toString().trim();
            if (dream.isEmpty()) {
                etDreamText.setError("Inserisci il testo del sogno!");
                return;
            }

            List<SmorfiaNapoletana.SmorfiaMatch> matches = SmorfiaNapoletana.parseDreamText(dream);
            containerSmorfiaMatches.removeAllViews();

            List<Integer> matchedNums = new ArrayList<>();
            for (SmorfiaNapoletana.SmorfiaMatch match : matches) {
                matchedNums.add(match.number);
                TextView tvMatch = new TextView(this);
                tvMatch.setText(String.format(Locale.ITALIAN, "• Numero %02d: %s (parola: '%s')", match.number, match.meaning, match.matchedKeyword));
                tvMatch.setTextColor(0xFFFBBF24); // Gold
                tvMatch.setTextSize(13);
                tvMatch.setPadding(0, 4, 0, 4);
                containerSmorfiaMatches.addView(tvMatch);
            }

            cardDreamResult.setVisibility(View.VISIBLE);
        });

        // Add Gemini AI option button dynamically
        MaterialButton btnGemini = new MaterialButton(this);
        btnGemini.setText("✨ Interpretazione Avanzata con Gemini AI");
        btnGemini.setTextColor(0xFFE879F9);
        btnGemini.setOnClickListener(v -> {
            String dream = etDreamText.getText().toString().trim();
            if (dream.isEmpty()) {
                etDreamText.setError("Inserisci il testo del sogno prima!");
                return;
            }

            final EditText etApiKeyInput = new EditText(this);
            etApiKeyInput.setHint("Inserisci la tua Gemini API Key");
            etApiKeyInput.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);

            new AlertDialog.Builder(this)
                    .setTitle("✨ Gemini AI Assistente Lotto")
                    .setMessage("Inserisci la tua chiave API Google Gemini per generare un'interpretazione della Smorfia avanzata con IA:")
                    .setView(etApiKeyInput)
                    .setPositiveButton("Invia a Gemini", (d, which) -> {
                        String key = etApiKeyInput.getText().toString().trim();
                        if (!key.isEmpty()) {
                            Snackbar.make(recyclerViewExtractions, "Invio in corso a Gemini AI...", Snackbar.LENGTH_SHORT).show();
                            GeminiLottoAssistant.interpretDreamWithGemini(key, dream, new GeminiLottoAssistant.GeminiCallback() {
                                @Override
                                public void onSuccess(String aiResponse, List<Integer> suggestedNumbers) {
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("✨ Risposta Gemini AI Smorfia")
                                            .setMessage(aiResponse)
                                            .setPositiveButton("Colora Archivio AI", (d2, w2) -> {
                                                if (!suggestedNumbers.isEmpty()) {
                                                    HighlightConfig config = new HighlightConfig(true, suggestedNumbers.get(0), currentSelectedWheel, suggestedNumbers, 6);
                                                    adapter.setHighlightConfig(config);
                                                    cardHighlightBanner.setVisibility(View.VISIBLE);
                                                    tvHighlightBannerText.setText("🎨 Evidenziazione Numeri Gemini AI Attiva!");
                                                }
                                            })
                                            .setNegativeButton("Chiudi", null)
                                            .show();
                                }

                                @Override
                                public void onError(String errorMsg) {
                                    Snackbar.make(recyclerViewExtractions, errorMsg, Snackbar.LENGTH_LONG).show();
                                }
                            });
                        }
                    })
                    .setNegativeButton("Annulla", null)
                    .show();
        });

        ((LinearLayout) view).addView(btnGemini, 2);

        btnCloseDream.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }

    private void showSpySearchDialog() {
        if (currentExtractions == null || currentExtractions.isEmpty()) {
            Snackbar.make(recyclerViewExtractions, "Nessuna estrazione in archivio per la ricerca.", Snackbar.LENGTH_LONG).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_spy_search, null);

        EditText etSpyNumber = view.findViewById(R.id.etSpyNumber);
        EditText etStartYear = view.findViewById(R.id.etStartYear);
        EditText etEndYear = view.findViewById(R.id.etEndYear);
        EditText etMaxColpi = view.findViewById(R.id.etMaxColpi);
        ChipGroup chipGroupSpyRuota = view.findViewById(R.id.chipGroupSpyRuota);
        MaterialButton btnRunSpySearch = view.findViewById(R.id.btnRunSpySearch);
        MaterialCardView cardReport = view.findViewById(R.id.cardReport);
        TextView tvReportTitle = view.findViewById(R.id.tvReportTitle);
        TextView tvReportSummary = view.findViewById(R.id.tvReportSummary);
        TextView tvSpyTop1 = view.findViewById(R.id.tvSpyTop1);
        TextView tvSpyTop2 = view.findViewById(R.id.tvSpyTop2);
        TextView tvSpyTop3 = view.findViewById(R.id.tvSpyTop3);
        TextView tvSpyTop4 = view.findViewById(R.id.tvSpyTop4);
        TextView tvSpyTop5 = view.findViewById(R.id.tvSpyTop5);
        MaterialButton btnApplyHighlight = view.findViewById(R.id.btnApplyHighlight);
        MaterialButton btnCloseSpySearch = view.findViewById(R.id.btnCloseSpySearch);

        // Pre-fill defaults
        final String[] spyWheelRef = {currentSelectedWheel != null ? currentSelectedWheel : "Bari"};

        chipGroupSpyRuota.removeAllViews();
        for (String ruota : Extraction.ALL_RUOTE) {
            Chip chip = new Chip(this);
            chip.setText(ruota);
            chip.setCheckable(true);
            if (ruota.equalsIgnoreCase(spyWheelRef[0])) {
                chip.setChecked(true);
            }
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> spyWheelRef[0] = ruota);
            chipGroupSpyRuota.addView(chip);
        }

        final SpyBacktestEngine.BacktestResult[] lastBacktestRef = new SpyBacktestEngine.BacktestResult[1];

        btnRunSpySearch.setOnClickListener(v -> {
            String spyText = etSpyNumber.getText().toString().trim();
            if (spyText.isEmpty()) {
                etSpyNumber.setError("Inserisci un numero spia!");
                return;
            }

            int spyNum;
            try {
                spyNum = Integer.parseInt(spyText);
                if (spyNum < 1 || spyNum > 90) {
                    etSpyNumber.setError("Numero tra 1 e 90!");
                    return;
                }
            } catch (Exception e) {
                etSpyNumber.setError("Numero non valido!");
                return;
            }

            int startYear = parseNum(etStartYear.getText().toString(), 2001);
            int endYear = parseNum(etEndYear.getText().toString(), 2025);
            int colpi = parseNum(etMaxColpi.getText().toString(), 6);

            SpyBacktestEngine.BacktestResult result = SpyBacktestEngine.runBacktest(
                    currentExtractions,
                    spyNum,
                    spyWheelRef[0],
                    startYear,
                    endYear,
                    colpi
            );

            lastBacktestRef[0] = result;

            cardReport.setVisibility(View.VISIBLE);
            btnApplyHighlight.setVisibility(View.VISIBLE);

            tvReportTitle.setText(String.format(Locale.ITALIAN, "RESOCONTO ESITI SPIA %d (%d - %d)", spyNum, startYear, endYear));

            String summaryStr = String.format(Locale.ITALIAN,
                    "• Presenze Numero Spia %d su %s: %d volte (cicli completi: %d)\n%s",
                    spyNum,
                    result.selectedWheel.toUpperCase(),
                    result.spyPresences,
                    result.totalSpyOccurrences,
                    describeWalkForward(result)
            );

            tvReportSummary.setText(summaryStr);

            List<Integer> top5 = result.top5AssociatedNumbers;
            TextView[] topViews = new TextView[]{tvSpyTop1, tvSpyTop2, tvSpyTop3, tvSpyTop4, tvSpyTop5};
            for (int i = 0; i < 5; i++) {
                if (i < top5.size()) {
                    topViews[i].setText(String.valueOf(top5.get(i)));
                    topViews[i].setVisibility(View.VISIBLE);
                } else {
                    topViews[i].setVisibility(View.GONE);
                }
            }
        });

        btnApplyHighlight.setOnClickListener(v -> {
            if (lastBacktestRef[0] != null) {
                SpyBacktestEngine.BacktestResult res = lastBacktestRef[0];
                HighlightConfig config = new HighlightConfig(
                        true,
                        res.spyNumber,
                        res.selectedWheel,
                        res.top5AssociatedNumbers,
                        res.maxColpi
                );

                adapter.setHighlightConfig(config);

                cardHighlightBanner.setVisibility(View.VISIBLE);
                tvHighlightBannerText.setText(String.format(Locale.ITALIAN,
                        "🎨 Evidenziazione Attiva: Spia %d su %s (Abbinamenti: %s)",
                        res.spyNumber,
                        res.selectedWheel.toUpperCase(),
                        res.top5AssociatedNumbers.toString()
                ));

                dialog.dismiss();
                recyclerViewExtractions.scrollToPosition(0);
            }
        });

        btnCloseSpySearch.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }


    // =====================================================================
    //  LE MIE GIOCATE  &  RITARDATARI
    // =====================================================================

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Formatta una chiave aaaammgg come gg/MM/aaaa. */
    private static String formatDateKey(int key) {
        return String.format(Locale.ITALY, "%02d/%02d/%04d", key % 100, (key / 100) % 100, key / 10000);
    }

    /** Evita che l'elenco superi circa metà schermo: oltre quella altezza lo scroll interno entra in azione. */
    private void limitScrollHeight(ScrollView scroll, View content) {
        content.post(() -> {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
            if (content.getHeight() > max) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = max;
                scroll.setLayoutParams(lp);
            }
        });
    }

    private TextView createBall(int number, boolean hit) {
        TextView ball = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.setMargins(dp(2), 0, dp(2), 0);
        ball.setLayoutParams(lp);
        ball.setGravity(android.view.Gravity.CENTER);
        ball.setText(String.valueOf(number));
        ball.setTextSize(14);
        ball.setTypeface(null, Typeface.BOLD);
        ball.setBackgroundResource(hit ? R.drawable.bg_lotto_ball_hit : R.drawable.bg_lotto_ball);
        ball.setTextColor(hit ? 0xFFFFFFFF : 0xFF0F172A);
        return ball;
    }

    /** Controlla le giocate salvate: se ce ne sono di nuove vincenti avvisa una sola volta. */
    private void checkGiocateWins() {
        if (currentExtractions == null || currentExtractions.isEmpty()) return;
        List<Giocata> list = GiocateStore.load(this);
        StringBuilder sb = new StringBuilder();
        int wins = 0;
        for (Giocata g : list) {
            if (g.notified) continue;
            GiocataChecker.Result r = GiocataChecker.check(g, currentExtractions);
            if (r.status == GiocataChecker.Status.VINTA) {
                g.notified = true;
                wins++;
                sb.append("• ").append(g.getTipoName()).append(" ").append(g.numeri)
                        .append(" su ").append(r.ruotaVincente.toUpperCase())
                        .append(" al colpo ").append(r.colpoVincente)
                        .append(" (").append(r.estrazioneVincente.getShortDate()).append(")\n")
                        .append("   usciti: ").append(r.numeriUsciti).append("\n");
            }
        }
        if (wins == 0) return;
        GiocateStore.save(this, list);
        new AlertDialog.Builder(this)
                .setTitle(wins == 1 ? "🎉 Giocata vincente!" : "🎉 " + wins + " giocate vincenti!")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Vedi le mie giocate", (d, w) -> showGiocateDialog())
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showGiocateDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_giocate, null);

        TextView tvSubtitle = view.findViewById(R.id.tvGiocateSubtitle);
        ScrollView scroll = view.findViewById(R.id.scrollGiocate);
        LinearLayout container = view.findViewById(R.id.containerGiocate);
        MaterialButton btnNuova = view.findViewById(R.id.btnNuovaGiocata);
        MaterialButton btnClose = view.findViewById(R.id.btnCloseGiocate);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            container.removeAllViews();
            List<Giocata> list = GiocateStore.load(this);
            if (list.isEmpty()) {
                tvSubtitle.setText("Nessuna giocata salvata. Premi \"Nuova giocata\" per iniziare.");
            } else {
                tvSubtitle.setText(list.size() + (list.size() == 1 ? " giocata salvata" : " giocate salvate"));
            }
            for (Giocata g : list) {
                container.addView(buildGiocataCard(g, refresh[0]));
            }
            limitScrollHeight(scroll, container);
        };
        refresh[0].run();

        btnNuova.setOnClickListener(v -> showNuovaGiocataDialog(null, null, refresh[0]));
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }

    private View buildGiocataCard(Giocata g, Runnable onChanged) {
        GiocataChecker.Result r = GiocataChecker.check(g, currentExtractions);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_wheel_badge);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);

        // intestazione: RUOTA · Tipo · N colpi + cestino
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String ruotaLabel = Giocata.TUTTE.equalsIgnoreCase(g.ruota) ? "TUTTE LE RUOTE" : g.ruota.toUpperCase();
        title.setText(ruotaLabel + " · " + g.getTipoName() + " · " + g.colpi + (g.colpi == 1 ? " colpo" : " colpi"));
        title.setTextColor(0xFFFBBF24);
        title.setTextSize(13);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        TextView delete = new TextView(this);
        delete.setText("🗑");
        delete.setTextSize(18);
        delete.setPadding(dp(10), dp(4), dp(4), dp(4));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Eliminare la giocata?")
                .setMessage(g.getTipoName() + " " + g.numeri + " su " + ruotaLabel)
                .setPositiveButton("Elimina", (d, w) -> {
                    GiocateStore.remove(this, g.id);
                    onChanged.run();
                })
                .setNegativeButton("Annulla", null)
                .show());
        header.addView(delete);
        card.addView(header);

        // numeri giocati (in verde quelli usciti nella vincita)
        LinearLayout balls = new LinearLayout(this);
        balls.setOrientation(LinearLayout.HORIZONTAL);
        balls.setPadding(0, dp(6), 0, dp(6));
        for (int n : g.numeri) {
            boolean hit = r.status == GiocataChecker.Status.VINTA && r.numeriUsciti.contains(n);
            balls.addView(createBall(n, hit));
        }
        HorizontalScrollViewWrapper.wrap(this, card, balls);

        // stato
        TextView status = new TextView(this);
        status.setTextSize(12);
        switch (r.status) {
            case VINTA:
                status.setText("🎉 VINTA al colpo " + r.colpoVincente + " su " + r.ruotaVincente.toUpperCase()
                        + " — " + r.estrazioneVincente.getShortDate());
                status.setTextColor(0xFF34D399);
                status.setTypeface(null, Typeface.BOLD);
                break;
            case SCADUTA:
                status.setText("⌛ Scaduta: " + g.colpi + " colpi senza vincita (dal " + formatDateKey(g.fromKey) + ")");
                status.setTextColor(0xFFF87171);
                break;
            default:
                status.setText("⏳ In corso: " + r.colpiEffettuati + " colpi su " + g.colpi
                        + " (dal " + formatDateKey(g.fromKey) + ")");
                status.setTextColor(0xFF94A3B8);
                break;
        }
        card.addView(status);
        return card;
    }

    /** Piccolo helper: mette la riga di palline in uno scroll orizzontale (fino a 10 numeri su schermi stretti). */
    private static final class HorizontalScrollViewWrapper {
        static void wrap(Context ctx, LinearLayout parent, LinearLayout row) {
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(ctx);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.addView(row);
            parent.addView(hsv);
        }
    }

    private void showNuovaGiocataDialog(String preRuota, Integer preNumero, Runnable onSaved) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_nuova_giocata, null);

        TextView tvInfo = view.findViewById(R.id.tvGiocataInfo);
        ChipGroup chipRuota = view.findViewById(R.id.chipGroupGiocataRuota);
        ChipGroup chipTipo = view.findViewById(R.id.chipGroupGiocataTipo);
        EditText etNumeri = view.findViewById(R.id.etGiocataNumeri);
        EditText etColpi = view.findViewById(R.id.etGiocataColpi);
        MaterialButton btnSalva = view.findViewById(R.id.btnSalvaGiocata);
        MaterialButton btnAnnulla = view.findViewById(R.id.btnAnnullaGiocata);

        // la giocata parte dall'estrazione successiva all'ultima presente in archivio
        int latestKey = 0;
        if (currentExtractions != null) {
            for (Extraction e : currentExtractions) latestKey = Math.max(latestKey, e.getDateKey());
        }
        final int fromKey = latestKey;
        tvInfo.setText(fromKey > 0
                ? "Verrà controllata dalle estrazioni successive al " + formatDateKey(fromKey)
                : "Verrà controllata dalle prossime estrazioni");

        final String[] ruotaRef = {preRuota != null ? preRuota : Giocata.TUTTE};
        final int[] tipoRef = {preNumero != null ? 1 : 2};

        List<String> ruoteOpts = new ArrayList<>();
        ruoteOpts.add(Giocata.TUTTE);
        ruoteOpts.addAll(Extraction.ALL_RUOTE);
        for (String ruota : ruoteOpts) {
            Chip chip = new Chip(this);
            chip.setText(Giocata.TUTTE.equals(ruota) ? "Tutte" : ruota);
            chip.setCheckable(true);
            chip.setChecked(ruota.equalsIgnoreCase(ruotaRef[0]));
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> ruotaRef[0] = ruota);
            chipRuota.addView(chip);
        }

        for (int hits = 1; hits <= 5; hits++) {
            final int h = hits;
            Chip chip = new Chip(this);
            chip.setText(Giocata.TIPI[hits]);
            chip.setCheckable(true);
            chip.setChecked(hits == tipoRef[0]);
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> tipoRef[0] = h);
            chipTipo.addView(chip);
        }

        if (preNumero != null) etNumeri.setText(String.valueOf(preNumero));

        btnSalva.setOnClickListener(v -> {
            List<Integer> numeri = new ArrayList<>();
            for (String part : etNumeri.getText().toString().trim().split("[^0-9]+")) {
                if (part.isEmpty()) continue;
                int n;
                try {
                    n = Integer.parseInt(part);
                } catch (NumberFormatException ex) {
                    n = -1;
                }
                if (n < 1 || n > 90) {
                    etNumeri.setError("I numeri devono essere tra 1 e 90");
                    return;
                }
                if (!numeri.contains(n)) numeri.add(n);
            }
            if (numeri.isEmpty() || numeri.size() > 10) {
                etNumeri.setError("Inserisci da 1 a 10 numeri");
                return;
            }
            if (numeri.size() < tipoRef[0]) {
                etNumeri.setError("Per " + Giocata.TIPI[tipoRef[0]] + " servono almeno " + tipoRef[0] + " numeri");
                return;
            }
            int colpi = Math.max(1, Math.min(99, parseNum(etColpi.getText().toString(), 9)));

            int from = fromKey > 0 ? fromKey
                    : Integer.parseInt(new java.text.SimpleDateFormat("yyyyMMdd", Locale.ITALY).format(new java.util.Date()));
            Giocata g = new Giocata(System.currentTimeMillis(), ruotaRef[0], numeri, tipoRef[0], colpi, from, false);
            GiocateStore.add(this, g);
            dialog.dismiss();
            if (onSaved != null) onSaved.run();
        });
        btnAnnulla.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.setOnShowListener(d -> dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED));
        dialog.show();
    }

    private void showRitardatariDialog() {
        if (currentExtractions == null || currentExtractions.isEmpty()) {
            Snackbar.make(recyclerViewExtractions, "Archivio vuoto: aggiorna prima le estrazioni.", Snackbar.LENGTH_LONG).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_ritardatari, null);

        TextView tvSubtitle = view.findViewById(R.id.tvRitSubtitle);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupRitRuota);
        ScrollView scroll = view.findViewById(R.id.scrollRitardatari);
        LinearLayout container = view.findViewById(R.id.containerRitardatari);
        MaterialButton btnClose = view.findViewById(R.id.btnCloseRit);

        final String[] ruotaRef = {currentSelectedWheel != null ? currentSelectedWheel : "Bari"};
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            RitardatariEngine.Result res = RitardatariEngine.compute(currentExtractions, ruotaRef[0]);
            tvSubtitle.setText("Ruota di " + ruotaRef[0].toUpperCase() + " · " + res.drawsConsidered + " estrazioni analizzate · primi 15");
            container.removeAllViews();

            int shown = Math.min(15, res.entries.size());
            for (int i = 0; i < shown; i++) {
                RitardatariEngine.Entry e = res.entries.get(i);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(4), 0, dp(4));
                row.setClickable(true);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    showNuovaGiocataDialog(ruotaRef[0], e.number, null);
                });

                row.addView(createBall(e.number, false));

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                infoLp.setMargins(dp(10), 0, dp(8), 0);
                info.setLayoutParams(infoLp);

                boolean nearRecord = e.maxDelay > 0 && e.delay >= 0.8f * e.maxDelay;
                TextView t = new TextView(this);
                t.setText("Ritardo " + e.delay + (nearRecord ? " 🔥" : "") + "  ·  record " + e.maxDelay);
                t.setTextColor(nearRecord ? 0xFFFBBF24 : 0xFFF8FAFC);
                t.setTextSize(13);
                t.setTypeface(null, Typeface.BOLD);
                info.addView(t);

                ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(Math.max(1, e.maxDelay));
                bar.setProgress(e.delay);
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(nearRecord ? 0xFFF59E0B : 0xFF2563EB));
                bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF334155));
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
                barLp.setMargins(0, dp(4), 0, 0);
                bar.setLayoutParams(barLp);
                info.addView(bar);

                row.addView(info);

                TextView hitsTv = new TextView(this);
                hitsTv.setText(e.hits + "×");
                hitsTv.setTextColor(0xFF94A3B8);
                hitsTv.setTextSize(12);
                row.addView(hitsTv);

                container.addView(row);
            }
            scroll.scrollTo(0, 0);
            limitScrollHeight(scroll, container);
        };

        for (String ruota : Extraction.ALL_RUOTE) {
            Chip chip = new Chip(this);
            chip.setText(ruota);
            chip.setCheckable(true);
            chip.setChecked(ruota.equalsIgnoreCase(ruotaRef[0]));
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> {
                ruotaRef[0] = ruota;
                render[0].run();
            });
            chipGroup.addView(chip);
        }
        render[0].run();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
    }

    // ------------------------------------------------------------------ Quadro estrazionale

    private void showQuadroDialog() {
        if (currentExtractions == null || currentExtractions.isEmpty()) {
            Snackbar.make(recyclerViewExtractions, "Archivio vuoto: aggiorna prima le estrazioni.", Snackbar.LENGTH_LONG).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_quadro, null);

        TextView tvSubtitle = view.findViewById(R.id.tvQuadroSubtitle);
        ChipGroup chipModo = view.findViewById(R.id.chipGroupQuadroModo);
        ChipGroup chipRuote = view.findViewById(R.id.chipGroupQuadroRuota);
        ScrollView scroll = view.findViewById(R.id.scrollQuadro);
        LinearLayout container = view.findViewById(R.id.containerQuadro);
        MaterialButton btnClose = view.findViewById(R.id.btnCloseQuadro);

        final List<Extraction> cron = new ArrayList<>(currentExtractions);
        java.util.Collections.sort(cron, (a, b) -> Integer.compare(a.getDateKey(), b.getDateKey()));

        String iniziale = currentSelectedWheel;
        if (iniziale == null || !Extraction.ALL_RUOTE.contains(iniziale)) iniziale = "Bari";
        final String[] ruotaRef = {iniziale};
        final int[] modoRef = {0}; // 0 = quadro, 1 = frequenza, 2 = ritardo
        final java.util.Map<String, Chip> chipPerRuota = new java.util.LinkedHashMap<>();
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            container.removeAllViews();
            Chip tutte = chipPerRuota.get("Tutte");
            if (tutte != null) tutte.setEnabled(modoRef[0] != 0);
            if (modoRef[0] == 0 && "Tutte".equals(ruotaRef[0])) {
                ruotaRef[0] = "Bari";
                for (java.util.Map.Entry<String, Chip> en : chipPerRuota.entrySet()) {
                    en.getValue().setChecked(en.getKey().equals("Bari"));
                }
            }
            if (modoRef[0] == 0) renderQuadroRighe(container, tvSubtitle, cron, ruotaRef[0]);
            else renderTabellone(container, tvSubtitle, cron, ruotaRef[0], modoRef[0] == 2);
            scroll.scrollTo(0, 0);
            limitScrollHeight(scroll, container);
        };

        String[] nomiModo = {"Quadro", "Frequenza 1-90", "Ritardo 1-90"};
        for (int i = 0; i < nomiModo.length; i++) {
            final int modo = i;
            Chip chip = new Chip(this);
            chip.setText(nomiModo[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> {
                chip.setChecked(true);
                modoRef[0] = modo;
                render[0].run();
            });
            chipModo.addView(chip);
        }

        List<String> ruote = new ArrayList<>();
        ruote.add("Tutte");
        ruote.addAll(Extraction.ALL_RUOTE);
        for (String ruota : ruote) {
            Chip chip = new Chip(this);
            chip.setText(ruota);
            chip.setCheckable(true);
            chip.setChecked(ruota.equalsIgnoreCase(ruotaRef[0]));
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> {
                chip.setChecked(true);
                ruotaRef[0] = ruota;
                render[0].run();
            });
            chipPerRuota.put(ruota, chip);
            chipRuote.addView(chip);
        }
        render[0].run();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
    }

    /** Le ultime 20 estrazioni della ruota scelta; i 5 numeri più usciti in blu scuro. */
    private void renderQuadroRighe(LinearLayout container, TextView tvSubtitle, List<Extraction> cron, String ruota) {
        int da = Math.max(0, cron.size() - 20);
        List<Extraction> ultime = new ArrayList<>(cron.subList(da, cron.size()));
        java.util.Collections.reverse(ultime); // la più recente in alto

        final int[] freq = new int[100];
        for (Extraction e : ultime) {
            List<Integer> nums = e.getNumbersForRuota(ruota);
            if (nums == null) continue;
            for (int x : nums) if (x >= 1 && x <= 90) freq[x]++;
        }
        List<Integer> top = topNumeri(freq, 5);
        tvSubtitle.setText("Ruota di " + ruota.toUpperCase() + " · ultime " + ultime.size()
                + " estrazioni · in blu scuro i " + top.size() + " più usciti");

        for (Extraction e : ultime) {
            List<Integer> nums = e.getNumbersForRuota(ruota);
            if (nums == null || nums.size() < 5) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(3), 0, dp(3));

            TextView data = new TextView(this);
            data.setText(e.getShortDate().toUpperCase(Locale.ITALY));
            data.setTextColor(0xFFFBBF24);
            data.setTextSize(12);
            data.setTypeface(null, Typeface.BOLD);
            data.setLayoutParams(new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(data);

            for (int i = 0; i < 5; i++) {
                int x = nums.get(i);
                TextView ball = createBall(x, false);
                if (top.contains(x)) {
                    ball.setBackgroundResource(R.drawable.bg_lotto_ball_blue);
                    ball.setTextColor(0xFFFFFFFF);
                }
                row.addView(ball);
            }
            container.addView(row);
        }
    }

    /** Tabellone 1-90 sulle ultime 300 estrazioni: frequenza o ritardo; i 5 in testa evidenziati. */
    private void renderTabellone(LinearLayout container, TextView tvSubtitle, List<Extraction> cron, String ruota, boolean ritardoMode) {
        int da = Math.max(0, cron.size() - 300);
        List<Extraction> win = cron.subList(da, cron.size());
        List<String> cols = "Tutte".equals(ruota) ? Extraction.ALL_RUOTE : java.util.Collections.singletonList(ruota);

        final int[] freq = new int[100];
        final int[] ritardo = new int[100];
        java.util.Arrays.fill(ritardo, win.size());
        for (int i = 0; i < win.size(); i++) {
            boolean[] presente = new boolean[100];
            for (String w : cols) {
                List<Integer> nums = win.get(i).getNumbersForRuota(w);
                if (nums == null) continue;
                for (int x : nums) if (x >= 1 && x <= 90) { freq[x]++; presente[x] = true; }
            }
            for (int x = 1; x <= 90; x++) if (presente[x]) ritardo[x] = win.size() - 1 - i;
        }
        final int[] valore = ritardoMode ? ritardo : freq;
        int max = 1;
        for (int x = 1; x <= 90; x++) max = Math.max(max, valore[x]);
        List<Integer> top = topNumeri(valore, 5);
        tvSubtitle.setText((ritardoMode ? "Ritardo" : "Frequenza") + " · " + ("Tutte".equals(ruota) ? "tutte le ruote" : ruota.toUpperCase())
                + " · ultime " + win.size() + " estrazioni · "
                + (ritardoMode ? "in rosso i 5 più in ritardo" : "in blu scuro i 5 più usciti"));

        android.widget.TableLayout table = new android.widget.TableLayout(this);
        table.setStretchAllColumns(true);
        for (int r = 0; r < 9; r++) {
            android.widget.TableRow row = new android.widget.TableRow(this);
            for (int c = 0; c < 10; c++) {
                final int x = r * 10 + c + 1;
                TextView cell = new TextView(this);
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setText(String.format(Locale.ITALY, "%02d\n", x) + (ritardoMode ? String.valueOf(valore[x]) : valore[x] + "×"));
                cell.setTextSize(10);
                cell.setTypeface(null, Typeface.BOLD);
                cell.setTextColor(0xFFF8FAFC);
                android.widget.TableRow.LayoutParams lp = new android.widget.TableRow.LayoutParams(0, dp(40), 1f);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                cell.setLayoutParams(lp);
                if (top.contains(x)) {
                    cell.setBackgroundResource(ritardoMode ? R.drawable.bg_quadro_cell_red : R.drawable.bg_quadro_cell_blue);
                } else {
                    int alpha = 20 + Math.round(170f * valore[x] / max);
                    cell.setBackgroundColor(ritardoMode
                            ? android.graphics.Color.argb(alpha, 239, 68, 68)
                            : android.graphics.Color.argb(alpha, 251, 191, 36));
                }
                cell.setOnClickListener(v -> android.widget.Toast.makeText(this,
                        String.format(Locale.ITALY, "Numero %02d · uscite %d · ritardo %d estrazioni", x, freq[x], ritardo[x]),
                        android.widget.Toast.LENGTH_SHORT).show());
                row.addView(cell);
            }
            table.addView(row);
        }
        container.addView(table);
    }

    /** I primi n numeri per valore (a parità il più piccolo), escludendo chi ha valore 0. */
    private List<Integer> topNumeri(int[] valore, int n) {
        List<Integer> numeri = new ArrayList<>();
        for (int x = 1; x <= 90; x++) numeri.add(x);
        java.util.Collections.sort(numeri, (a, b) -> valore[b] != valore[a] ? Integer.compare(valore[b], valore[a]) : Integer.compare(a, b));
        List<Integer> top = new ArrayList<>();
        for (int x : numeri) {
            if (top.size() >= n || valore[x] <= 0) break;
            top.add(x);
        }
        return top;
    }

    private int parseNum(String str, int defaultVal) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private void openCsvPicker() {
        try {
            csvPickerLauncher.launch("*/*");
        } catch (Exception e) {
            Snackbar.make(recyclerViewExtractions, "Impossibile aprire il selettore file: " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCsvFileSelected(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            List<Extraction> importedList = LottoCsvImporter.parseCsvUri(this, uri);
            if (!importedList.isEmpty()) {
                List<Extraction> merged = LottoNetworkManager.mergeExtractions(currentExtractions, importedList);
                LottoLocalCache.saveExtractions(this, merged);

                currentExtractions = merged;
                adapter.setExtractions(merged);

                updateHeaderStats();
                updateEmptyState();

                Snackbar.make(recyclerViewExtractions, "Importazione completata: " + importedList.size() + " estrazioni caricate dal file CSV!", Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFF15803D)
                        .setTextColor(0xFFFFFFFF)
                        .show();
            } else {
                Snackbar.make(recyclerViewExtractions, "Formato CSV non riconosciuto o file vuoto. Verifica il file.", Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFFB91C1C)
                        .setTextColor(0xFFFFFFFF)
                        .show();
            }
        } catch (Exception e) {
            Snackbar.make(recyclerViewExtractions, "Errore durante il caricamento del file CSV: " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG)
                    .setBackgroundTint(0xFFB91C1C)
                    .setTextColor(0xFFFFFFFF)
                    .show();
        }
    }


    private void confirmClearArchive() {
        new AlertDialog.Builder(this)
                .setTitle("Cancellare l'archivio Lotto?")
                .setMessage("L'archivio verrà svuotato dopo aver creato una copia di sicurezza. Potrai poi usare 'Ricostruisci archivio Lotto'.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Crea backup e cancella", (dialog, which) -> {
                    try {
                        java.io.File backup = LottoLocalCache.clearArchive(this);
                        currentExtractions = new ArrayList<>();
                        adapter.setExtractions(currentExtractions);
                        updateHeaderStats();
                        updateEmptyState();
                        String backupText = backup == null ? "Nessun archivio precedente da salvare." : "Backup: " + backup.getName();
                        Snackbar.make(recyclerViewExtractions, "Archivio Lotto svuotato. " + backupText, Snackbar.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Snackbar.make(recyclerViewExtractions, "Cancellazione non eseguita: " + e.getLocalizedMessage(), Snackbar.LENGTH_LONG)
                                .setBackgroundTint(0xFFB91C1C).setTextColor(0xFFFFFFFF).show();
                    }
                }).show();
    }

    private void confirmRebuildArchive() {
        new AlertDialog.Builder(this)
                .setTitle("Ricostruire l'archivio Lotto?")
                .setMessage("Scarica nuovamente l'archivio ufficiale, controlla la data più recente e il numero di ruote, poi sostituisce l'archivio locale. Prima viene creato un backup.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Ricostruisci", (dialog, which) -> {
                    updateNetworkStatusUI();
                    layoutLoading.setVisibility(View.VISIBLE);
                    LottoNetworkManager.rebuildArchiveFromInternet(this, new LottoNetworkManager.NetworkCallback() {
                        @Override public void onSuccess(List<Extraction> extractions, String message) {
                            layoutLoading.setVisibility(View.GONE);
                            currentExtractions = extractions;
                            adapter.setExtractions(extractions);
                            updateHeaderStats();
                            updateEmptyState();
                            Snackbar.make(recyclerViewExtractions, message, Snackbar.LENGTH_LONG)
                                    .setBackgroundTint(0xFF15803D).setTextColor(0xFFFFFFFF).show();
                            checkGiocateWins();
                        }
                        @Override public void onError(String error) {
                            layoutLoading.setVisibility(View.GONE);
                            Snackbar.make(recyclerViewExtractions, error, Snackbar.LENGTH_LONG)
                                    .setBackgroundTint(0xFFB91C1C).setTextColor(0xFFFFFFFF).show();
                        }
                    });
                }).show();
    }

    private void showSourcesDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Fonti Archivio Lotto")
                .setMessage("\"Aggiorna da Internet\" scarica l'archivio storico ufficiale del Lotto (file ZIP dal 2001 a oggi, "
                        + "sito Lotto Italia) e unisce le estrazioni per data: nessun dato viene inventato.\n\n" +
                        "Puoi anche caricare un file CSV personalizzato tramite il menu 'Carica File CSV' in alto.")
                .setPositiveButton("Carica CSV", (dialog, which) -> openCsvPicker())
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void showPredictionDialog(String selectedWheel) {
        if (currentExtractions == null || currentExtractions.isEmpty()) {
            Snackbar.make(recyclerViewExtractions, "Nessuna estrazione in archivio per la previsione.", Snackbar.LENGTH_LONG).show();
            return;
        }

        String wheelToUse = (selectedWheel == null || selectedWheel.isEmpty()) ? "Bari" : selectedWheel;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_prediction, null);

        TextView tvPredictionSubtitle = view.findViewById(R.id.tvPredictionSubtitle);
        ChipGroup chipGroupPredictionRuota = view.findViewById(R.id.chipGroupPredictionRuota);
        TextView tvTargetWheelBadge = view.findViewById(R.id.tvTargetWheelBadge);
        TextView tvPred1 = view.findViewById(R.id.tvPred1);
        TextView tvPred2 = view.findViewById(R.id.tvPred2);
        TextView tvPred3 = view.findViewById(R.id.tvPred3);
        TextView tvPred4 = view.findViewById(R.id.tvPred4);
        TextView tvPred5 = view.findViewById(R.id.tvPred5);
        LinearLayout containerExplanations = view.findViewById(R.id.containerExplanations);
        MaterialButton btnSharePrediction = view.findViewById(R.id.btnSharePrediction);
        MaterialButton btnClosePrediction = view.findViewById(R.id.btnClosePrediction);

        chipGroupPredictionRuota.removeAllViews();
        for (String ruota : Extraction.ALL_RUOTE) {
            Chip chip = new Chip(this);
            chip.setText(ruota);
            chip.setCheckable(true);
            if (ruota.equalsIgnoreCase(wheelToUse)) {
                chip.setChecked(true);
            }
            chip.setChipBackgroundColorResource(R.color.card_background);
            chip.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            chip.setOnClickListener(v -> showPredictionDialog(ruota));
            chipGroupPredictionRuota.addView(chip);
        }

        PredictionEngine.PredictionResult result = PredictionEngine.generatePrediction(currentExtractions, wheelToUse);
        List<Integer> top5 = result.getTop5Numbers();

        tvPredictionSubtitle.setText("Concorso N° " + result.getLastConcorsoNumber() + " (" + result.getLastConcorsoDate() + ")");
        tvTargetWheelBadge.setText("CINQUINA CONSIGLIATA - " + wheelToUse.toUpperCase());

        if (top5.size() >= 5) {
            tvPred1.setText(String.valueOf(top5.get(0)));
            tvPred2.setText(String.valueOf(top5.get(1)));
            tvPred3.setText(String.valueOf(top5.get(2)));
            tvPred4.setText(String.valueOf(top5.get(3)));
            tvPred5.setText(String.valueOf(top5.get(4)));
        }

        containerExplanations.removeAllViews();
        for (int num : top5) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(0, 8, 0, 8);

            TextView tvHeader = new TextView(this);
            tvHeader.setText("★ NUMERO " + num + " (Ruota " + wheelToUse.toUpperCase() + ")");
            tvHeader.setTextColor(0xFFFBBF24); // Gold
            tvHeader.setTextSize(14);
            tvHeader.setTypeface(null, Typeface.BOLD);

            TextView tvDetails = new TextView(this);
            tvDetails.setText(result.getExplanationForNumber(num));
            tvDetails.setTextColor(0xFF94A3B8); // Slate secondary
            tvDetails.setTextSize(12);

            itemLayout.addView(tvHeader);
            itemLayout.addView(tvDetails);
            containerExplanations.addView(itemLayout);
        }

        btnSharePrediction.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String msg = "🔮 Previsione 5 Numeri per AMBO e TERNO su Ruota " + wheelToUse.toUpperCase() +
                    "\n\nNumeri Consigliati: " + top5.get(0) + " - " + top5.get(1) + " - " + top5.get(2) + " - " + top5.get(3) + " - " + top5.get(4) +
                    "\n\nAnalisi effettuata con Verifica Incrociata: Numeri Spia + Ciclometria + Frequenze.";
            shareIntent.putExtra(Intent.EXTRA_TEXT, msg);
            startActivity(Intent.createChooser(shareIntent, "Condividi Previsione Lotto"));
        });

        btnClosePrediction.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }

    private void loadCachedData() {
        currentExtractions = LottoLocalCache.loadExtractions(this);
        adapter.setExtractions(currentExtractions);

        updateHeaderStats();
        updateEmptyState();
        checkGiocateWins();
    }

    private void updateHeaderStats() {
        int count = currentExtractions != null ? currentExtractions.size() : 0;
        tvTotalCount.setText("Estrazioni in archivio: " + count);
        tvLastSync.setText("Ultimo aggiornamento: " + LottoLocalCache.getLastSyncFormatted(this));
    }

    private void updateNetworkStatusUI() {
        boolean online = LottoNetworkManager.isNetworkAvailable(this);
        if (online) {
            tvNetworkStatus.setText("• Online");
            tvNetworkStatus.setTextColor(0xFF34D399); // Green
        } else {
            tvNetworkStatus.setText("• Offline");
            tvNetworkStatus.setTextColor(0xFFF87171); // Red
        }
    }

    private void performInternetUpdate() {
        updateNetworkStatusUI();

        if (!LottoNetworkManager.isNetworkAvailable(this)) {
            swipeRefreshLayout.setRefreshing(false);
            Snackbar.make(recyclerViewExtractions, "Connessione Internet assente. Impossibile aggiornare.", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(0xFF991B1B)
                    .setTextColor(0xFFFFFFFF)
                    .show();
            return;
        }

        layoutLoading.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setRefreshing(true);

        LottoNetworkManager.updateArchiveFromInternet(this, new LottoNetworkManager.NetworkCallback() {
            @Override
            public void onSuccess(List<Extraction> extractions, String message) {
                layoutLoading.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                currentExtractions = extractions;
                adapter.setExtractions(extractions);

                updateHeaderStats();
                updateEmptyState();

                Snackbar.make(recyclerViewExtractions, message, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFF15803D)
                        .setTextColor(0xFFFFFFFF)
                        .show();
                checkGiocateWins();
            }

            @Override
            public void onError(String error) {
                layoutLoading.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                Snackbar.make(recyclerViewExtractions, error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFFB91C1C)
                        .setTextColor(0xFFFFFFFF)
                        .show();
            }
        });
    }

    private void updateEmptyState() {
        if (adapter.getFilteredCount() == 0) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerViewExtractions.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerViewExtractions.setVisibility(View.VISIBLE);
        }
    }

    private void showExtractionDetailDialog(Extraction extraction) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_extraction_detail, null);

        TextView tvDetailTitle = view.findViewById(R.id.tvDetailTitle);
        TextView tvDetailSubtitle = view.findViewById(R.id.tvDetailSubtitle);
        LinearLayout containerAllWheels = view.findViewById(R.id.containerAllWheels);
        MaterialButton btnShare = view.findViewById(R.id.btnShare);
        MaterialButton btnCloseDetail = view.findViewById(R.id.btnCloseDetail);

        tvDetailTitle.setText("Estrazione Completa");
        tvDetailSubtitle.setText(formatDataEstrazione(extraction));

        containerAllWheels.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (String ruota : Extraction.ALL_RUOTE) {
            List<Integer> nums = extraction.getNumbersForRuota(ruota);
            if (nums != null && nums.size() >= 5) {
                View rowView = inflater.inflate(R.layout.item_wheel_row, containerAllWheels, false);

                TextView tvRuotaName = rowView.findViewById(R.id.tvRuotaName);
                TextView tvNum1 = rowView.findViewById(R.id.tvNum1);
                TextView tvNum2 = rowView.findViewById(R.id.tvNum2);
                TextView tvNum3 = rowView.findViewById(R.id.tvNum3);
                TextView tvNum4 = rowView.findViewById(R.id.tvNum4);
                TextView tvNum5 = rowView.findViewById(R.id.tvNum5);

                tvRuotaName.setText(ruota.toUpperCase());
                if ("Nazionale".equalsIgnoreCase(ruota)) {
                    tvRuotaName.setBackgroundResource(R.drawable.bg_wheel_badge);
                    tvRuotaName.setTextColor(0xFFFBBF24);
                }

                tvNum1.setText(String.valueOf(nums.get(0)));
                tvNum2.setText(String.valueOf(nums.get(1)));
                tvNum3.setText(String.valueOf(nums.get(2)));
                tvNum4.setText(String.valueOf(nums.get(3)));
                tvNum5.setText(String.valueOf(nums.get(4)));

                containerAllWheels.addView(rowView);
            }
        }

        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            StringBuilder sb = new StringBuilder();
            sb.append("Estrazione del Lotto - ").append(formatDataEstrazione(extraction)).append("\n\n");
            for (String ruota : Extraction.ALL_RUOTE) {
                List<Integer> nums = extraction.getNumbersForRuota(ruota);
                if (nums != null && !nums.isEmpty()) {
                    sb.append(ruota.toUpperCase()).append(": ").append(nums.toString()).append("\n");
                }
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(shareIntent, "Condividi Estrazione"));
        });

        btnCloseDetail.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        dialog.show();
    }
}
