import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import { WS_URL } from '../config'
import { applyRoomEvent } from '../lib/roomEventReducer'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'
import type { GameSocketConnectHeaders, RoomSubscription, RoomWsEvent } from '../types/ws'

export type SocketStatus = 'connecting' | 'open' | 'reconnecting' | 'error' | 'disconnected'

const RECONNECT_DELAY_MS = 2_000
const HEARTBEAT_MS = 20_000

export interface UseGameSocketResult {
  room: Room
  status: SocketStatus
}

export interface UseGameSocketOptions {
  code: string
  identity: TelegramIdentity
  initialRoom: Room
  /** Signed Telegram initData. The backend must validate it before trusting headers. */
  initData?: string
}

function roomDestination(code: string): RoomSubscription['destination'] {
  return `/topic/room/${encodeURIComponent(code)}`
}

function parseRoomEvent(body: string): RoomWsEvent | undefined {
  try {
    const value: unknown = JSON.parse(body)
    if (typeof value === 'object' && value !== null && 'event' in value) {
      return value as RoomWsEvent
    }
  } catch {
    // A malformed network frame must never take down the React tree.
  }
  return undefined
}

/** Keeps a STOMP connection for a lobby room alive and exposes its network state to the UI. */
export function useGameSocket({
  code,
  identity,
  initialRoom,
  initData,
}: UseGameSocketOptions): UseGameSocketResult {
  const [room, setRoom] = useState(initialRoom)
  const [status, setStatus] = useState<SocketStatus>('connecting')
  const roomRef = useRef(initialRoom)

  useEffect(() => {
    let disposed = false
    const connectHeaders: GameSocketConnectHeaders = {
      'X-Telegram-Id': identity.telegramId,
      'X-Telegram-Display-Name': identity.displayName,
      ...(identity.username ? { 'X-Telegram-Username': identity.username } : {}),
      ...(initData ? { 'X-Telegram-Init-Data': initData } : {}),
    }

    const client = new Client({
      brokerURL: `${WS_URL}/ws`,
      connectHeaders,
      reconnectDelay: RECONNECT_DELAY_MS,
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      onConnect: () => {
        if (disposed) return
        setStatus('open')
        client.subscribe(roomDestination(code), (message) => {
          const event = parseRoomEvent(message.body)
          if (!event || disposed) return
          const next = applyRoomEvent(roomRef.current, event)
          roomRef.current = next
          setRoom(next)
        })
      },
      onWebSocketClose: () => {
        if (!disposed) setStatus('reconnecting')
      },
      onWebSocketError: () => {
        if (!disposed) setStatus('error')
      },
      onStompError: () => {
        if (!disposed) setStatus('error')
      },
    })

    client.activate()
    return () => {
      disposed = true
      void client.deactivate()
    }
  }, [code, identity.telegramId, identity.username, identity.displayName, initData])

  return { room, status }
}

/** @deprecated Use useGameSocket for new code. */
export function useRoomSocket(
  code: string,
  identity: TelegramIdentity,
  initialRoom: Room,
): UseGameSocketResult {
  return useGameSocket({ code, identity, initialRoom })
}
