package de.openpoker.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.openpoker.common.model.Card;
import de.openpoker.common.model.GamePhase;
import de.openpoker.common.model.Rank;
import de.openpoker.common.model.Suit;
import de.openpoker.common.network.ActionType;
import de.openpoker.common.network.GameStateDTO;
import de.openpoker.common.network.PlayerAction;
import de.openpoker.server.HandEvaluator.HandRank;

public class Test {
    // testklasse mit ki-hilfe erstellt
    private static int bestanden;
    private static int anzahl;

    public static void main(String[] args) throws IOException {
        testeDeck();
        testePaar();
        testeKleineStrasse();
        testePot();
        testeChips();
        testeRundenstart();
        testeFlop();
        testeFold();

        System.out.println("\nBestanden: " + bestanden + " von " + anzahl);
        if (bestanden != anzahl) {
            System.exit(1);
        }
    }

    private static void pruefe(String name, boolean richtig) {
        anzahl++;
        if (richtig) {
            bestanden++;
            System.out.println("OK: " + name);
        } else {
            System.out.println("FEHLER: " + name);
        }
    }

    private static void testeDeck() {
        Deck deck = new Deck();
        Set<Card> karten = new HashSet<>();
        for (int i = 0; i < 52; i++) {
            karten.add(deck.drawCard());
        }
        pruefe("Deck hat 52 verschiedene Karten", karten.size() == 52);
    }

    private static void testePaar() {
        List<Card> hand = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.CLUBS, Rank.ACE));
        List<Card> tisch = List.of(
                new Card(Suit.SPADES, Rank.TWO),
                new Card(Suit.DIAMONDS, Rank.FIVE),
                new Card(Suit.HEARTS, Rank.NINE));

        HandRank ergebnis = HandEvaluator.evaluateHand(hand, tisch).rank();
        pruefe("Ein Paar wird erkannt", ergebnis == HandRank.ONE_PAIR);
    }

    private static void testeKleineStrasse() {
        List<Card> hand = List.of(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.CLUBS, Rank.TWO));
        List<Card> tisch = List.of(
                new Card(Suit.SPADES, Rank.THREE),
                new Card(Suit.DIAMONDS, Rank.FOUR),
                new Card(Suit.HEARTS, Rank.FIVE));

        HandEvaluator.HandResult ergebnis = HandEvaluator.evaluateHand(hand, tisch);
        boolean richtig = ergebnis.rank() == HandRank.STRAIGHT
                && ergebnis.tieBreakers().get(0) == 5;
        pruefe("A-2-3-4-5 wird als Strasse erkannt", richtig);
    }

    private static void testePot() {
        GameTable tisch = new GameTable();
        tisch.addPot(10);
        tisch.addPot(20);
        int auszahlung = tisch.takePot();

        pruefe("Pot enthaelt 30 Chips und ist danach leer",
                auszahlung == 30 && tisch.getPot() == 0);
    }

    private static void testeChips() {
        Player spieler = new Player("P1", "Anna", 100, null);
        spieler.prepareForHand(true);
        int bezahlt = spieler.commitChips(150);

        pruefe("Ein Spieler kann nicht mehr als seine Chips setzen",
                bezahlt == 100 && spieler.getChips() == 0 && spieler.isAllIn());
    }

    private static void testeRundenstart() throws IOException {
        GameController spiel = new GameController();
        Player[] spieler = fuegeSpielerHinzu(spiel);
        GameStateDTO stand = spiel.snapshot(spieler[0]);

        pruefe("Eine Runde startet mit Blinds und Pot",
                stand.phase() == GamePhase.PREFLOP
                && stand.currentBet() == 20
                && stand.pot() == 30);
    }

    private static void testeFlop() throws IOException {
        GameController spiel = new GameController();
        Player[] spieler = fuegeSpielerHinzu(spiel);
        Player anna = spieler[0];
        Player ben = spieler[1];

        spiel.handleAction(anna, neueAktion(spiel, anna, ActionType.CALL));
        spiel.handleAction(ben, neueAktion(spiel, ben, ActionType.CHECK));
        GameStateDTO stand = spiel.snapshot(anna);

        pruefe("Nach Call und Check wird der Flop aufgedeckt",
                stand.phase() == GamePhase.FLOP && stand.communityCards().size() == 3);
    }

    private static void testeFold() throws IOException {
        GameController spiel = new GameController();
        Player[] spieler = fuegeSpielerHinzu(spiel);
        Player anna = spieler[0];
        Player ben = spieler[1];

        spiel.handleAction(anna, neueAktion(spiel, anna, ActionType.FOLD));
        GameStateDTO stand = spiel.snapshot(ben);

        pruefe("Nach einem Fold bekommt der andere Spieler den Pot",
                ben.getChips() == 1010
                && stand.pot() == 0
                && stand.phase() == GamePhase.SHOWDOWN);
    }

    private static Player[] fuegeSpielerHinzu(GameController spiel) throws IOException {
        Player anna = spiel.addPlayer("Anna", leereAusgabe());
        Player ben = spiel.addPlayer("Ben", leereAusgabe());
        return new Player[] {anna, ben};
    }

    private static ObjectOutputStream leereAusgabe() throws IOException {
        return new ObjectOutputStream(OutputStream.nullOutputStream());
    }

    private static PlayerAction neueAktion(GameController spiel, Player player, ActionType typ) {
        return new PlayerAction(typ, spiel.snapshot(player).turnId());
    }
}
