package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomStatus;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты выхода из комнаты и миграции хоста (RoomService.leaveRoom).
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceLeaveTest {

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
     * Проверяет выход обычного игрока: уменьшается счетчик участников,
     * а если игроков стало меньше 4 (например, было 4, стало 3), флаг isReadyToStart сбрасывается в false.
     *
     * <p><b>Предусловия (Given):</b> В комнате находятся 4 игрока, флаг готовности {@code isReadyToStart == true}.</p>
     *
     * <p><b>Действие (When):</b> Четвертый игрок (обычный участник) отправляет запрос на выход.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Количество игроков уменьшается до 3.</li>
     *   <li>Флаг {@code isReadyToStart} становится {@code false}.</li>
     *   <li>Создатель комнаты не изменяется.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Выход обычного игрока: уменьшение счетчика участников и сброс isReadyToStart в false при < 4 игроков")
    void leaveRoom_NormalPlayerLeaves_DecreasesCountAndResetsReadyToStart() {
        RoomEntity room = createRoomWithHost("LEAV01", 6);
        addParticipant(room, "2", "P2", false, Instant.now().plusSeconds(1));
        addParticipant(room, "3", "P3", false, Instant.now().plusSeconds(2));
        addParticipant(room, "4", "P4", false, Instant.now().plusSeconds(3));

        assertThat(room.getParticipants()).hasSize(4);

        when(roomJpaRepository.findByCodeForUpdate("LEAV01")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.leaveRoom("LEAV01", "4");

        assertThat(response.currentPlayersCount()).isEqualTo(3);
        assertThat(response.isReadyToStart()).isFalse();
        assertThat(response.creatorId()).isEqualTo("1001");
        assertThat(response.players().stream().noneMatch(p -> "4".equals(p.getTelegramId()))).isTrue();
    }

    /**
     * Проверяет передачу прав создателя (Host Migration) при выходе текущего хоста:
     * права хоста автоматически переходят участнику с самым ранним временем подключения (joinedAt).
     *
     * <p><b>Предусловия (Given):</b> В комнате 3 игрока: хост (joinedAt t0), игрок 2 (joinedAt t1), игрок 3 (joinedAt t2), где t1 < t2.</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет запрос на выход {@code leaveRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Создатель удаляется из комнаты.</li>
     *   <li>Игрок 2 (с наименьшим joinedAt) становится новым создателем (isCreator = true, creatorId = "2").</li>
     *   <li>Игрок 3 остается обычным участником.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Host Migration: при выходе создателя права хоста переходят игроку с самым ранним joinedAt")
    void leaveRoom_CreatorLeaves_MigratesHostToEarliestParticipant() {
        Instant t0 = Instant.now().minusSeconds(100);
        Instant t1 = Instant.now().minusSeconds(50);
        Instant t2 = Instant.now().minusSeconds(20);

        RoomEntity room = new RoomEntity(
                UUID.randomUUID(), "HOSTM1", "Комната", "1001", RoomStatus.LOBBY,
                (short) 6, t0, t0, t0
        );
        RoomParticipantEntity host = new RoomParticipantEntity(
                UUID.randomUUID(), room, "1001", "host", "Хост", true, ConnectionStatus.ONLINE, t0, null
        );
        room.addParticipant(host);

        addParticipant(room, "2", "Второй", false, t1);
        addParticipant(room, "3", "Третий", false, t2);

        when(roomJpaRepository.findByCodeForUpdate("HOSTM1")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.leaveRoom("HOSTM1", "1001");

        assertThat(response.currentPlayersCount()).isEqualTo(2);
        assertThat(response.creatorId()).isEqualTo("2");

        var newHost = response.players().stream()
                .filter(p -> "2".equals(p.getTelegramId()))
                .findFirst().orElseThrow();
        assertThat(newHost.isCreator()).isTrue();

        var thirdPlayer = response.players().stream()
                .filter(p -> "3".equals(p.getTelegramId()))
                .findFirst().orElseThrow();
        assertThat(thirdPlayer.isCreator()).isFalse();
    }

    /**
     * Проверяет автоматическое удаление комнаты из БД при выходе последнего оставшегося игрока.
     *
     * <p><b>Предусловия (Given):</b> В комнате в статусе LOBBY остался ровно 1 участник (создатель).</p>
     *
     * <p><b>Действие (When):</b> Создатель покидает комнату.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Комната удаляется из репозитория через {@code roomJpaRepository.delete(room)}.</li>
     *   <li>Количество оставшихся игроков равно 0.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Выход последнего игрока: автоматическое удаление комнаты в статусе LOBBY")
    void leaveRoom_LastPlayerLeaves_DeletesRoom() {
        RoomEntity room = createRoomWithHost("EMPTY1", 6);

        when(roomJpaRepository.findByCodeForUpdate("EMPTY1")).thenReturn(Optional.of(room));

        RoomResponse response = roomService.leaveRoom("EMPTY1", "1001");

        assertThat(response.currentPlayersCount()).isEqualTo(0);
        verify(roomJpaRepository).delete(room);
    }

    private RoomEntity createRoomWithHost(String code, int maxPlayers) {
        Instant now = Instant.now();
        RoomEntity room = new RoomEntity(
                UUID.randomUUID(), code, "Комната " + code, "1001", RoomStatus.LOBBY,
                (short) maxPlayers, now, now, now
        );
        RoomParticipantEntity host = new RoomParticipantEntity(
                UUID.randomUUID(), room, "1001", "creator", "Хост", true, ConnectionStatus.ONLINE, now, null
        );
        room.addParticipant(host);
        return room;
    }

    private void addParticipant(RoomEntity room, String telegramId, String name, boolean isCreator, Instant joinedAt) {
        RoomParticipantEntity participant = new RoomParticipantEntity(
                UUID.randomUUID(), room, telegramId, telegramId, name, isCreator,
                ConnectionStatus.ONLINE, joinedAt, null
        );
        room.addParticipant(participant);
    }
}
