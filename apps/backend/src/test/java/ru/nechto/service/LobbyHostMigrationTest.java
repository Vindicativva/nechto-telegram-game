package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.NotEnoughPlayersException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты механизма миграции хоста (передачи прав создателя), таймаута удержания прав (5 минут)
 * и запуска игры согласно ТЗ 4.2.1 и задаче BE-5.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LobbyHostMigrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @BeforeEach
    void setUp() {
        roomJpaRepository.deleteAll();
    }

    /**
     * Проверяет немедленную передачу прав создателя при явном выходе текущего хоста из комнаты.
     * Права хоста должны быть переданы участнику с самым ранним временем подключения (joinedAt).
     *
     * <p><b>Предусловия (Given):</b> В комнате находятся 3 игрока: хост (id="101", joinedAt=T0),
     * второй игрок (id="102", joinedAt=T1) и третий игрок (id="103", joinedAt=T2), где T1 < T2.</p>
     *
     * <p><b>Действие (When):</b> Хост "101" явно выходит из комнаты методом {@code leaveRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Хост "101" удален из списка участников комнаты.</li>
     *   <li>Права создателя переданы игроку "102" (самый ранний joinedAt).</li>
     *   <li>Поле {@code creatorId} комнаты равно "102".</li>
     *   <li>У игрока "102" флаг {@code isCreator} равен true.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Явный выход хоста: немедленная передача прав участнику с минимальным joinedAt")
    void leaveRoom_HostExplicitLeave_MigratesToEarliestParticipant() {
        TelegramUser host = TelegramUser.of("101", "host_nick", "Хост");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Партия миграции", 6));

        TelegramUser p2 = TelegramUser.of("102", "player2", "Игрок 2");
        roomService.joinRoom(created.code(), p2);

        TelegramUser p3 = TelegramUser.of("103", "player3", "Игрок 3");
        roomService.joinRoom(created.code(), p3);

        RoomResponse afterLeave = roomService.leaveRoom(created.code(), "101");

        assertThat(afterLeave.creatorId()).isEqualTo("102");
        assertThat(afterLeave.players()).hasSize(2);
        assertThat(afterLeave.players().stream().filter(p -> "102".equals(p.getTelegramId())).findFirst().get().isCreator()).isTrue();
        assertThat(afterLeave.players().stream().filter(p -> "103".equals(p.getTelegramId())).findFirst().get().isCreator()).isFalse();
    }

    /**
     * Проверяет удержание прав создателя за отключившимся хостом в течение льготного периода
     * 5 минут (п. 4.2.1) при сетевом сбое (SessionDisconnect).
     *
     * <p><b>Предусловия (Given):</b> В комнате находятся хост ("201") и участник ("202").
     * Хост теряет соединение (статус DISCONNECTED, disconnectedAt = 2 минуты назад).</p>
     *
     * <p><b>Действие (When):</b> Запускается проверка {@code checkAndMigrateDisconnectedHost}
     * со стандартным таймаутом 5 минут (300 секунд).</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Метод возвращает {@code false} (миграция не произошла).</li>
     *   <li>Создателем комнаты остается игрок "201".</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Сетевой сбой хоста: удержание прав создателя в течение 5 минут (миграция блокируется)")
    void hostDisconnect_Within5Minutes_HostRightsRetained() {
        TelegramUser host = TelegramUser.of("201", "host", "Хост 201");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Удержание прав", 6));

        TelegramUser p2 = TelegramUser.of("202", "p2", "Игрок 202");
        roomService.joinRoom(created.code(), p2);

        // Хост отключается
        roomService.updatePlayerConnectionStatus(created.code(), "201", ConnectionStatus.DISCONNECTED);

        // Устанавливаем disconnectedAt на 2 минуты назад (в пределах 5 минут)
        RoomEntity roomEntity = roomJpaRepository.findByCode(created.code()).orElseThrow();
        RoomParticipantEntity creatorEntity = roomEntity.getParticipants().stream()
                .filter(p -> "201".equals(p.getTelegramId()))
                .findFirst()
                .orElseThrow();
        creatorEntity.setDisconnectedAt(Instant.now().minus(Duration.ofMinutes(2)));
        roomJpaRepository.save(roomEntity);

        // Проверяем миграцию с таймаутом 5 минут
        boolean migrated = roomService.checkAndMigrateDisconnectedHost(created.code(), Duration.ofMinutes(5));

        assertThat(migrated).isFalse();
        RoomEntity unchanged = roomJpaRepository.findByCode(created.code()).orElseThrow();
        assertThat(unchanged.getCreatorTelegramId()).isEqualTo("201");
    }

    /**
     * Проверяет успешное повторное подключение хоста в течение 5 минут после сбоя:
     * права создателя сохраняются, статус восстанавливается в ONLINE.
     *
     * <p><b>Предусловия (Given):</b> Хост ("301") отключился 3 минуты назад.</p>
     *
     * <p><b>Действие (When):</b> Хост повторно подключается к комнате вызовом {@code joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Статус хоста изменен на {@code ONLINE}.</li>
     *   <li>Метка {@code disconnectedAt} сброшена в {@code null}.</li>
     *   <li>Хост сохранил статус создателя ({@code isCreator = true}, {@code creatorId = "301"}).</li>
     *   <li>Дубликатов в списке участников не возникло (строго 2 участника).</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Переподключение хоста в течение 5 минут: восстановление ONLINE и сохранение прав создателя")
    void hostReconnect_Within5Minutes_RestoresStatusAndRetainsRights() {
        TelegramUser host = TelegramUser.of("301", "host301", "Хост 301");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Переподключение", 6));

        TelegramUser p2 = TelegramUser.of("302", "p302", "Игрок 302");
        roomService.joinRoom(created.code(), p2);

        // Обрыв связи хоста
        roomService.updatePlayerConnectionStatus(created.code(), "301", ConnectionStatus.DISCONNECTED);

        // Повторный вход хоста
        RoomResponse reconnected = roomService.joinRoom(created.code(), host);

        assertThat(reconnected.creatorId()).isEqualTo("301");
        assertThat(reconnected.players()).hasSize(2);

        var hostParticipant = reconnected.players().stream()
                .filter(p -> "301".equals(p.getTelegramId()))
                .findFirst()
                .orElseThrow();

        assertThat(hostParticipant.isCreator()).isTrue();
        assertThat(hostParticipant.getConnectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
        assertThat(hostParticipant.getDisconnectedAt()).isNull();
    }

    /**
     * Проверяет автоматическую передачу прав создателя при истечении 5-минутного таймаута
     * отсутствия хоста в сети (п. 4.2.1).
     *
     * <p><b>Предусловия (Given):</b> Хост ("401") отключился 6 минут назад (превышение 5 минут).
     * В комнате остался игрок ("402").</p>
     *
     * <p><b>Действие (When):</b> Запускается проверка {@code checkAndMigrateDisconnectedHost}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Метод возвращает {@code true} (миграция успешно выполнена).</li>
     *   <li>Новым создателем комнаты становится игрок "402".</li>
     *   <li>У старого хоста "401" флаг {@code isCreator} сброшен в {@code false}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Истечение 5 минут отсутствия хоста: автоматическая передача прав следующему игроку")
    void hostDisconnect_TimeoutExpired_MigratesHostToNextParticipant() {
        TelegramUser host = TelegramUser.of("401", "host401", "Хост 401");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Истечение таймаута", 6));

        TelegramUser p2 = TelegramUser.of("402", "p402", "Игрок 402");
        roomService.joinRoom(created.code(), p2);

        roomService.updatePlayerConnectionStatus(created.code(), "401", ConnectionStatus.DISCONNECTED);

        // Имитируем, что хост отключен 6 минут назад (> 5 минут)
        RoomEntity roomEntity = roomJpaRepository.findByCode(created.code()).orElseThrow();
        RoomParticipantEntity creatorEntity = roomEntity.getParticipants().stream()
                .filter(p -> "401".equals(p.getTelegramId()))
                .findFirst()
                .orElseThrow();
        creatorEntity.setDisconnectedAt(Instant.now().minus(Duration.ofMinutes(6)));
        roomJpaRepository.save(roomEntity);

        boolean migrated = roomService.checkAndMigrateDisconnectedHost(created.code(), Duration.ofMinutes(5));

        assertThat(migrated).isTrue();
        RoomEntity updated = roomJpaRepository.findByCode(created.code()).orElseThrow();
        assertThat(updated.getCreatorTelegramId()).isEqualTo("402");

        var newHost = updated.getParticipants().stream()
                .filter(p -> "402".equals(p.getTelegramId()))
                .findFirst()
                .orElseThrow();
        assertThat(newHost.isCreator()).isTrue();

        var oldHost = updated.getParticipants().stream()
                .filter(p -> "401".equals(p.getTelegramId()))
                .findFirst()
                .orElseThrow();
        assertThat(oldHost.isCreator()).isFalse();
    }

    /**
     * Проверяет успешный запуск игры создателем комнаты при наличии 4 участников
     * (минимальный порог диапазона 4..12 человек).
     *
     * <p><b>Предусловия (Given):</b> В комнате зарегистрированы ровно 4 участника со статусом LOBBY.</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет команду {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Статус комнаты переходит в {@code IN_PROGRESS}.</li>
     *   <li>В ответе поле {@code status} равно {@code IN_PROGRESS}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Запуск игры: успешный старт партии при 4 игроках (минимальный порог)")
    void startGame_With4Players_StartsSuccessfully() {
        TelegramUser host = TelegramUser.of("501", "host501", "Хост 501");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Старт игры", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("502", "p502", "Игрок 502"));
        roomService.joinRoom(created.code(), TelegramUser.of("503", "p503", "Игрок 503"));
        roomService.joinRoom(created.code(), TelegramUser.of("504", "p504", "Игрок 504"));

        RoomResponse started = roomService.startGame(created.code(), "501");

        assertThat(started.status()).isEqualTo(RoomStatus.IN_PROGRESS);
        RoomEntity inDb = roomJpaRepository.findByCode(created.code()).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
    }

    /**
     * Проверяет отказ в запуске игры при количестве участников менее 4 человек.
     *
     * <p><b>Предусловия (Given):</b> В комнате находятся только 3 участника.</p>
     *
     * <p><b>Действие (When):</b> Хост пытается запустить игру методом {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Выбрасывается исключение {@link NotEnoughPlayersException} с кодом "NOT_ENOUGH_PLAYERS".</li>
     *   <li>Статус комнаты в базе данных остается {@code LOBBY}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Запуск игры: отказ с NotEnoughPlayersException при менее чем 4 участниках")
    void startGame_FewerThan4Players_ThrowsNotEnoughPlayersException() {
        TelegramUser host = TelegramUser.of("601", "host601", "Хост 601");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Мало игроков", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("602", "p602", "Игрок 602"));
        roomService.joinRoom(created.code(), TelegramUser.of("603", "p603", "Игрок 603"));

        assertThatThrownBy(() -> roomService.startGame(created.code(), "601"))
                .isInstanceOf(NotEnoughPlayersException.class)
                .hasMessageContaining("минимум 4 игрока");

        RoomEntity inDb = roomJpaRepository.findByCode(created.code()).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RoomStatus.LOBBY);
    }

    /**
     * Проверяет отказ в запуске игры пользователем, не являющимся создателем комнаты.
     *
     * <p><b>Предусловия (Given):</b> В комнате 4 игрока. Пользователь "702" является обычным участником.</p>
     *
     * <p><b>Действие (When):</b> Игрок "702" отправляет команду {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Выбрасывается исключение {@link ForbiddenException} с кодом "FORBIDDEN_NOT_CREATOR".</li>
     *   <li>Статус комнаты остается {@code LOBBY}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Запуск игры: отказ с ForbiddenException при попытке запуска не создателем")
    void startGame_NotHost_ThrowsForbiddenException() {
        TelegramUser host = TelegramUser.of("701", "host701", "Хост 701");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Чужой запуск", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("702", "p702", "Игрок 702"));
        roomService.joinRoom(created.code(), TelegramUser.of("703", "p703", "Игрок 703"));
        roomService.joinRoom(created.code(), TelegramUser.of("704", "p704", "Игрок 704"));

        assertThatThrownBy(() -> roomService.startGame(created.code(), "702"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Только создатель комнаты");
    }

    /**
     * Проверяет отказ в повторном запуске игры, если комната уже находится в статусе {@code IN_PROGRESS}.
     *
     * <p><b>Предусловия (Given):</b> Игра в комнате уже успешно запущена.</p>
     *
     * <p><b>Действие (When):</b> Хост повторно вызывает {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@link RoomAlreadyStartedException} с кодом "ROOM_ALREADY_STARTED".
     * </p>
     */
    @Test
    @DisplayName("Запуск игры: отказ при попытке повторного старта начатой игры")
    void startGame_AlreadyStarted_ThrowsRoomAlreadyStartedException() {
        TelegramUser host = TelegramUser.of("801", "host801", "Хост 801");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Повторный старт", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("802", "p802", "Игрок 802"));
        roomService.joinRoom(created.code(), TelegramUser.of("803", "p803", "Игрок 803"));
        roomService.joinRoom(created.code(), TelegramUser.of("804", "p804", "Игрок 804"));

        roomService.startGame(created.code(), "801");

        assertThatThrownBy(() -> roomService.startGame(created.code(), "801"))
                .isInstanceOf(RoomAlreadyStartedException.class)
                .hasMessageContaining("Игра уже началась");
    }
}
