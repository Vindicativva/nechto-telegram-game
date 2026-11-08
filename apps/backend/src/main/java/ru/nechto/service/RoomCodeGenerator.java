package ru.nechto.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.nechto.repository.jpa.RoomJpaRepository;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * Генератор уникальных публичных кодов комнат и ссылок-приглашений.
 */
@Component
public class RoomCodeGenerator {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();
    private final RoomJpaRepository roomJpaRepository;
    private final String botUsername;

    public RoomCodeGenerator(RoomJpaRepository roomJpaRepository,
                             @Value("${nechto.telegram.bot-username:StayAwayGameBot}") String botUsername) {
        this.roomJpaRepository = roomJpaRepository;
        this.botUsername = botUsername;
    }

    /**
     * Генерирует уникальный код комнаты длины 6 из безопасного алфавита.
     * При коллизии повторяет генерацию.
     */
    public String generateCode() {
        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (roomJpaRepository == null || !roomJpaRepository.existsByCode(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /**
     * Формирует ссылку-приглашение Telegram Mini App.
     */
    public String generateInviteLink(String code) {
        return "https://t.me/" + botUsername + "/app?startapp=" + code;
    }

    public String getBotUsername() {
        return botUsername;
    }
}
