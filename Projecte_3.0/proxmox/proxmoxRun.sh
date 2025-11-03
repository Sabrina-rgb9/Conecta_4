#!/bin/bash

source ./config.env

USER=${1:-$DEFAULT_USER}
RSA_PATH=${2:-$DEFAULT_RSA_PATH}
SERVER_PORT=${3:-$DEFAULT_SERVER_PORT}
SSH_OPTS='-oHostKeyAlgorithms=+ssh-rsa -oPubkeyAcceptedAlgorithms=+ssh-rsa'

echo "User: $USER"
echo "Ruta RSA: $RSA_PATH"
echo "Server port: $SERVER_PORT"

JAR_NAME="server-package.jar"
JAR_PATH="./target/$JAR_NAME"

cd ..

if [[ ! -f "$RSA_PATH" ]]; then
    echo "Error: No s'ha trobat el fitxer de clau privada: $RSA_PATH"
    cd proxmox
    exit 1
fi

echo "Generant el fitxer JAR..."
rm -f "$JAR_PATH"
./run.sh com.server.ServerMain build  # CAMBIO A SERVER MAIN

if [[ ! -f "$JAR_PATH" ]]; then
    echo "Error: No s'ha trobat l'arxiu JAR: $JAR_PATH"
    cd proxmox
    exit 1
fi

eval "$(ssh-agent -s)"
ssh-add "$RSA_PATH"
if [[ $? -ne 0 ]]; then
    echo "Error: No s'ha pogut carregar la clau RSA."
    exit 1
fi

echo "Enviant $JAR_PATH al servidor..."
scp -P 20127 $SSH_OPTS "$JAR_PATH" "$USER@ieticloudpro.ieti.cat:~/"
if [[ $? -ne 0 ]]; then
    echo "Error durant l'enviament SCP"
    ssh-agent -k
    cd proxmox
    exit 1
fi

ssh -t -p 20127 $SSH_OPTS "$USER@ieticloudpro.ieti.cat" << 'EOF'  
    cd "$HOME/"

    # Detener proceso anterior - buscar por clase correcta
    PID=$(ps aux | grep 'com.server.ServerMain' | grep -v 'grep' | awk '{print $2}')
    if [ -n "$PID" ]; then
      kill -15 $PID
      echo "Senyal SIGTERM enviat al procés $PID."
      for i in {1..10}; do
        if ! ps -p $PID > /dev/null; then
          echo "Procés $PID aturat correctament."
          break
        fi
        echo "Esperant que el procés finalitzi..."
        sleep 1
      done
      if ps -p $PID > /dev/null; then
        echo "Procés $PID encara actiu, forçant aturada..."
        kill -9 $PID
      fi
    else
      echo "No s'ha trobat el procés anterior."
    fi

    # Esperar que el puerto se libere
    MAX_RETRIES=10
    RETRIES=0
    while netstat -an | grep -q ':$SERVER_PORT.*LISTEN'; do
      echo "Esperant que el port $SERVER_PORT es desalliberi..."
      sleep 1
      RETRIES=$((RETRIES + 1))
      if [ $RETRIES -ge $MAX_RETRIES ]; then
        echo "Error: El port $SERVER_PORT no es desallibera després de $MAX_RETRIES segons."
        exit 1
      fi
    done

    # ✅ CORREGIDO: Usar la clase correcta y dar más tiempo
    echo "Iniciando servidor con: java -cp server-package.jar com.server.ServerMain"
    nohup java -cp server-package.jar com.server.ServerMain > output.log 2>&1 &
    
    # ⭐ AUMENTAR TIEMPO DE ESPERA
    sleep 5
    
    # Verificar si el proceso está vivo
    PID=$(ps aux | grep 'com.server.ServerMain' | grep -v 'grep' | awk '{print $2}')
    if [ -n "$PID" ]; then
      echo "✅ Nou procés ServerMain amb PID $PID arrencat correctament."
      echo "=== Verificando logs ==="
      tail -5 output.log
    else
      echo "❌ Error: No s'ha pogut arrencar el nou procés."
      echo "=== Últimas líneas del log ==="
      tail -10 output.log
      exit 1
    fi
EOF

ssh-agent -k
cd proxmox