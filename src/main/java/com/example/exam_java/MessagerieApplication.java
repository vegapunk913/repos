package com.example.exam_java;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MessagerieApplication extends Application {

    /**
     * Point d'entrée JavaFX : charge l'écran de login (login-view.fxml), affiche la fenêtre et transmet le stage au LoginController.
     * Paramètre : stage – fenêtre principale. Peut lever IOException si le FXML est introuvable.
     */
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(MessagerieApplication.class.getResource("login-view.fxml"));
        Parent root = loader.load();
        com.example.exam_java.LoginController loginCtrl = loader.getController();
        loginCtrl.setStage(stage);
        Scene scene = new Scene(root, 440, 520);
        stage.setTitle("Messagerie Interne - Association & Événements");
        stage.setResizable(true);
        stage.setMinWidth(320);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Lance l'application JavaFX. Paramètre : args – arguments de la ligne de commande (non utilisés). Ne renvoie rien.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
