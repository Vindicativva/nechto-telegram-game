package ru.nechto.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.request.JoinRoomRequest;
import ru.nechto.dto.request.UpdateRoomSettingsRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.exception.UnauthorizedException;
import ru.nechto.model.TelegramUser;
import ru.nechto.service.RoomService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @RequestHeader(value = "X-Telegram-Id", required = false) String headerTelegramId,
            @RequestHeader(value = "X-Telegram-Username", required = false) String headerUsername,
            @RequestHeader(value = "X-Telegram-Display-Name", required = false) String headerDisplayName) {

        TelegramUser creator = resolveTelegramUser(
                headerTelegramId, headerUsername, headerDisplayName,
                request.getTelegramId(), request.getUsername(), request.getDisplayName()
        );

        RoomResponse response = roomService.createRoom(creator, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String code) {
        return ResponseEntity.ok(roomService.getRoomByCode(code));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<RoomResponse> joinRoom(
            @PathVariable String code,
            @RequestBody(required = false) JoinRoomRequest request,
            @RequestHeader(value = "X-Telegram-Id", required = false) String headerTelegramId,
            @RequestHeader(value = "X-Telegram-Username", required = false) String headerUsername,
            @RequestHeader(value = "X-Telegram-Display-Name", required = false) String headerDisplayName) {

        String bodyTelegramId = request != null ? request.getTelegramId() : null;
        String bodyUsername = request != null ? request.getUsername() : null;
        String bodyDisplayName = request != null ? request.getDisplayName() : null;

        TelegramUser user = resolveTelegramUser(
                headerTelegramId, headerUsername, headerDisplayName,
                bodyTelegramId, bodyUsername, bodyDisplayName
        );

        RoomResponse response = roomService.joinRoom(code, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<RoomResponse> leaveRoom(
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Telegram-Id", required = false) String headerTelegramId) {

        String telegramId = (headerTelegramId != null && !headerTelegramId.isBlank()) ?
                headerTelegramId.trim() : (body != null && body.containsKey("telegramId") ?
                String.valueOf(body.get("telegramId")).trim() : null);

        if (telegramId == null || telegramId.isBlank()) {
            throw new UnauthorizedException();
        }

        RoomResponse response = roomService.leaveRoom(code, telegramId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{code}/settings")
    public ResponseEntity<RoomResponse> updateSettings(
            @PathVariable String code,
            @Valid @RequestBody UpdateRoomSettingsRequest request,
            @RequestHeader(value = "X-Telegram-Id", required = false) String headerTelegramId) {

        String telegramId = (headerTelegramId != null && !headerTelegramId.isBlank()) ?
                headerTelegramId.trim() : (request.getTelegramId() != null && !request.getTelegramId().isBlank() ?
                request.getTelegramId().trim() : null);

        if (telegramId == null || telegramId.isBlank()) {
            throw new UnauthorizedException();
        }

        RoomResponse response = roomService.updateSettings(code, telegramId, request.getMaxPlayers());
        return ResponseEntity.ok(response);
    }

    private TelegramUser resolveTelegramUser(String headerId, String headerUsername, String headerDisplayName,
                                             String bodyId, String bodyUsername, String bodyDisplayName) {
        String id = (headerId != null && !headerId.isBlank()) ? headerId.trim() : bodyId;
        if (id == null || id.isBlank()) {
            throw new UnauthorizedException();
        }
        String username = (headerUsername != null && !headerUsername.isBlank()) ? headerUsername.trim() : bodyUsername;
        String displayName = (headerDisplayName != null && !headerDisplayName.isBlank()) ? headerDisplayName.trim() : bodyDisplayName;
        return TelegramUser.of(id, username, displayName);
    }
}
