# Connecta 4 amb JavaFX i WebSockets

Joc multijugador en temps real del clàssic **Connecta 4**, implementat amb **JavaFX** (client) i **WebSockets** (comunicació), amb tota la lògica de joc al servidor.

> Projecte per a l'assignatura d'Aplicacions Distribuïdes — IETI

---

## 🎮 Característiques

- **5 vistes completes**:
  1. **Configuració**: URL del servidor i nom del jugador.
  2. **Selecció de contrincant**: Llista de jugadors disponibles i invitacions 1v1.
  3. **Sala d’espera**: Espera que l’altre jugador accepti.
  4. **Compte enrere**: Mostra “3, 2, 1” abans de començar.
  5. **Partida**: Tauler 7×6 amb animacions i interacció en temps real.
  6. **Resultat**: Guanyador / perdedor / empat.

- **Interacció avançada**:
  - Veus el punter del ratolí del contrincant en temps real.
  - Animació de caiguda de fitxes.
  - Validació de torns i columnes plenes.
  - Il·luminació de les 4 en línia en cas de victòria.

- **Arquitectura robusta**:
  - **Servidor**: Lògica de joc, validació, sincronització.
  - **Client**: Renderitzat amb `Canvas`, animacions amb `Timeline`, gestió d’escenes amb `UtilsViews`.

---

## 🛠️ Requisits

- **Java 17+** (per a JavaFX i WebSockets)
- **Maven** (per a la gestió de dependències)
- **Connexió a Internet** (per descarregar dependències)

---
