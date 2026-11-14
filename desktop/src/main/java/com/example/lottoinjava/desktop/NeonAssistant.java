package com.example.lottoinjava.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Piccola assistente visuale mostrata solo dopo un'elaborazione riuscita. */
final class NeonAssistant {
    private NeonAssistant() {}
    static void show(Stage owner, String detail, String css) {
        Stage popup = new Stage();
        popup.initOwner(owner); popup.initModality(Modality.NONE);
        popup.setTitle("LottoInJava · Calcolo eseguito");
        ImageView art = new ImageView();
        try { art.setImage(new Image(NeonAssistant.class.getResourceAsStream("/assistente_notte_neon.png"))); } catch (Exception ignored) {}
        art.setFitWidth(480); art.setFitHeight(300); art.setPreserveRatio(true); art.setSmooth(true);
        Label title = new Label("♡ Calcolo eseguito!"); title.getStyleClass().add("assistant-title");
        Label message = new Label(detail); message.setWrapText(true); message.setMaxWidth(450); message.setStyle("-fx-text-fill: -c-toast-text; -fx-font-size: 14px;");
        Button close = new Button("Continua 💜"); close.getStyleClass().add("button-primary"); close.setOnAction(e -> popup.close());
        VBox box = new VBox(10, art, title, message, close); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(16)); box.getStyleClass().add("assistant-popup");
        Scene scene = new Scene(box, 520, 470);
        try { if (css != null && !css.isBlank()) scene.getStylesheets().add(css); } catch (Exception ignored) {}
        popup.setScene(scene); popup.setResizable(false); popup.show();
        // Entrata morbida: la mascotte appare con dissolvenza e un leggero effetto pop.
        box.setOpacity(0.0);
        box.setScaleX(0.94);
        box.setScaleY(0.94);
        FadeTransition fade = new FadeTransition(Duration.millis(320), box);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        ScaleTransition pop = new ScaleTransition(Duration.millis(320), box);
        pop.setFromX(0.94); pop.setFromY(0.94);
        pop.setToX(1.0); pop.setToY(1.0);
        fade.play();
        pop.play();
    }
}
