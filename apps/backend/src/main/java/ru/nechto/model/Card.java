package ru.nechto.model;

import java.util.Objects;

/**
 * Доменная модель игровой карты из реестра допустимости Приложения А (Таблица А.3).
 */
public class Card {

    private final int id;
    private final CardName name;
    private final CardType type;
    private final int minPlayers;
    private final String title;
    private final String registryType;

    public Card(int id, CardName name, CardType type, int minPlayers, String title, String registryType) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "CardName must not be null");
        this.type = Objects.requireNonNull(type, "CardType must not be null");
        this.minPlayers = minPlayers;
        this.title = title != null ? title : name.getRussianName();
        this.registryType = registryType != null ? registryType : (type == CardType.PANIC ? "Паника" : "Событие");
    }

    public Card(int id, CardName name, CardType type, int minPlayers, String title) {
        this(id, name, type, minPlayers, title, null);
    }

    public Card(int id, CardName name, CardType type, int minPlayers) {
        this(id, name, type, minPlayers, name.getRussianName(), null);
    }

    public int id() {
        return id;
    }

    public int getId() {
        return id;
    }

    public CardName name() {
        return name;
    }

    public CardName getName() {
        return name;
    }

    public CardType type() {
        return type;
    }

    public CardType getType() {
        return type;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public String title() {
        return title;
    }

    public String getTitle() {
        return title;
    }

    public String getRussianName() {
        return title;
    }

    public String registryType() {
        return registryType;
    }

    public String getRegistryType() {
        return registryType;
    }

    /**
     * Проверяет, является ли карта картой «Нечто» (карта №1).
     *
     * @return {@code true}, если это карта «Нечто»
     */
    public boolean isTheThing() {
        return id == 1 || name.isTheThing() || "Нечто".equalsIgnoreCase(registryType);
    }

    /**
     * Проверяет, является ли карта картой заражения («Заражение!»).
     *
     * @return {@code true}, если это карта заражения
     */
    public boolean isInfection() {
        return name.isInfection() || "Заражение".equalsIgnoreCase(registryType);
    }

    /**
     * Проверяет, является ли карта картой паники.
     *
     * @return {@code true}, если тип карты — {@link CardType#PANIC}
     */
    public boolean isPanic() {
        return type == CardType.PANIC || "Паника".equalsIgnoreCase(registryType);
    }

    /**
     * Проверяет, является ли карта картой события.
     *
     * @return {@code true}, если тип карты — {@link CardType#EVENT}
     */
    public boolean isEvent() {
        return type == CardType.EVENT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return id == card.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Card{" +
                "id=" + id +
                ", name=" + name +
                ", type=" + type +
                ", minPlayers=" + minPlayers +
                ", title='" + title + '\'' +
                '}';
    }
}
