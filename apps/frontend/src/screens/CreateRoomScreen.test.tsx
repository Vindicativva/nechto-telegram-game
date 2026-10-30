import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/errors'
import * as roomsApi from '../api/rooms'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'
import { CreateRoomScreen } from './CreateRoomScreen'

const identity: TelegramIdentity = { telegramId: '1', username: null, displayName: 'Иван' }

const room: Room = {
  id: '1',
  code: 'X7K9M2',
  inviteLink: '',
  name: 'Вечерняя партия',
  creatorId: '1',
  status: 'LOBBY',
  maxPlayers: 4,
  currentPlayersCount: 1,
  isReadyToStart: false,
  players: [],
  createdAt: '2026-10-29T12:00:00.000Z',
}

describe('CreateRoomScreen', () => {
  it('blocks submission and shows an inline error when the name is empty, without calling the API', async () => {
    const createRoom = vi.spyOn(roomsApi, 'createRoom')
    const onCreated = vi.fn()
    const user = userEvent.setup()

    render(<CreateRoomScreen identity={identity} onBack={vi.fn()} onCreated={onCreated} />)
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(screen.getByRole('alert')).toHaveTextContent('от 1 до 32 символов')
    expect(createRoom).not.toHaveBeenCalled()
    expect(onCreated).not.toHaveBeenCalled()
  })

  it('disables the submit button while the request is in flight and calls onCreated on success', async () => {
    let resolveCreate!: (room: Room) => void
    vi.spyOn(roomsApi, 'createRoom').mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = resolve
      }),
    )
    const onCreated = vi.fn()
    const user = userEvent.setup()

    render(<CreateRoomScreen identity={identity} onBack={vi.fn()} onCreated={onCreated} />)
    await user.type(screen.getByPlaceholderText('Вечерняя партия'), 'Моя комната')

    const submitButton = screen.getByRole('button', { name: /Создать|Загрузка/ })
    await user.click(submitButton)

    expect(screen.getByRole('button', { name: 'Загрузка…' })).toBeDisabled()

    resolveCreate(room)
    await screen.findByRole('button', { name: 'Создать' })
    expect(onCreated).toHaveBeenCalledWith(room)
  })

  it('shows the server error message when room creation fails', async () => {
    vi.spyOn(roomsApi, 'createRoom').mockRejectedValue(
      new ApiError(409, { code: 'ACTIVE_ROOM_ALREADY_EXISTS', message: 'У вас уже есть активная комната' }),
    )
    const user = userEvent.setup()

    render(<CreateRoomScreen identity={identity} onBack={vi.fn()} onCreated={vi.fn()} />)
    await user.type(screen.getByPlaceholderText('Вечерняя партия'), 'Моя комната')
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('У вас уже есть активная комната')
  })
})
