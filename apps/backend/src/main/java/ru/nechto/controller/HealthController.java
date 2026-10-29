package ru.nechto.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "Проверка доступности и работоспособности сервиса")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "Проверка состояния сервиса (Health Check)", description = "Возвращает приветственное сообщение при успешной работе сервера.")
    @ApiResponse(responseCode = "200", description = "Сервер доступен и готов к обработке запросов")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Hello, Nechto!");
    }
}
