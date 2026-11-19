import type { Room } from '../types/room'
import type { RoomWsEvent } from '../types/ws'

export function applyRoomEvent(room: Room, event: RoomWsEvent): Room {
  switch (event.event) {
    case 'room:state_sync':
      return event.data

    case 'room:player_joined':
    case 'room:player_left':
    case 'room:settings_updated':
      return { ...room, ...event.data.room }

    case 'room:host_changed':
      return {
        ...room,
        creatorId: event.data.newCreatorId,
        players: event.data.players,
      }

    case 'room:player_connection_changed':
      return {
        ...room,
        players: room.players.map((player) =>
          player.telegramId === event.data.telegramId
            ? { ...player, connectionStatus: event.data.connectionStatus }
            : player,
        ),
      }

    case 'pong':
      return room
  }
}
