export type RoomStatus = 'LOBBY' | 'IN_PROGRESS' | 'FINISHED' | 'ARCHIVED'

export type ConnectionStatus = 'ONLINE' | 'DISCONNECTED'

export interface RoomParticipant {
  telegramId: string
  username: string | null
  displayName: string | null
  isCreator: boolean
  connectionStatus: ConnectionStatus
  joinedAt: string
  disconnectedAt: string | null
}

export interface Room {
  id: string
  code: string
  inviteLink: string
  name: string
  creatorId: string
  status: RoomStatus
  maxPlayers: number
  currentPlayersCount: number
  isReadyToStart: boolean
  players: RoomParticipant[]
  createdAt: string
}

export interface CreateRoomRequest {
  name: string
  maxPlayers: number
}

export const MIN_PLAYERS = 4
export const MAX_PLAYERS = 12

export const ROOM_CODE_PATTERN = /^[A-Z0-9]{4,12}$/
