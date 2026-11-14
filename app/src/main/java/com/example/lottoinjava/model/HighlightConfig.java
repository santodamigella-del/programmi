package com.example.lottoinjava.model;

import java.util.ArrayList;
import java.util.List;

public class HighlightConfig {

    private boolean active = false;
    private int spyNumber = 0;
    private String targetWheel = "Bari";
    private List<Integer> associatedNumbers = new ArrayList<>();
    private int maxColpi = 6;
    private List<Integer> blueNumbers = new ArrayList<>(); // i più usciti tra i numeri associati (blu scuro)

    public HighlightConfig() {}

    public HighlightConfig(boolean active, int spyNumber, String targetWheel, List<Integer> associatedNumbers, int maxColpi) {
        this.active = active;
        this.spyNumber = spyNumber;
        this.targetWheel = targetWheel;
        this.associatedNumbers = associatedNumbers != null ? associatedNumbers : new ArrayList<>();
        this.maxColpi = maxColpi;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSpyNumber() {
        return spyNumber;
    }

    public String getTargetWheel() {
        return targetWheel;
    }

    public List<Integer> getAssociatedNumbers() {
        return associatedNumbers;
    }

    public int getMaxColpi() {
        return maxColpi;
    }

    public boolean isSpyNumber(int num) {
        return active && num == spyNumber;
    }

    public boolean isAssociatedNumber(int num) {
        return active && associatedNumbers.contains(num);
    }

    public void setBlueNumbers(List<Integer> numbers) {
        this.blueNumbers = numbers != null ? numbers : new ArrayList<>();
    }

    public List<Integer> getBlueNumbers() {
        return blueNumbers;
    }

    public boolean isBlueNumber(int num) {
        return active && blueNumbers.contains(num);
    }
}
