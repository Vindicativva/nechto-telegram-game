package ru.nechto.dto.game;

import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.model.Card;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.PlayerRole;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TurnDirection;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверное активное состояние начатой партии в оперативной памяти приложения.
 * Хранит полный защищенный контекст игры.
 */
public class ActiveGameState {

    private final String roomCode;
    private volatile RoomStatus status;
    private final List<String> seatingOrder; // telegramIds in seating order (0..N-1)
    private final Map<String, PlayerRole> roles;
    private final Map<String, List<Card>> hands;
    private final List<Card> drawPile;
    private final List<Card> discardPile;
    private volatile int currentTurnIndex;
    private volatile TurnDirection turnDirection;
    private final Map<String, Integer> quarantineRounds;
    private final Map<String, Boolean> aliveStatus;
    private final Instant startedAt;

    public ActiveGameState(String roomCode,
                           RoomStatus status,
                           List<String> seatingOrder,
                           Map<String, PlayerRole> roles,
                           Map<String, List<Card>> hands,
                           List<Card> drawPile,
                           List<Card> discardPile,
                           int currentTurnIndex,
                           TurnDirection turnDirection,
                           Map<String, Integer> quarantineRounds,
                           Map<String, Boolean> aliveStatus,
                           Instant startedAt) {
        this.roomCode = roomCode;
        this.status = status;
        this.seatingOrder = Collections.unmodifiableList(new ArrayList<>(seatingOrder));
        this.roles = new ConcurrentHashMap<>(roles);
        Map<String, List<Card>> handMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<Card>> entry : hands.entrySet()) {
            handMap.put(entry.getKey(), Collections.synchronizedList(new ArrayList<>(entry.getValue())));
        }
        this.hands = handMap;
        this.drawPile = Collections.synchronizedList(new ArrayList<>(drawPile));
        this.discardPile = Collections.synchronizedList(new ArrayList<>(discardPile));
        this.currentTurnIndex = currentTurnIndex;
        this.turnDirection = turnDirection;
        this.quarantineRounds = new ConcurrentHashMap<>(quarantineRounds);
        this.aliveStatus = new ConcurrentHashMap<>(aliveStatus);
        this.startedAt = startedAt;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public List<String> getSeatingOrder() {
        return seatingOrder;
    }

    public PlayerRole getRole(String telegramId) {
        return roles.get(telegramId);
    }

    public Map<String, PlayerRole> getRoles() {
        return Collections.unmodifiableMap(roles);
    }

    public List<Card> getHand(String telegramId) {
        List<Card> hand = hands.get(telegramId);
        return hand != null ? Collections.unmodifiableList(hand) : Collections.emptyList();
    }

    public Map<String, List<Card>> getHands() {
        return Collections.unmodifiableMap(hands);
    }

    public List<Card> getDrawPile() {
        return Collections.unmodifiableList(drawPile);
    }

    public List<Card> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    public int getCurrentTurnIndex() {
        return currentTurnIndex;
    }

    public void setCurrentTurnIndex(int currentTurnIndex) {
        this.currentTurnIndex = currentTurnIndex;
    }

    public String getCurrentTurnPlayerId() {
        if (seatingOrder.isEmpty()) return null;
        int idx = Math.floorMod(currentTurnIndex, seatingOrder.size());
        return seatingOrder.get(idx);
    }

    public TurnDirection getTurnDirection() {
        return turnDirection;
    }

    public void setTurnDirection(TurnDirection turnDirection) {
        this.turnDirection = turnDirection;
    }

    public int getPosition(String telegramId) {
        return seatingOrder.indexOf(telegramId);
    }

    public boolean isAlive(String telegramId) {
        return aliveStatus.getOrDefault(telegramId, true);
    }

    public void setAlive(String telegramId, boolean alive) {
        aliveStatus.put(telegramId, alive);
    }

    public int getQuarantineRounds(String telegramId) {
        return quarantineRounds.getOrDefault(telegramId, 0);
    }

    public void setQuarantineRounds(String telegramId, int rounds) {
        quarantineRounds.put(telegramId, rounds);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public PublicGameState toPublicGameState(List<RoomParticipantEntity> participants) {
        Map<String, RoomParticipantEntity> pMap = new HashMap<>();
        if (participants != null) {
            for (RoomParticipantEntity p : participants) {
                pMap.put(p.getTelegramId(), p);
            }
        }

        List<PublicPlayerInfo> playerInfos = new ArrayList<>(seatingOrder.size());
        for (int i = 0; i < seatingOrder.size(); i++) {
            String telegramId = seatingOrder.get(i);
            RoomParticipantEntity entity = pMap.get(telegramId);
            String username = entity != null ? entity.getUsername() : null;
            String displayName = entity != null ? entity.getDisplayName() : telegramId;
            ConnectionStatus connStatus = entity != null ? entity.getConnectionStatus() : ConnectionStatus.ONLINE;
            int cardsCount = getHand(telegramId).size();
            boolean alive = isAlive(telegramId);
            int quarantine = getQuarantineRounds(telegramId);

            playerInfos.add(new PublicPlayerInfo(
                    telegramId,
                    username,
                    displayName,
                    i,
                    cardsCount,
                    alive,
                    quarantine,
                    connStatus
            ));
        }

        return new PublicGameState(
                roomCode,
                status,
                currentTurnIndex,
                getCurrentTurnPlayerId(),
                turnDirection,
                drawPile.size(),
                discardPile.size(),
                Collections.unmodifiableList(playerInfos)
        );
    }

    public PlayerPrivateState toPlayerPrivateState(String telegramId) {
        PlayerRole role = roles.get(telegramId);
        List<Card> cards = getHand(telegramId);
        int pos = getPosition(telegramId);
        boolean isMyTurn = telegramId != null && telegramId.equals(getCurrentTurnPlayerId());
        List<String> actions = isMyTurn ? List.of("DRAW_CARD") : List.of();

        return new PlayerPrivateState(
                roomCode,
                telegramId,
                role,
                cards,
                pos,
                isMyTurn,
                actions
        );
    }
}
