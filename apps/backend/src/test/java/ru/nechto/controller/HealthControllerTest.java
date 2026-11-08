package ru.nechto.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты проверки доступности сервиса (Health Check).
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Проверяет доступность эндпоинта /api/health и возврат приветственного сообщения.
     *
     * <p><b>Предусловия (Given):</b> Сервер запущен и готов обрабатывать HTTP-запросы.</p>
     *
     * <p><b>Действие (When):</b> Клиент отправляет запрос {@code GET /api/health}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сервер возвращает HTTP-статус {@code 200 OK}.</li>
     *   <li>Тело ответа содержит строку {@code "Hello, Nechto!"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("GET /api/health возвращает 200 OK и строку Hello, Nechto!")
    void healthEndpointReturnsOkAndMessage() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Nechto!"));
    }
}
