# OpenPoker – Technische Dokumentation & Architektur

OpenPoker ist ein netzwerkfähiges Multiplayer-Pokerspiel (Texas Hold'em No-Limit), umgesetzt in Java mit Swing-GUI und Socket-Kommunikation.

---

## 🏗️ 1. Projekt- und Paketstruktur

Der Code ist in drei funktionale Schichten unterteilt:

```
src/de/openpoker/
├── common/             # Geteilte Datenmodelle und Netzwerk-DTOs
│   ├── model/
│   │   ├── Card.java        # Record für eine Spielkarte (Farbe + Wert)
│   │   ├── Suit.java        # Enum für Kartenfarben (Clubs, Diamonds, Hearts, Spades)
│   │   ├── Rank.java        # Enum für Kartenwerte (Two bis Ace)
│   │   └── GamePhase.java   # Enum der Spielphasen (Preflop, Flop, Turn, River, Showdown)
│   └── network/
│       ├── PlayerAction.java   # Sealed Interface aller Client-Aktionen (Fold, Call, Raise, Chat, ...)
│       ├── PlayerStateDTO.java # Öffentliche Daten eines Spielers am Tisch (Chips, Einsatz, Status)
│       └── GameStateDTO.java   # Kompletter Spielzustand für die GUI-Aktualisierung
│
├── server/             # Server- und Spiellogik
│   ├── Server.java          # ServerSocket: nimmt Verbindungen an und startet Client-Threads
│   ├── GameController.java  # Kern-Zustandsautomat: regelt Rundenablauf, Blinds, Einsätze und Pots
│   ├── HandEvaluator.java   # Algorithmus zur Ermittlung der besten 5-Karten-Kombination
│   ├── Player.java          # Verwaltet Spielerdaten, Chipstände und Socket-Streams
│   ├── GameTable.java       # Hält den Tisch-Pot, Deck und Gemeinschaftskarten
│   └── Deck.java            # Standard 52-Karten-Deck mit Misch- und Zieh-Methoden
│
└── client/             # Client-Anwendung & Benutzeroberfläche
    ├── Client.java          # Client-Socket, Empfangs-Thread und Action-Dispatcher
    └── ui/
        ├── PokerWindow.java     # Hauptfenster (Layout, Action-Buttons, Tisch-Chat, Statusleiste)
        ├── PokerTablePanel.java # Graphics2D-Zeichnung: Tisch, Avatare, Dealer-Button, Pots, Karten
        └── CardPanel.java       # Zeichnet einzelne Spielkarten mit Schattierung, Index und Symbolen
```

---

## 🌐 2. Client-Server-Architektur & Datenfluss

Die Netzwerkkommunikation basiert auf **TCP-Sockets** und Java-Objektserialisierung (`ObjectInputStream` / `ObjectOutputStream`):

```
       [ Client A ]                  [ Server ]                  [ Client B ]
            │                            │                            │
            │── 1. PlayerAction.Raise ──>│                            │
            │   (z. B. Erhöhung +50)     │                            │
            │                            │── 2. GameController prüft  │
            │                            │      und aktualisiert      │
            │                            │      den Spielzustand      │
            │                            │                            │
            │<────── 3. GameStateDTO ────┼────── 3. GameStateDTO ────>│
            │   (Erhöhung für alle)      │   (Erhöhung für alle)      │
            │                            │                            │
```

### Kommunikationsablauf:
1. **Verbindungsaufbau**: Der Server wartet auf `Port 8888`. Bei jedem eingehenden Client startet `Server.java` einen neuen Thread, liest den Spielernamen und registriert den Spieler im `GameController`.
2. **Aktion senden**: Klickt ein Spieler einen Button (z. B. *Call* oder *Raise*), sendet der Client ein `PlayerAction`-Objekt an den Server.
3. **Zustandsaktualisierung (State Machine)**: Der `GameController` verarbeitet die Aktion synchronisiert (`synchronized`), berechnet Einsätze und Phasenübergänge und verteilt das neue `GameStateDTO` an alle Clients.
4. **GUI-Update**: Jeder Client empfängt das DTO im Hintergrund-Thread und stößt via `SwingUtilities.invokeLater()` die Neuzeichnung des Fensters an.

### Schutz vor Cheaten (Information Hiding):
* Während der laufenden Hand (Preflop bis River) enthält das `PlayerStateDTO` für fremde Spieler als Handkarten **`null`**.
* Der Client kennt also im Speicher nur die eigenen Handkarten.
* Erst beim **Showdown** schickt der Server die Handkarten der verbleibenden Spieler mit, damit der Tisch sie aufdecken kann.

---

## 🎮 3. Spiellogik & Spielphasen (`GameController`)

Der `GameController` steuert den vollständigen Ablauf eines Texas Hold'em Spiels:

1. **Rundenbeginn & Blinds**:
   * Der Dealer-Button wandert reihum (`dealerIndex`).
   * Die beiden Spieler nach dem Dealer zahlen automatisch **Small Blind (10)** und **Big Blind (20)** ein.
   * Der Mindesteinsatz der Runde (`currentBet`) wird auf 20 gesetzt.
2. **Setzrunden (Preflop $\rightarrow$ Flop $\rightarrow$ Turn $\rightarrow$ River)**:
   * Jeder Spieler am Zug kann **Folden**, **Checken** (wenn kein Einsatz offen ist), **Callen** oder **Raisen** (+50, +100 oder All-In).
   * Sobald alle aktiven Spieler denselben Betrag eingezahlt haben (`pendingPlayerIds` ist leer), wechselt die Phase:
     * **Flop**: 3 Gemeinschaftskarten werden aufgedeckt.
     * **Turn**: 1 weitere Karte wird aufgedeckt.
     * **River**: Die 5. und letzte Gemeinschaftskarte wird aufgedeckt.
3. **Sonderfall Fold-Sieg**:
   * Wenn alle bis auf einen Spieler folden, gewinnt der letzte verbleibende Spieler sofort den Pot, ohne seine Hand aufdecken zu müssen.

---

## 🃏 4. Hand-Auswertungsalgorithmus (`HandEvaluator`)

Der `HandEvaluator` ermittelt beim Showdown für jeden Spieler den exakten Pokerwert:

1. **Kombinatorik**:
   * Ein Spieler hat 2 private Handkarten und bis zu 5 Tischkarten (insgesamt 7 Karten).
   * Aus diesen 7 Karten werden alle möglichen 5-Karten-Teilmengen gebildet:
     $$\binom{7}{5} = \frac{7!}{5! \cdot 2!} = 21 \text{ Kombinationen}$$
2. **Klassifizierung jeder 5er-Kombination**:
   * Prüft auf Flush (gleiche Farbe) und Straße (`straightHigh`).
   * Zählt gleiche Kartenwerte (Paare, Drillinge, Vierlinge, Full House).
   * Berücksichtigt Spezialregeln wie die **Wheel-Straße** (Ass als 1 bei `A-2-3-4-5`).
3. **Vergleich & Tie-Breaker**:
   * `HandResult` implementiert `Comparable<HandResult>`.
   * Bei gleichem Hand-Rang (z. B. beide haben Two-Pair) entscheiden die sortierten `tieBreakers` (höheres Paar $\rightarrow$ zweites Paar $\rightarrow$ Kicker-Karte).

---

## 💰 5. Side-Pot & Auszahlungslogik (`calculatePayouts`)

Wenn ein Spieler mit wenigen Chips All-In geht, wird der Pot mathematisch korrekt aufgeteilt:

1. **Beitragsebenen**: Für jeden Spieler wird festgehalten, wie viele Chips er insgesamt in die Hand investiert hat (`handContribution`).
2. **Pot-Splitting**:
   * **Haupttopf (Main Pot)**: Bildet sich aus dem kleinsten All-In-Beitrag multipliziert mit der Anzahl aller Mitspieler. Um diesen Pot spielen alle Spieler mit.
   * **Nebentöpfe (Side Pots)**: Entstehen für alle Einsätze, die über das All-In hinausgehen. Um diese Töpfe spielen nur die Spieler, die den höheren Betrag ebenfalls gezahlt haben.
3. **Auszahlung**: Jeder Pot wird separat an den jeweils besten Spieler vergeben, der für diesen Teilpot qualifiziert ist.

---

## 👥 6. Aufgabenverteilung & Präsentationsschwerpunkte

Das Projekt wurde modular in vier gleichwertige Kernbereiche aufgeteilt:

| Teammitglied | Schwerpunktbereich | Zuständige Klassen | Hauptthemen in der Präsentation |
| :--- | :--- | :--- | :--- |
| **Konrad** | **Netzwerk-Stack & Datenfluss** | `Server.java`, `Client.java`, `PlayerAction.java`, `GameStateDTO.java` | TCP-Sockets, Multithreading (`poker-client-handler`, `poker-reader`), Objekt-Serialisierung, Information Hiding (Cheating-Schutz). |
| **Leon** | **Spiellogik & State Machine** | `GameController.java`, `GamePhase.java`, `Deck.java`, `GameTable.java` | Zustandsautomat (Preflop bis Showdown), Pflichteinsätze (SB 10 / BB 20), rotierender Dealer-Button, Thread-Sicherheit via `synchronized` und `turnId`. |
| **Raphael** | **Poker-Mathematik & Algorithmen** | `HandEvaluator.java`, `calculatePayouts()` in `GameController.java` | Hand-Kombinatorik ($\binom{7}{5} = 21$), Ranking-Logik & Wheel-Straße, Tie-Breaker/Kicker-Vergleich (`Comparable<HandResult>`), mathematische Side-Pot-Aufteilung bei All-Ins. |
| **Alex** | **GUI, Custom Painting & UX** | `PokerWindow.java`, `PokerTablePanel.java`, `CardPanel.java` | 2D-Rendering mit `Graphics2D` (Casino-Filz, Mahagoni-Reling, plastische Karten), trigonometrische Spieler-Verteilung (`sin`/`cos`), responsive Steuerung & `ModernButton`. |
