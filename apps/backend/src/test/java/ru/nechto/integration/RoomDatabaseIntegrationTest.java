package ru.nechto.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.ErrorResponse;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.exception.GlobalExceptionHandler;
import ru.nechto.exception.InvalidPlayerNameException;
import ru.nechto.exception.InvalidRoomCodeException;
import ru.nechto.exception.InvalidRoomSettingsException;
import ru.nechto.exception.RoomFullException;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.repository.jpa.RoomParticipantJpaRepository;
import ru.nechto.service.RoomCodeGenerator;
import ru.nechto.service.RoomService;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты слоя базы данных, миграций, транзакций и безопасности.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomDatabaseIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomParticipantJpaRepository roomParticipantJpaRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @MockitoBean
    private RoomWebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        roomParticipantJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    /**
     * Проверяет сохранность данных в БД при пересоздании экземпляра сервиса (имитация перезапуска приложения).
     *
     * <p><b>Предусловия (Given):</b> Создана комната с кодом {@code created.code()} и добавлены два участника.</p>
     *
     * <p><b>Действие (When):</b> Создается новый экземпляр {@code RoomService} с подключением к той же БД,
     * после чего вызывается метод {@code getRoomByCode}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Комната и все параметры лобби успешно загружаются из БД.</li>
     *   <li>Список участников содержит сохраненных игроков.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("1. Тест персистентности: данные комнаты и участников сохраняются в БД при пересоздании сервиса")
    void testPersistenceAcrossServiceInstances() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator Player");
        RoomResponse created = roomService.createRoom(creator, new CreateRoomRequest("Партия сохранения", 6));

        TelegramUser p2 = TelegramUser.of("1002", "player2", "Second Player");
        roomService.joinRoom(created.code(), p2);

        // Имитируем перезапуск приложения: создаем новый экземпляр RoomService с тем же репозиторием
        RoomCodeGenerator codeGen = new RoomCodeGenerator(roomJpaRepository, "StayAwayGameBot");
        RoomService newServiceInstance = new RoomService(roomJpaRepository, broadcaster, codeGen);

        RoomResponse reloaded = newServiceInstance.getRoomByCode(created.code());

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.code()).isEqualTo(created.code());
        assertThat(reloaded.name()).isEqualTo("Партия сохранения");
        assertThat(reloaded.maxPlayers()).isEqualTo(6);
        assertThat(reloaded.currentPlayersCount()).isEqualTo(2);
        assertThat(reloaded.status()).isEqualTo(RoomStatus.LOBBY);
        assertThat(reloaded.players()).extracting("telegramId").containsExactly("1001", "1002");
    }

    /**
     * Проверяет корректное применение миграций Flyway на чистую базу данных:
     * существование таблиц, первичных и внешних ключей.
     *
     * <p><b>Предусловия (Given):</b> Flyway миграции применились на чистую схему БД.</p>
     *
     * <p><b>Действие (When):</b> Извлекаются метаданные JDBC соединения {@code connection.getMetaData()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Таблицы {@code rooms}, {@code room_participants} и {@code flyway_schema_history} присутствуют в БД.</li>
     *   <li>Таблица {@code rooms} имеет первичный ключ {@code id}.</li>
     *   <li>Таблица {@code room_participants} имеет внешний ключ на {@code rooms(id)}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("2. Тест чистых миграций: схемы таблиц, первичные ключи и ограничения созданы корректно")
    void testFlywayMigrationsOnCleanDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Проверяем наличие таблиц rooms и room_participants
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                List<String> tableNames = new ArrayList<>();
                while (tables.next()) {
                    tableNames.add(tables.getString("TABLE_NAME").toLowerCase());
                }
                assertThat(tableNames).contains("rooms", "room_participants", "flyway_schema_history");
            }

            // Проверяем первичный ключ таблицы rooms
            boolean pkFound = false;
            try (ResultSet pks = metaData.getPrimaryKeys(null, null, "rooms")) {
                if (pks.next()) {
                    pkFound = true;
                    assertThat(pks.getString("COLUMN_NAME").toLowerCase()).isEqualTo("id");
                }
            }
            if (!pkFound) {
                try (ResultSet pks = metaData.getPrimaryKeys(null, null, "ROOMS")) {
                    assertThat(pks.next()).isTrue();
                    assertThat(pks.getString("COLUMN_NAME").toLowerCase()).isEqualTo("id");
                }
            }

            // Проверяем внешний ключ room_participants -> rooms
            boolean fkFound = false;
            try (ResultSet fks = metaData.getImportedKeys(null, null, "room_participants")) {
                if (fks.next()) {
                    fkFound = true;
                    assertThat(fks.getString("PKTABLE_NAME").toLowerCase()).isEqualTo("rooms");
                    assertThat(fks.getString("FKCOLUMN_NAME").toLowerCase()).isEqualTo("room_id");
                }
            }
            if (!fkFound) {
                try (ResultSet fks = metaData.getImportedKeys(null, null, "ROOM_PARTICIPANTS")) {
                    assertThat(fks.next()).isTrue();
                    assertThat(fks.getString("PKTABLE_NAME").toLowerCase()).isEqualTo("rooms");
                    assertThat(fks.getString("FKCOLUMN_NAME").toLowerCase()).isEqualTo("room_id");
                }
            }
        }
    }

    /**
     * Проверяет защиту от Race Condition при одновременной попытке 5 параллельных потоков занять последнее место.
     *
     * <p><b>Предусловия (Given):</b> Комната на 4 игрока с 3 уже зарегистрированными участниками.</p>
     *
     * <p><b>Действие (When):</b> 5 потоков одновременно через {@code CountDownLatch} отправляют запрос на вход.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Ровно 1 поток успешно занимает слот (200 OK).</li>
     *   <li>Ровно 4 потока получают отказ {@code RoomFullException} (409 Conflict).</li>
     *   <li>В базе данных находится ровно 4 участника.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("3. Тест на состояние гонки (Race Condition): ровно 1 из 5 одновременных запросов занимает последнее место")
    void testRaceConditionOnLastSlotConcurrentJoins() throws Exception {
        TelegramUser creator = TelegramUser.of("100", "c", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Гонка", 4));

        roomService.joinRoom(room.code(), TelegramUser.of("101", "p1", "Player 1"));
        roomService.joinRoom(room.code(), TelegramUser.of("102", "p2", "Player 2"));

        assertThat(roomService.getRoomByCode(room.code()).currentPlayersCount()).isEqualTo(3);

        int concurrentUsers = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fullConflictCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentUsers; i++) {
            final String telegramId = "concurrent_user_" + i;
            final String name = "Racer " + i;

            Callable<Void> task = () -> {
                startLatch.await();
                try {
                    roomService.joinRoom(room.code(), TelegramUser.of(telegramId, telegramId, name));
                    successCount.incrementAndGet();
                } catch (RoomFullException e) {
                    fullConflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
                return null;
            };
            futures.add(executor.submit(task));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(fullConflictCount.get()).isEqualTo(4);

        RoomEntity roomInDb = roomJpaRepository.findByCodeWithParticipants(room.code()).orElseThrow();
        assertThat(roomInDb.getParticipants()).hasSize(4);
    }

    /**
     * Проверяет атомарность транзакций: невалидные запросы (XSS, спецсимволы, некорректный лимит)
     * не меняют состояние базы данных.
     *
     * <p><b>Предусловия (Given):</b> Комната с 1 создателем в БД.</p>
     *
     * <p><b>Действие (When):</b> Выполняются невалидные вызовы методов сервиса.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Сервис выбрасывает исключения валидации, а число записей в БД остаётся неизменным.
     * </p>
     */
    @Test
    @DisplayName("4. Тест атомарности: невалидные запросы не изменяют состояние БД")
    void testAtomicityOnInvalidRequests() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator Player");
        RoomResponse created = roomService.createRoom(creator, new CreateRoomRequest("Тест атомарности", 6));

        // 1. Попытка подключения с HTML-тегом в имени (XSS)
        TelegramUser xssUser = TelegramUser.of("1002", "hacker", "<script>alert(1)</script>");
        assertThatThrownBy(() -> roomService.joinRoom(created.code(), xssUser))
                .isInstanceOf(InvalidPlayerNameException.class);

        // 2. Попытка подключения с управляющими символами
        TelegramUser controlCharsUser = TelegramUser.of("1003", "user", "Bad\u0000Name");
        assertThatThrownBy(() -> roomService.joinRoom(created.code(), controlCharsUser))
                .isInstanceOf(InvalidPlayerNameException.class);

        // 3. Попытка обращения с невалидным кодом комнаты
        assertThatThrownBy(() -> roomService.getRoomByCode("INVALID!@#$"))
                .isInstanceOf(InvalidRoomCodeException.class);

        // 4. Попытка установки maxPlayers = 15
        assertThatThrownBy(() -> roomService.updateSettings(created.code(), "1001", 15))
                .isInstanceOf(InvalidRoomSettingsException.class);

        RoomEntity roomInDb = roomJpaRepository.findByCodeWithParticipants(created.code()).orElseThrow();
        assertThat(roomInDb.getParticipants()).hasSize(1);
        assertThat(roomInDb.getMaxPlayers()).isEqualTo(6);
    }

    /**
     * Проверяет маскирование системных ошибок базы данных: ответ 500 не содержит SQL-запросов и стектрейса.
     *
     * <p><b>Предусловия (Given):</b> Низкоуровневое исключение СУБД {@code DataIntegrityViolationException} с фрагментами SQL.</p>
     *
     * <p><b>Действие (When):</b> Исключение передается в глобальный обработчик ошибок.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Ответ содержит статус 500 и обезличенное сообщение, без утечки SQL или стектрейса.
     * </p>
     */
    @Test
    @DisplayName("5. Тест маскирования внутренних ошибок: клиенту не утекают детали SQL, таблицы и стектрейсы")
    void testErrorMaskingOnDatabaseException() {
        DataIntegrityViolationException dbException = new DataIntegrityViolationException(
                "ERROR: insert or update on table \"room_participants\" violates foreign key constraint \"fk_participants_room\" " +
                        "Detail: Key (room_id)=(550e8400-e29b-41d4-a716-446655440000) is not present in table \"rooms\". " +
                        "at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2400) " +
                        "SELECT * FROM rooms;"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms/TEST");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(dbException, request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();

        ErrorResponse errorBody = response.getBody();
        assertThat(errorBody.error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(errorBody.error().message()).isEqualTo("Внутренняя ошибка сервера. Пожалуйста, попробуйте позже.");

        String message = errorBody.error().message();
        assertThat(message)
                .doesNotContainIgnoringCase("SELECT")
                .doesNotContainIgnoringCase("INSERT")
                .doesNotContainIgnoringCase("postgres")
                .doesNotContainIgnoringCase("mysql")
                .doesNotContainIgnoringCase("table")
                .doesNotContainIgnoringCase("stack")
                .doesNotContain("at ")
                .doesNotContain("room_participants");
    }
}
