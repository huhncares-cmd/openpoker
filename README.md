# OpenPoker

Texas Hold'em im Netzwerk, Java + Swing. Teamprojekt Softwaretechnik WS26/27.

## Starten

- `de.openpoker.server.Server` starten
- pro Spieler `de.openpoker.Main` starten
- Name + Server-Adresse eingeben, Port 8888

## Pakete

- `common` – Karten, Spielphasen, Aktionen, DTOs (brauchen Client und Server)
- `server` – Server, GameController, Player, Deck, GameTable, HandEvaluator
- `client` – Client, Verbindungsdialog
- `client/ui` – Fenster, Tisch, Karten, Buttons

## Netzwerk

- TCP-Sockets, Objekte per Java-Serialisierung
- Server: ein Thread pro Spieler
- Client → Server: `PlayerAction` (Fold, Call, Raise, Chat …)
- Server → alle Clients: `GameStateDTO` (kompletter Spielstand)
- GameController ist `synchronized`
- GUI-Update über `SwingUtilities.invokeLater()`
- GameController kennt keine Streams, sendet nur über `ClientConnection` (DIP)
- Anti-Cheat: fremde Karten (`revealedCards`) erst beim Showdown, vorher `null`

## Spiel

- Dealer-Button rotiert
- Blinds 10 / 20
- Fold, Check, Call, Raise (+50, +100, All-In)
- Preflop → Flop → Turn → River → Showdown
- alle folden bis auf einen → der gewinnt sofort
- Handauswertung: 7 Karten, alle 21 Fünferkombis testen, beste zählt
- Wheel-Straße A-2-3-4-5, Kicker bei Gleichstand
- Side Pots bei All-In: jeder spielt nur um das, was er selbst gesetzt hat

## Team

| | Thema | Klassen |
|---|---|---|
| Konrad | Netzwerk | Server, Client, ClientConnection, ConnectionConfig, PlayerAction, ActionType, GameStateDTO, PlayerStateDTO |
| Leon | Spielablauf | GameController, GamePhase, Deck, GameTable |
| Raphael | Handauswertung, Side Pots | HandEvaluator, HandRank, HandResult, `calculatePayouts()` |
| Alex | GUI | PokerWindow, PokerTablePanel, CardPanel, ModernButton, PokerActionListener |

## Tests

- `de.openpoker.server.Test` starten → `OK` / `FEHLER` pro Test
- Screenshots in `Testbeispiele.pdf`
