import type { ConnectionStatus, Room, RoomParticipant } from './room'

export type RoomSnapshotPatch = Pick<
  Room,
  'code' | 'currentPlayersCount' | 'isReadyToStart' | 'players'
>

export interface StateSyncEvent {
  event: 'room:state_sync'
  data: Room
}

export interface PlayerJoinedEvent {
  event: 'room:player_joined'
  data: { joinedPlayer: RoomParticipant; room: RoomSnapshotPatch }
}

export interface PlayerLeftEvent {
  event: 'room:player_left'
  data: { leftPlayerId: string; newCreatorId: string | null; room: RoomSnapshotPatch }
}

export interface HostChangedEvent {
  event: 'room:host_changed'
  data: { previousCreatorId: string; newCreatorId: string; players: RoomParticipant[] }
}

export interface SettingsUpdatedEvent {
  event: 'room:settings_updated'
  data: { maxPlayers: number; isReadyToStart: boolean; room: RoomSnapshotPatch }
}

export interface PlayerConnectionChangedEvent {
  event: 'room:player_connection_changed'
  data: { telegramId: string; connectionStatus: ConnectionStatus }
}

export interface PongEvent {
  event: 'pong'
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
