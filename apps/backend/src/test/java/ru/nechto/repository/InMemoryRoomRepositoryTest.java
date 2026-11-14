package ru.nechto.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.nechto.model.Player;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты потокобезопасного in-memory репозитория комнат {@link InMemoryRoomRepository}.
 */
class InMemoryRoomRepositoryTest {

    private InMemoryRoomRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRoomRepository();
    }

    private Room createTestRoom(String code, String name, String creatorId, RoomStatus status) {
        Room room = new Room(code, name, 6, status);
        room.setId(UUID.randomUUID());
        room.setCreatorId(creatorId);
        room.setCreatedAt(Instant.now());
        room.setLastActivityAt(Instant.now());
        room.addPlayer(new Player(creatorId, "host_user", true));
        return room;
    }

    /**
     * Проверяет сохранение комнаты в репозиторий и её успешный поиск по коду.
     *
     * <p><b>Предусловия (Given):</b> Инициализированный чистый репозиторий и созданная комната
     * с кодом {@code "ABC123"} в статусе {@code LOBBY}.</p>
     *
     * <p><b>Действие (When):</b> Комната сохраняется в репозиторий методом {@code save()},
     * а затем запрашивается методом {@code findByCode("ABC123")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Метод {@code findByCode} возвращает непустой {@link Optional}.</li>
     *   <li>Код и название найденной комнаты совпадают с сохраненной.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Успешное сохранение и поиск комнаты по коду")
    void saveAndFindByCode_Success() {
        Room room = createTestRoom("ABC123", "Тестовая комната", "1001", RoomStatus.LOBBY);

        repository.save(room);
        Optional<Room> found = repository.findByCode("ABC123");

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("ABC123");
        assertThat(found.get().getName()).isEqualTo("Тестовая комната");
    }

    /**
     * Проверяет регистронезависимый поиск комнат по коду (case-insensitivity).
     *
     * <p><b>Предусловия (Given):</b> В репозитории сохранена комната с верхнерегистровым
     * кодом {@code "XYZ789"}.</p>
     *
     * <p><b>Действие (When):</b> Поиск выполняется по коду в нижнем регистре {@code "xyz789"}
     * и со случайными пробелами {@code " xyz789 "}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * В обоих случаях комната успешно находится.
     * </p>
     */
    @Test
    @DisplayName("Поиск комнаты по коду не зависит от регистра символов и концевых пробелов")
    void findByCode_CaseInsensitiveAndTrimming() {
        Room room = createTestRoom("XYZ789", "Регистронезависимая", "1002", RoomStatus.LOBBY);
        repository.save(room);

        Optional<Room> lower = repository.findByCode("xyz789");
        Optional<Room> padded = repository.findByCode("  xyz789  ");

        assertThat(lower).isPresent();
        assertThat(lower.get().getCode()).isEqualTo("XYZ789");
        assertThat(padded).isPresent();
        assertThat(padded.get().getCode()).isEqualTo("XYZ789");
    }

    /**
     * Проверяет поиск комнаты по внутреннему уникальному идентификатору {@link UUID}.
     *
     * <p><b>Предусловия (Given):</b> В репозитории сохранена комната с определенным {@link UUID}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code findById(uuid)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Возвращается найденная комната с соответствующим идентификатором.
     * </p>
     */
    @Test
    @DisplayName("Поиск комнаты по уникальному UUID идентификатору")
    void findById_Success() {
        Room room = createTestRoom("UUID01", "UUID комната", "1003", RoomStatus.LOBBY);
        repository.save(room);

        Optional<Room> found = repository.findById(room.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(room.getId());
    }

    /**
     * Проверяет поведение метода {@code findById} при передаче {@code null}
     * или несуществующего идентификатора.
     *
     * <p><b>Предусловия (Given):</b> Репозиторий комнат без искомого идентификатора.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code findById} с {@code null} и случайным UUID.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Метод возвращает {@link Optional#empty()}.</p>
     */
    @Test
    @DisplayName("Поиск по несуществующему UUID или null возвращает Optional.empty()")
    void findById_MissingOrNull_ReturnsEmpty() {
        assertThat(repository.findById(null)).isEmpty();
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    /**
     * Проверяет поиск активной комнаты создателя, находящейся в статусе {@code LOBBY}.
     *
     * <p><b>Предусловия (Given):</b> В репозитории сохранена комната пользователя {@code "1004"}
     * в статусе {@code LOBBY}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code findActiveByCreatorId("1004")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Возвращается активная комната создателя.
     * </p>
     */
    @Test
    @DisplayName("Поиск активной комнаты создателя в статусе LOBBY")
    void findActiveByCreatorId_LobbyStatus_ReturnsRoom() {
        Room room = createTestRoom("LOBB01", "Лобби создателя", "1004", RoomStatus.LOBBY);
        repository.save(room);

        Optional<Room> active = repository.findActiveByCreatorId("1004");

        assertThat(active).isPresent();
        assertThat(active.get().getCode()).isEqualTo("LOBB01");
    }

    /**
     * Проверяет поиск активной комнаты создателя, когда статус комнаты перешел в {@code IN_PROGRESS}.
     *
     * <p><b>Предусловия (Given):</b> Комната пользователя {@code "1005"} находится в статусе {@code IN_PROGRESS}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code findActiveByCreatorId("1005")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Возвращается комната, так как запущенная игра также считается активной сессией.
     * </p>
     */
    @Test
    @DisplayName("Поиск активной комнаты создателя в статусе IN_PROGRESS")
    void findActiveByCreatorId_InProgressStatus_ReturnsRoom() {
        Room room = createTestRoom("PROG01", "Игра в процессе", "1005", RoomStatus.IN_PROGRESS);
        repository.save(room);

        Optional<Room> active = repository.findActiveByCreatorId("1005");

        assertThat(active).isPresent();
        assertThat(active.get().getCode()).isEqualTo("PROG01");
    }

    /**
     * Проверяет, что завершенные или архивированные комнаты не считаются активными для создателя.
     *
     * <p><b>Предусловия (Given):</b> В репозитории находятся две комнаты пользователя {@code "1006"}:
     * одна со статусом {@code FINISHED}, вторая со статусом {@code ARCHIVED}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code findActiveByCreatorId("1006")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Метод возвращает {@link Optional#empty()}, позволяя пользователю создать новую комнату.
     * </p>
     */
    @Test
    @DisplayName("Завершенные и архивированные комнаты игнорируются при поиске активной комнаты")
    void findActiveByCreatorId_FinishedOrArchived_ReturnsEmpty() {
        Room finished = createTestRoom("FIN001", "Завершенная", "1006", RoomStatus.FINISHED);
        Room archived = createTestRoom("ARC001", "Архивная", "1006", RoomStatus.ARCHIVED);
        repository.save(finished);
        repository.save(archived);

        Optional<Room> active = repository.findActiveByCreatorId("1006");

        assertThat(active).isEmpty();
    }

    /**
     * Проверяет метод {@code existsByCode} для существующего и несуществующего кодов.
     *
     * <p><b>Предусловия (Given):</b> В репозитории присутствует комната с кодом {@code "EXT123"}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code existsByCode} для существующего и фиктивного кодов.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Для {@code "EXT123"} возвращается {@code true}.</li>
     *   <li>Для {@code "NOPE99"} возвращается {@code false}.</li>
     *   <li>Для {@code null} и пустой строки возвращается {@code false}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Проверка существования комнаты по коду через existsByCode")
    void existsByCode_ReturnsCorrectBoolean() {
        repository.save(createTestRoom("EXT123", "Комната", "1007", RoomStatus.LOBBY));

        assertThat(repository.existsByCode("EXT123")).isTrue();
        assertThat(repository.existsByCode("ext123")).isTrue();
        assertThat(repository.existsByCode("NOPE99")).isFalse();
        assertThat(repository.existsByCode(null)).isFalse();
        assertThat(repository.existsByCode("  ")).isFalse();
    }

    /**
     * Проверяет удаление комнаты по коду.
     *
     * <p><b>Предусловия (Given):</b> В репозитории зарегистрирована комната с кодом {@code "DEL001"}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code delete("DEL001")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Комната больше не находится методом {@code findByCode}.</li>
     *   <li>Метод {@code existsByCode} возвращает {@code false}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Удаление комнаты из репозитория по коду")
    void delete_RemovesRoomSuccessfully() {
        repository.save(createTestRoom("DEL001", "На удаление", "1008", RoomStatus.LOBBY));
        assertThat(repository.existsByCode("DEL001")).isTrue();

        repository.delete("DEL001");

        assertThat(repository.existsByCode("DEL001")).isFalse();
        assertThat(repository.findByCode("DEL001")).isEmpty();
    }

    /**
     * Проверяет получение полного списка всех комнат через метод {@code findAll}.
     *
     * <p><b>Предусловия (Given):</b> В репозиторий добавлены 3 различные комнаты.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code findAll()}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Метод возвращает список из 3 элементов, содержащий все добавленные комнаты.
     * </p>
     */
    @Test
    @DisplayName("Получение списка всех комнат через findAll")
    void findAll_ReturnsAllStoredRooms() {
        repository.save(createTestRoom("ALL001", "Комната 1", "101", RoomStatus.LOBBY));
        repository.save(createTestRoom("ALL002", "Комната 2", "102", RoomStatus.LOBBY));
        repository.save(createTestRoom("ALL003", "Комната 3", "103", RoomStatus.LOBBY));

        List<Room> all = repository.findAll();

        assertThat(all).hasSize(3);
        assertThat(all).extracting(Room::getCode).containsExactlyInAnyOrder("ALL001", "ALL002", "ALL003");
        assertThat(repository.count()).isEqualTo(3);
    }

    /**
     * Проверяет выброс исключения {@link IllegalArgumentException} при попытке сохранить
     * {@code null} объект или комнату без кода.
     *
     * <p><b>Предусловия (Given):</b> Инициализированный репозиторий комнат.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code save(null)} и {@code save(roomWithNullCode)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@link IllegalArgumentException}.
     * </p>
     */
    @Test
    @DisplayName("Выброс IllegalArgumentException при сохранении некорректных объектов")
    void save_InvalidRoom_ThrowsException() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(IllegalArgumentException.class);

        Room nullCodeRoom = new Room();
        nullCodeRoom.setCode(null);
        assertThatThrownBy(() -> repository.save(nullCodeRoom))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Проверяет потокобезопасность {@link InMemoryRoomRepository} при одновременных
     * операциях сохранения, чтения и удаления из 20 параллельных потоков.
     *
     * <p><b>Предусловия (Given):</b> Пул из 20 параллельных потоков и синхронизатор {@link CountDownLatch}.</p>
     *
     * <p><b>Действие (When):</b> Каждый поток одновременно сохраняет 50 комнат с уникальными кодами,
     * выполняет поиск по коду и проверяет существование.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Все задачи успешно завершаются без исключений и deadlock-ов.</li>
     *   <li>В репозитории корректно зарегистрированы 1000 комнат (20 потоков * 50 комнат).</li>
     *   <li>Конкурентная хеш-таблица сохраняет целостность данных.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Потокобезопасность репозитория при конкурентной записи и чтении (20 потоков)")
    void concurrentOperations_ThreadSafeExecution() throws Exception {
        int threadsCount = 20;
        int roomsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int t = 0; t < threadsCount; t++) {
            final int threadId = t;
            tasks.add(() -> {
                startLatch.await();
                for (int i = 0; i < roomsPerThread; i++) {
                    String code = String.format("T%02dR%03d", threadId, i);
                    Room room = createTestRoom(code, "Room-" + code, "user-" + threadId, RoomStatus.LOBBY);
                    repository.save(room);
                    Optional<Room> found = repository.findByCode(code);
                    if (found.isEmpty() || !repository.existsByCode(code)) {
                        return false;
                    }
                }
                return true;
            });
        }

        startLatch.countDown();
        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        for (Future<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }
        assertThat(repository.count()).isEqualTo(threadsCount * roomsPerThread);
    }
}
