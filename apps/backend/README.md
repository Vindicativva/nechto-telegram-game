# Нечто — Backend

Серверная часть для Telegram Mini App «Нечто» на Java с использованием Spring Boot и Maven.

## Технологический стек

- Java 25
- Spring Boot 4
- Maven (с поддержкой Maven Wrapper)
- Spring Web (MVC)
- JUnit 5 / Spring Boot Test

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

## Структура проекта

```
apps/backend/
├── .mvn/wrapper/                  # Файлы Maven Wrapper
├── src/
│   ├── main/
│   │   ├── java/ru/nechto/
│   │   │   ├── NechtoApplication.java            # Главный класс приложения
│   │   │   └── controller/
│   │   │       └── HealthController.java         # GET /api/health
│   │   └── resources/
│   │       └── application.properties            # Конфигурация приложения
│   └── test/
│       └── java/ru/nechto/
│           ├── NechtoApplicationTests.java       # Тест поднятия контекста
│           └── controller/
│               └── HealthControllerTest.java     # Тест эндпоинта /api/health
├── mvnw                           # Скрипт Maven Wrapper для Unix/macOS
├── mvnw.cmd                       # Скрипт Maven Wrapper для Windows
├── pom.xml                        # Конфигурация Maven
└── README.md                      # Инструкция по запуску и разработке
```
