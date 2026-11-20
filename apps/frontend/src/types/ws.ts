import type { ConnectionStatus, Room, RoomParticipant } from './room'

/** Headers attached to a STOMP CONNECT frame. */
export interface GameSocketConnectHeaders {
  [header: string]: string
  'X-Telegram-Id': string
  'X-Telegram-Display-Name': string
}

/** Client message for backends that require an explicit lobby subscription. */
export interface LobbySubscribeMessage {
  event: 'lobby:subscribe'
  data: {
    code: string
    telegramId: string
    username?: string
    displayName: string
  }
}

export interface RoomSubscription {
  destination: `/topic/room/${string}`
}

export type RoomSnapshotPatch = Pick<
  Room,
  'code' | 'currentPlayersCount' | 'isReadyToStart' | 'players'
>

export interface StateSyncEvent {
  event: 'room:state_sync'
  timestamp?: string
  data: Room
}

export interface PlayerJoinedEvent {
  event: 'room:player_joined'
  timestamp?: string
  data: { joinedPlayer: RoomParticipant; room: RoomSnapshotPatch }
}

export interface PlayerLeftEvent {
  event: 'room:player_left'
  timestamp?: string
  data: { leftPlayerId: string; newCreatorId: string | null; room: RoomSnapshotPatch }
}

export interface HostChangedEvent {
  event: 'room:host_changed'
  timestamp?: string
  data: { previousCreatorId: string; newCreatorId: string; players: RoomParticipant[] }
}

export interface SettingsUpdatedEvent {
  event: 'room:settings_updated'
  timestamp?: string
  data: { maxPlayers: number; isReadyToStart: boolean; room: RoomSnapshotPatch }
}

export interface PlayerConnectionChangedEvent {
  event: 'room:player_connection_changed'
  timestamp?: string
  data: { telegramId: string; connectionStatus: ConnectionStatus }
}

export interface PongEvent {
  event: 'pong'
  timestamp?: string
  data?: undefined
}

export type RoomWsEvent =
  | StateSyncEvent
  | PlayerJoinedEvent
  | PlayerLeftEvent
  | HostChangedEvent
  | SettingsUpdatedEvent
  | PlayerConnectionChangedEvent
  | PongEvent
