package ru.nechto.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Модульные тесты для доменной модели игрока {@link Player}.
 */
class PlayerTest {

    /**
     * Проверяет создание объекта {@link Player} через конструктор со всеми полями
     * и корректность возврата значений геттерами.
     *
     * <p><b>Предусловия (Given):</b> Исходные параметры: идентификатор {@code "p-1"},
     * Telegram ID {@code "1001"}, username {@code "alex_nechto"}, флаг хоста {@code true}
     * и фиксированная временная метка подключения.</p>
     *
     * <p><b>Действие (When):</b> Создается экземпляр {@link Player} через полный конструктор.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Поле {@code id} равно {@code "p-1"}.</li>
     *   <li>Поле {@code telegramId} равно {@code "1001"}.</li>
     *   <li>Поле {@code username} равно {@code "alex_nechto"}.</li>
     *   <li>Поле {@code isHost} равно {@code true}.</li>
     *   <li>Поле {@code connectedAt} соответствует переданной метке времени.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Создание игрока через полный конструктор и проверка геттеров")
    void fullConstructor_SetsAllFieldsCorrectly() {
        String id = "p-1";
        String telegramId = "1001";
        String username = "alex_nechto";
        boolean isHost = true;
        Instant connectedAt = Instant.parse("2026-11-14T10:00:00Z");

        Player player = new Player(id, telegramId, username, isHost, connectedAt);

        assertThat(player.getId()).isEqualTo(id);
        assertThat(player.getTelegramId()).isEqualTo(telegramId);
        assertThat(player.getUsername()).isEqualTo(username);
        assertThat(player.isHost()).isTrue();
        assertThat(player.getConnectedAt()).isEqualTo(connectedAt);
    }

    /**
     * Проверяет создание объекта {@link Player} через удобный конструктор
     * с автогенерацией уникального ID и текущего времени подключения.
     *
     * <p><b>Предусловия (Given):</b> Заданы Telegram ID {@code "2002"}, username {@code "bob"}
     * и флаг хоста {@code false}.</p>
     *
     * <p><b>Действие (When):</b> Создается экземпляр {@link Player} через краткий конструктор.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Поле {@code id} автоматически сгенерировано и не равно {@code null}.</li>
     *   <li>Поле {@code telegramId} равно {@code "2002"}.</li>
     *   <li>Поле {@code username} равно {@code "bob"}.</li>
     *   <li>Поле {@code isHost} равно {@code false}.</li>
     *   <li>Поле {@code connectedAt} заполнено текущим временем.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Создание игрока через краткий конструктор с автогенерацией ID и connectedAt")
    void compactConstructor_GeneratesIdAndConnectedAt() {
        Player player = new Player("2002", "bob", false);

        assertThat(player.getId()).isNotBlank();
        assertThat(player.getTelegramId()).isEqualTo("2002");
        assertThat(player.getUsername()).isEqualTo("bob");
        assertThat(player.isHost()).isFalse();
        assertThat(player.getConnectedAt()).isNotNull();
    }

    /**
     * Проверяет работу сеттеров и конструктора по умолчанию {@link Player}.
     *
     * <p><b>Предусловия (Given):</b> Создан пустой объект {@link Player} через конструктор по умолчанию.</p>
     *
     * <p><b>Действие (When):</b> Через сеттеры устанавливаются все поля игрока.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Все установленные значения корректно возвращаются соответствующими геттерами.
     * </p>
     */
    @Test
    @DisplayName("Конструктор по умолчанию и сеттеры объекта Player")
    void defaultConstructorAndSetters_WorkCorrectly() {
        Player player = new Player();
        Instant now = Instant.now();

        player.setId("custom-id");
        player.setTelegramId("3003");
        player.setUsername("charlie");
        player.setHost(true);
        player.setConnectedAt(now);

        assertThat(player.getId()).isEqualTo("custom-id");
        assertThat(player.getTelegramId()).isEqualTo("3003");
        assertThat(player.getUsername()).isEqualTo("charlie");
        assertThat(player.isHost()).isTrue();
        assertThat(player.getConnectedAt()).isEqualTo(now);
    }

    /**
     * Проверяет статическую фабрику {@link Player#from(RoomParticipant)} для конвертации
     * участника комнаты в доменную модель игрока.
     *
     * <p><b>Предусловия (Given):</b> Объект {@link RoomParticipant} с Telegram ID {@code "4004"},
     * отображаемым именем {@code "Дмитрий"}, флагом создателя {@code true} и временной меткой входа.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code Player.from(participant)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Создается доменный объект {@link Player}.</li>
     *   <li>Поля {@code telegramId}, {@code isHost} и {@code connectedAt} совпадают с участником.</li>
     *   <li>Имя пользователя заполнено из {@code displayName}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Конвертация RoomParticipant в Player через фабричный метод")
    void fromRoomParticipant_ConvertsCorrectly() {
        Instant joinedAt = Instant.parse("2026-11-14T09:00:00Z");
        RoomParticipant participant = new RoomParticipant(
                "4004",
                null,
                "Дмитрий",
                true,
                ConnectionStatus.ONLINE,
                joinedAt,
                null
        );

        Player player = Player.from(participant);

        assertThat(player).isNotNull();
        assertThat(player.getTelegramId()).isEqualTo("4004");
        assertThat(player.getUsername()).isEqualTo("Дмитрий");
        assertThat(player.isHost()).isTrue();
        assertThat(player.getConnectedAt()).isEqualTo(joinedAt);
    }

    /**
     * Проверяет фабричный метод {@link Player#from(RoomParticipant)} при передаче {@code null}.
     *
     * <p><b>Предусловия (Given):</b> Входной параметр {@code null}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code Player.from(null)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Возвращается {@code null}.</p>
     */
    @Test
    @DisplayName("Конвертация null RoomParticipant возвращает null")
    void fromRoomParticipant_NullReturnsNull() {
        assertThat(Player.from(null)).isNull();
    }

    /**
     * Проверяет метод {@link Player#toParticipant()} для обратной конвертации
     * доменной модели в модель участника комнаты.
     *
     * <p><b>Предусловия (Given):</b> Доменный объект {@link Player} с Telegram ID {@code "5005"},
     * именем {@code "elena"} и флагом хоста {@code false}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code player.toParticipant()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Возвращается корректно сконфигурированный объект {@link RoomParticipant}
     * со статусом {@code ONLINE}.
     * </p>
     */
    @Test
    @DisplayName("Конвертация Player в RoomParticipant")
    void toParticipant_ConvertsCorrectly() {
        Instant connectedAt = Instant.parse("2026-11-14T11:00:00Z");
        Player player = new Player("p-5", "5005", "elena", false, connectedAt);

        RoomParticipant participant = player.toParticipant();

        assertThat(participant).isNotNull();
        assertThat(participant.getTelegramId()).isEqualTo("5005");
        assertThat(participant.getUsername()).isEqualTo("elena");
        assertThat(participant.getDisplayName()).isEqualTo("elena");
        assertThat(participant.isCreator()).isFalse();
        assertThat(participant.getConnectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
        assertThat(participant.getJoinedAt()).isEqualTo(connectedAt);
    }

    /**
     * Проверяет контракт {@code equals} и {@code hashCode} для игроков:
     * равенство определяется по уникальному {@code telegramId}.
     *
     * <p><b>Предусловия (Given):</b> Два игрока с одинаковым {@code telegramId = "777"}
     * и разным именем, а также третий игрок с другим ID.</p>
     *
     * <p><b>Действие (When):</b> Выполняется сравнение объектов через {@code equals} и вычисление {@code hashCode}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Первый и второй игроки равны между собой и имеют одинаковый хэш-код.</li>
     *   <li>Первый и третий игроки не равны.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Контракт equals и hashCode базируется на telegramId")
    void equalsAndHashCode_BasedOnTelegramId() {
        Player player1 = new Player("1", "777", "UserA", true, Instant.now());
        Player player2 = new Player("2", "777", "UserB", false, Instant.now());
        Player player3 = new Player("3", "888", "UserC", false, Instant.now());

        assertThat(player1).isEqualTo(player2);
        assertThat(player1.hashCode()).isEqualTo(player2.hashCode());
        assertThat(player1).isNotEqualTo(player3);
    }

    /**
     * Проверяет информативность строкового представления {@code toString()}.
     *
     * <p><b>Предусловия (Given):</b> Игрок с Telegram ID {@code "999"} и именем {@code "tester"}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code player.toString()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Строка содержит ключевые атрибуты игрока.</p>
     */
    @Test
    @DisplayName("Строковое представление Player содержит все ключевые поля")
    void toString_ContainsExpectedFields() {
        Player player = new Player("id-9", "999", "tester", true, Instant.now());
        String str = player.toString();

        assertThat(str).contains("id-9");
        assertThat(str).contains("999");
        assertThat(str).contains("tester");
        assertThat(str).contains("isHost=true");
    }
}
