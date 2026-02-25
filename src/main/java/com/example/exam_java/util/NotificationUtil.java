package com.example.exam_java.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Notification type Slack - popup discret
 */
public final class NotificationUtil {

    public static void show(javafx.stage.Window owner, String title, String message) {
        Label lbl = new Label(title + "\n" + message);
        lbl.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-padding: 12 20; -fx-font-size: 12px;");
        lbl.setWrapText(true);
        lbl.setMaxWidth(280);

        StackPane root = new StackPane(lbl);
        root.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 6;");
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        Stage notifStage = new Stage();
        notifStage.initOwner(owner);
        notifStage.initStyle(StageStyle.TRANSPARENT);
        notifStage.setScene(scene);
        notifStage.setAlwaysOnTop(true);

        if (owner != null) {
            notifStage.setX(owner.getX() + owner.getWidth() - 320);
            notifStage.setY(owner.getY() + 60);
        }

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
        seq.setOnFinished(e -> notifStage.close());
        notifStage.show();
        seq.play();
    }
}
