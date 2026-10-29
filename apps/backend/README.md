# Нечто — Backend

Серверная часть для Telegram Mini App «Нечто» на Java с использованием Spring Boot, Spring Data JPA, PostgreSQL, Flyway и Docker Compose.

## Технологический стек

- Java 25
- Spring Boot 4
- Spring Data JPA (Hibernate 7)
- PostgreSQL 16 (с поддержкой H2 для локального юнит/интеграционного тестирования)
- Flyway (версионированные миграции схемы БД)
- Spring Web (MVC)
- Spring WebSocket (Raw WebSocket handler с thread-safe броадкастером)
- Jakarta Validation & кастомные валидаторы (XSS/Control char filtering)
- Docker & Docker Compose (Multi-stage build)
- JUnit 5 / Spring Boot Test / MockMvc / AssertJ

---

## Быстрый запуск через Docker Compose

Вся инфраструктура (PostgreSQL + автоматические миграции Flyway + Spring Boot backend) поднимается из корня репозитория:

```bash
# Скопируйте шаблон переменных окружения (при необходимости отредактируйте)
cp .env.example .env

# Соберите и запустите контейнеры
docker compose up --build -d
```

После старта:
- HTTP API: `http://localhost:8080/api`
- WebSocket: `ws://localhost:8080/ws`
- Healthcheck: `http://localhost:8080/api/health`
- PostgreSQL: `localhost:5432`

Остановка сервисов:
```bash
docker compose down
# С очисткой данных БД:
docker compose down -v
```

---

## Локальный запуск (без Docker)

### Требования
- JDK 25 или выше (например, Eclipse Adoptium Temurin 25+)
- PostgreSQL 16+ (или запущенный контейнер `docker compose up db -d`)

Перейдите в директорию `apps/backend`:
```bash
cd apps/backend
```

### Запуск через Maven Wrapper

**Windows (PowerShell):**
```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**
```bash
chmod +x mvnw
./mvnw spring-boot:run
```

Сервер автоматически применит миграции Flyway из `src/main/resources/db/migration/` при запуске.

---

## Переменные окружения

| Переменная | По умолчанию | Описание |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Порт HTTP/WebSocket сервера |
| `DB_HOST` | `localhost` | Хост базы данных PostgreSQL |
| `DB_PORT` | `5432` | Порт PostgreSQL |
| `DB_NAME` | `nechto_db` | Имя базы данных |
| `DB_USER` | `nechto_user` | Пользователь БД |
| `DB_PASSWORD` | `nechto_secret_password` | Пароль пользователя БД |
| `DATABASE_URL` | *динамический* | Полная строка JDBC URL (опционально) |
| `TELEGRAM_BOT_USERNAME` | `StayAwayGameBot` | Имя бота Telegram для формирования диплинков |

---

## Архитектура хранения и схемы БД

База данных версионируется с помощью Flyway (`V1__init_rooms_and_participants.sql`).

### 1. Таблица `rooms`
- `id` (UUID, PK) — внутренний уникальный идентификатор.
- `code` (VARCHAR(12), UNIQUE, INDEX) — публичный короткий код комнаты (4–12 символов `[A-Z0-9]`).
- `name` (VARCHAR(32), CHECK 1..32) — название комнаты.
- `creator_telegram_id` (VARCHAR(64)) — Telegram ID текущего хоста.
- `status` (VARCHAR(20)) — `LOBBY`, `IN_PROGRESS`, `FINISHED`, `ARCHIVED`.
- `max_players` (SMALLINT, CHECK 4..12) — лимит участников.
- `created_at`, `updated_at`, `last_activity_at` (TIMESTAMP WITH TIME ZONE).

### 2. Таблица `room_participants`
- `id` (UUID, PK) — идентификатор участника.
- `room_id` (UUID, FK -> rooms.id ON DELETE CASCADE).
- `telegram_id` (VARCHAR(64)) — Telegram ID игрока.
- `username` (VARCHAR(64), NULL) — никнейм.
- `display_name` (VARCHAR(64), CHECK 1..64) — отображаемое имя (санитизировано, без HTML/тегов).
- `is_creator` (BOOLEAN) — признак хоста.
- `connection_status` (VARCHAR(20)) — `ONLINE`, `DISCONNECTED`.
- `joined_at`, `disconnected_at` (TIMESTAMP WITH TIME ZONE).
- Ограничение целостности: `UNIQUE(room_id, telegram_id)`.

---

## Защита от состояния гонки (Race Conditions)

Подключение к комнате защищено транзакцией с пессимистической блокировкой строки комнаты:
```sql
SELECT * FROM rooms WHERE code = ? FOR UPDATE;
```
Параллельные запросы выстраиваются в очередь на уровне БД. При попытке превышения `max_players` транзакция откатывается и возвращает ошибку `409 Conflict` (`ROOM_FULL`).
WebSocket-уведомление `room:player_joined` отправляется **строго после фиксации транзакции (`TransactionSynchronization.afterCommit`)**.

---

## Валидация и безопасность

1. **Код комнаты:** строго `^[A-Z0-9]{4,12}$`.
2. **Имя игрока (`displayName`):** от 1 до 64 символов, запрещены HTML-теги (`<`, `>`), спецсимволы и управляющие символы.
3. **Название комнаты (`name`):** от 1 до 32 символов.
4. **Количество игроков (`maxPlayers`):** строго от 4 до 12.
5. **Маскирование ошибок:**
   - Ошибки валидации возвращаются в формате `400 Bad Request` с кодом `"VALIDATION_ERROR"` и списком деталей.
   - Любые непредвиденные системные/SQL ошибки маскируются до безопасного `500 Internal Server Error` с кодом `"INTERNAL_SERVER_ERROR"`. Исходные SQL-запросы, имена таблиц и стектрейсы клиенту **не возвращаются**.

---

## Тестирование и верификация

Все тесты запускаются автономно (в памяти используется эмуляция PostgreSQL в H2):

```bash
# Запуск всех 40 тестов:
.\mvnw.cmd test

# Запуск только интеграционных тестов БД:
.\mvnw.cmd test -Dtest=RoomDatabaseIntegrationTest
```

Набор тестов проверяет:
1. **Персистентность:** сохранение состояния комнат и участников при перезапуске сервиса.
2. **Чистые миграции:** применение миграций Flyway на пустую БД, валидность внешних и первичных ключей.
3. **Race Condition:** одновременное подключение 5 потоков на последнее свободное место (ровно 1 успешный вход, 4 отказа `409 ROOM_FULL`).
4. **Атомарность:** откат изменений при невалидных входных данных.
5. **Маскирование ошибок:** отсутствие SQL/стектрейсов в ответах клиенту.
6. **WebSocket-синхронизация:** корректная рассылка событий в реальном времени.

---

## Сборка JAR-архива

```bash
.\mvnw.cmd clean package
```
Результат сборки: `target/nechto-backend-0.0.1-SNAPSHOT.jar`.
