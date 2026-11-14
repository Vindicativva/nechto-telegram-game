package ru.nechto.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Модульные тесты для доменной модели комнаты {@link Room}.
 */
class RoomModelTest {

    /**
     * Проверяет создание объекта {@link Room} через конструктор со всеми ключевыми
     * доменными полями: {@code code}, {@code name}, {@code maxPlayers}, {@code players}, {@code status}.
     *
     * <p><b>Предусловия (Given):</b> Подготовлены параметры комнаты: код {@code "ROOM01"},
     * название {@code "Тестовая комната"}, лимит игроков 8, статус {@code LOBBY}
     * и начальный список из одного игрока.</p>
     *
     * <p><b>Действие (When):</b> Создается экземпляр {@link Room} через доменный конструктор.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Поля {@code code}, {@code name}, {@code maxPlayers}, {@code status} установлены корректно.</li>
     *   <li>Список игроков содержит 1 элемент.</li>
     *   <li>Внутренний идентификатор {@code id} автоматически сгенерирован.</li>
     *   <li>Временные метки создания не равны {@code null}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Создание комнаты через конструктор с ключевыми доменными полями")
    void domainConstructor_SetsAllFieldsCorrectly() {
        RoomParticipant host = new RoomParticipant("101", "host", "Хост", true, ConnectionStatus.ONLINE, Instant.now(), null);
        Room room = new Room("ROOM01", "Тестовая комната", 8, List.of(host), RoomStatus.LOBBY);

        assertThat(room.getCode()).isEqualTo("ROOM01");
        assertThat(room.getName()).isEqualTo("Тестовая комната");
        assertThat(room.getMaxPlayers()).isEqualTo(8);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.LOBBY);
        assertThat(room.getPlayers()).hasSize(1);
        assertThat(room.getId()).isNotNull();
        assertThat(room.getCreatedAt()).isNotNull();
        assertThat(room.getLastActivityAt()).isNotNull();
    }

    /**
     * Проверяет добавление игрока в комнату через метод {@link Room#addPlayer(Player)}.
     *
     * <p><b>Предусловия (Given):</b> Создана пустая комната со статусом {@code LOBBY}.</p>
     *
     * <p><b>Действие (When):</b> Добавляется новый игрок {@link Player} с {@code telegramId = "202"}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Количество игроков в комнате увеличивается до 1.</li>
     *   <li>Метод {@link Room#getDomainPlayers()} возвращает список с добавленным игроком.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Добавление доменного игрока в комнату через addPlayer(Player)")
    void addPlayer_AddsDomainPlayerToRoom() {
        Room room = new Room("ROOM02", "Комната для добавления", 6, RoomStatus.LOBBY);
        Player player = new Player("202", "alex", false);

        room.addPlayer(player);

        assertThat(room.getCurrentPlayersCount()).isEqualTo(1);
        List<Player> domainPlayers = room.getDomainPlayers();
        assertThat(domainPlayers).hasSize(1);
        assertThat(domainPlayers.get(0).getTelegramId()).isEqualTo("202");
        assertThat(domainPlayers.get(0).getUsername()).isEqualTo("alex");
    }

    /**
     * Проверяет удаление игрока из комнаты по его {@code telegramId}.
     *
     * <p><b>Предусловия (Given):</b> В комнате зарегистрированы два игрока с ID {@code "301"} и {@code "302"}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code room.removePlayer("301")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Игрок с ID {@code "301"} удален.</li>
     *   <li>В комнате остается 1 игрок с ID {@code "302"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Удаление игрока из комнаты по telegramId")
    void removePlayer_RemovesCorrectPlayer() {
        Room room = new Room("ROOM03", "Комната", 6, RoomStatus.LOBBY);
        room.addPlayer(new Player("301", "user1", true));
        room.addPlayer(new Player("302", "user2", false));
        assertThat(room.getCurrentPlayersCount()).isEqualTo(2);

        room.removePlayer("301");

        assertThat(room.getCurrentPlayersCount()).isEqualTo(1);
        assertThat(room.getPlayers().get(0).getTelegramId()).isEqualTo("302");
    }

    /**
     * Проверяет вычисление флага готовности к старту {@link Room#isReadyToStart()}:
     * комната готова, только если участников от 4 до {@code maxPlayers} и статус {@code LOBBY}.
     *
     * <p><b>Предусловия (Given):</b> Комната с лимитом 6 игроков в статусе {@code LOBBY}.</p>
     *
     * <p><b>Действие (When):</b> Поочередно добавляются игроки от 1 до 4, затем меняется статус на {@code IN_PROGRESS}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>При количестве игроков 1, 2, 3 флаг равен {@code false}.</li>
     *   <li>При 4 игроках флаг становится {@code true}.</li>
     *   <li>При переводе в статус {@code IN_PROGRESS} флаг становится {@code false}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Вычисление флага готовности к старту партии isReadyToStart")
    void isReadyToStart_CalculatedCorrectly() {
        Room room = new Room("ROOM04", "Готовность", 6, RoomStatus.LOBBY);

        room.addPlayer(new Player("1", "u1", true));
        room.addPlayer(new Player("2", "u2", false));
        room.addPlayer(new Player("3", "u3", false));
        assertThat(room.isReadyToStart()).isFalse();

        room.addPlayer(new Player("4", "u4", false));
        assertThat(room.isReadyToStart()).isTrue();

        room.setStatus(RoomStatus.IN_PROGRESS);
        assertThat(room.isReadyToStart()).isFalse();
    }
}
