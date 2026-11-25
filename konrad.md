# Aufgabe 4 – Verstöße gegen Architekturprinzipien (Netzwerk-Stack)

## 1. Dependency Inversion Principle (umgesetzt)
**Stelle:** `Server.java` (Z. 41) → `GameController.addPlayer` (Z. 54) → `Player` (Z. 20, 100–114)

```java
player = gameController.addPlayer(playerName, out);   // out ist ein ObjectOutputStream
```

**Problem:**
Der Netzwerk-Stream wird bis in die Spiellogik durchgereicht. `GameController` und `Player` hängen dadurch direkt von `ObjectOutputStream` ab, also von der Übertragungstechnik. Das verstößt gegen das **DIP**: Fachlogik soll nicht von technischen Details abhängen. Gleichzeitig sind fachliche und technische Teile nicht getrennt (**Separation of Concerns**). Wer das Netzwerkformat ändert, muss auch die Spiellogik ändern.

**Verbesserung:**
Neues Interface `ClientConnection`. Die Spiellogik kennt nur noch dieses Interface. Wie gesendet wird, weiß nur der `Server`.

```java
public interface ClientConnection {
    boolean send(GameStateDTO state);
}

// Server.java
player = gameController.addPlayer(playerName, state -> send(out, state));
```

Ergebnis: `GameController` und `Player` brauchen kein `java.io` mehr. In Tests reicht `stand -> true` statt eines echten Streams. Neuer Test für einen Verbindungsabbruch: `stand -> false`. Alle 9 Tests bestehen.

---

## 2. Separation of Concerns & Hohe Kohäsion
**Stelle:** `Client.java` (`promptConnectionSettings` Z. 144–174, `main` Z. 176–201)

**Problem:**
`Client` ist für das Netzwerk zuständig, baut aber auch einen Swing-Dialog (`JTextField`, `JOptionPane`) und wertet die Startargumente aus. Das sind verschiedene Aufgaben in einer Klasse (**SoC**). Diese Methoden benutzen keine Attribute der Klasse, deshalb ist die Kohäsion gering (**LCOM = 5**, ohne diese beiden Methoden wäre LCOM = 0). Außerdem erzeugt `Client` das konkrete `PokerWindow` selbst, statt nur ein Interface zu kennen (**Separation of Interface and Implementation**).

**Verbesserungsvorschlag:**
Dialog in eine eigene UI-Klasse verschieben, Argumente in `Main` auswerten.

---

## 3. Information Hiding
**Stelle:** `PlayerAction.java` (Konstruktoren Z. 13–34)

```java
new PlayerAction(ActionType.RAISE);           // kompiliert: Raise ohne Betrag
new PlayerAction(ActionType.FOLD, "hallo");   // kompiliert: Fold mit Chat-Text
```

**Problem:**
Welche Felder zu welcher Aktion gehören, regelt `PlayerAction` nicht selbst. Das wissen nur `PokerWindow` (erzeugt) und `GameController` (wertet aus). Dieses Wissen ist also nicht verborgen (**Information Hiding**), und beide Klassen sind über diese Absprache gekoppelt (**Geringe Kopplung**).

**Verbesserungsvorschlag:**
Privater Konstruktor plus `PlayerAction.raise(turnId, amount)`, `PlayerAction.chat(text)` usw. Dann lassen sich nur noch gültige Aktionen bauen.
