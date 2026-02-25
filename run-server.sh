#!/bin/sh
# Démarrer le serveur de messagerie
# Prérequis: docker compose up -d (PostgreSQL)
cd "$(dirname "$0")"
./mvnw exec:java -Dexec.mainClass="com.example.exam_java.server.MessagerieServer"
