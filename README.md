# Многопользовательская карточная игра «Нечто» (Stay Away!) — Telegram Mini App

Backend-сервис карточной психологической игры «Нечто» для платформы Telegram Mini Apps.

---

## Технологический стек

- **Язык и рантайм:** Java 25
- **Фреймворк:** Spring Boot 4
- **СУБД и персистентность:** PostgreSQL 16 (с эмуляцией в H2 для автономных тестов), Spring Data JPA (Hibernate 7)
- **Миграции схемы БД:** Flyway
- **Real-time взаимодействие:** Spring WebSocket / STOMP Message Broker (`/ws-game`, `/topic/room/{code}`) и Raw WebSocket handler
- **Документация API:** OpenAPI 3.0 / Swagger UI (`springdoc-openapi`)
- **Контейнеризация:** Docker & Docker Compose (Multi-stage build)
- **CI/CD:** GitHub Actions CI (`.github/workflows/backend-ci.yml`)


---

## Структура репозитория

```
nechto-telegram-game/
├── .github/workflows/
│   ├── backend-ci.yml        # GitHub Actions CI для сборки бэкенда и запуска тестов
│   └── ci.yml                # Базовый CI
├── docs/
│   └── API.md                # Полная документация REST API, WebSocket и форматов ошибок
├── .env.example              # Шаблон конфигурации переменных окружения
├── docker-compose.yml        # Docker Compose (PostgreSQL 16 + Spring Boot Backend)
├── Dockerfile                # Multi-stage Dockerfile для бэкенда (сборка из корня)
├── apps/
│   └── backend/              # Модуль бэкенда (Java / Spring Boot)
│       ├── Dockerfile        # Multi-stage Dockerfile бэкенда
│       ├── pom.xml           # Конфигурация Maven и зависимостей
│       ├── be_1.md           # ТЗ: API комнат, лобби и WebSocket
│       ├── be_2.md           # ТЗ: PostgreSQL, Flyway, Race Condition, Docker
│       ├── be_3.md           # ТЗ: Тестирование, унификация ошибок, Swagger, CI
│       ├── be_4.md           # ТЗ: Доменная модель и базовый API игровых комнат

│       └── src/              # Исходный код и тесты
└── README.md                 # Документация проекта
```

---

## Быстрый запуск (Docker Compose)

Поднять полностью настроенное окружение (PostgreSQL + авто-миграции Flyway + Spring Boot бэкенд) можно одной командой:

```bash
# 1. Скопируйте шаблон переменных окружения
cp .env.example .env

# 2. Соберите и запустите контейнеры
docker compose up --build -d
```

### Доступные сервисы:
- **Интерактивный Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.0 JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Healthcheck:** `http://localhost:8080/api/health` -> `Hello, Nechto!`

- **WebSocket сервер:** `ws://localhost:8080/ws`
- **WebSocket STOMP брокер:** `ws://localhost:8080/ws-game`
- **PostgreSQL:** `localhost:5432` (пользователь: `nechto_user`, база: `nechto_db`)

Остановка сервисов:
```bash
docker compose down
# С удалением сохраненного тома БД:
docker compose down -v
```

---

## Локальная разработка и запуск тестов

### Требования
- JDK 25 или выше (например, Eclipse Adoptium Temurin 25)
- Maven Wrapper (`mvnw` / `mvnw.cmd` входит в состав репозитория)

### Запуск тестов
Все 202 теста запускаются автономно одной командой (используется встроенный профиль H2 в режиме совместимости с PostgreSQL, не требующий внешних зависимостей):

```bash
cd apps/backend

# Windows
.\mvnw.cmd clean test

# Linux / macOS
chmod +x mvnw
./mvnw clean test
```

### Сборка JAR-пакета
```bash
# Windows
.\mvnw.cmd clean package

# Linux / macOS
./mvnw clean package
```

Собранный исполняемый артефакт сохраняется в `apps/backend/target/nechto-backend-0.0.1-SNAPSHOT.jar`.

---

## Документация API для Frontend-разработчиков

Полная спецификация всех HTTP-эндпоинтов, форматов запросов, единых кодов ошибок и протокола WebSocket находится в файле:
👉 [**docs/API.md**](file:///C:/5sem/spo/nechto/nechto-telegram-game/docs/API.md)

### Краткий обзор функционала (BE-1, BE-2, BE-3, BE-4, BE-5):
1. **Lobby API:** Создание комнат (`POST /api/v1/rooms`), просмотр (`GET /api/v1/rooms/{code}`), вход (`POST /join`), выход (`POST /leave`), изменение настроек (`PATCH /settings`), запуск игры (`POST /start`).
2. **PostgreSQL & Flyway:** Таблицы `rooms` и `room_participants` с внешними ключами `ON DELETE CASCADE` и `CHECK`-ограничениями.
3. **Pessimistic Locking (`SELECT ... FOR UPDATE`):** Транзакционная защита от состояния гонки при одновременном занятии последнего места.
4. **Унификация ошибок:** Единый ответ `@RestControllerAdvice` и `@MessageExceptionHandler` со стандартизированными кодами (`ROOM_FULL`, `ROOM_NOT_FOUND`, `VALIDATION_ERROR`, `FORBIDDEN_NOT_CREATOR`, `NOT_ENOUGH_PLAYERS`, `TOO_MANY_PLAYERS` и др.).
5. **Маскирование внутренних ошибок:** Статус 500 никогда не возвращает клиенту детали SQL, имена таблиц или Java stacktrace.
6. **OpenAPI / Swagger:** Полная автогенерация схемы OpenAPI и Swagger UI.
7. **WebSocket & STOMP Synchronizer:** Мгновенная рассылка событий (`ROOM_STATE`, `room:state_sync`, `room:player_joined`, `room:player_left`, `room:host_changed`, `room:settings_updated`, `room:player_connection_changed`) строго после коммита транзакции в БД.
8. **Host Migration & 5-минутный Grace Period (ТЗ 4.2.1):** Немедленная передача прав создателя при явном выходе хоста и 5-минутное удержание прав при внезапном сетевом обрыве (`SessionDisconnectEvent`).
9. **Запуск игры:** Контроль диапазона участников (4–12 игроков) с переводом статуса комнаты в `IN_PROGRESS`.
10. **GitHub Actions CI:** Автоматическая сборка, поднятие сервиса PostgreSQL и прогон всех 224 тестов при push/PR.
