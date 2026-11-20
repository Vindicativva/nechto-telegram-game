package ru.nechto.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Модульные тесты доменных моделей карт: {@link Card}, {@link CardType}, {@link CardName}.
 */
class CardModelTest {

    /**
     * Проверяет корректность инициализации и доступности свойств карты:
     * id, name, type, minPlayers, title, флаги типа (isTheThing, isInfection, isPanic, isEvent).
     *
     * <p><b>Предусловия (Given):</b> Создаются экземпляры карты «Нечто», «Заражение!», «Огнемёт» и «Цепная реакция».</p>
     *
     * <p><b>Действие (When):</b> Вызываются геттеры и предикаты состояния.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Для карты №1: {@code isTheThing() == true}, {@code isInfection() == false}, {@code isPanic() == false}, {@code isEvent() == true}.</li>
     *   <li>Для карты заражения: {@code isInfection() == true}, {@code isTheThing() == false}.</li>
     *   <li>Для карты события: {@code isEvent() == true}, {@code isPanic() == false}.</li>
     *   <li>Для карты паники: {@code isPanic() == true}, {@code isEvent() == false}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Проверка свойств и флагов типов карт (Нечто, Заражение, Событие, Паника)")
    void card_PropertiesAndTypeFlags_ReturnExpectedValues() {
        Card thing = new Card(1, CardName.THE_THING, CardType.EVENT, 4, "Нечто", "Нечто");
        assertThat(thing.id()).isEqualTo(1);
        assertThat(thing.getId()).isEqualTo(1);
        assertThat(thing.name()).isEqualTo(CardName.THE_THING);
        assertThat(thing.getName()).isEqualTo(CardName.THE_THING);
        assertThat(thing.type()).isEqualTo(CardType.EVENT);
        assertThat(thing.getType()).isEqualTo(CardType.EVENT);
        assertThat(thing.minPlayers()).isEqualTo(4);
        assertThat(thing.getMinPlayers()).isEqualTo(4);
        assertThat(thing.title()).isEqualTo("Нечто");
        assertThat(thing.getTitle()).isEqualTo("Нечто");
        assertThat(thing.getRussianName()).isEqualTo("Нечто");
        assertThat(thing.registryType()).isEqualTo("Нечто");
        assertThat(thing.isTheThing()).isTrue();
        assertThat(thing.isInfection()).isFalse();
        assertThat(thing.isPanic()).isFalse();
        assertThat(thing.isEvent()).isTrue();

        Card infection = new Card(2, CardName.INFECTION, CardType.EVENT, 4, "Заражение!", "Заражение");
        assertThat(infection.isInfection()).isTrue();
        assertThat(infection.isTheThing()).isFalse();
        assertThat(infection.isPanic()).isFalse();
        assertThat(infection.isEvent()).isTrue();

        Card flamethrower = new Card(22, CardName.FLAMETHROWER, CardType.EVENT, 4, "Огнемёт", "Событие");
        assertThat(flamethrower.isEvent()).isTrue();
        assertThat(flamethrower.isPanic()).isFalse();
        assertThat(flamethrower.isTheThing()).isFalse();
        assertThat(flamethrower.isInfection()).isFalse();

        Card panic = new Card(99, CardName.CHAIN_REACTION, CardType.PANIC, 4, "Цепная реакция", "Паника");
        assertThat(panic.isPanic()).isTrue();
        assertThat(panic.isEvent()).isFalse();
        assertThat(panic.isTheThing()).isFalse();
        assertThat(panic.isInfection()).isFalse();
    }

    /**
     * Проверяет реализацию методов {@code equals} и {@code hashCode} на основе уникального идентификатора карты.
     *
     * <p><b>Предусловия (Given):</b> Созданы два объекта карты с одинаковым id = 10 и один с id = 11.</p>
     *
     * <p><b>Действие (When):</b> Сравниваются объекты через {@code equals} и вычисляются хэш-коды.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Карты с одинаковым id равны и имеют одинаковый hashCode, с разными — не равны.</p>
     */
    @Test
    @DisplayName("equals и hashCode основаны на уникальном идентификаторе карты")
    void card_EqualsAndHashCode_BasedOnId() {
        Card cardA = new Card(10, CardName.INFECTION, CardType.EVENT, 6, "Заражение!", "Заражение");
        Card cardB = new Card(10, CardName.INFECTION, CardType.EVENT, 6, "Заражение!", "Заражение");
        Card cardC = new Card(11, CardName.INFECTION, CardType.EVENT, 6, "Заражение!", "Заражение");

        assertThat(cardA).isEqualTo(cardB);
        assertThat(cardA.hashCode()).isEqualTo(cardB.hashCode());
        assertThat(cardA).isNotEqualTo(cardC);
        assertThat(cardA.toString()).contains("id=10");
    }

    /**
     * Проверяет фабричный метод {@link CardName#fromRussian(String)} для поиска элементов перечисления по русскому имени.
     *
     * <p><b>Предусловия (Given):</b> Корректные русские названия карт из реестра («Нечто», «Заражение!», «Огнемёт», «Виски», «Биски»).</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code CardName.fromRussian(name)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Возвращаются корректные значения перечисления {@link CardName}.</p>
     */
    @Test
    @DisplayName("CardName.fromRussian корректно находит имена карт по русскому названию")
    void cardName_FromRussian_FindsCorrectEnum() {
        assertThat(CardName.fromRussian("Нечто")).isEqualTo(CardName.THE_THING);
        assertThat(CardName.fromRussian("Заражение!")).isEqualTo(CardName.INFECTION);
        assertThat(CardName.fromRussian("Огнемёт")).isEqualTo(CardName.FLAMETHROWER);
        assertThat(CardName.fromRussian("Виски")).isEqualTo(CardName.WHISKEY);
        assertThat(CardName.fromRussian("Биски")).isEqualTo(CardName.WHISKEY);
        assertThat(CardName.fromRussian("Цепная реакция")).isEqualTo(CardName.CHAIN_REACTION);

        assertThatThrownBy(() -> CardName.fromRussian("Несуществующая карта"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Проверяет перечисление {@link CardType} и его вспомогательные методы {@code isPanic()} и {@code isEvent()}.
     *
     * <p><b>Предусловия (Given):</b> Элементы перечисления {@link CardType#EVENT} и {@link CardType#PANIC}.</p>
     *
     * <p><b>Действие (When):</b> Вызываются предикаты {@code isEvent()} и {@code isPanic()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Для {@code EVENT}: {@code isEvent() == true}, {@code isPanic() == false}.</li>
     *   <li>Для {@code PANIC}: {@code isPanic() == true}, {@code isEvent() == false}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("CardType возвращает корректные флаги для EVENT и PANIC")
    void cardType_Predicates_ReturnCorrectBooleans() {
        assertThat(CardType.EVENT.isEvent()).isTrue();
        assertThat(CardType.EVENT.isPanic()).isFalse();

        assertThat(CardType.PANIC.isPanic()).isTrue();
        assertThat(CardType.PANIC.isEvent()).isFalse();
    }
}
