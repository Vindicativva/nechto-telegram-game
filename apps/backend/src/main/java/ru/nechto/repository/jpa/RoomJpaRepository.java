package ru.nechto.repository.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.nechto.entity.RoomEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomJpaRepository extends JpaRepository<RoomEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomEntity r WHERE r.code = :code")
    Optional<RoomEntity> findByCodeForUpdate(@Param("code") String code);

    @Query("SELECT r FROM RoomEntity r LEFT JOIN FETCH r.participants WHERE r.code = :code")
    Optional<RoomEntity> findByCodeWithParticipants(@Param("code") String code);

    Optional<RoomEntity> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT r FROM RoomEntity r WHERE r.creatorTelegramId = :creatorId AND (r.status = 'LOBBY' OR r.status = 'IN_PROGRESS')")
    List<RoomEntity> findActiveByCreatorId(@Param("creatorId") String creatorId);

    @Query("SELECT r FROM RoomEntity r LEFT JOIN FETCH r.participants WHERE r.status = 'LOBBY' AND r.lastActivityAt < :beforeTime")
    List<RoomEntity> findInactiveLobbies(@Param("beforeTime") Instant beforeTime);
}
