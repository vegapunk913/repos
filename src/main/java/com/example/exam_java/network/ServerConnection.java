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

    public void setMessageListener(MessageListener listener) {
        messageListeners.clear();
        if (listener != null) messageListeners.add(listener);
    }

    public void addMessageListener(MessageListener listener) {
        if (listener != null && !messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }

    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

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

    public void disconnect(String reason) {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        for (MessageListener l : new ArrayList<>(messageListeners)) {
            l.onDisconnected(reason);
        }
    }

    public void send(String... parts) {
        if (writer != null && connected) {
            writer.println(Protocol.build(parts).trim());
        }
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }

    public String readLineBlocking() throws InterruptedException {
        return incomingMessages.take();
    }
}
