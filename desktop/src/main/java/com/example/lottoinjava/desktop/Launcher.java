package com.example.lottoinjava.desktop;

import javafx.application.Application;

/** Punto di ingresso (non estende Application, così JavaFX parte anche da un jar unico). */
public final class Launcher {
    public static void main(String[] args) {
        Application.launch(DesktopApp.class, args);
    }
}
