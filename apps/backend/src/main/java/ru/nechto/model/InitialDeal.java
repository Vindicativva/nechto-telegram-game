package ru.nechto.model;

import java.util.*;

/**
 * Результат стартовой раздачи колоды: карты на руках каждого игрока и сформированная общая колода добора.
 */
public class InitialDeal {

    private final Map<String, List<Card>> playerHands;
    private final List<List<Card>> hands;
    private final List<Card> drawPile;
    private final List<Card> discardPile;

    public InitialDeal(Map<String, List<Card>> playerHands, List<Card> drawPile) {
        Map<String, List<Card>> unmodifiableHands = new LinkedHashMap<>();
        for (Map.Entry<String, List<Card>> entry : playerHands.entrySet()) {
            unmodifiableHands.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.playerHands = Collections.unmodifiableMap(unmodifiableHands);
        this.hands = Collections.unmodifiableList(new ArrayList<>(unmodifiableHands.values()));
        this.drawPile = Collections.unmodifiableList(new ArrayList<>(drawPile));
        this.discardPile = Collections.emptyList();
    }

    public Map<String, List<Card>> playerHands() {
        return playerHands;
    }

    public Map<String, List<Card>> getPlayerHands() {
        return playerHands;
    }

    public List<List<Card>> hands() {
        return hands;
    }

    public List<List<Card>> getHands() {
        return hands;
    }

    public List<Card> getHand(String playerId) {
        return playerHands.get(playerId);
    }

    public List<Card> getHand(int playerIndex) {
        return hands.get(playerIndex);
    }

    public List<Card> drawPile() {
        return drawPile;
    }

    public List<Card> getDrawPile() {
        return drawPile;
    }

    public List<Card> getDrawDeck() {
        return drawPile;
    }

    public List<Card> discardPile() {
        return discardPile;
    }

    public List<Card> getDiscardPile() {
        return discardPile;
    }

    /**
     * Находит идентификатор игрока, которому досталась карта «Нечто».
     *
     * @return идентификатор игрока или {@code null}, если карта не найдена
     */
    public String findTheThingPlayerId() {
        for (Map.Entry<String, List<Card>> entry : playerHands.entrySet()) {
            for (Card card : entry.getValue()) {
                if (card.isTheThing()) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public String getTheThingPlayerId() {
        return findTheThingPlayerId();
    }
}
