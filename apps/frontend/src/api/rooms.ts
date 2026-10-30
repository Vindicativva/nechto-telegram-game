import { API_URL } from '../config'
import type { TelegramIdentity } from '../lib/telegram'
import type { CreateRoomRequest, Room } from '../types/room'
import { ApiError, type ApiErrorBody } from './errors'

function authHeaders(identity: TelegramIdentity): HeadersInit {
  const headers: Record<string, string> = {
    'X-Telegram-Id': identity.telegramId,
    'X-Telegram-Display-Name': identity.displayName,
  }
  if (identity.username) {
    headers['X-Telegram-Username'] = identity.username
  }
  return headers
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T
  }

  const body = (await response.json()) as { error: ApiErrorBody }
  throw new ApiError(response.status, body.error)
}

export async function createRoom(
  identity: TelegramIdentity,
  request: CreateRoomRequest,
): Promise<Room> {
  const response = await fetch(`${API_URL}/api/v1/rooms`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(identity),
    },
    body: JSON.stringify(request),
  })
  return parseResponse<Room>(response)
}

export async function getRoom(code: string): Promise<Room> {
  const response = await fetch(`${API_URL}/api/v1/rooms/${encodeURIComponent(code)}`)
  return parseResponse<Room>(response)
}

export async function joinRoom(identity: TelegramIdentity, code: string): Promise<Room> {
  const response = await fetch(`${API_URL}/api/v1/rooms/${encodeURIComponent(code)}/join`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(identity),
    },
    body: JSON.stringify({}),
  })
  return parseResponse<Room>(response)
}

export async function leaveRoom(identity: TelegramIdentity, code: string): Promise<Room> {
  const response = await fetch(`${API_URL}/api/v1/rooms/${encodeURIComponent(code)}/leave`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(identity),
    },
    body: JSON.stringify({}),
  })
  return parseResponse<Room>(response)
}
