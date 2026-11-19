import { API_URL } from '../config'
import type { TelegramIdentity } from '../lib/telegram'
import type { CreateRoomRequest, Room } from '../types/room'
import { ApiError, NetworkError, type ApiErrorBody } from './errors'

function authHeaders(identity: TelegramIdentity): Record<string, string> {
  const headers: Record<string, string> = {
    'X-Telegram-Id': identity.telegramId,
    'X-Telegram-Display-Name': identity.displayName,
  }
  if (identity.username) {
    headers['X-Telegram-Username'] = identity.username
  }
  return headers
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, init)
  } catch {
    throw new NetworkError()
  }

  if (response.ok) {
    return (await response.json()) as T
  }

  try {
    const body = (await response.json()) as { error: ApiErrorBody }
    throw new ApiError(response.status, body.error)
  } catch (parseError) {
    if (parseError instanceof ApiError) throw parseError
    throw new ApiError(response.status, {
      code: 'UNKNOWN',
      message: 'Сервер вернул непредвиденный ответ. Попробуйте ещё раз.',
    })
  }
}

export async function createRoom(
  identity: TelegramIdentity,
  body: CreateRoomRequest,
): Promise<Room> {
  return request<Room>('/api/v1/rooms', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(identity),
    },
    body: JSON.stringify(body),
  })
}

export async function getRoom(code: string): Promise<Room> {
  return request<Room>(`/api/v1/rooms/${encodeURIComponent(code)}`)
}

export async function joinRoom(identity: TelegramIdentity, code: string): Promise<Room> {
  return request<Room>(`/api/v1/rooms/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(identity),
    },
    body: JSON.stringify({}),
  })
}

export async function leaveRoom(identity: TelegramIdentity, code: string): Promise<Room> {
  return request<Room>(`/api/v1/rooms/${encodeURIComponent(code)}/leave`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(identity),
    },
    body: JSON.stringify({}),
  })
}
