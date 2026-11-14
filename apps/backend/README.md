# Нечто — Backend Service

Серверная часть для Telegram Mini App «Нечто» на Java с использованием Spring Boot 4, Spring Data JPA, PostgreSQL 16, Flyway, OpenAPI (Swagger UI) и Docker Compose.

---

## Технологический стек

- **Java:** 25
- **Spring Boot:** 4
- **ORM / СУБД:** Spring Data JPA (Hibernate 7) + PostgreSQL 16
- **Миграции БД:** Flyway (схема `V1__init_rooms_and_participants.sql`)
- **WebSocket:** Spring WebSocket Handler с thread-safe броадкастером сессий
- **API Документация:** SpringDoc OpenAPI 3.0 / Swagger UI
- **Безопасность и валидация:** Jakarta Validation, санитизация XSS/HTML и управляющих символов
- **Тестирование:** JUnit 5 (Unit, Integration, Concurrency, WebSocket), MockMvc, AssertJ, Mockito (202 теста)

---

## Документация API

- **Интерактивный Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Спецификация OpenAPI 3.0 JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Полное руководство для фронтенд-разработчика:** [../../docs/API.md](../../docs/API.md)

### Базовые эндпоинты управления комнатами
- `POST /api/rooms` (а также `POST /api/v1/rooms`) — создание комнаты с серверной валидацией имени (`1–32` символа) и вместимости (`4–12` человек)
- `GET /api/rooms/{code}` (а также `GET /api/v1/rooms/{code}`) — проверка существования и доступности комнаты
- `POST /api/rooms/{code}/join` — подключение к комнате
- `POST /api/rooms/{code}/leave` — выход из комнаты
- `PATCH /api/rooms/{code}/settings` — изменение вместимости хостом комнаты

---

## Быстрый запуск через Docker Compose

Из корня репозитория:

```bash
# 1. Создайте файл .env из примера
cp .env.example .env

# 2. Соберите и запустите сервисы
docker compose up --build -d
```

Сервисы:
- HTTP API: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- WebSocket: `ws://localhost:8080/ws`
- PostgreSQL: `localhost:5432`

---

## Локальный запуск (Maven Wrapper)

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
chmod +x mvnw
./mvnw spring-boot:run
```

---

## Автоматическое тестирование

Каждый тестовый метод в кодовой базе снабжен подробным JavaDoc с секциями **Given**, **When**, **Then**.

### Запуск всех тестов:
```bash
# Windows
.\mvnw.cmd clean test

# Linux / macOS
./mvnw clean test
```

### Структура тестов:
1. **Модульные тесты (Unit Tests):**
   - `PlayerTest` — доменная модель игрока `Player` (id, telegramId, username, isHost, connectedAt, конвертация, equals/hashCode).
   - `RoomModelTest` — доменная модель комнаты `Room` (code, name, maxPlayers, players, status, флаг готовности, управление игроками).
   - `InMemoryRoomRepositoryTest` — потокобезопасный in-memory репозиторий на базе `ConcurrentHashMap` (сохранение, регистронезависимый поиск, удаление, 20 конкурентных потоков).
   - `RoomCodeGeneratorTest` — генерация безопасных кодов длины 6, проверка коллизий (JPA и InMemory), inviteLink.
   - `RoomServiceCreateTest` — создание комнат, граничные значения, лимиты, роли.
   - `RoomServiceGetTest` — регистронезависимый поиск, 404, невалидные коды.
   - `RoomServiceJoinTest` — подключение, динамика флага готовности, идемпотентный reconnect, заполненные комнаты.
   - `RoomServiceLeaveTest` — уменьшение счетчика, Host Migration (ранний joinedAt), удаление пустых комнат.
   - `RoomServiceSettingsTest` — изменение maxPlayers, права создателя, проверки лимитов.
   - `InputValidatorTest` — валидация кодов, имен игроков (XSS/контрольные символы), лимитов.
2. **Интеграционные тесты REST API:**
   - `RoomControllerApiRoomsTest` — сквозные тесты `POST /api/rooms` и `GET /api/rooms/{code}`, валидация 1–32 символов и 4–12 игроков, проверка ошибок 400, 401, 404, 409.
   - `RoomControllerIntegrationTest` — сквозной HTTP-жизненный цикл, единый формат ошибок (400, 401, 403, 404, 409, 500), маскирование SQL.
   - `RoomControllerTest` — расширенные тесты контроллера.
3. **Многопоточные тесты конкурентности (Concurrency Tests):**
   - `RoomConcurrencyIntegrationTest` — гонка 10 потоков за 1 свободное место (ровно 1 успешный вход, 9 отказов 409), параллельные дублирующие запросы.
4. **Интеграционные тесты слоя БД:**
   - `RoomDatabaseIntegrationTest` — персистентность при перезапуске, Flyway-миграции, атомарность, безопасность.
5. **WebSocket-тесты:**
   - `RoomWebSocketIntegrationTest` — начальный `room:state_sync`, `room:player_joined`, `room:player_left`, `room:host_changed`, `room:settings_updated`, `room:player_connection_changed`.
