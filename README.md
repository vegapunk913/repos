# Messagerie Instantanée pour Associations & Événements

Application client-serveur de messagerie interne :

- Authentification des utilisateurs
- Échange de messages en temps réel
- Envoi et réception de fichiers
- Persistance des données avec PostgreSQL
- Interface graphique en JavaFX

##  Technologies utilisées

- **Java 17+**
- **JavaFX** (interface graphique desktop)
- **Sockets Java** (ServerSocket, Socket)
- **Hibernate/JPA** (persistance ORM)
- **PostgreSQL** (base de données relationnelle)
- **Docker Compose** (PostgreSQL + pgAdmin)
- **BCrypt** (hachage des mots de passe)

##  Prérequis

- Docker et Docker Compose installés
- Java 17+ et Maven

##  Démarrage

### 1. Lancer la base de données (PostgreSQL + pgAdmin)

```bash
docker compose up -d
```

- **PostgreSQL** : base `exam_messagerie`, user `postgres`, mot de passe `admin`, port **5432**
- **pgAdmin** : accessible sur http://localhost:5050  
  - Login : `admin@example.com`  
  - Password : `admin`

### 2. Lancer le serveur

```bash
./mvnw exec:java -Dexec.mainClass="com.example.exam_java.server.MessagerieServer"
```

Avec port personnalisé :

```bash
./mvnw exec:java -Dexec.mainClass="com.example.exam_java.server.MessagerieServer" -Dexec.args="9999"
```

### 3. Lancer le client (une ou plusieurs instances)

```bash
./mvnw javafx:run
```

##  Utilisation en réseau (LAN / Wi-Fi)

**L'hôte (admin) :**

1. Clone le projet
2. Lance Docker + PostgreSQL
3. Démarre le serveur :

```bash
docker compose up -d
./mvnw exec:java -Dexec.mainClass="com.example.exam_java.server.MessagerieServer"
```

Le serveur affiche les IP disponibles (ex. `192.168.1.10:9999`).

**Les autres membres :**

1. Clonent le projet
2. Lancent uniquement le client :

```bash
./mvnw javafx:run
```

Dans le champ **Hôte**, ils saisissent l'IP du serveur (ex. `192.168.1.10`) au lieu de `localhost`.  
**Port** : `9999` (par défaut).

 **Note** : si la connexion échoue, vérifier que le pare-feu de la machine serveur autorise le port choisi.

##  Comptes par défaut

Créés automatiquement au démarrage si inexistants :

| Login  | Mot de passe |
|--------|--------------|
| admin  | admin        |
| user1  | user123      |
| user2  | user123      |

Les nouvelles inscriptions doivent être **validées par l'admin** avant connexion.

##  Fonctionnalités

### Gestion des comptes

- Admin par défaut (admin/admin)
- Inscription avec rôle (ORGANISATEUR, MEMBRE, BENEVOLE)
- Validation des inscriptions par l'admin
- Connexion / déconnexion
- Statut ONLINE / OFFLINE

### Messagerie

- Conversations privées
- Envoi de messages texte
- Envoi de fichiers (max 5 Mo)
- Téléchargement des fichiers reçus
- Réception en temps réel
- Historique chronologique
- Liste des contacts connectés / déconnectés
- Notifications (messages non lus, popup)

##  Règles de gestion implémentées

| RG   | Description |
|------|-------------|
| RG1  | Username unique |
| RG2  | Authentification obligatoire |
| RG3  | Connexion unique par utilisateur |
| RG4  | Statut ONLINE/OFFLINE |
| RG5  | Message valide uniquement si expéditeur connecté et destinataire existant |
| RG6  | Message hors ligne stocké et délivré à la reconnexion |
| RG7  | Contenu non vide, max 1000 caractères |
| RG8  | Historique chronologique |
| RG9  | Mots de passe hachés (BCrypt) |
| RG10 | Perte de connexion → erreur + passage hors ligne |
| RG11 | Chaque client géré dans un thread séparé |
| RG12 | Journalisation serveur (connexions, déconnexions, messages) |
| RG13 | ORGANISATEUR → accès à la liste complète des membres |

## Structure du projet

```
exam_java/
├── docker-compose.yml
├── pom.xml
├── run-server.sh
└── src/main/
    ├── java/com/example/exam_java/
    │   ├── entity/       # User, Message, Role, Status
    │   ├── dao/          # UserDao, MessageDao, JpaUtil
    │   ├── model/        # ContactItem
    │   ├── protocol/     # Protocole client-serveur
    │   ├── server/       # MessagerieServer, ClientHandler
    │   ├── network/      # ServerConnection
    │   ├── util/         # PasswordUtil, NotificationUtil
    │   ├── MessagerieApplication.java
    │   ├── LoginController.java
    │   └── MessagingController.java
    └── resources/
        ├── META-INF/persistence.xml
        └── com/example/exam_java/
            ├── login-view.fxml
            ├── messaging-view.fxml
            └── styles.css
```

##  Envoi et réception de fichiers

- **Envoyer** : sélectionner un contact → choisir fichier (max 5 Mo)
- **Télécharger** : conversation →  Télécharger

Les fichiers sont stockés côté serveur dans `uploads/`.

##  Configuration base de données

- **URL** : `jdbc:postgresql://localhost:5432/exam_messagerie`
- **Utilisateur** : `postgres`
- **Mot de passe** : `admin`

##  Commandes utiles

| Action              | Commande |
|---------------------|----------|
| Démarrer DB + pgAdmin | `docker compose up -d` |
| Arrêter             | `docker compose down` |
| Serveur             | `./mvnw exec:java -Dexec.mainClass="com.example.exam_java.server.MessagerieServer"` |
| Client              | `./mvnw javafx:run` |
| pgAdmin             | http://localhost:5050 |
