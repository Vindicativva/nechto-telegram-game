import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/errors'
import * as roomsApi from '../api/rooms'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'
import { JoinRoomScreen } from './JoinRoomScreen'

const identity: TelegramIdentity = { telegramId: '1', username: null, displayName: 'Иван' }

const room: Room = {
  id: '1',
  code: 'X7K9M2',
  inviteLink: '',
  name: 'Вечерняя партия',
  creatorId: '2',
  status: 'LOBBY',
  maxPlayers: 8,
  currentPlayersCount: 2,
  isReadyToStart: false,
  players: [],
  createdAt: '2026-10-29T12:00:00.000Z',
}

describe('JoinRoomScreen', () => {
  it('joins by a valid code and hands the room off to onJoined', async () => {
    vi.spyOn(roomsApi, 'joinRoom').mockResolvedValue(room)
    const onJoined = vi.fn()
    const user = userEvent.setup()

    render(<JoinRoomScreen identity={identity} onBack={vi.fn()} onJoined={onJoined} />)
    await user.type(screen.getByPlaceholderText('Код комнаты'), 'x7k9m2')
    await user.click(screen.getByRole('button', { name: 'Присоединиться' }))

    expect(roomsApi.joinRoom).toHaveBeenCalledWith(identity, 'X7K9M2')
    await screen.findByRole('button', { name: 'Присоединиться' })
    expect(onJoined).toHaveBeenCalledWith(room)
  })

  it('rejects a too-short code locally before calling the API', async () => {
    const joinRoom = vi.spyOn(roomsApi, 'joinRoom')
    const user = userEvent.setup()

    render(<JoinRoomScreen identity={identity} onBack={vi.fn()} onJoined={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('Код комнаты'), 'AB')
    await user.click(screen.getByRole('button', { name: 'Присоединиться' }))

    expect(screen.getByRole('alert')).toHaveTextContent('от 4 до 12 символов')
    expect(joinRoom).not.toHaveBeenCalled()
  })

  it('shows ROOM_FULL as a friendly message from the server', async () => {
    vi.spyOn(roomsApi, 'joinRoom').mockRejectedValue(
      new ApiError(409, { code: 'ROOM_FULL', message: 'Комната заполнена' }),
    )
    const user = userEvent.setup()

    render(<JoinRoomScreen identity={identity} onBack={vi.fn()} onJoined={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('Код комнаты'), 'X7K9M2')
    await user.click(screen.getByRole('button', { name: 'Присоединиться' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Комната заполнена')
  })
})
