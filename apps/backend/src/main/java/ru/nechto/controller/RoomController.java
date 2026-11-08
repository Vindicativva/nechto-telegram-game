package ru.nechto.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.nechto.dto.response.ErrorResponse;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.exception.UnauthorizedException;
import ru.nechto.model.TelegramUser;
import ru.nechto.service.RoomService;

import java.util.Map;

@Tag(name = "Rooms", description = "REST API для управления игровыми комнатами и лобби")
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "Создание новой игровой комнаты", description = "Создает новую комнату с уникальным кодом и регистрирует пользователя в качестве хоста.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Комната успешно создана",
                    content = @Content(schema = @Schema(implementation = RoomResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации входных данных (VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован (UNAUTHORIZED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "У пользователя уже есть активная комната (ACTIVE_ROOM_ALREADY_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @Parameter(description = "ID пользователя в Telegram")
            @RequestHeader(value = "X-Telegram-Id", required = false) String headerTelegramId,
            @Parameter(description = "Username пользователя в Telegram")
            @RequestHeader(value = "X-Telegram-Username", required = false) String headerUsername,
            @Parameter(description = "Отображаемое имя пользователя")
            @RequestHeader(value = "X-Telegram-Display-Name", required = false) String headerDisplayName) {

        TelegramUser creator = resolveTelegramUser(
                headerTelegramId, headerUsername, headerDisplayName,
                request.getTelegramId(), request.getUsername(), request.getDisplayName()
        );

        RoomResponse response = roomService.createRoom(creator, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Получение информации о комнате по коду", description = "Возвращает текущее состояние комнаты, параметры готовности и список участников.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Информация о комнате успешно получена",
                    content = @Content(schema = @Schema(implementation = RoomResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный формат кода комнаты (INVALID_ROOM_CODE)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Комната не найдена (ROOM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{code}")
    public ResponseEntity<RoomResponse> getRoom(
            @Parameter(description = "Код комнаты (4-12 символов A-Z, 0-9)", example = "X7K9M2")
            @PathVariable String code) {
        return ResponseEntity.ok(roomService.getRoomByCode(code));
    }

    @Operation(summary = "Подключение к игровой комнате", description = "Подключает игрока к существующей комнате по коду. Поддерживает идемпотентное повторное подключение.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешное подключение или переподключение к комнате",
                    content = @Content(schema = @Schema(implementation = RoomResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный код комнаты или имя игрока (INVALID_ROOM_CODE, INVALID_PLAYER_NAME)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не переданы данные пользователя Telegram (UNAUTHORIZED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Комната не найдена (ROOM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Комната заполнена или игра уже началась (ROOM_FULL, ROOM_ALREADY_STARTED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{code}/join")
    public ResponseEntity<RoomResponse> joinRoom(
            @Parameter(description = "Код комнаты", example = "X7K9M2")
            @PathVariable String code,
            @RequestBody(required = false) JoinRoomRequest request,
            @Parameter(description = "ID пользователя в Telegram")
            @RequestHeader(value = "X-Telegram-Id", required = false) String headerTelegramId,
            @Parameter(description = "Username пользователя в Telegram")
            @RequestHeader(value = "X-Telegram-Username", required = false) String headerUsername,
            @Parameter(description = "Отображаемое имя пользователя")
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

    @Operation(summary = "Выход из игровой комнаты", description = "Удаляет игрока из комнаты. Если выходит создатель, права хоста передаются следующему по времени подключения игроку.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Игрок успешно вышел из комнаты",
                    content = @Content(schema = @Schema(implementation = RoomResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный код комнаты (INVALID_ROOM_CODE)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не переданы данные пользователя Telegram (UNAUTHORIZED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Комната не найдена (ROOM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{code}/leave")
    public ResponseEntity<RoomResponse> leaveRoom(
            @Parameter(description = "Код комнаты", example = "X7K9M2")
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> body,
            @Parameter(description = "ID пользователя в Telegram")
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

    @Operation(summary = "Изменение настроек комнаты", description = "Позволяет создателю комнаты изменить максимальное количество игроков.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Настройки успешно обновлены",
                    content = @Content(schema = @Schema(implementation = RoomResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректное значение maxPlayers (VALIDATION_ERROR, INVALID_MAX_PLAYERS)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не переданы данные пользователя Telegram (UNAUTHORIZED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Пользователь не является создателем комнаты (FORBIDDEN_NOT_CREATOR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Комната не найдена (ROOM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Игра уже началась (ROOM_ALREADY_STARTED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{code}/settings")
    public ResponseEntity<RoomResponse> updateSettings(
            @Parameter(description = "Код комнаты", example = "X7K9M2")
            @PathVariable String code,
            @Valid @RequestBody UpdateRoomSettingsRequest request,
            @Parameter(description = "ID пользователя в Telegram")
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
