# Документация REST API и WebSocket-протокола «Нечто» (Stay Away!)

Данный документ содержит полное описание контрактов REST API и WebSocket-протокола бэкенда для интеграции с клиентом (Frontend / Telegram Mini App).

---

## 1. Общие сведения и интерактивная документация (Swagger)

- **Базовый URL API:** `http://localhost:8080/api/v1`
- **Swagger UI (OpenAPI 3.0):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON спецификация:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **WebSocket URL:** `ws://localhost:8080/ws`

---

## 2. Аутентификация и идентификация пользователя Telegram

При выполнении HTTP-запросов клиент идентифицирует пользователя Telegram через HTTP-заголовки (или поля в теле запроса):

| Заголовок | Тип | Обязательный | Описание |
| :--- | :--- | :--- | :--- |
| `X-Telegram-Id` | `String` / `Long` | Да | Уникальный цифровой ID пользователя в Telegram (например, `"123456789"`) |
| `X-Telegram-Username` | `String` | Нет | Username пользователя в Telegram (например, `"ivan_ivanov"`) |
| `X-Telegram-Display-Name` | `String` | Нет | Отображаемое имя игрока (1–64 символа, без HTML-тегов) |

*Примечание:* Если заголовки не переданы, значения `telegramId`, `username`, `displayName` могут быть переданы в JSON-теле запроса (в зависимости от эндпоинта). Если идентификатор пользователя не передан ни в заголовке, ни в теле, сервер возвращает `401 Unauthorized`.

---

## 3. Единый формат ответов об ошибках

Все HTTP-ошибки возвращаются в строго стандартизированной JSON-структуре:

```json
{
  "timestamp": "2026-10-29T12:15:30.123Z",
  "status": 409,
  "path": "/api/v1/rooms/X7K9M2/join",
  "error": {
    "code": "ROOM_FULL",
    "message": "Комната заполнена",
    "details": []
  }
}
```

### Формат ошибки валидации (`400 Bad Request` / `VALIDATION_ERROR`):

```json
{
  "timestamp": "2026-10-29T12:16:00.000Z",
  "status": 400,
  "path": "/api/v1/rooms",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Ошибка валидации входных параметров",
    "details": [
      {
        "field": "maxPlayers",
        "issue": "Количество участников должно быть в диапазоне от 4 до 12"
      },
      {
        "field": "name",
        "issue": "Название комнаты должно содержать от 1 до 32 символов"
      }
    ]
  }
}
```

### Сводная таблица кодов ошибок:

| HTTP Статус | Код ошибки (`error.code`) | Описание ситуации |
| :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_ERROR` | Некорректные параметры DTO (`name`, `maxPlayers` и др.) |
| `400 Bad Request` | `INVALID_ROOM_CODE` | Неверный формат кода комнаты (должен быть `^[A-Z0-9]{4,12}$`) |
| `400 Bad Request` | `INVALID_PLAYER_NAME` | Имя игрока пустое, > 64 символов или содержит HTML-теги/управляющие символы |
| `400 Bad Request` | `INVALID_MAX_PLAYERS` | Попытка установить `maxPlayers` меньше текущего количества участников в комнате |
| `401 Unauthorized` | `UNAUTHORIZED` | Отсутствует `X-Telegram-Id` и идентификатор пользователя в теле |
| `403 Forbidden` | `FORBIDDEN_NOT_CREATOR` | Попытка изменить настройки комнаты не создателем |
| `404 Not Found` | `ROOM_NOT_FOUND` | Комната с данным кодом не найдена или была удалена |
| `409 Conflict` | `ROOM_FULL` | В комнате достигнут лимит игроков `maxPlayers` |
| `409 Conflict` | `ROOM_ALREADY_STARTED` | Попытка входа или смены настроек, когда статус не `LOBBY` |
| `409 Conflict` | `ACTIVE_ROOM_ALREADY_EXISTS` | У данного пользователя уже есть активная созданная комната |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | Внутренняя ошибка сервера (детали SQL и стектрейсы маскируются) |

---

## 4. Спецификация REST API

### 4.1. Проверка работоспособности (Healthcheck)
`GET /api/health`
- **Ответ (`200 OK`):**
  ```text
  Hello, Nechto!
  ```

---

### 4.2. Создание игровой комнаты
`POST /api/v1/rooms`

**Заголовки:**
- `X-Telegram-Id: 123456789`
- `X-Telegram-Username: ivan_ivanov` *(опционально)*
- `X-Telegram-Display-Name: Иван` *(опционально)*
- `Content-Type: application/json`

**Тело запроса:**
```json
{
  "name": "Вечерняя партия",
  "maxPlayers": 8
}
```

**Успешный ответ (`201 Created`):**
```json
{
  "id": "e4eaaaf2-d142-11e1-b3e4-080027620cdd",
  "code": "X7K9M2",
  "inviteLink": "https://t.me/StayAwayGameBot/app?startapp=X7K9M2",
  "name": "Вечерняя партия",
  "creatorId": "123456789",
  "status": "LOBBY",
  "maxPlayers": 8,
  "currentPlayersCount": 1,
  "isReadyToStart": false,
  "players": [
    {
      "telegramId": "123456789",
      "username": "ivan_ivanov",
      "displayName": "Иван",
      "isCreator": true,
      "connectionStatus": "ONLINE",
      "joinedAt": "2026-10-29T12:00:00.000Z",
      "disconnectedAt": null
    }
  ],
  "createdAt": "2026-10-29T12:00:00.000Z"
}
```

**Ошибки:**
- `400 Bad Request` (`VALIDATION_ERROR`, `INVALID_PLAYER_NAME`)
- `401 Unauthorized` (`UNAUTHORIZED`)
- `409 Conflict` (`ACTIVE_ROOM_ALREADY_EXISTS`)

---

### 4.3. Получение информации о комнате
`GET /api/v1/rooms/{code}`

Поиск кода регистронезависим (например, `"x7k9m2"` и `"X7K9M2"` эквивалентны).

**Успешный ответ (`200 OK`):**
Структура `RoomResponse` (аналогично созданию).

**Ошибки:**
- `400 Bad Request` (`INVALID_ROOM_CODE`)
- `404 Not Found` (`ROOM_NOT_FOUND`)

---

### 4.4. Подключение к комнате (Join Room)
`POST /api/v1/rooms/{code}/join`

**Заголовки или тело запроса:**
```json
{
  "telegramId": "987654321",
  "username": "petr_petrov",
  "displayName": "Петр"
}
```

**Успешный ответ (`200 OK`):**
Обновленный объект `RoomResponse`.
- Запрос **идемпотентен**: повторный вызов от уже зарегистрированного игрока не дублирует запись, восстанавливает статус соединения в `ONLINE` и возвращает `200 OK` (даже если комната полностью заполнена).
- При наборе 4 игроков поле `isReadyToStart` автоматически становится `true`.

**Ошибки:**
- `400 Bad Request` (`INVALID_ROOM_CODE`, `INVALID_PLAYER_NAME`)
- `401 Unauthorized` (`UNAUTHORIZED`)
- `404 Not Found` (`ROOM_NOT_FOUND`)
- `409 Conflict` (`ROOM_FULL`, `ROOM_ALREADY_STARTED`)

---

### 4.5. Выход из комнаты (Leave Room)
`POST /api/v1/rooms/{code}/leave`

**Заголовки:**
- `X-Telegram-Id: 987654321` *(или в JSON теле: `{"telegramId": "987654321"}`)*

**Успешный ответ (`200 OK`):**
Обновленный объект `RoomResponse`.
- При выходе создателя комната передает права хоста (`creatorId` и `isCreator = true`) участнику с самым ранним временем подключения (`joinedAt`).
- При выходе последнего игрока комната в статусе `LOBBY` автоматически удаляется.

**Ошибки:**
- `400 Bad Request` (`INVALID_ROOM_CODE`)
- `401 Unauthorized` (`UNAUTHORIZED`)
- `404 Not Found` (`ROOM_NOT_FOUND`)

---

### 4.6. Изменение настроек комнаты
`PATCH /api/v1/rooms/{code}/settings`

**Заголовки:**
- `X-Telegram-Id: 123456789` *(ID создателя)*
- `Content-Type: application/json`

**Тело запроса:**
```json
{
  "maxPlayers": 10
}
```

**Успешный ответ (`200 OK`):**
Обновленный объект `RoomResponse`.

**Ошибки:**
- `400 Bad Request` (`VALIDATION_ERROR`, `INVALID_MAX_PLAYERS`)
- `401 Unauthorized` (`UNAUTHORIZED`)
- `403 Forbidden` (`FORBIDDEN_NOT_CREATOR`)
- `404 Not Found` (`ROOM_NOT_FOUND`)
- `409 Conflict` (`ROOM_ALREADY_STARTED`)

---

## 5. Спецификация WebSocket (Real-Time события)

### 5.1. Установка соединения

Подключение возможно двумя способами:

1. **Прямое подключение к комнате по URL:**
   ```
   ws://localhost:8080/ws/rooms/{code}?telegramId={id}&displayName={name}
   ```
   Сразу после подключения сервер отправляет клиенту событие `room:state_sync`.

2. **Подключение к базовой точке с отправкой сообщения подписки:**
   - Подключение: `ws://localhost:8080/ws`
   - Отправка сообщения:
     ```json
     {
       "event": "lobby:subscribe",
       "data": {
         "code": "X7K9M2",
         "telegramId": "123456789",
         "displayName": "Иван"
       }
     }
     ```

### 5.2. Keep-Alive (Ping / Pong)
Для проверки активности соединения клиент может отправлять:
```json
{
  "event": "ping"
}
```
Сервер отвечает:
```json
{
  "event": "pong"
}
```

### 5.3. Серверные события WebSocket

Все серверные сообщения имеют структуру:
```json
{
  "event": "<event_name>",
  "timestamp": "2026-10-29T12:00:00.000Z",
  "data": { ... }
}
```

#### 1. `room:state_sync` (Синхронизация состояния)
Отправляется подключившемуся клиенту сразу после установления сессии:
```json
{
  "event": "room:state_sync",
  "timestamp": "2026-10-29T12:00:00.000Z",
  "data": {
    "id": "e4eaaaf2-d142-11e1-b3e4-080027620cdd",
    "code": "X7K9M2",
    "name": "Вечерняя партия",
    "creatorId": "123456789",
    "status": "LOBBY",
    "maxPlayers": 8,
    "currentPlayersCount": 2,
    "isReadyToStart": false,
    "players": [ ... ]
  }
}
```

#### 2. `room:player_joined` (Вход нового игрока)
Рассылается всем участникам при подключении нового игрока:
```json
{
  "event": "room:player_joined",
  "timestamp": "2026-10-29T12:01:05.000Z",
  "data": {
    "joinedPlayer": {
      "telegramId": "987654321",
      "username": "petr_petrov",
      "displayName": "Петр",
      "isCreator": false,
      "connectionStatus": "ONLINE",
      "joinedAt": "2026-10-29T12:01:05.000Z",
      "disconnectedAt": null
    },
    "room": {
      "code": "X7K9M2",
      "currentPlayersCount": 2,
      "isReadyToStart": false,
      "players": [ ... ]
    }
  }
}
```

#### 3. `room:player_left` (Выход игрока)
Рассылается при выходе игрока из комнаты:
```json
{
  "event": "room:player_left",
  "timestamp": "2026-10-29T12:05:00.000Z",
  "data": {
    "leftPlayerId": "987654321",
    "newCreatorId": null,
    "room": {
      "code": "X7K9M2",
      "currentPlayersCount": 1,
      "isReadyToStart": false,
      "players": [ ... ]
    }
  }
}
```

#### 4. `room:host_changed` (Смена хоста)
Рассылается участникам, если комнату покинул создатель:
```json
{
  "event": "room:host_changed",
  "timestamp": "2026-10-29T12:05:00.000Z",
  "data": {
    "previousCreatorId": "123456789",
    "newCreatorId": "987654321",
    "players": [ ... ]
  }
}
```

#### 5. `room:settings_updated` (Обновление настроек лобби)
Рассылается при смене `maxPlayers` создателем:
```json
{
  "event": "room:settings_updated",
  "timestamp": "2026-10-29T12:03:00.000Z",
  "data": {
    "maxPlayers": 10,
    "isReadyToStart": false,
    "room": { ... }
  }
}
```

#### 6. `room:player_connection_changed` (Изменение статуса соединения)
Рассылается при обрыве или восстановлении WebSocket-соединения игрока:
```json
{
  "event": "room:player_connection_changed",
  "timestamp": "2026-10-29T12:04:00.000Z",
  "data": {
    "telegramId": "987654321",
    "connectionStatus": "DISCONNECTED",
    "status": "DISCONNECTED"
  }
}
```
*(При восстановлении соединения статус принимает значение `"ONLINE"`).*
