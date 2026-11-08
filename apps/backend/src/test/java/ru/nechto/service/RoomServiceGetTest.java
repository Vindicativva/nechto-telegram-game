package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.InvalidRoomCodeException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomStatus;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты поиска и получения информации о комнате (RoomService.getRoomByCode).
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceGetTest {

    @Mock
    private RoomJpaRepository roomJpaRepository;

    @Mock
    private RoomWebSocketBroadcaster broadcaster;

    @Mock
    private RoomCodeGenerator codeGenerator;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomJpaRepository, broadcaster, codeGenerator);
    }

    /**
     * Проверяет успешное получение информации о комнате по существующему коду в верхнем регистре.
     *
     * <p><b>Предусловия (Given):</b> В базе данных существует активная комната с кодом {@code "X7K9M2"}
     * и одним участником-создателем.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code getRoomByCode("X7K9M2")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается DTO {@code RoomResponse} с кодом {@code "X7K9M2"}.</li>
     *   <li>Данные участников и параметры комнаты соответствуют сохранённым в БД.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Успешное получение комнаты по существующему коду в верхнем регистре")
    void getRoomByCode_ExistingCode_Success() {
        RoomEntity room = createSampleRoomEntity("X7K9M2", "Комната", 6);
        when(roomJpaRepository.findByCodeWithParticipants("X7K9M2")).thenReturn(Optional.of(room));

        RoomResponse response = roomService.getRoomByCode("X7K9M2");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("X7K9M2");
        assertThat(response.name()).isEqualTo("Комната");
        assertThat(response.maxPlayers()).isEqualTo(6);
    }

    /**
     * Проверяет регистронезависимый поиск и автоматическую обрезку пробелов при передаче кода комнаты.
     *
     * <p><b>Предусловия (Given):</b> В базе данных сохранена комната с кодом {@code "X7K9M2"}.</p>
     *
     * <p><b>Действие (When):</b> Метод {@code getRoomByCode} вызывается с вариациями регистра и пробелов:
     * {@code "x7k9m2"}, {@code " X7k9M2 "}, {@code "  x7k9m2  "}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Код нормализуется в {@code "X7K9M2"} и комната успешно находится.
     * </p>
     */
    @ParameterizedTest(name = "Поиск по коду: \"{0}\"")
    @ValueSource(strings = {"x7k9m2", " X7k9M2 ", "  x7k9m2  "})
    @DisplayName("Регистронезависимый поиск комнаты и очистка пробелов (trim)")
    void getRoomByCode_CaseInsensitiveAndWhitespace_Success(String inputCode) {
        RoomEntity room = createSampleRoomEntity("X7K9M2", "Партия", 8);
        when(roomJpaRepository.findByCodeWithParticipants("X7K9M2")).thenReturn(Optional.of(room));

        RoomResponse response = roomService.getRoomByCode(inputCode);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("X7K9M2");
    }

    /**
     * Проверяет выброс исключения RoomNotFoundException (404) при поиске несуществующего кода комнаты.
     *
     * <p><b>Предусловия (Given):</b> Репозиторий возвращает {@code Optional.empty()} для переданного кода.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code getRoomByCode("NOTFND")}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code RoomNotFoundException} с кодом ошибки {@code ROOM_NOT_FOUND}.
     * </p>
     */
    @Test
    @DisplayName("Выброс 404 ROOM_NOT_FOUND при запросе несуществующей комнаты")
    void getRoomByCode_NonExistentCode_ThrowsRoomNotFoundException() {
        when(roomJpaRepository.findByCodeWithParticipants("NOTFND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomByCode("NOTFND"))
                .isInstanceOf(RoomNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ROOM_NOT_FOUND");
    }

    /**
     * Проверяет отказ и выброс InvalidRoomCodeException (400) при передаче кода невалидного формата.
     *
     * <p><b>Предусловия (Given):</b> Некорректные строки кодов (короткие, длинные, спецсимволы, пробелы).</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code getRoomByCode}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code InvalidRoomCodeException} с кодом ошибки {@code INVALID_ROOM_CODE}.
     * </p>
     */
    @ParameterizedTest(name = "Невалидный код: \"{0}\"")
    @ValueSource(strings = {"X7K-9M!", "DROP TABLE", "123", "AB", "TOOLONGCODE12345", "<script>"})
    @DisplayName("Выброс 400 INVALID_ROOM_CODE при недопустимом формате кода комнаты")
    void getRoomByCode_InvalidCodePattern_ThrowsInvalidRoomCodeException(String invalidCode) {
        assertThatThrownBy(() -> roomService.getRoomByCode(invalidCode))
                .isInstanceOf(InvalidRoomCodeException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ROOM_CODE");
    }

    private RoomEntity createSampleRoomEntity(String code, String name, int maxPlayers) {
        Instant now = Instant.now();
        RoomEntity room = new RoomEntity(
                UUID.randomUUID(), code, name, "1001", RoomStatus.LOBBY, (short) maxPlayers, now, now, now
        );
        RoomParticipantEntity creator = new RoomParticipantEntity(
                UUID.randomUUID(), room, "1001", "creator", "Хост", true, ConnectionStatus.ONLINE, now, null
        );
        room.addParticipant(creator);
        return room;
    }
}
