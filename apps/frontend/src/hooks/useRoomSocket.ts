import { useEffect, useRef, useState } from 'react'
import { WS_URL } from '../config'
import { applyRoomEvent } from '../lib/roomEventReducer'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'
import type { RoomWsEvent } from '../types/ws'

export type SocketStatus = 'connecting' | 'open' | 'reconnecting'

const PING_INTERVAL_MS = 25_000
const RECONNECT_DELAY_MS = 2_000

export interface UseRoomSocketResult {
  room: Room
  status: SocketStatus
}

export function useRoomSocket(
  code: string,
  identity: TelegramIdentity,
  initialRoom: Room,
): UseRoomSocketResult {
  const [room, setRoom] = useState(initialRoom)
  const [status, setStatus] = useState<SocketStatus>('connecting')
  const roomRef = useRef(initialRoom)

  useEffect(() => {
    let cancelled = false
    let socket: WebSocket | undefined
    let pingTimer: ReturnType<typeof setInterval> | undefined
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined

    function connect(isReconnect: boolean) {
      if (cancelled) return
      setStatus(isReconnect ? 'reconnecting' : 'connecting')

      const params = new URLSearchParams({
        telegramId: identity.telegramId,
        displayName: identity.displayName,
      })
      socket = new WebSocket(`${WS_URL}/ws/rooms/${encodeURIComponent(code)}?${params}`)

      socket.onopen = () => {
        if (cancelled) return
        setStatus('open')
        pingTimer = setInterval(() => {
          socket?.send(JSON.stringify({ event: 'ping' }))
        }, PING_INTERVAL_MS)
      }

      socket.onmessage = (message: MessageEvent<string>) => {
        if (cancelled) return
        const parsed = JSON.parse(message.data) as RoomWsEvent
        const next = applyRoomEvent(roomRef.current, parsed)
        roomRef.current = next
        setRoom(next)
      }

      socket.onclose = () => {
        if (cancelled) return
        if (pingTimer) clearInterval(pingTimer)
        setStatus('reconnecting')
        reconnectTimer = setTimeout(() => connect(true), RECONNECT_DELAY_MS)
      }
    }

    connect(false)

    return () => {
      cancelled = true
      if (pingTimer) clearInterval(pingTimer)
      if (reconnectTimer) clearTimeout(reconnectTimer)
      socket?.close()
    }
  }, [code, identity.telegramId, identity.displayName])

  return { room, status }
}
