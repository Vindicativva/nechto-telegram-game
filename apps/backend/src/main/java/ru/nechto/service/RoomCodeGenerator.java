package ru.nechto.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.nechto.repository.RoomRepository;
import ru.nechto.repository.jpa.RoomJpaRepository;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Генератор уникальных 6-значных буквенно-цифровых кодов комнат с проверкой на коллизии
 * и ссылок-приглашений Telegram Mini App.
 */
@Component
public class RoomCodeGenerator {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();
    private final RoomJpaRepository roomJpaRepository;
    private final Predicate<String> collisionChecker;
    private final String botUsername;

    @org.springframework.beans.factory.annotation.Autowired
    public RoomCodeGenerator(RoomJpaRepository roomJpaRepository,
                             @Value("${nechto.telegram.bot-username:StayAwayGameBot}") String botUsername) {
        this.roomJpaRepository = roomJpaRepository;
        this.collisionChecker = null;
        this.botUsername = botUsername;
    }

    public RoomCodeGenerator(RoomRepository roomRepository, String botUsername) {
        this.roomJpaRepository = null;
        this.collisionChecker = roomRepository != null ? roomRepository::existsByCode : null;
        this.botUsername = botUsername;
    }

    public RoomCodeGenerator(Predicate<String> collisionChecker, String botUsername) {
        this.roomJpaRepository = null;
        this.collisionChecker = collisionChecker;
        this.botUsername = botUsername;
    }

    /**
     * Генерирует уникальный 6-значный буквенно-цифровой код комнаты из безопасного алфавита.
     * При коллизии с уже существующим кодом повторяет генерацию.
     */
    public String generateCode() {
        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!isCodeTaken(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private boolean isCodeTaken(String code) {
        if (roomJpaRepository != null && roomJpaRepository.existsByCode(code)) {
            return true;
        }
        if (collisionChecker != null && collisionChecker.test(code)) {
            return true;
        }
        return false;
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
