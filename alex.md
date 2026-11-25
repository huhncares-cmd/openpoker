# Aufgabe 4 – Verstöße gegen Architekturprinzipien (OpenPoker – GUI)

## 1. Separation of Concerns (SoC), Hohe Kohäsion & Information Hiding (umgesetzt)
**Stelle:** `src/de/openpoker/client/ui/PokerWindow.java` (gesamte Klasse, 332 Zeilen, 16 Attribute; insb. `createChatPanel` Z. 72–114, `createActionPanel` Z. 116–165, `updateStatus` Z. 290–321)

```java
public final class PokerWindow extends JFrame {
    private final PokerTablePanel tablePanel = new PokerTablePanel();
    private final JPanel myCardsPanel = new JPanel(...);
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField(12);
    private final JLabel turnStatusLabel = new JLabel(...);
    private final JButton foldBtn = new ModernButton(...);
    // ... 6 weitere Buttons (checkBtn, callBtn, raise50Btn, raise100Btn, allInBtn, nextRoundBtn)
    private final JButton sendBtn = new ModernButton(...);
    // ... createChatPanel(), sendChat(), createActionPanel(), refreshControls(), updateStatus()
}
```

**Problem:**
`PokerWindow` bündelt vier unterschiedliche UI-Verantwortlichkeiten in einer einzigen Klasse: die obere Statusleiste (`turnStatusLabel`, `updateStatus`), den Chat-Bereich (`chatArea`, `chatInput`, `sendBtn`, `createChatPanel`, `sendChat`), die eigene Hand- und Aktionssteuerung (`myCardsPanel`, sieben Spiel-Buttons, `createActionPanel`, `refreshControls`) sowie die Koordination des Fensters und Verbindungszustands. Das verstößt gegen **Separation of Concerns**, weil Änderungen an einem einzelnen UI-Teilbereich (z. B. Chat-Layout oder Statusmeldungen) immer das gesamte Hauptfenster betreffen.

Gleichzeitig ist die Kohäsion sehr gering (**Hohe Kohäsion**): Die Methoden arbeiten auf weitgehend disjunkten Attributgruppen (z. B. greift `updateStatus` nur auf `turnStatusLabel` zu, `createChatPanel`/`sendChat` nur auf die Chat-Felder, `refreshControls` auf die Aktions-Buttons). Daraus ergibt sich ein hoher Wert für **LCOM = 38** (`|P| = 58`, `|Q| = 20`) – laut Vorlesung ein klares Signal, die Klasse aufzuspalten. Zudem verletzt die Klasse **Information Hiding**, da alle internen Swing-Komponenten der Teilbereiche direkt als Felder von `PokerWindow` verwaltet werden.

**Verbesserung:**
`PokerWindow` wurde in eigenständige, kohäsive Swing-Komponenten aufgespalten:
- `StatusBarPanel`: kapselt die Statusleiste (`statusLabel`), Farbkonstanten sowie Verbindungs- und Phasen-Statusmeldungen (`LCOM = 0`).
- `ChatPanel`: kapselt Chat-Verlauf, Eingabefeld und Sende-Button und meldet neue Nachrichten über ein `Predicate<String>` nach außen.
- `ActionBarPanel`: kapselt die Anzeige der eigenen Handkarten sowie die sieben Aktions-Buttons und übermittelt Spielzüge über Callbacks (`BiConsumer<ActionType, Integer>`, `Runnable`), ohne die `toCall`-/`allInRaise`-Berechnung im `allInBtn`-Listener zu duplizieren.
- `PokerWindow`: koordiniert nur noch die vier Teilpanels (`statusBar`, `tablePanel`, `chatPanel`, `actionBar`) und den Verbindungs-/Zugzustand.

Ergebnis: `PokerWindow` schrumpft von **332 auf 124 Zeilen** und von **16 auf 8 Attribute**; die Kohäsionsmetrik verbessert sich von **LCOM = 38 auf LCOM = 0**. Die öffentliche Schnittstelle von `PokerWindow` bleibt unverändert, sodass `Client.java` ohne Anpassung weiterarbeitet und das beobachtbare Programmverhalten erhalten bleibt.

---

## 2. Don't Repeat Yourself (DRY) & Neid (Feature Envy)
**Stelle:** `src/de/openpoker/client/ui/CardPanel.java` (`drawCardSymbols`, Z. 93–101) und `src/de/openpoker/client/ui/PokerTablePanel.java` (`drawMiniCard`, Z. 286–314)

```java
// CardPanel.java (Z. 94–97)
boolean isRed = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS;
Color primaryColor = isRed ? new Color(215, 35, 35) : new Color(28, 30, 38);
String suitSymbol = card.suit().getSymbol();
String rankStr = card.rank().getSymbol();

// PokerTablePanel.java (Z. 301–305)
boolean isRed = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS;
g2.setColor(isRed ? new Color(215, 35, 35) : new Color(28, 30, 38));
String rankStr = card.rank().getSymbol();
String suitSym = card.suit().getSymbol();
```

**Problem:**
Hier liegt der Code-Smell **Duplizierter Code in zwei unterschiedlichen Klassen** (**DRY**) vor: Sowohl `CardPanel` als auch `PokerTablePanel.drawMiniCard` implementieren unabhängig voneinander die Bestimmung der Kartenfarbe, exakt dieselben RGB-Farbwerte (`215, 35, 35` für Rot, `28, 30, 38` für Schwarz, `255, 215, 0` für Gold/Gewinner-Rahmen) sowie Schatten, Kartenhintergrund und Symbol-Rendering. Ändert sich das Kartendesign (z. B. Farbschema oder Vier-Farben-Deck), müssen mehrere Klassen angepasst werden.

Zusätzlich tritt der Code-Smell **Neid (Feature Envy)** auf: Beide UI-Klassen prüfen explizit auf `Suit.HEARTS` und `Suit.DIAMONDS`, statt diese Eigenschaft direkt im Enum `Suit` zu kapseln. Gleichzeitig übernimmt `PokerTablePanel` neben dem Tisch- und Avatar-Rendering auch das komplette Zeichnen von Spielkarten, obwohl dafür bereits `CardPanel` existiert (**Separation of Concerns**).

**Verbesserungsvorschlag:**
Eine Methode `isRed()` in `Suit` einführen sowie gemeinsame Farbkonstanten und Karten-Zeichenlogik in einer zentralen Hilfsklasse (z. B. `CardRenderer`) bündeln, die sowohl von `CardPanel` als auch von `PokerTablePanel` genutzt wird.

---

## 3. Information Hiding & Geringe Kopplung (Nachrichtenketten)
**Stelle:** `src/de/openpoker/server/GameTable.java` (`getCommunityCards` Z. 12–14, `getDeck` Z. 16–18) und `src/de/openpoker/server/GameController.java` (`resetRound` Z. 173–176, `dealHoleCards` Z. 195–201)

```java
// GameTable.java (Z. 12–18)
public List<Card> getCommunityCards() { return communityCards; }
public Deck getDeck() { return deck; }

// GameController.java (Z. 174–176, 198)
gameTable.getDeck().reset();
gameTable.getCommunityCards().clear();
gameTable.setPot(0);
// ...
player.getCards().add(gameTable.getDeck().drawCard());
```

**Problem:**
`GameTable` (und ebenso `Player.getCards()`) gibt interne, veränderbare Datenstrukturen (`List<Card>` und `Deck`) direkt über Getter nach außen. Das verletzt **Information Hiding** (Parnas-Gesetz: *„Nur Verborgenes kann ohne Risiko verändert werden“*): `GameController` kennt die interne Repräsentation von `GameTable` und manipuliert deren Liste (`clear()`) sowie das `Deck` direkt von außen.

Dadurch entstehen typische **Nachrichtenketten** (`gameTable.getDeck().reset()`, `gameTable.getCommunityCards().clear()`, `player.getCards().add(gameTable.getDeck().drawCard())`), die gegen das Prinzip der **geringen Kopplung** verstoßen: `GameController` ist unnötigerweise vom Typ `Deck` und dessen Schnittstelle abhängig, obwohl `GameTable` das Deck bereits besitzt und verwaltet.

**Verbesserungsvorschlag:**
Die Operationen in die besitzenden Klassen verschieben (`GameTable.reset()`, `GameTable.drawCard()`, `Player.addCard(Card)`) und `getCommunityCards()` bzw. `getCards()` nur als unveränderliche Sicht (`Collections.unmodifiableList(...)`) zurückgeben.
