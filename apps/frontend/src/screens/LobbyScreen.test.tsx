import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'
import { LobbyScreen } from './LobbyScreen'

const { useRoomSocket } = vi.hoisted(() => ({ useRoomSocket: vi.fn() }))

vi.mock('../hooks/useRoomSocket', () => ({ useRoomSocket }))

const host: TelegramIdentity = { telegramId: '1', username: 'host', displayName: 'Хост' }

const room: Room = {
  id: 'room-1',
  code: 'X7K9M2',
  inviteLink: 'https://t.me/StayAwayGameBot/app?startapp=X7K9M2',
  name: 'Вечерняя партия',
  creatorId: '1',
  status: 'LOBBY',
  maxPlayers: 4,
  currentPlayersCount: 3,
  isReadyToStart: false,
  players: [],
  createdAt: '2026-10-29T12:00:00.000Z',
}

describe('LobbyScreen', () => {
  it('shows a disabled start button to the host until four players are present', () => {
    useRoomSocket.mockReturnValue({ room, status: 'open' })

    render(<LobbyScreen identity={host} initialRoom={room} onLeave={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Начать игру' })).toBeDisabled()
  })

  it('does not expose the start action to a player who is not the host', () => {
    useRoomSocket.mockReturnValue({ room: { ...room, currentPlayersCount: 4 }, status: 'open' })

    render(
      <LobbyScreen
        identity={{ telegramId: '2', username: 'guest', displayName: 'Гость' }}
        initialRoom={room}
        onLeave={vi.fn()}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Начать игру' })).not.toBeInTheDocument()
  })
})
