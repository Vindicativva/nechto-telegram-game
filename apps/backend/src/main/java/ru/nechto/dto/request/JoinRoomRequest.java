package ru.nechto.dto.request;

public class JoinRoomRequest {
    private String telegramId;
    private String username;
    private String displayName;

    public JoinRoomRequest() {}

    public JoinRoomRequest(String telegramId, String username, String displayName) {
        this.telegramId = telegramId;
        this.username = username;
        this.displayName = displayName;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
