package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nechto.model.Card;
import ru.nechto.model.CardName;
import ru.nechto.model.CardType;
import ru.nechto.model.InitialDeal;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Модульные тесты сервиса генерации колоды карт {@link DeckFactory}
 * в строгом соответствии с реестром допустимости Приложения А (Таблица А.3)
 * и логики стартовой раздачи на руки игрокам.
 */
class DeckFactoryTest {

    private DeckFactory deckFactory;

    @BeforeEach
    void setUp() {
        deckFactory = new DeckFactory();
    }

    /**
     * Проверяет полноту и корректность реестра всех 108 карт согласно Таблице А.3 Приложения А:
     * уникальность ID от 1 до 108, ровно одна карта «Нечто», 20 карт заражения, 67 карт событий и 20 карт паники.
     *
     * <p><b>Предусловия (Given):</b> Инициализирован сервис {@link DeckFactory}.</p>
     *
     * <p><b>Действие (When):</b> Запрашивается полный реестр карт через {@code getAllCards()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Количество карт в реестре равно ровно 108.</li>
     *   <li>Все идентификаторы карт уникальны и находятся в диапазоне 1..108.</li>
     *   <li>Ровно 1 карта «Нечто» с идентификатором №1.</li>
     *   <li>Ровно 20 карт заражения («Заражение!»).</li>
     *   <li>Ровно 67 карт событий (Event).</li>
     *   <li>Ровно 20 карт паники (Panic).</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Реестр карт содержит ровно 108 карт согласно Таблице А.3")
    void registryContainsExactly108Cards() {
        List<Card> allCards = deckFactory.getAllCards();

        assertThat(allCards).hasSize(108);

        Set<Integer> uniqueIds = new HashSet<>();
        for (Card card : allCards) {
            assertThat(card.id()).isBetween(1, 108);
            uniqueIds.add(card.id());
        }
        assertThat(uniqueIds).hasSize(108);

        Card card1 = allCards.get(0);
        assertThat(card1.id()).isEqualTo(1);
        assertThat(card1.isTheThing()).isTrue();
        assertThat(card1.name()).isEqualTo(CardName.THE_THING);

        long infectionCount = allCards.stream().filter(Card::isInfection).count();
        assertThat(infectionCount).isEqualTo(20);

        long panicCount = allCards.stream().filter(Card::isPanic).count();
        assertThat(panicCount).isEqualTo(20);

        long regularEventCount = allCards.stream()
                .filter(c -> c.type() == CardType.EVENT && !c.isInfection() && !c.isTheThing())
                .count();
        assertThat(regularEventCount).isEqualTo(67);
    }

    /**
     * Проверяет выборку карт для 4 участников по порогу минимального количества игроков:
     * для $N = 4$ в выборку должны войти ровно 34 карты.
     *
     * <p><b>Предусловия (Given):</b> Число участников $N = 4$.</p>
     *
     * <p><b>Действие (When):</b> Запрашивается список допустимых карт через {@code getCardsForPlayers(4)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Размер пула допустимых карт равен ровно 34.</li>
     *   <li>Присутствует карта «Нечто» (ID=1).</li>
     *   <li>Присутствуют 8 карт заражения.</li>
     *   <li>Присутствуют 4 карты паники.</li>
     *   <li>Присутствует 21 карта регулярных событий.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Выборка карт для N=4 участников возвращает ровно 34 карты")
    void getCardsForPlayers_When4Players_Returns34Cards() {
        List<Card> cards = deckFactory.getCardsForPlayers(4);

        assertThat(cards).hasSize(34);
        assertThat(cards.stream().filter(Card::isTheThing).count()).isEqualTo(1);
        assertThat(cards.stream().filter(Card::isInfection).count()).isEqualTo(8);
        assertThat(cards.stream().filter(Card::isPanic).count()).isEqualTo(4);
        assertThat(cards.stream().filter(c -> c.isEvent() && !c.isInfection() && !c.isTheThing()).count()).isEqualTo(21);
    }

    /**
     * Проверяет выборку карт для 6 участников по порогу минимального количества игроков:
     * для $N = 6$ в выборку должны войти ровно 53 карты.
     *
     * <p><b>Предусловия (Given):</b> Число участников $N = 6$.</p>
     *
     * <p><b>Действие (When):</b> Запрашивается список карт через {@code getCardsForPlayers(6)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Размер пула допустимых карт равен ровно 53.</li>
     *   <li>Присутствует ровно 1 карта «Нечто», 10 карт заражения, 8 карт паники и 34 карты событий.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Выборка карт для N=6 участников возвращает ровно 53 карты")
    void getCardsForPlayers_When6Players_Returns53Cards() {
        List<Card> cards = deckFactory.getCardsForPlayers(6);

        assertThat(cards).hasSize(53);
        assertThat(cards.stream().filter(Card::isTheThing).count()).isEqualTo(1);
        assertThat(cards.stream().filter(Card::isInfection).count()).isEqualTo(10);
        assertThat(cards.stream().filter(Card::isPanic).count()).isEqualTo(8);
        assertThat(cards.stream().filter(c -> c.isEvent() && !c.isInfection() && !c.isTheThing()).count()).isEqualTo(34);
    }

    /**
     * Проверяет выборку карт для 8 участников по порогу минимального количества игроков:
     * для $N = 8$ в выборку должны войти ровно 68 карт.
     *
     * <p><b>Предусловия (Given):</b> Число участников $N = 8$.</p>
     *
     * <p><b>Действие (When):</b> Запрашивается список карт через {@code getCardsForPlayers(8)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Размер пула допустимых карт равен ровно 68.</li>
     *   <li>Присутствует ровно 1 карта «Нечто», 13 карт заражения, 11 карт паники и 43 карты событий.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Выборка карт для N=8 участников возвращает ровно 68 карт")
    void getCardsForPlayers_When8Players_Returns68Cards() {
        List<Card> cards = deckFactory.getCardsForPlayers(8);

        assertThat(cards).hasSize(68);
        assertThat(cards.stream().filter(Card::isTheThing).count()).isEqualTo(1);
        assertThat(cards.stream().filter(Card::isInfection).count()).isEqualTo(13);
        assertThat(cards.stream().filter(Card::isPanic).count()).isEqualTo(11);
        assertThat(cards.stream().filter(c -> c.isEvent() && !c.isInfection() && !c.isTheThing()).count()).isEqualTo(43);
    }

    /**
     * Проверяет выборку карт для максимального количества участников ($N = 12$):
     * в выборку должны войти все 108 карт реестра.
     *
     * <p><b>Предусловия (Given):</b> Число участников $N = 12$.</p>
     *
     * <p><b>Действие (When):</b> Запрашивается список карт через {@code getCardsForPlayers(12)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Размер пула допустимых карт равен ровно 108.</li>
     *   <li>Присутствует ровно 1 карта «Нечто», 20 карт заражения, 20 карт паники и 67 карт событий.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Выборка карт для N=12 участников возвращает все 108 карт")
    void getCardsForPlayers_When12Players_Returns108Cards() {
        List<Card> cards = deckFactory.getCardsForPlayers(12);

        assertThat(cards).hasSize(108);
        assertThat(cards.stream().filter(Card::isTheThing).count()).isEqualTo(1);
        assertThat(cards.stream().filter(Card::isInfection).count()).isEqualTo(20);
        assertThat(cards.stream().filter(Card::isPanic).count()).isEqualTo(20);
        assertThat(cards.stream().filter(c -> c.isEvent() && !c.isInfection() && !c.isTheThing()).count()).isEqualTo(67);
    }

    /**
     * Проверяет выброс исключения при запросе карт для недопустимого числа игроков ($N < 4$ или $N > 12$).
     *
     * <p><b>Предусловия (Given):</b> Недопустимые значения количества игроков (3 и 13).</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code getCardsForPlayers}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link IllegalArgumentException}.</p>
     */
    @Test
    @DisplayName("Отказ с IllegalArgumentException при недопустимом количестве игроков")
    void getCardsForPlayers_InvalidPlayerCount_ThrowsException() {
        assertThatThrownBy(() -> deckFactory.getCardsForPlayers(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deckFactory.getCardsForPlayers(13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Проверяет стартовую раздачу для $N = 4$ участников:
     * ровно 4 карты на руку каждому игроку, ровно 1 карта «Нечто», отсутствие паники и заражения на руках,
     * а также корректный размер колоды добора (18 карт).
     *
     * <p><b>Предусловия (Given):</b> 4 участника партии.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code deal(4)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сформировано ровно 4 руки игроков.</li>
     *   <li>У каждого игрока на руке ровно 4 карты.</li>
     *   <li>Суммарно на руках находится $4 \times 4 = 16$ карт.</li>
     *   <li>Среди всех карт на руках ровно одна карта «Нечто» (карта №1).</li>
     *   <li>На руках игроков полностью отсутствуют карты паники и заражения.</li>
     *   <li>Колода добора содержит $34 - 16 = 18$ карт.</li>
     *   <li>Колода добора содержит все 8 доступных карт заражения и все 4 доступные карты паники.</li>
     *   <li>Суммарное количество карт (руки + колода добора) равно 34.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Стартовая раздача для N=4 участников: состав рук и колоды добора")
    void deal_When4Players_ValidatesHandsAndDrawDeck() {
        InitialDeal deal = deckFactory.deal(4);

        assertThat(deal.hands()).hasSize(4);
        for (List<Card> hand : deal.hands()) {
            assertThat(hand).hasSize(4);
            for (Card card : hand) {
                assertThat(card.isPanic()).isFalse();
                assertThat(card.isInfection()).isFalse();
            }
        }

        List<Card> allHandCards = deal.hands().stream().flatMap(List::stream).toList();
        assertThat(allHandCards).hasSize(16);

        long thingCount = allHandCards.stream().filter(Card::isTheThing).count();
        assertThat(thingCount).isEqualTo(1);
        assertThat(deal.findTheThingPlayerId()).isNotNull();

        assertThat(deal.drawPile()).hasSize(18);
        assertThat(deal.drawPile().stream().filter(Card::isInfection).count()).isEqualTo(8);
        assertThat(deal.drawPile().stream().filter(Card::isPanic).count()).isEqualTo(4);

        assertThat(allHandCards.size() + deal.drawPile().size()).isEqualTo(34);
    }

    /**
     * Проверяет стартовую раздачу для $N = 6$ участников:
     * ровно 4 карты на руку каждому игроку, ровно 1 карта «Нечто», отсутствие паники и заражения на руках,
     * а также размер колоды добора 29 карт.
     *
     * <p><b>Предусловия (Given):</b> 6 участников партии.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code deal(6)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сформировано 6 рук по 4 карты (всего 24 карты на руках).</li>
     *   <li>Ровно одна карта «Нечто» на всех участников.</li>
     *   <li>0 карт паники и 0 карт заражения на руках.</li>
     *   <li>Колода добора содержит $53 - 24 = 29$ карт.</li>
     *   <li>Колода добора содержит все 10 карт заражения и 8 карт паники.</li>
     *   <li>Сумма карт равна 53.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Стартовая раздача для N=6 участников: состав рук и колоды добора")
    void deal_When6Players_ValidatesHandsAndDrawDeck() {
        InitialDeal deal = deckFactory.deal(6);

        assertThat(deal.hands()).hasSize(6);
        for (List<Card> hand : deal.hands()) {
            assertThat(hand).hasSize(4);
            for (Card card : hand) {
                assertThat(card.isPanic()).isFalse();
                assertThat(card.isInfection()).isFalse();
            }
        }

        List<Card> allHandCards = deal.hands().stream().flatMap(List::stream).toList();
        assertThat(allHandCards).hasSize(24);
        assertThat(allHandCards.stream().filter(Card::isTheThing).count()).isEqualTo(1);

        assertThat(deal.drawPile()).hasSize(29);
        assertThat(deal.drawPile().stream().filter(Card::isInfection).count()).isEqualTo(10);
        assertThat(deal.drawPile().stream().filter(Card::isPanic).count()).isEqualTo(8);

        assertThat(allHandCards.size() + deal.drawPile().size()).isEqualTo(53);
    }

    /**
     * Проверяет стартовую раздачу для $N = 8$ участников:
     * ровно 4 карты на руку каждому игроку, ровно 1 карта «Нечто», отсутствие паники и заражения на руках,
     * а также размер колоды добора 36 карт.
     *
     * <p><b>Предусловия (Given):</b> 8 участников партии.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code deal(8)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сформировано 8 рук по 4 карты (всего 32 карты на руках).</li>
     *   <li>Ровно 1 карта «Нечто» на руках, 0 карт паники, 0 карт заражения.</li>
     *   <li>Колода добора содержит $68 - 32 = 36$ карт.</li>
     *   <li>Колода добора содержит все 13 карт заражения и 11 карт паники.</li>
     *   <li>Сумма карт равна 68.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Стартовая раздача для N=8 участников: состав рук и колоды добора")
    void deal_When8Players_ValidatesHandsAndDrawDeck() {
        InitialDeal deal = deckFactory.deal(8);

        assertThat(deal.hands()).hasSize(8);
        for (List<Card> hand : deal.hands()) {
            assertThat(hand).hasSize(4);
            for (Card card : hand) {
                assertThat(card.isPanic()).isFalse();
                assertThat(card.isInfection()).isFalse();
            }
        }

        List<Card> allHandCards = deal.hands().stream().flatMap(List::stream).toList();
        assertThat(allHandCards).hasSize(32);
        assertThat(allHandCards.stream().filter(Card::isTheThing).count()).isEqualTo(1);

        assertThat(deal.drawPile()).hasSize(36);
        assertThat(deal.drawPile().stream().filter(Card::isInfection).count()).isEqualTo(13);
        assertThat(deal.drawPile().stream().filter(Card::isPanic).count()).isEqualTo(11);

        assertThat(allHandCards.size() + deal.drawPile().size()).isEqualTo(68);
    }

    /**
     * Проверяет стартовую раздачу для максимального числа участников ($N = 12$):
     * ровно 4 карты на руку каждому игроку, ровно 1 карта «Нечто», отсутствие паники и заражения на руках,
     * а также размер колоды добора 60 карт.
     *
     * <p><b>Предусловия (Given):</b> 12 участников партии.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code deal(12)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сформировано 12 рук по 4 карты (всего 48 карт на руках).</li>
     *   <li>Ровно 1 карта «Нечто» на руках, 0 карт паники, 0 карт заражения.</li>
     *   <li>Колода добора содержит $108 - 48 = 60$ карт.</li>
     *   <li>Колода добора содержит все 20 карт заражения и 20 карт паники.</li>
     *   <li>Сумма карт равна 108 (весь реестр карт задействован).</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Стартовая раздача для N=12 участников: состав рук и колоды добора")
    void deal_When12Players_ValidatesHandsAndDrawDeck() {
        InitialDeal deal = deckFactory.deal(12);

        assertThat(deal.hands()).hasSize(12);
        for (List<Card> hand : deal.hands()) {
            assertThat(hand).hasSize(4);
            for (Card card : hand) {
                assertThat(card.isPanic()).isFalse();
                assertThat(card.isInfection()).isFalse();
            }
        }

        List<Card> allHandCards = deal.hands().stream().flatMap(List::stream).toList();
        assertThat(allHandCards).hasSize(48);
        assertThat(allHandCards.stream().filter(Card::isTheThing).count()).isEqualTo(1);

        assertThat(deal.drawPile()).hasSize(60);
        assertThat(deal.drawPile().stream().filter(Card::isInfection).count()).isEqualTo(20);
        assertThat(deal.drawPile().stream().filter(Card::isPanic).count()).isEqualTo(20);

        assertThat(allHandCards.size() + deal.drawPile().size()).isEqualTo(108);
    }

    /**
     * Параметризованный тест, проверяющий корректность раздачи для ВСЕХ граничных и промежуточных
     * значений числа участников $N \in [4..12]$.
     *
     * <p><b>Предусловия (Given):</b> Значение $N$ из диапазона от 4 до 12 включительно.</p>
     *
     * <p><b>Действие (When):</b> Выполняется {@code deal(n)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Количество рук равно $N$.</li>
     *   <li>В каждой руке ровно 4 карты.</li>
     *   <li>Суммарно на руках ровно 1 карта «Нечто».</li>
     *   <li>На руках полностью отсутствуют карты паники и карты заражения.</li>
     *   <li>Суммарное количество карт (руки + добор) в точности равно размеру выборки {@code getCardsForPlayers(n)}.</li>
     * </ul>
     * </p>
     */
    @ParameterizedTest(name = "N = {0} участников")
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11, 12})
    @DisplayName("Инварианты раздачи выполняются для всех допустимых N от 4 до 12")
    void deal_AllPlayerCountsFrom4To12_GuaranteesCorrectHandSizesAndThingCount(int n) {
        InitialDeal deal = deckFactory.deal(n);

        assertThat(deal.hands()).hasSize(n);
        int totalExpectedEligible = deckFactory.getCardsForPlayers(n).size();

        int handCardsCount = 0;
        int thingCount = 0;

        for (List<Card> hand : deal.hands()) {
            assertThat(hand).hasSize(4);
            handCardsCount += hand.size();
            for (Card card : hand) {
                assertThat(card.isPanic()).isFalse();
                assertThat(card.isInfection()).isFalse();
                if (card.isTheThing()) {
                    thingCount++;
                }
            }
        }

        assertThat(handCardsCount).isEqualTo(4 * n);
        assertThat(thingCount).isEqualTo(1);
        assertThat(deal.findTheThingPlayerId()).isNotNull();

        assertThat(handCardsCount + deal.drawPile().size()).isEqualTo(totalExpectedEligible);
    }

    /**
     * Проверяет распределение карт по конкретным идентификаторам игроков при вызове {@code deal(List<String>)}.
     *
     * <p><b>Предусловия (Given):</b> Список уникальных идентификаторов игроков {@code ["alice", "bob", "charlie", "david"]}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code deal(playerIds)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Мапа {@code playerHands} содержит ровно переданные 4 ключа.</li>
     *   <li>Каждый игрок получил ровно 4 карты.</li>
     *   <li>Ровно один из игроков получил карту «Нечто».</li>
     *   <li>Метод {@code findTheThingPlayerId()} возвращает корректный идентификатор игрока.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Раздача по списку ID игроков корректно связывает руки с идентификаторами")
    void deal_WithCustomPlayerIds_AssignsHandsCorrectly() {
        List<String> playerIds = List.of("alice", "bob", "charlie", "david");
        InitialDeal deal = deckFactory.deal(playerIds);

        assertThat(deal.playerHands()).containsOnlyKeys("alice", "bob", "charlie", "david");
        for (String pid : playerIds) {
            assertThat(deal.getHand(pid)).hasSize(4);
        }

        String thingPlayerId = deal.findTheThingPlayerId();
        assertThat(thingPlayerId).isIn("alice", "bob", "charlie", "david");
        assertThat(deal.getHand(thingPlayerId).stream().anyMatch(Card::isTheThing)).isTrue();
    }

    /**
     * Проверяет детерминированную раздачу при передаче генератора случайных чисел с фиксированным seed:
     * две раздачи с одинаковым seed дают абсолютно идентичный результат.
     *
     * <p><b>Предусловия (Given):</b> Два генератора {@link Random} с одинаковым seed = 42.</p>
     *
     * <p><b>Действие (When):</b> Выполняются две независимые раздачи {@code deal(4, random1)} и {@code deal(4, random2)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Карты в руках и колоде добора совпадают поэлементно.</p>
     */
    @Test
    @DisplayName("Детерминированный Random обеспечивает полную воспроизводимость раздачи")
    void deal_WithDeterministicRandom_ProducesReproducibleDeal() {
        InitialDeal deal1 = deckFactory.deal(4, new Random(42));
        InitialDeal deal2 = deckFactory.deal(4, new Random(42));

        assertThat(deal1.findTheThingPlayerId()).isEqualTo(deal2.findTheThingPlayerId());
        assertThat(deal1.hands()).isEqualTo(deal2.hands());
        assertThat(deal1.drawPile()).isEqualTo(deal2.drawPile());
    }

    /**
     * Проверяет отказ в раздаче при недопустимом количестве игроков ($N < 4$ или $N > 12$).
     *
     * <p><b>Предусловия (Given):</b> Списки игроков размером 2 и 15.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code deal}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link IllegalArgumentException}.</p>
     */
    @Test
    @DisplayName("Отказ с IllegalArgumentException при раздаче на недопустимое число игроков")
    void deal_InvalidPlayerCount_ThrowsException() {
        assertThatThrownBy(() -> deckFactory.deal(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deckFactory.deal(15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deckFactory.deal(List.of("p1", "p2", "p3")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
