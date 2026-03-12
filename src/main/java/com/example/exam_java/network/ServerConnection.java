package com.example.exam_java.network;

import com.example.exam_java.protocol.Protocol;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Gestion de la connexion au serveur - RG10: affichage erreur et passage hors ligne en cas de perte
 * Plusieurs listeners peuvent être enregistrés (ex: messagerie + fenêtre admin).
 */
public class ServerConnection {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread readerThread;
    private volatile boolean connected;
    private final BlockingQueue<String> incomingMessages = new LinkedBlockingQueue<>();
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();

    public interface MessageListener {
        void onMessage(String line);
        void onDisconnected(String reason);
    }

    /**
     * Remplace le listener de messages par un seul (efface les précédents). Utilisé pour le login/register.
     * Paramètre : listener – nouveau listener, ou null pour vider. Ne renvoie rien.
     */
    public void setMessageListener(MessageListener listener) {
        messageListeners.clear();
        if (listener != null) messageListeners.add(listener);
    }

    /**
     * Ajoute un listener sans retirer les autres (ex. admin en plus de la messagerie).
     * Paramètre : listener – non null. Ne renvoie rien.
     */
    public void addMessageListener(MessageListener listener) {
        if (listener != null && !messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }

    /**
     * Retire un listener de la liste. Paramètre : listener – le listener à retirer. Ne renvoie rien.
     */
    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    /**
     * Établit la connexion TCP au serveur et démarre un thread qui lit les lignes et notifie les listeners.
     * Paramètres : host – adresse ; port – port. Retourne true si la connexion réussit, false en cas d'IOException (ex. timeout 5 s).
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;

            readerThread = new Thread(() -> {
                try {
                    String line;
                    while (connected && (line = reader.readLine()) != null) {
                        if (!messageListeners.isEmpty()) {
                            for (MessageListener l : messageListeners) {
                                l.onMessage(line);
                            }
                        } else {
                            incomingMessages.offer(line);
                        }
                    }
                } catch (IOException e) {
                    if (connected) {
                        disconnect("Perte de connexion: " + e.getMessage());
                    }
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ferme la socket et notifie tous les listeners avec onDisconnected(reason).
     * Paramètre : reason – texte expliquant la déconnexion. Ne renvoie rien.
     */
    public void disconnect(String reason) {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        for (MessageListener l : new ArrayList<>(messageListeners)) {
            l.onDisconnected(reason);
        }
    }

    /**
     * Envoie une ligne au serveur (build avec Protocol.build(parts)). Ne fait rien si déconnecté.
     * Paramètres : parts – commande et paramètres (ex. Protocol.LOGIN, username, password). Ne renvoie rien.
     */
    public void send(String... parts) {
        if (writer != null && connected) {
            writer.println(Protocol.build(parts).trim());
        }
    }

    /**
     * Indique si la connexion est active (socket ouverte et flag connected).
     * Aucun paramètre. Retourne true si connecté, false sinon.
     */
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }

    /**
     * Bloque jusqu'à recevoir une ligne (utilisé si aucun listener n'est enregistré). Peut lever InterruptedException.
     * Aucun paramètre. Retourne la ligne reçue.
     */
    public String readLineBlocking() throws InterruptedException {
        return incomingMessages.take();
    }
}
