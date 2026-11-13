import { useState } from 'react'
import { getErrorMessage } from '../api/errors'
import { leaveRoom } from '../api/rooms'
import { Button } from '../components/Button'
import { ErrorBanner } from '../components/ErrorBanner'
import { ParticipantList } from '../components/ParticipantList'
import { RoomCodeBadge } from '../components/RoomCodeBadge'
import { useGameSocket } from '../hooks/useRoomSocket'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'

interface LobbyScreenProps {
  identity: TelegramIdentity
  initialRoom: Room
  initData?: string
  onLeave: () => void
}

const CONNECTION_LABEL: Record<'connecting' | 'open' | 'reconnecting' | 'error' | 'disconnected', string> = {
  connecting: 'Подключение…',
  open: 'На связи',
  reconnecting: 'Переподключение…',
  error: 'Ошибка сети. Повторное подключение…',
  disconnected: 'Нет соединения',
}

export function LobbyScreen({ identity, initialRoom, initData, onLeave }: LobbyScreenProps) {
  const { room, status } = useGameSocket({ code: initialRoom.code, identity, initialRoom, initData })
  const [isLeaving, setIsLeaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isHost = room.creatorId === identity.telegramId
  const canStart = isHost && room.status === 'LOBBY' && room.currentPlayersCount >= 4

  const handleLeave = () => {
    if (isLeaving) return
    setError(null)
    setIsLeaving(true)
    leaveRoom(identity, room.code)
      .then(onLeave)
      .catch((leaveError: unknown) => setError(getErrorMessage(leaveError)))
      .finally(() => setIsLeaving(false))
  }

  return (
    <main className="screen">
      <h1 className="title title-small">{room.name}</h1>

      <RoomCodeBadge code={room.code} inviteLink={room.inviteLink} />

      <p className="connection-status">{CONNECTION_LABEL[status]}</p>

      <div className="participants-header">
        <span>Участники</span>
        <span>
          {room.currentPlayersCount} / {room.maxPlayers}
        </span>
      </div>

      <ParticipantList players={room.players} currentTelegramId={identity.telegramId} />

      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      <div className="actions">
        {isHost && (
          <Button
            type="button"
            variant="primary"
            disabled={!canStart}
            title={!canStart ? 'Для начала игры нужно минимум 4 игрока' : undefined}
          >
            Начать игру
          </Button>
        )}
        <Button variant="ghost" onClick={handleLeave} isLoading={isLeaving}>
          Покинуть комнату
        </Button>
      </div>
    </main>
  )
}
