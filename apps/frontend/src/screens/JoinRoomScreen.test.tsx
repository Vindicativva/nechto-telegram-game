import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/errors'
import * as roomsApi from '../api/rooms'
import type { TelegramIdentity } from '../lib/telegram'
import { JoinRoomScreen } from './JoinRoomScreen'

const identity: TelegramIdentity = { telegramId: '1', username: null, displayName: 'Иван' }

describe('JoinRoomScreen', () => {
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
