package ru.nechto.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nechto.entity.RoomParticipantEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomParticipantJpaRepository extends JpaRepository<RoomParticipantEntity, UUID> {

    int countByRoomId(UUID roomId);

    Optional<RoomParticipantEntity> findByRoomIdAndTelegramId(UUID roomId, String telegramId);
}
