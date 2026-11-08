CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY,
    code VARCHAR(12) NOT NULL,
    name VARCHAR(32) NOT NULL,
    creator_telegram_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'LOBBY',
    max_players SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rooms_code UNIQUE (code),
    CONSTRAINT chk_rooms_name CHECK (char_length(trim(name)) BETWEEN 1 AND 32),
    CONSTRAINT chk_rooms_max_players CHECK (max_players BETWEEN 4 AND 12)
);

CREATE INDEX IF NOT EXISTS idx_rooms_code ON rooms(code);

CREATE TABLE IF NOT EXISTS room_participants (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    telegram_id VARCHAR(64) NOT NULL,
    username VARCHAR(64),
    display_name VARCHAR(64) NOT NULL,
    is_creator BOOLEAN NOT NULL DEFAULT FALSE,
    connection_status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_participants_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT uq_room_telegram UNIQUE (room_id, telegram_id),
    CONSTRAINT chk_participant_display_name CHECK (char_length(trim(display_name)) BETWEEN 1 AND 64)
);

CREATE INDEX IF NOT EXISTS idx_participants_room_joined ON room_participants(room_id, joined_at ASC);
