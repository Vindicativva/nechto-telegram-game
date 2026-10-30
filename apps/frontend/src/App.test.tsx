import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as roomsApi from './api/rooms'
import type { Room } from './types/room'

afterEach(() => {
  delete window.Telegram
  vi.restoreAllMocks()
})

describe('App', () => {
  it('blocks the app outside of Telegram', () => {
    render(<App />)
    expect(
      screen.getByText('Откройте приложение через Telegram, чтобы создать комнату или присоединиться к игре.'),
    ).toBeInTheDocument()
  })

  it('lets a Telegram user create a room and reach the lobby', async () => {
    window.Telegram = {
      WebApp: {
        ready: vi.fn(),
        expand: vi.fn(),
        initDataUnsafe: { user: { id: 123456789, username: 'ivan_ivanov', first_name: 'Иван' } },
      },
    }

    const room: Room = {
      id: '1',
      code: 'X7K9M2',
      inviteLink: '',
      name: 'Вечерняя партия',
      creatorId: '123456789',
      status: 'LOBBY',
      maxPlayers: 4,
      currentPlayersCount: 1,
      isReadyToStart: false,
      players: [
        {
          telegramId: '123456789',
          username: 'ivan_ivanov',
          displayName: 'Иван',
          isCreator: true,
          connectionStatus: 'ONLINE',
          joinedAt: '2026-10-29T12:00:00.000Z',
          disconnectedAt: null,
        },
      ],
      createdAt: '2026-10-29T12:00:00.000Z',
    }
    vi.spyOn(roomsApi, 'createRoom').mockResolvedValue(room)

    class StubSocket {
      close = vi.fn()
      send = vi.fn()
      onopen: (() => void) | null = null
      onmessage: ((event: MessageEvent<string>) => void) | null = null
      onclose: (() => void) | null = null
    }
    vi.stubGlobal('WebSocket', StubSocket)

    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Создать комнату' }))
    await user.type(screen.getByPlaceholderText('Вечерняя партия'), 'Моя комната')
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByText('X7K9M2')).toBeInTheDocument()
    expect(screen.getByText('Иван (вы)')).toBeInTheDocument()
  })
})
