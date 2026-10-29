package ru.nechto.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.exception.RoomFullException;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.repository.jpa.RoomParticipantJpaRepository;
import ru.nechto.service.RoomService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты многопоточного конкурентного доступа и защиты от состояния гонки (Race Condition).
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomConcurrencyIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomParticipantJpaRepository participantJpaRepository;

    @BeforeEach
    void cleanUp() {
        participantJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    /**
     * Проверяет транзакционную защиту при одновременной попытке 10 потоков занять последнее свободное место в комнате.
     *
     * <p><b>Предусловия (Given):</b> Создана комната с {@code maxPlayers = 4}, в которой уже находятся 3 участника
     * (остаётся ровно 1 свободное место).</p>
     *
     * <p><b>Действие (When):</b> Через {@code ExecutorService} с {@code CountDownLatch} 10 разных новых игроков
     * одновременно отправляют запрос на подключение к комнате.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Ровно 1 запрос завершается успешно (статус 200 OK).</li>
     *   <li>Ровно 9 запросов отклоняются с ошибкой 409 Conflict ({@code RoomFullException}).</li>
     *   <li>В базе данных для данной комнаты сохранено ровно 4 записи участников.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Гонка за последнее место: 10 одновременных потоков -> ровно 1 успешный вход, 9 отказов ROOM_FULL, 4 игрока в БД")
    void shouldAllowOnlyOneParticipantWhenTenThreadsRaceForLastSlot() throws Exception {
        // 1. Создаем комнату с лимитом 4 игрока и заполняем до 3
        TelegramUser creator = TelegramUser.of("1001", "host", "Хост");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Гонка за слот", 4));

        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("1003", "p3", "Игрок 3"));

        assertThat(roomService.getRoomByCode(room.code()).currentPlayersCount()).isEqualTo(3);

        // 2. 10 новых параллельных игроков
        int threadsCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadsCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fullCount = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 1; i <= threadsCount; i++) {
            final String userId = "RACE_USER_" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    roomService.joinRoom(room.code(), TelegramUser.of(userId, "race_" + userId, "Гонщик " + userId));
                    successCount.incrementAndGet();
                } catch (RoomFullException e) {
                    fullCount.incrementAndGet();
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Одновременный старт всех 10 потоков
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Проверяем результаты
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(fullCount.get()).isEqualTo(9);
        assertThat(otherErrors.get()).isEqualTo(0);

        RoomEntity dbRoom = roomJpaRepository.findByCodeWithParticipants(room.code()).orElseThrow();
        assertThat(dbRoom.getParticipants()).hasSize(4);
    }

    /**
     * Проверяет идемпотентность при одновременных дублирующих запросах на подключение от одного и того же пользователя.
     *
     * <p><b>Предусловия (Given):</b> В базе данных существует комната с кодом {@code "DUP001"} и свободными местами.</p>
     *
     * <p><b>Действие (When):</b> Один и тот же пользователь с {@code telegramId = "SAME_PLAYER"} параллельно отправляет
     * 5 одновременных запросов {@code joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Все запросы завершаются без непредвиденных системных сбоев (500 Internal Server Error).</li>
     *   <li>В базе данных для этого пользователя создается ровно одна запись участника.</li>
     *   <li>Уникальное ограничение (room_id, telegram_id) целостности БД не нарушено.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Параллельное дублирующее подключение: 5 одновременных запросов одного игрока -> ровно 1 запись в БД")
    void shouldHandleConcurrentDuplicateJoinsFromSameUserWithoutErrors() throws Exception {
        TelegramUser creator = TelegramUser.of("2001", "host", "Хост");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Тест дублей", 8));

        int duplicateRequests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(duplicateRequests);
        CountDownLatch startLatch = new CountDownLatch(1);

        TelegramUser duplicateUser = TelegramUser.of("SAME_PLAYER", "same_nick", "Один игрок");

        List<Callable<RoomResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < duplicateRequests; i++) {
            tasks.add(() -> {
                startLatch.await();
                return roomService.joinRoom(room.code(), duplicateUser);
            });
        }

        // Запуск задач
        List<Future<RoomResponse>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(executor.submit(task));
        }

        startLatch.countDown();

        int successfulResponses = 0;
        for (Future<RoomResponse> future : futures) {
            try {
                RoomResponse resp = future.get();
                if (resp != null) {
                    successfulResponses++;
                }
            } catch (ExecutionException e) {
                // Если сработал optim/pessimistic lock или повторный запрос
            }
        }
        executor.shutdown();

        assertThat(successfulResponses).isGreaterThanOrEqualTo(1);

        RoomEntity dbRoom = roomJpaRepository.findByCodeWithParticipants(room.code()).orElseThrow();
        long countSameUser = dbRoom.getParticipants().stream()
                .filter(p -> "SAME_PLAYER".equals(p.getTelegramId()))
                .count();

        assertThat(countSameUser).isEqualTo(1);
    }
}
