import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'
import { ApiError } from './errors'
import { createRoom, joinRoom } from './rooms'

const identity: TelegramIdentity = {
  telegramId: '123456789',
  username: 'ivan_ivanov',
  displayName: 'Иван',
}

const room: Room = {
  id: 'e4eaaaf2-d142-11e1-b3e4-080027620cdd',
  code: 'X7K9M2',
  inviteLink: 'https://t.me/StayAwayGameBot/app?startapp=X7K9M2',
  name: 'Вечерняя партия',
  creatorId: '123456789',
  status: 'LOBBY',
  maxPlayers: 8,
  currentPlayersCount: 1,
  isReadyToStart: false,
  players: [],
  createdAt: '2026-10-29T12:00:00.000Z',
}

describe('createRoom', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('sends the Telegram identity headers and the request body', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify(room), { status: 201 }))

    await createRoom(identity, { name: 'Вечерняя партия', maxPlayers: 8 })

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/rooms'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'X-Telegram-Id': '123456789',
          'X-Telegram-Username': 'ivan_ivanov',
          'X-Telegram-Display-Name': 'Иван',
        }),
      }),
    )
  })

  it('throws a typed ApiError on a non-2xx response', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          error: { code: 'ACTIVE_ROOM_ALREADY_EXISTS', message: 'У пользователя уже есть активная комната' },
        }),
        { status: 409 },
      ),
    )

    await expect(createRoom(identity, { name: 'Игра', maxPlayers: 4 })).rejects.toMatchObject({
      code: 'ACTIVE_ROOM_ALREADY_EXISTS',
      status: 409,
    })
  })
})

describe('joinRoom', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('posts to the join endpoint for the given code', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify(room), { status: 200 }))

    await joinRoom(identity, 'x7k9m2')

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/rooms/x7k9m2/join'),
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('rejects with the parsed error body', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ error: { code: 'ROOM_FULL', message: 'Комната заполнена' } }), {
        status: 409,
      }),
    )

    const error = await joinRoom(identity, 'X7K9M2').catch((caught: unknown) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('ROOM_FULL')
  })
})
