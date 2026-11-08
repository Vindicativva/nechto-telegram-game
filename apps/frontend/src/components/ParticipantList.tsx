import type { RoomParticipant } from '../types/room'

interface ParticipantListProps {
  players: RoomParticipant[]
  currentTelegramId: string
}

export function ParticipantList({ players, currentTelegramId }: ParticipantListProps) {
  return (
    <ul className="participant-list">
      {players.map((player) => (
        <li key={player.telegramId} className="participant-row">
          <span
            className={`connection-dot connection-dot-${player.connectionStatus.toLowerCase()}`}
            aria-hidden="true"
          />
          <span className="participant-name">
            {player.displayName ?? player.username ?? player.telegramId}
            {player.telegramId === currentTelegramId ? ' (вы)' : ''}
          </span>
          {player.isCreator && <span className="participant-badge">Хост</span>}
        </li>
      ))}
    </ul>
  )
}
