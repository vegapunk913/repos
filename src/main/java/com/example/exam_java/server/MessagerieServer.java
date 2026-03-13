package com.example.exam_java.server;

import com.example.exam_java.dao.MessageDao;
import com.example.exam_java.dao.UserDao;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serveur de messagerie - RG11: chaque client dans un thread séparé
 * RG12: journalisation des connexions, déconnexions et envois
 */
public class MessagerieServer {

    public static final int DEFAULT_PORT = 9999;

    private final int port;
    private final UserDao userDao;
    private final MessageDao messageDao;
    private final ConcurrentHashMap<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    /**
     * Crée le serveur avec un port et initialise les DAO (UserDao, MessageDao).
     * Paramètre : port – port d'écoute TCP. Ne renvoie rien.
     */
    public MessagerieServer(int port) {
        this.port = port;
        this.userDao = new UserDao();
        this.messageDao = new MessageDao();
    }

    /**
     * Démarre le serveur : crée les utilisateurs par défaut en base, écoute sur le port, accepte les clients dans un pool de threads.
     * En cas d'échec de connexion à PostgreSQL, affiche un message et ne démarre pas. Ne prend pas de paramètre. Ne renvoie rien.
     */
    public void start() {
        try {
            userDao.ensureDefaultUsersExist();
            log("Utilisateurs par défaut chargés: admin/admin, user1/user123, user2/user123");
        } catch (Exception e) {
            log("ERREUR: Impossible de se connecter à PostgreSQL. Démarrez Docker: docker compose up -d");
            log("Détail: " + e.getMessage());
            return;
        }
        log("=== Serveur Messagerie démarré sur le port " + port + " ===");
        logLocalAddresses(port);
        ExecutorService executor = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, userDao, messageDao, onlineUsers, this);
                executor.submit(handler);
            }
        } catch (IOException e) {
            if (running) {
                log("Erreur serveur: " + e.getMessage());
            }
        }
        executor.shutdown();
    }

    /**
     * Affiche dans les logs les adresses IP locales (hors loopback) pour que les clients sur le même réseau puissent se connecter.
     * Paramètre : port – port d'écoute à afficher. Ne renvoie rien.
     */
    private void logLocalAddresses(int port) {
        try {
            log("--- Connexion réseau (même Wi-Fi) ---");
            log("Les autres utilisateurs doivent entrer l'une de ces adresses dans le champ Hôte :");
            log("  • localhost (sur cette machine)");
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces(); nics.hasMoreElements(); ) {
                NetworkInterface nic = nics.nextElement();
                if (nic.isUp() && !nic.isLoopback()) {
                    for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                        if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                            log("  • " + addr.getHostAddress() + ":" + port);
                        }
                    }
                }
            }
            log("--------------------------------------");
        } catch (SocketException e) {
            log("Impossible de lister les adresses réseau");
        }
    }

    /**
     * Écrit un message dans la console avec un horodatage (RG12 : journalisation).
     * Paramètre : message – texte à afficher. Ne renvoie rien.
     */
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] " + message);
    }

    /**
     * Arrête la boucle d'acceptation des clients (running = false). Les clients déjà connectés restent gérés jusqu'à déconnexion.
     * Aucun paramètre. Ne renvoie rien.
     */
    public void stop() {
        running = false;
    }

    /**
     * Point d'entrée : lance le serveur sur le port passé en argument, ou 9999 par défaut.
     * Paramètre : args – optionnellement args[0] = numéro de port.
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        MessagerieServer server = new MessagerieServer(port);
        server.start();
    }
}
