# Многопользовательская игра «Нечто» (Stay Away!) — Telegram Mini App

Проект карточной психологической игры «Нечто» для платформы Telegram Mini Apps.

## Структура репозитория

```
nechto-telegram-game/
├── .env.example              # Шаблон переменных окружения
├── docker-compose.yml        # Docker Compose для запуска БД и бэкенда
├── Dockerfile                # Multi-stage Dockerfile для бэкенда (из корня)
├── apps/
│   └── backend/              # Java / Spring Boot 4 бэкенд
│       ├── Dockerfile        # Multi-stage Dockerfile для бэкенда
│       ├── pom.xml           # Зависимости и конфигурация Maven
│       └── src/              # Исходный код и миграции Flyway
└── README.md                 # Документация проекта
```

---

## Быстрый запуск (Docker Compose)

Для быстрого развертывания всего окружения (PostgreSQL + Spring Boot Backend) выполните:

```bash
# 1. Создайте конфигурацию .env из шаблона
cp .env.example .env

# 2. Соберите и запустите сервисы
docker compose up --build -d
```

### Проверка работоспособности:
- **HTTP API:** `http://localhost:8080/api/health` -> `Hello, Nechto!`
- **Swagger / REST API комнат:** `http://localhost:8080/api/v1/rooms`
- **WebSocket сервер:** `ws://localhost:8080/ws`
- **PostgreSQL:** порт `5432`

### Остановка:
```bash
docker compose down
```

---

## Функциональность бэкенда (BE-1 & BE-2)

1. **Управление комнатами (Lobby REST API):**
   - Создание комнат (`POST /api/v1/rooms`) с автогенерацией уникальных кодов.
   - Подключение игроков (`POST /api/v1/rooms/{code}/join`) и выход (`POST /api/v1/rooms/{code}/leave`).
   - Получение состояния комнаты (`GET /api/v1/rooms/{code}`).
   - Изменение настроек (`PATCH /api/v1/rooms/{code}/settings`).
2. **Персистентность и миграции (PostgreSQL + Flyway):**
   - Таблицы `rooms` и `room_participants` с внешними ключами и ограничениями `CHECK`/`UNIQUE`.
   - Автоматическое применение миграций при старте.
3. **Безопасность и валидация:**
   - Строгая валидация входных данных (код комнаты `^[A-Z0-9]{4,12}$`, имя 1..64 символов без HTML/тегов, лимит 4..12 игроков).
   - Маскирование внутренних ошибок сервера (`500 INTERNAL_SERVER_ERROR`) без утечки SQL и стектрейсов.
4. **Транзакционная защита от Race Conditions:**
   - Пессимистическая блокировка `SELECT ... FOR UPDATE` гарантирует атомарность при одновременных попытках занять последнее место.
   - WebSocket события рассылаются строго после коммита транзакции.
5. **WebSocket синхронизация:**
   - Мгновенные обновления состояния лобби (`room:state_sync`, `room:player_joined`, `room:player_left`, `room:host_changed`, `room:settings_updated`, `room:player_connection_changed`).
