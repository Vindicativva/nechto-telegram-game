# Нечто — Backend

Серверная часть для Telegram Mini App «Нечто» на Java с использованием Spring Boot и Maven.

## Технологический стек

- Java 25
- Spring Boot 4
- Maven (с поддержкой Maven Wrapper)
- Spring Web (MVC)
- Spring WebSocket (Raw WebSocket handler)
- Jakarta Validation
- JUnit 5 / Spring Boot Test / MockMvc

## Требования

- JDK 25 или выше (например, Eclipse Adoptium Temurin 25+)
- Maven 3.9+ (опционально, в проекте присутствует Maven Wrapper `mvnw` / `mvnw.cmd`)

> [!NOTE]
> Убедитесь, что переменная окружения `JAVA_HOME` указывает на установленный JDK 25 (или выше), а `java -version` возвращает версию 25+.

## Установка и запуск

Перейдите в директорию `apps/backend`:

```bash
cd apps/backend
```

### Запуск через Maven Wrapper

**Windows (PowerShell / CMD):**
```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
chmod +x mvnw
./mvnw spring-boot:run
```

### Запуск через установленный Maven

```bash
mvn spring-boot:run
```

После старта сервер будет доступен по адресу `http://localhost:8080`.

---

## Проверка работоспособности (Healthcheck)

Сервер предоставляет эндпоинт проверки здоровья:

- **Метод:** `GET`
- **URL:** `http://localhost:8080/api/health`
- **Ожидаемый ответ:** HTTP `200 OK`
- **Тело ответа:** `Hello, Nechto!`

### Пример запроса

**cURL:**
```bash
curl -i http://localhost:8080/api/health
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health"
```

---

## Тестирование и сборка

### Запуск тестов

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

### Сборка проекта

```bash
# Windows
.\mvnw.cmd clean package

# Linux / macOS
./mvnw clean package
```

Собранный исполняемый JAR-файл будет находиться в `target/nechto-backend-0.0.1-SNAPSHOT.jar`.

Запуск собранного JAR:
```bash
java -jar target/nechto-backend-0.0.1-SNAPSHOT.jar
```

---

## Документация REST API (Игровые комнаты и Лобби)

Все запросы, требующие идентификации игрока, принимают Telegram-данные через заголовки (или опционально в теле запроса):
- `X-Telegram-Id` — уникальный числовой или строковый ID пользователя Telegram (обязателен).
- `X-Telegram-Username` — username в Telegram (опционально).
- `X-Telegram-Display-Name` — отображаемое имя игрока (опционально).

### 1. Создание комнаты
- **Метод:** `POST`
- **URL:** `/api/v1/rooms`
- **Тело запроса:**
  ```json
  {
    "name": "Вечерняя партия",
    "maxPlayers": 8
  }
  ```
  *(Ограничения: `name` от 1 до 32 символов, `maxPlayers` от 4 до 12).*
- **Ответ (`201 Created`):**
  ```json
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
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
        "username": "ivan_player",
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

### 2. Получение состояния комнаты
- **Метод:** `GET`
- **URL:** `/api/v1/rooms/{code}` (регистронезависимо)
- **Ответ (`200 OK`):** Возвращает объект комнаты со списком участников и статусом.
- **Ошибки:** `404 Not Found` (`ROOM_NOT_FOUND`).

### 3. Подключение к комнате
- **Метод:** `POST`
- **URL:** `/api/v1/rooms/{code}/join`
- **Тело запроса (опционально при наличии заголовков):**
  ```json
  {
    "telegramId": "987654321",
    "username": "alex_g",
    "displayName": "Алексей"
  }
  ```
- **Ответ (`200 OK`):** Актуальное состояние комнаты.
- **Особенности:**
  - Подключение идемпотентно: повторный вход возвращает `200 OK` и восстанавливает статус `ONLINE` без дублирования игрока.
  - При достижении 4 участников флаг `isReadyToStart` автоматически переключается в `true`.
  - При входе нового участника всем в комнате рассылается событие `room:player_joined` по WebSocket.
- **Ошибки:**
  - `404 Not Found` (`ROOM_NOT_FOUND`)
  - `409 Conflict` (`ROOM_FULL`) — «Комната заполнена»
  - `409 Conflict` (`ROOM_ALREADY_STARTED`) — «Игра уже началась, подключение невозможно»

### 4. Выход игрока из лобби
- **Метод:** `POST`
- **URL:** `/api/v1/rooms/{code}/leave`
- **Заголовок:** `X-Telegram-Id: <id>` (или тело `{"telegramId": "<id>"}`)
- **Ответ (`200 OK`):**
  - Участник удаляется из комнаты, рассылается событие `room:player_left`.
  - Если вышел создатель, права (`creatorId`, `isCreator = true`) автоматически передаются участнику с самым ранним `joinedAt` (рассылается `room:host_changed`).
  - Если комнату покинули все игроки, она удаляется.

### 5. Изменение настроек комнаты
- **Метод:** `PATCH`
- **URL:** `/api/v1/rooms/{code}/settings`
- **Заголовок:** `X-Telegram-Id: <id>`
- **Тело запроса:**
  ```json
  {
    "maxPlayers": 10
  }
  ```
- **Ограничения:** доступно только создателю комнаты в статусе `LOBBY`. Значение `maxPlayers` не может быть меньше текущего числа уже подключенных игроков.
- **Ответ (`200 OK`):** Обновленное состояние комнаты (рассылается `room:settings_updated`).

### 6. Формат HTTP-ошибок
Все ошибки возвращаются в едином JSON-формате:
```json
{
  "error": {
    "code": "ROOM_FULL",
    "message": "Комната заполнена"
  }
}
```

---

## Спецификация WebSocket (Real-Time обновления)

### Подключение
- **URL соединения:**
  - Прямое: `ws://<host>/ws/rooms/{code}?telegramId=<id>&displayName=<name>`
  - Или общее: `ws://<host>/ws`, после чего отправляется сообщение подписки:
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

### Формат сообщений
Все серверные события передаются в формате:
```json
{
  "event": "<название_события>",
  "timestamp": "2026-10-29T12:01:00.000Z",
  "data": { ... }
}
```

### События:
1. `room:state_sync` — отправляется клиенту сразу после подключения с полным объектом комнаты `Room`.
2. `room:player_joined` — рассылается всем участникам при входе нового игрока:
   ```json
   {
     "event": "room:player_joined",
     "timestamp": "2026-10-29T12:01:05.000Z",
     "data": {
       "joinedPlayer": { "telegramId": "...", "displayName": "...", "isCreator": false, ... },
       "room": { ... }
     }
   }
   ```
3. `room:player_left` — рассылается при выходе игрока (`leftPlayerId`, `newCreatorId`, `room`).
4. `room:host_changed` — рассылается при передаче прав хоста (`previousCreatorId`, `newCreatorId`, `players`).
5. `room:settings_updated` — рассылается при смене настроек создателем (`maxPlayers`, `isReadyToStart`, `room`).
6. `room:player_connection_changed` — рассылается при обрыве/восстановлении соединения участника (`telegramId`, `connectionStatus: ONLINE | DISCONNECTED`).

---

## Структура проекта

```
apps/backend/
├── .mvn/wrapper/                  # Файлы Maven Wrapper
├── src/
│   ├── main/
│   │   ├── java/ru/nechto/
│   │   │   ├── config/                           # WebSocket конфигурация
│   │   │   │   └── WebSocketConfig.java
│   │   │   ├── controller/                       # REST-контроллеры
│   │   │   │   ├── HealthController.java
│   │   │   │   └── RoomController.java
│   │   │   ├── dto/                              # DTO запросов, ответов и WS-событий
│   │   │   │   ├── request/
│   │   │   │   ├── response/
│   │   │   │   └── ws/
│   │   │   ├── exception/                        # Бизнес-исключения и глобальный хэндлер
│   │   │   ├── model/                            # Доменные модели (Room, RoomParticipant, etc.)
│   │   │   ├── repository/                       # Репозиторий комнат (InMemory)
│   │   │   ├── service/                          # RoomService (бизнес-логика)
│   │   │   ├── websocket/                        # WebSocketHandler и Broadcaster
│   │   │   └── NechtoApplication.java            # Главный класс приложения
│   │   └── resources/
│   │       └── application.properties            # Конфигурация приложения
│   └── test/
│       └── java/ru/nechto/
│           ├── controller/
│           │   ├── HealthControllerTest.java
│           │   └── RoomControllerTest.java       # Интеграционные тесты REST API
│           ├── service/
│           │   └── RoomServiceTest.java          # Unit-тесты бизнес-логики
│           ├── websocket/
│           │   └── RoomWebSocketIntegrationTest.java # Тесты WebSocket синхронизации
│           └── NechtoApplicationTests.java
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```
