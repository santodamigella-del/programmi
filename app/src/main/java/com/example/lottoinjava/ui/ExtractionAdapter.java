package com.example.lottoinjava.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lottoinjava.R;
import com.example.lottoinjava.model.Extraction;
import com.example.lottoinjava.model.HighlightConfig;

import java.util.ArrayList;
import java.util.List;

public class ExtractionAdapter extends RecyclerView.Adapter<ExtractionAdapter.ViewHolder> {

    public interface OnExtractionClickListener {
        void onExtractionClick(Extraction extraction);
    }

    private final Context context;
    private List<Extraction> allExtractions = new ArrayList<>();
    private List<Extraction> filteredExtractions = new ArrayList<>();
    private String selectedRuotaFilter = null; // null = all
    private String searchQuery = "";
    private HighlightConfig highlightConfig = new HighlightConfig();
    private final OnExtractionClickListener listener;

    public ExtractionAdapter(Context context, OnExtractionClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setExtractions(List<Extraction> extractions) {
        this.allExtractions = new ArrayList<>(extractions);
        computeBlue();
        applyFilters();
    }

    public void setRuotaFilter(String ruota) {
        this.selectedRuotaFilter = ruota;
        applyFilters();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
        applyFilters();
    }

    public void setHighlightConfig(HighlightConfig config) {
        this.highlightConfig = config != null ? config : new HighlightConfig();
        computeBlue();
        notifyDataSetChanged();
    }

    /**
     * Tra i numeri associati della tecnica, i più usciti nell'archivio diventano BLU SCURO:
     * con più di 5 numeri se ne colorano 5, con 5 o meno circa la metà (minimo 1).
     */
    private void computeBlue() {
        HighlightConfig h = highlightConfig;
        h.setBlueNumbers(new ArrayList<>());
        if (!h.isActive()) return;
        List<Integer> pool = new ArrayList<>();
        for (int x : h.getAssociatedNumbers()) if (!pool.contains(x)) pool.add(x);
        if (pool.size() < 2) return;
        int quanti = pool.size() > 5 ? 5 : Math.max(1, pool.size() / 2);
        final int[] freq = new int[100];
        String tw = h.getTargetWheel();
        boolean tutte = tw == null || tw.isEmpty() || "TUTTE".equalsIgnoreCase(tw);
        for (Extraction e : allExtractions) {
            List<String> ruote = tutte ? Extraction.ALL_RUOTE : java.util.Collections.singletonList(tw);
            for (String w : ruote) {
                List<Integer> n = e.getNumbersForRuota(w);
                if (n == null) continue;
                for (int x : n) if (x >= 1 && x <= 90) freq[x]++;
            }
        }
        java.util.Collections.sort(pool, (a, b) -> freq[b] != freq[a] ? Integer.compare(freq[b], freq[a]) : Integer.compare(a, b));
        List<Integer> blu = new ArrayList<>();
        for (int x : pool) {
            if (blu.size() >= quanti || freq[x] == 0) break;
            blu.add(x);
        }
        h.setBlueNumbers(blu);
    }

    public HighlightConfig getHighlightConfig() {
        return highlightConfig;
    }

    private void applyFilters() {
        filteredExtractions.clear();
        for (Extraction ext : allExtractions) {
            boolean matchesSearch = ext.matchesSearch(searchQuery);
            boolean matchesRuota = true;

            if (selectedRuotaFilter != null && !selectedRuotaFilter.isEmpty()) {
                List<Integer> nums = ext.getNumbersForRuota(selectedRuotaFilter);
                matchesRuota = nums != null && !nums.isEmpty();
            }

            if (matchesSearch && matchesRuota) {
                filteredExtractions.add(ext);
            }
        }
        notifyDataSetChanged();
    }

    public int getFilteredCount() {
        return filteredExtractions.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_extraction_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Extraction extraction = filteredExtractions.get(position);
        holder.bind(extraction, selectedRuotaFilter, highlightConfig, listener);
    }

    @Override
    public int getItemCount() {
        return filteredExtractions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvConcorsoBadge;
        private final TextView tvDate;
        private final LinearLayout containerWheelsPreview;
        private final TextView tvExpandPrompt;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConcorsoBadge = itemView.findViewById(R.id.tvConcorsoBadge);
            tvDate = itemView.findViewById(R.id.tvDate);
            containerWheelsPreview = itemView.findViewById(R.id.containerWheelsPreview);
            tvExpandPrompt = itemView.findViewById(R.id.tvExpandPrompt);
        }

        public void bind(Extraction extraction, String filterRuota, HighlightConfig highlight, OnExtractionClickListener listener) {
            tvConcorsoBadge.setText(extraction.getShortDate().toUpperCase(java.util.Locale.ITALY));
            tvDate.setVisibility(View.GONE);

            containerWheelsPreview.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());

            List<String> ruoteToShow;
            if (filterRuota != null && !filterRuota.isEmpty()) {
                ruoteToShow = new ArrayList<>();
                ruoteToShow.add(filterRuota);
            } else {
                ruoteToShow = new ArrayList<>();
                ruoteToShow.add("Bari");
                ruoteToShow.add("Milano");
                ruoteToShow.add("Roma");
                ruoteToShow.add("Nazionale");
            }

            for (String ruota : ruoteToShow) {
                List<Integer> nums = extraction.getNumbersForRuota(ruota);
                if (nums != null && nums.size() >= 5) {
                    View rowView = inflater.inflate(R.layout.item_wheel_row, containerWheelsPreview, false);

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

                        // Apply color highlighting for Spy Search & Hits
                        if (highlight != null && highlight.isActive()) {
                            if (highlight.isSpyNumber(num)) {
                                ballView.setBackgroundResource(R.drawable.bg_lotto_ball_spy);
                                ballView.setTextColor(0xFFFFFFFF);
                            } else if (highlight.isBlueNumber(num)) {
                                ballView.setBackgroundResource(R.drawable.bg_lotto_ball_blue);
                                ballView.setTextColor(0xFFFFFFFF);
                            } else if (highlight.isAssociatedNumber(num)) {
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

                    containerWheelsPreview.addView(rowView);
                }
            }

            if (filterRuota != null && !filterRuota.isEmpty()) {
                tvExpandPrompt.setText("Tocca per vedere tutte le ruote del " + extraction.getShortDate());
            } else {
                tvExpandPrompt.setText("Tocca per vedere tutte le 11 Ruote");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExtractionClick(extraction);
                }
            });
        }
    }
}
