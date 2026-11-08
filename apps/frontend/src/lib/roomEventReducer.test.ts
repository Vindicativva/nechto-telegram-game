import { describe, expect, it } from 'vitest'
import type { Room, RoomParticipant } from '../types/room'
import { applyRoomEvent } from './roomEventReducer'

const host: RoomParticipant = {
  telegramId: '123456789',
  username: 'ivan_ivanov',
  displayName: 'Иван',
  isCreator: true,
  connectionStatus: 'ONLINE',
  joinedAt: '2026-10-29T12:00:00.000Z',
  disconnectedAt: null,
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
  players: [host],
  createdAt: '2026-10-29T12:00:00.000Z',
}

describe('applyRoomEvent', () => {
  it('merges the players list and counters from room:player_joined', () => {
    const joined: RoomParticipant = {
      telegramId: '987654321',
      username: 'petr_petrov',
      displayName: 'Петр',
      isCreator: false,
      connectionStatus: 'ONLINE',
      joinedAt: '2026-10-29T12:01:05.000Z',
      disconnectedAt: null,
    }

    const next = applyRoomEvent(room, {
      event: 'room:player_joined',
      data: {
        joinedPlayer: joined,
        room: { code: room.code, currentPlayersCount: 2, isReadyToStart: false, players: [host, joined] },
      },
    })

    expect(next.currentPlayersCount).toBe(2)
    expect(next.players).toHaveLength(2)
    expect(next.name).toBe(room.name)
  })

  it('patches a single participant connection status without touching others', () => {
    const next = applyRoomEvent(room, {
      event: 'room:player_connection_changed',
      data: { telegramId: '123456789', connectionStatus: 'DISCONNECTED' },
    })

    expect(next.players[0].connectionStatus).toBe('DISCONNECTED')
    expect(next.currentPlayersCount).toBe(room.currentPlayersCount)
  })

  it('transfers host on room:host_changed', () => {
    const next = applyRoomEvent(room, {
      event: 'room:host_changed',
      data: {
        previousCreatorId: '123456789',
        newCreatorId: '987654321',
        players: [{ ...host, isCreator: false }],
      },
    })

    expect(next.creatorId).toBe('987654321')
  })

  it('replaces the whole room on room:state_sync', () => {
    const synced: Room = { ...room, currentPlayersCount: 3 }
    const next = applyRoomEvent(room, { event: 'room:state_sync', data: synced })
    expect(next).toEqual(synced)
  })
})
