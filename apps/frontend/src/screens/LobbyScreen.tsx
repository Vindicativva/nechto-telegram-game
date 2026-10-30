import { useState } from 'react'
import { getErrorMessage } from '../api/errors'
import { leaveRoom } from '../api/rooms'
import { Button } from '../components/Button'
import { ErrorBanner } from '../components/ErrorBanner'
import { ParticipantList } from '../components/ParticipantList'
import { RoomCodeBadge } from '../components/RoomCodeBadge'
import { useRoomSocket } from '../hooks/useRoomSocket'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'

interface LobbyScreenProps {
  identity: TelegramIdentity
  initialRoom: Room
  onLeave: () => void
}

const CONNECTION_LABEL: Record<'connecting' | 'open' | 'reconnecting', string> = {
  connecting: 'Подключение…',
  open: 'На связи',
  reconnecting: 'Переподключение…',
}

export function LobbyScreen({ identity, initialRoom, onLeave }: LobbyScreenProps) {
  const { room, status } = useRoomSocket(initialRoom.code, identity, initialRoom)
  const [isLeaving, setIsLeaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

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

      <RoomCodeBadge code={room.code} />

      <p className="connection-status">{CONNECTION_LABEL[status]}</p>

      <div className="participants-header">
        <span>Участники</span>
        <span>
          {room.currentPlayersCount} / {room.maxPlayers}
        </span>
      </div>

      <ParticipantList players={room.players} currentTelegramId={identity.telegramId} />

      {error && <ErrorBanner message={error} />}

      <div className="actions">
        <Button variant="ghost" onClick={handleLeave} isLoading={isLeaving}>
          Покинуть комнату
        </Button>
      </div>
    </main>
  )
}
