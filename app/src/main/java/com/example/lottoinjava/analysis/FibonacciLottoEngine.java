package com.example.lottoinjava.analysis;

import com.example.lottoinjava.model.Extraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FibonacciLottoEngine {

    public static final List<Integer> FIBONACCI_NUMBERS = Arrays.asList(1, 2, 3, 5, 8, 13, 21, 34, 55, 89);

    public static class FibonacciItem {
        public int number;
        public int frequency;
        public int currentDelay;
        public double score;

        public String getFormatted() {
            return String.format(Locale.ITALIAN, "Numero Fibonacci %02d | Freq: %d | Ritardo: %d concorsi | Score: %.1f",
                    number, frequency, currentDelay, score);
        }
    }

    public static class FibonacciResult {
        public String wheel;
        public List<FibonacciItem> fibonacciRanking = new ArrayList<>();
        public List<Integer> top5FibonacciNumbers = new ArrayList<>();
        public String reportText;
    }

    public static FibonacciResult analyzeFibonacci(List<Extraction> archive, String targetWheel) {
        FibonacciResult result = new FibonacciResult();
        result.wheel = (targetWheel == null || targetWheel.isEmpty()) ? "Bari" : targetWheel;

        if (archive == null || archive.isEmpty()) {
            result.reportText = "Archivio vuoto.";
            return result;
        }

        Set<Integer> fibSet = new HashSet<>(FIBONACCI_NUMBERS);
        Map<Integer, Integer> freqMap = new HashMap<>();
        Map<Integer, Integer> delayMap = new HashMap<>();

        for (int fib : FIBONACCI_NUMBERS) {
            freqMap.put(fib, 0);
            delayMap.put(fib, -1);
        }

        for (int idx = 0; idx < archive.size(); idx++) {
            Extraction ext = archive.get(idx); // Most recent is at idx 0
            List<Integer> nums = ext.getNumbersForRuota(result.wheel);

            if (nums != null) {
                for (int fib : FIBONACCI_NUMBERS) {
                    if (nums.contains(fib)) {
                        freqMap.put(fib, freqMap.getOrDefault(fib, 0) + 1);
                        if (delayMap.get(fib) == -1) {
                            delayMap.put(fib, idx); // Delay in concorsi
                        }
                    }
                }
            }
        }

        // Set max delay for never extracted numbers
        for (int fib : FIBONACCI_NUMBERS) {
            if (delayMap.get(fib) == -1) {
                delayMap.put(fib, archive.size());
            }
        }

        List<FibonacciItem> items = new ArrayList<>();
        for (int fib : FIBONACCI_NUMBERS) {
            FibonacciItem item = new FibonacciItem();
            item.number = fib;
            item.frequency = freqMap.getOrDefault(fib, 0);
            item.currentDelay = delayMap.getOrDefault(fib, 0);

            // Score balancing frequency and golden delay
            item.score = (item.frequency * 10.0) + (item.currentDelay * 1.5);
            items.add(item);
        }

        items.sort((a, b) -> Double.compare(b.score, a.score));
        result.fibonacciRanking = items;

        for (int i = 0; i < Math.min(5, items.size()); i++) {
            result.top5FibonacciNumbers.add(items.get(i).number);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🌀 ANALISI SERIE SEZIONE AUREA DI FIBONACCI\n");
        sb.append("====================================================\n");
        sb.append(String.format(Locale.ITALIAN, "Ruota: %s | Numeri Fibonacci: %s\n\n", result.wheel.toUpperCase(), FIBONACCI_NUMBERS.toString()));
        sb.append("CLASSIFICA ARMONICA FIBONACCI:\n");

        for (int i = 0; i < items.size(); i++) {
            FibonacciItem item = items.get(i);
            sb.append(String.format(Locale.ITALIAN, "%02d. %s\n", (i + 1), item.getFormatted()));
        }

        result.reportText = sb.toString();
        return result;
    }
}
