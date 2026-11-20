package ru.nechto.service;

import org.springframework.stereotype.Service;
import ru.nechto.model.Card;
import ru.nechto.model.CardName;
import ru.nechto.model.CardType;
import ru.nechto.model.InitialDeal;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис генерации колоды карт в строгом соответствии с реестром допустимости
 * Приложения А (Таблица А.3) и логики стартовой раздачи на руки игрокам.
 */
@Service
public class DeckFactory {

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 12;
    public static final int CARDS_PER_PLAYER = 4;
    public static final int TOTAL_REGISTRY_CARDS = 108;

    private static final List<Card> REGISTRY_CARDS = Collections.unmodifiableList(initRegistryCards());

    /**
     * Возвращает полный реестр всех 108 карт (Приложение А, Таблица А.3).
     *
     * @return неизменяемый список всех 108 карт
     */
    public List<Card> getAllCards() {
        return REGISTRY_CARDS;
    }

    /**
     * Выборка допустимых карт из реестра по порогу минимального количества игроков ($N$).
     *
     * @param playerCount количество участников ($4 \le N \le 12$)
     * @return список карт, для которых {@code minPlayers <= playerCount}
     * @throws IllegalArgumentException если количество игроков вне диапазона 4..12
     */
    public List<Card> getCardsForPlayers(int playerCount) {
        validatePlayerCount(playerCount);
        return REGISTRY_CARDS.stream()
                .filter(card -> card.minPlayers() <= playerCount)
                .collect(Collectors.toList());
    }

    /**
     * Синоним метода {@link #getCardsForPlayers(int)}.
     *
     * @param playerCount количество участников ($4 \le N \le 12$)
     * @return список карт, для которых {@code minPlayers <= playerCount}
     */
    public List<Card> getEligibleCards(int playerCount) {
        return getCardsForPlayers(playerCount);
    }

    /**
     * Выполняет сборку стартового набора, перемешивание и стартовую раздачу
     * ровно по 4 карты на руку каждому участнику, а также формирование колоды добора.
     *
     * @param playerCount количество участников ($4 \le N \le 12$)
     * @return объект {@link InitialDeal} с картами на руках и колодой добора
     */
    public InitialDeal deal(int playerCount) {
        return deal(playerCount, new Random());
    }

    /**
     * Выполняет стартовую раздачу для заданного количества игроков с возможностью
     * передачи детерминированного генератора случайных чисел (для воспроизводимости в тестах).
     *
     * @param playerCount количество участников ($4 \le N \le 12$)
     * @param random      генератор псевдослучайных чисел
     * @return объект {@link InitialDeal}
     */
    public InitialDeal deal(int playerCount, Random random) {
        validatePlayerCount(playerCount);
        List<String> playerIds = new ArrayList<>(playerCount);
        for (int i = 1; i <= playerCount; i++) {
            playerIds.add("player_" + i);
        }
        return deal(playerIds, random);
    }

    /**
     * Выполняет стартовую раздачу для конкретного списка идентификаторов игроков.
     *
     * @param playerIds список идентификаторов игроков (длина от 4 до 12)
     * @return объект {@link InitialDeal}
     */
    public InitialDeal deal(List<String> playerIds) {
        return deal(playerIds, new Random());
    }

    /**
     * Выполняет стартовую раздачу для списка игроков с переданным генератором случайных чисел.
     *
     * <p><b>Алгоритм формирования колоды (ТЗ 4.2.2, Приложение А):</b></p>
     * <ol>
     *   <li>Отобрать допустимые карты для текущего $N$ (порог {@code minPlayers <= N}).</li>
     *   <li>Выделить ровно 1 карту «Нечто» (карта №1).</li>
     *   <li>Выделить карты паники и заражения (они исключаются из стартовой раздачи).</li>
     *   <li>Сформировать стартовый набор: карта «Нечто» + $(4 \times N - 1)$ случайных карт событий.</li>
     *   <li>Перемешать стартовый набор и раздать ровно по 4 карты каждому игроку.</li>
     *   <li>Сформировать общую колоду добора из всех оставшихся карт (включая все «Заражения» и «Панику») и перемешать её.</li>
     * </ol>
     *
     * @param playerIds список идентификаторов игроков
     * @param random    генератор случайных чисел
     * @return объект {@link InitialDeal}
     */
    public InitialDeal deal(List<String> playerIds, Random random) {
        Objects.requireNonNull(playerIds, "playerIds must not be null");
        Objects.requireNonNull(random, "random must not be null");
        int n = playerIds.size();
        validatePlayerCount(n);

        List<Card> eligible = getCardsForPlayers(n);

        Card theThing = eligible.stream()
                .filter(Card::isTheThing)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Карта «Нечто» отсутствует в реестре"));

        List<Card> regularEvents = eligible.stream()
                .filter(c -> c.type() == CardType.EVENT && !c.isInfection() && !c.isTheThing())
                .collect(Collectors.toList());

        List<Card> panicAndInfection = eligible.stream()
                .filter(c -> c.isPanic() || c.isInfection())
                .collect(Collectors.toList());

        int requiredRegularCards = 4 * n - 1;
        if (regularEvents.size() < requiredRegularCards) {
            throw new IllegalStateException("Недостаточно карт событий для стартовой раздачи: требуется "
                    + requiredRegularCards + ", доступно " + regularEvents.size());
        }

        Collections.shuffle(regularEvents, random);

        List<Card> selectedForStart = new ArrayList<>(regularEvents.subList(0, requiredRegularCards));
        List<Card> remainingEvents = new ArrayList<>(regularEvents.subList(requiredRegularCards, regularEvents.size()));

        List<Card> startPile = new ArrayList<>(4 * n);
        startPile.add(theThing);
        startPile.addAll(selectedForStart);
        Collections.shuffle(startPile, random);

        Map<String, List<Card>> playerHands = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String pid = playerIds.get(i);
            List<Card> hand = new ArrayList<>(startPile.subList(i * CARDS_PER_PLAYER, (i + 1) * CARDS_PER_PLAYER));
            playerHands.put(pid, hand);
        }

        List<Card> drawDeck = new ArrayList<>(panicAndInfection.size() + remainingEvents.size());
        drawDeck.addAll(panicAndInfection);
        drawDeck.addAll(remainingEvents);
        Collections.shuffle(drawDeck, random);

        return new InitialDeal(playerHands, drawDeck);
    }

    private void validatePlayerCount(int playerCount) {
        if (playerCount < MIN_PLAYERS) {
            throw new IllegalArgumentException("Минимальное количество игроков для формирования колоды — "
                    + MIN_PLAYERS + " (передано: " + playerCount + ")");
        }
        if (playerCount > MAX_PLAYERS) {
            throw new IllegalArgumentException("Максимальное количество игроков для формирования колоды — "
                    + MAX_PLAYERS + " (передано: " + playerCount + ")");
        }
    }

    private static List<Card> initRegistryCards() {
        List<Card> list = new ArrayList<>(TOTAL_REGISTRY_CARDS);

        // 1: Нечто (Нечто, —)
        list.add(new Card(1, CardName.THE_THING, CardType.EVENT, 4, "Нечто", "Нечто"));

        // 2..21: Заражение! (Заражение)
        list.add(new Card(2, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(3, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(4, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(5, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(6, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(7, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(8, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(9, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение"));
        list.add(new Card(10, CardName.INFECTION, CardType.EVENT, 6, "Заражение!", "Заражение"));
        list.add(new Card(11, CardName.INFECTION, CardType.EVENT, 6, "Заражение!", "Заражение"));
        list.add(new Card(12, CardName.INFECTION, CardType.EVENT, 7, "Заражение!", "Заражение"));
        list.add(new Card(13, CardName.INFECTION, CardType.EVENT, 7, "Заражение!", "Заражение"));
        list.add(new Card(14, CardName.INFECTION, CardType.EVENT, 8, "Заражение!", "Заражение"));
        list.add(new Card(15, CardName.INFECTION, CardType.EVENT, 9, "Заражение!", "Заражение"));
        list.add(new Card(16, CardName.INFECTION, CardType.EVENT, 9, "Заражение!", "Заражение"));
        list.add(new Card(17, CardName.INFECTION, CardType.EVENT, 10, "Заражение!", "Заражение"));
        list.add(new Card(18, CardName.INFECTION, CardType.EVENT, 10, "Заражение!", "Заражение"));
        list.add(new Card(19, CardName.INFECTION, CardType.EVENT, 11, "Заражение!", "Заражение"));
        list.add(new Card(20, CardName.INFECTION, CardType.EVENT, 11, "Заражение!", "Заражение"));
        list.add(new Card(21, CardName.INFECTION, CardType.EVENT, 11, "Заражение!", "Заражение"));

        // 22..88: События (Событие)
        list.add(new Card(22, CardName.FLAMETHROWER, CardType.EVENT, 4, "Огнемёт", "Событие"));
        list.add(new Card(23, CardName.FLAMETHROWER, CardType.EVENT, 6, "Огнемёт", "Событие"));
        list.add(new Card(24, CardName.FLAMETHROWER, CardType.EVENT, 9, "Огнемёт", "Событие"));
        list.add(new Card(25, CardName.FLAMETHROWER, CardType.EVENT, 9, "Огнемёт", "Событие"));
        list.add(new Card(26, CardName.FLAMETHROWER, CardType.EVENT, 11, "Огнемёт", "Событие"));

        list.add(new Card(27, CardName.ANALYSIS, CardType.EVENT, 5, "Анализ", "Событие"));
        list.add(new Card(28, CardName.ANALYSIS, CardType.EVENT, 6, "Анализ", "Событие"));
        list.add(new Card(29, CardName.ANALYSIS, CardType.EVENT, 9, "Анализ", "Событие"));

        list.add(new Card(30, CardName.AXE, CardType.EVENT, 4, "Топор", "Событие"));
        list.add(new Card(31, CardName.AXE, CardType.EVENT, 9, "Топор", "Событие"));

        list.add(new Card(32, CardName.SUSPICION, CardType.EVENT, 4, "Подозрение", "Событие"));
        list.add(new Card(33, CardName.SUSPICION, CardType.EVENT, 4, "Подозрение", "Событие"));
        list.add(new Card(34, CardName.SUSPICION, CardType.EVENT, 4, "Подозрение", "Событие"));
        list.add(new Card(35, CardName.SUSPICION, CardType.EVENT, 4, "Подозрение", "Событие"));
        list.add(new Card(36, CardName.SUSPICION, CardType.EVENT, 7, "Подозрение", "Событие"));
        list.add(new Card(37, CardName.SUSPICION, CardType.EVENT, 8, "Подозрение", "Событие"));
        list.add(new Card(38, CardName.SUSPICION, CardType.EVENT, 9, "Подозрение", "Событие"));
        list.add(new Card(39, CardName.SUSPICION, CardType.EVENT, 10, "Подозрение", "Событие"));

        list.add(new Card(40, CardName.WHISKEY, CardType.EVENT, 4, "Биски", "Событие"));
        list.add(new Card(41, CardName.WHISKEY, CardType.EVENT, 6, "Виски", "Событие"));
        list.add(new Card(42, CardName.WHISKEY, CardType.EVENT, 10, "Виски", "Событие"));

        list.add(new Card(43, CardName.PERSEVERANCE, CardType.EVENT, 4, "Упорство", "Событие"));
        list.add(new Card(44, CardName.PERSEVERANCE, CardType.EVENT, 4, "Упорство", "Событие"));
        list.add(new Card(45, CardName.PERSEVERANCE, CardType.EVENT, 6, "Упорство", "Событие"));
        list.add(new Card(46, CardName.PERSEVERANCE, CardType.EVENT, 9, "Упорство", "Событие"));
        list.add(new Card(47, CardName.PERSEVERANCE, CardType.EVENT, 10, "Упорство", "Событие"));

        list.add(new Card(48, CardName.LOOK_AROUND, CardType.EVENT, 4, "Гляди по сторонам", "Событие"));
        list.add(new Card(49, CardName.LOOK_AROUND, CardType.EVENT, 9, "Гляди по сторонам", "Событие"));

        list.add(new Card(50, CardName.CHANGE_PLACES, CardType.EVENT, 4, "Меняемся местами!", "Событие"));
        list.add(new Card(51, CardName.CHANGE_PLACES, CardType.EVENT, 4, "Меняемся местами!", "Событие"));
        list.add(new Card(52, CardName.CHANGE_PLACES, CardType.EVENT, 7, "Меняемся местами!", "Событие"));
        list.add(new Card(53, CardName.CHANGE_PLACES, CardType.EVENT, 9, "Меняемся местами!", "Событие"));
        list.add(new Card(54, CardName.CHANGE_PLACES, CardType.EVENT, 11, "Меняемся местами!", "Событие"));

        list.add(new Card(55, CardName.RUN_AWAY, CardType.EVENT, 4, "Сматывай удочки!", "Событие"));
        list.add(new Card(56, CardName.RUN_AWAY, CardType.EVENT, 4, "Сматывай удочки!", "Событие"));
        list.add(new Card(57, CardName.RUN_AWAY, CardType.EVENT, 7, "Сматывай удочки!", "Событие"));
        list.add(new Card(58, CardName.RUN_AWAY, CardType.EVENT, 9, "Сматывай удочки!", "Событие"));
        list.add(new Card(59, CardName.RUN_AWAY, CardType.EVENT, 11, "Сматывай удочки!", "Событие"));

        list.add(new Card(60, CardName.SEDUCTION, CardType.EVENT, 4, "Соблазн", "Событие"));
        list.add(new Card(61, CardName.SEDUCTION, CardType.EVENT, 4, "Соблазн", "Событие"));
        list.add(new Card(62, CardName.SEDUCTION, CardType.EVENT, 6, "Соблазн", "Событие"));
        list.add(new Card(63, CardName.SEDUCTION, CardType.EVENT, 7, "Соблазн", "Событие"));
        list.add(new Card(64, CardName.SEDUCTION, CardType.EVENT, 8, "Соблазн", "Событие"));
        list.add(new Card(65, CardName.SEDUCTION, CardType.EVENT, 10, "Соблазн", "Событие"));
        list.add(new Card(66, CardName.SEDUCTION, CardType.EVENT, 11, "Соблазн", "Событие"));

        list.add(new Card(67, CardName.FEAR, CardType.EVENT, 5, "Страх", "Событие"));
        list.add(new Card(68, CardName.FEAR, CardType.EVENT, 6, "Страх", "Событие"));
        list.add(new Card(69, CardName.FEAR, CardType.EVENT, 8, "Страх", "Событие"));
        list.add(new Card(70, CardName.FEAR, CardType.EVENT, 11, "Страх", "Событие"));

        list.add(new Card(71, CardName.IM_FINE_HERE, CardType.EVENT, 4, "Мне и здесь неплохо", "Событие"));
        list.add(new Card(72, CardName.IM_FINE_HERE, CardType.EVENT, 6, "Мне и здесь неплохо", "Событие"));
        list.add(new Card(73, CardName.IM_FINE_HERE, CardType.EVENT, 11, "Мне и здесь неплохо", "Событие"));

        list.add(new Card(74, CardName.NO_THANKS, CardType.EVENT, 4, "Нет уж, спасибо!", "Событие"));
        list.add(new Card(75, CardName.NO_THANKS, CardType.EVENT, 6, "Нет уж, спасибо!", "Событие"));
        list.add(new Card(76, CardName.NO_THANKS, CardType.EVENT, 8, "Нет уж, спасибо!", "Событие"));
        list.add(new Card(77, CardName.NO_THANKS, CardType.EVENT, 11, "Нет уж, спасибо!", "Событие"));

        list.add(new Card(78, CardName.MISSED, CardType.EVENT, 4, "Мимо!", "Событие"));
        list.add(new Card(79, CardName.MISSED, CardType.EVENT, 6, "Мимо!", "Событие"));
        list.add(new Card(80, CardName.MISSED, CardType.EVENT, 11, "Мимо!", "Событие"));

        list.add(new Card(81, CardName.NO_BARBECUE, CardType.EVENT, 4, "Никакого шашлыка!", "Событие"));
        list.add(new Card(82, CardName.NO_BARBECUE, CardType.EVENT, 6, "Никакого шашлыка!", "Событие"));
        list.add(new Card(83, CardName.NO_BARBECUE, CardType.EVENT, 11, "Никакого шашлыка!", "Событие"));

        list.add(new Card(84, CardName.QUARANTINE, CardType.EVENT, 5, "Карантин", "Событие"));
        list.add(new Card(85, CardName.QUARANTINE, CardType.EVENT, 9, "Карантин", "Событие"));

        list.add(new Card(86, CardName.LOCKED_DOOR, CardType.EVENT, 4, "Заколоченная дверь", "Событие"));
        list.add(new Card(87, CardName.LOCKED_DOOR, CardType.EVENT, 7, "Заколоченная дверь", "Событие"));
        list.add(new Card(88, CardName.LOCKED_DOOR, CardType.EVENT, 11, "Заколоченная дверь", "Событие"));

        // 89..108: Паника (Паника)
        list.add(new Card(89, CardName.OLD_ROPES, CardType.PANIC, 6, "Старые верёвки", "Паника"));
        list.add(new Card(90, CardName.OLD_ROPES, CardType.PANIC, 9, "Старые верёвки", "Паника"));

        list.add(new Card(91, CardName.ONE_TWO, CardType.PANIC, 5, "Раз, два…", "Паника"));
        list.add(new Card(92, CardName.ONE_TWO, CardType.PANIC, 9, "Раз, два…", "Паника"));

        list.add(new Card(93, CardName.THREE_FOUR, CardType.PANIC, 4, "…Три, четыре…", "Паника"));
        list.add(new Card(94, CardName.THREE_FOUR, CardType.PANIC, 9, "…Три, четыре…", "Паника"));

        list.add(new Card(95, CardName.IS_THIS_A_PARTY, CardType.PANIC, 5, "И это вы называете вечеринкой?", "Паника"));
        list.add(new Card(96, CardName.IS_THIS_A_PARTY, CardType.PANIC, 9, "И это вы называете вечеринкой?", "Паника"));

        list.add(new Card(97, CardName.GET_OUT, CardType.PANIC, 5, "Убирайся прочь!", "Паника"));

        list.add(new Card(98, CardName.FORGETFULNESS, CardType.PANIC, 4, "Забывчивость", "Паника"));

        list.add(new Card(99, CardName.CHAIN_REACTION, CardType.PANIC, 4, "Цепная реакция", "Паника"));
        list.add(new Card(100, CardName.CHAIN_REACTION, CardType.PANIC, 9, "Цепная реакция", "Паника"));

        list.add(new Card(101, CardName.LETS_BE_FRIENDS, CardType.PANIC, 7, "Давай дружить?", "Паника"));
        list.add(new Card(102, CardName.LETS_BE_FRIENDS, CardType.PANIC, 9, "Давай дружить?", "Паника"));

        list.add(new Card(103, CardName.BLIND_DATE, CardType.PANIC, 4, "Свидание вслепую", "Паника"));
        list.add(new Card(104, CardName.BLIND_DATE, CardType.PANIC, 9, "Свидание вслепую", "Паника"));

        list.add(new Card(105, CardName.OOPS, CardType.PANIC, 10, "Чупс!", "Паника"));

        list.add(new Card(106, CardName.JUST_BETWEEN_US, CardType.PANIC, 7, "Только между нами…", "Паника"));
        list.add(new Card(107, CardName.JUST_BETWEEN_US, CardType.PANIC, 9, "Только между нами…", "Паника"));

        list.add(new Card(108, CardName.CONFESSION_TIME, CardType.PANIC, 8, "Время признаний", "Паника"));

        return list;
    }
}
