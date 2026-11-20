package ru.nechto.model;

/**
 * Перечисление наименований всех карт реестра допустимости (Приложение А, Таблица А.3).
 */
public enum CardName {
    THE_THING("Нечто", CardType.EVENT),
    INFECTION("Заражение!", CardType.EVENT),
    FLAMETHROWER("Огнемёт", CardType.EVENT),
    ANALYSIS("Анализ", CardType.EVENT),
    AXE("Топор", CardType.EVENT),
    SUSPICION("Подозрение", CardType.EVENT),
    WHISKEY("Виски", CardType.EVENT),
    PERSEVERANCE("Упорство", CardType.EVENT),
    LOOK_AROUND("Гляди по сторонам", CardType.EVENT),
    CHANGE_PLACES("Меняемся местами!", CardType.EVENT),
    RUN_AWAY("Сматывай удочки!", CardType.EVENT),
    SEDUCTION("Соблазн", CardType.EVENT),
    FEAR("Страх", CardType.EVENT),
    IM_FINE_HERE("Мне и здесь неплохо", CardType.EVENT),
    NO_THANKS("Нет уж, спасибо!", CardType.EVENT),
    MISSED("Мимо!", CardType.EVENT),
    NO_BARBECUE("Никакого шашлыка!", CardType.EVENT),
    QUARANTINE("Карантин", CardType.EVENT),
    LOCKED_DOOR("Заколоченная дверь", CardType.EVENT),
    OLD_ROPES("Старые верёвки", CardType.PANIC),
    ONE_TWO("Раз, два…", CardType.PANIC),
    THREE_FOUR("…Три, четыре…", CardType.PANIC),
    IS_THIS_A_PARTY("И это вы называете вечеринкой?", CardType.PANIC),
    GET_OUT("Убирайся прочь!", CardType.PANIC),
    FORGETFULNESS("Забывчивость", CardType.PANIC),
    CHAIN_REACTION("Цепная реакция", CardType.PANIC),
    LETS_BE_FRIENDS("Давай дружить?", CardType.PANIC),
    BLIND_DATE("Свидание вслепую", CardType.PANIC),
    OOPS("Чупс!", CardType.PANIC),
    JUST_BETWEEN_US("Только между нами…", CardType.PANIC),
    CONFESSION_TIME("Время признаний", CardType.PANIC),

    // Транслитерационные и альтернативные псевдонимы для максимальной совместимости
    NECHTO("Нечто", CardType.EVENT),
    ZARAZHENIE("Заражение!", CardType.EVENT),
    OGNEMET("Огнемёт", CardType.EVENT),
    ANALIZ("Анализ", CardType.EVENT),
    TOPOR("Топор", CardType.EVENT),
    PODOZRENIE("Подозрение", CardType.EVENT),
    VISKI("Виски", CardType.EVENT),
    BISKI("Биски", CardType.EVENT),
    UPORSTVO("Упорство", CardType.EVENT),
    DETERMINATION("Упорство", CardType.EVENT),
    GLYADI_PO_STORONAM("Гляди по сторонам", CardType.EVENT),
    WATCH_YOUR_BACK("Гляди по сторонам", CardType.EVENT),
    MENYAEMSYA_MESTAMI("Меняемся местами!", CardType.EVENT),
    SMATYVAY_UDOCHKI("Сматывай удочки!", CardType.EVENT),
    SOBLAZN("Соблазн", CardType.EVENT),
    TEMPTATION("Соблазн", CardType.EVENT),
    STRAH("Страх", CardType.EVENT),
    MNE_I_ZDES_NEPLOHO("Мне и здесь неплохо", CardType.EVENT),
    NET_UZH_SPASIBO("Нет уж, спасибо!", CardType.EVENT),
    MIMO("Мимо!", CardType.EVENT),
    NIKAKOGO_SHASHLYKA("Никакого шашлыка!", CardType.EVENT),
    KARANTIN("Карантин", CardType.EVENT),
    ZAKOLOCHENNAYA_DVER("Заколоченная дверь", CardType.EVENT),
    STARYE_VEREVKI("Старые верёвки", CardType.PANIC),
    RAZ_DVA("Раз, два…", CardType.PANIC),
    TRI_CHETYRE("…Три, четыре…", CardType.PANIC),
    PARTY("И это вы называете вечеринкой?", CardType.PANIC),
    UBIRAYSYA_PROCH("Убирайся прочь!", CardType.PANIC),
    ZABYVCHIVOST("Забывчивость", CardType.PANIC),
    TSEPNAYA_REAKTSIYA("Цепная реакция", CardType.PANIC),
    DAVAY_DRUZHIT("Давай дружить?", CardType.PANIC),
    SVIDANIE_VSLEPUYU("Свидание вслепую", CardType.PANIC),
    CHUPS("Чупс!", CardType.PANIC),
    TOLKO_MEZHDU_NAMI("Только между нами…", CardType.PANIC),
    BETWEEN_US("Только между нами…", CardType.PANIC),
    VREMYA_PRIZNANIY("Время признаний", CardType.PANIC);

    private final String russianName;
    private final CardType defaultType;

    CardName(String russianName, CardType defaultType) {
        this.russianName = russianName;
        this.defaultType = defaultType;
    }

    public String getRussianName() {
        return russianName;
    }

    public CardType getDefaultType() {
        return defaultType;
    }

    public boolean isTheThing() {
        return this == THE_THING || this == NECHTO;
    }

    public boolean isInfection() {
        return this == INFECTION || this == ZARAZHENIE;
    }

    public boolean isPanic() {
        return defaultType == CardType.PANIC;
    }

    public boolean isEvent() {
        return defaultType == CardType.EVENT;
    }

    /**
     * Поиск элемента перечисления по русскому наименованию карты из реестра.
     *
     * @param rawName наименование карты на русском языке
     * @return соответствующий {@link CardName}
     */
    public static CardName fromRussian(String rawName) {
        if (rawName == null) {
            return null;
        }
        String normalized = rawName.trim();
        if ("Биски".equalsIgnoreCase(normalized) || "Виски".equalsIgnoreCase(normalized)) {
            return WHISKEY;
        }
        for (CardName cn : values()) {
            if (cn.russianName.equalsIgnoreCase(normalized)) {
                return cn;
            }
        }
        throw new IllegalArgumentException("Неизвестное имя карты: " + rawName);
    }
}
