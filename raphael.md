# Aufgabe 4 – Verstöße gegen Architekturprinzipien (OpenPoker)

## 1. Open-Closed Principle (OCP)
**Stelle:** `src/de/openpoker/client/ui/PokerTablePanel.java` (`drawLastAction`, Z. 272–276)

```java
Color actBg = isWinner ? new Color(255, 215, 0) :
              lastAction.contains("ALL-IN") ? new Color(215, 40, 20) :
              lastAction.contains("RAISE") ? new Color(210, 130, 20) :
              lastAction.contains("FOLD") ? new Color(190, 40, 40) :
              new Color(40, 140, 60);
```

**Problem:**
Die UI erkennt Spielaktionen über String-Vergleiche (`contains("ALL-IN")`, `contains("RAISE")`, ebenso `contains("GEWINNT")` in Z. 156) statt über ein Enum oder eine Map. Das verstößt gegen das **Open-Closed Principle**, weil bei jeder neuen Aktion oder Umbenennung eines Textes diese Abfrage geändert werden muss. Gleichzeitig entsteht eine enge Kopplung zwischen GUI-Darstellung und den exakten Anzeige-Strings des Servers.

---

## 2. KISS & DRY
**Stelle:** `src/de/openpoker/server/HandEvaluator.java` (`evaluateFive`, Z. 55–137 – 83 Zeilen)

```java
private static HandResult evaluateFive(List<Card> cards) {
    // 1. Kartenwerte zählen & gruppieren (Z. 56–80)
    int[] rankCounts = new int[15];
    // ... fourOfAKind, threeOfAKind, pairs, singleCards füllen

    // 2. Flush prüfen (Z. 83–90)
    boolean flush = true;
    // ... alle Kartenfarben vergleichen

    // 3. Straight prüfen (Z. 92–98)
    int straightHigh = straightHigh(ranksAscending);

    // 4. Alle 10 Handränge auswerten (Z. 100–136)
    if (flush && straightHigh == 14) { return result(HandRank.ROYAL_FLUSH, List.of(14)); }
    // ... Straight Flush, Four of a Kind, Full House, Flush, Straight ...
    if (!threeOfAKind.isEmpty()) {
        List<Integer> tieBreakers = new ArrayList<>();
        tieBreakers.add(threeOfAKind.get(0));
        tieBreakers.addAll(singleCards);
        return result(HandRank.THREE_OF_A_KIND, tieBreakers);
    }
    // ... Two Pair ...
    if (pairs.size() == 1) {
        List<Integer> tieBreakers = new ArrayList<>();
        tieBreakers.add(pairs.get(0));
        tieBreakers.addAll(singleCards);
        return result(HandRank.ONE_PAIR, tieBreakers);
    }
    return result(HandRank.HIGH_CARD, ranksDescending);
}
```

**Problem:**
`evaluateFive` macht vier Aufgaben in einer 83-zeiligen Methode: Häufigkeiten zählen, Flush prüfen, Straight prüfen und alle 10 Handränge auswerten (hohe zyklomatische Komplexität, Verstoß gegen **KISS** / Code-Smell **Lange Methode**). Zusätzlich ist der Aufbau von `tieBreakers` bei `THREE_OF_A_KIND` und `ONE_PAIR` identisch dupliziert (Verstoß gegen **DRY**).

---

## 3. Separation of Concerns (SoC)
**Stelle:** `src/de/openpoker/client/ui/PokerWindow.java` (`refreshControls`, Z. 268–273)

```java
int toCall = myTurn ? Math.max(0, gameState.currentBet() - me.currentBet()) : 0;
int maxRaise = me != null ? me.chips() - toCall : 0;

foldBtn.setEnabled(myTurn);
checkBtn.setEnabled(myTurn && toCall == 0);
callBtn.setEnabled(myTurn && toCall > 0 && me.chips() > 0);
```

**Problem:**
Die Swing-GUI berechnet hier selbst Poker-Regeln (`toCall`, `maxRaise` und wann `CHECK` oder `CALL` erlaubt ist), statt nur den Spielzustand vom Server anzuzeigen. Dadurch sind Darstellung und Fachlogik nicht sauber getrennt. Außerdem steht dieselbe `toCall`-/`allInRaise`-Berechnung im `allInBtn`-Listener (Z. 145–146) noch einmal.
