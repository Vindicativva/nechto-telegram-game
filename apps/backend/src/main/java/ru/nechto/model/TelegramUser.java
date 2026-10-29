package ru.nechto.model;

public record TelegramUser(
        String telegramId,
        String username,
        String displayName
) {
    public static TelegramUser of(String telegramId, String username, String displayName) {
        String effectiveDisplayName = displayName;
        if (effectiveDisplayName == null || effectiveDisplayName.isBlank()) {
            effectiveDisplayName = (username != null && !username.isBlank()) ? username : "Player_" + telegramId;
        }
        return new TelegramUser(telegramId, username, effectiveDisplayName.trim());
    }
}
