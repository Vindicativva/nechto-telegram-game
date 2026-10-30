import { useEffect, useMemo, useState } from 'react'
import { TelegramGate } from './components/TelegramGate'
import { getTelegramIdentity, initTelegramWebApp } from './lib/telegram'
import { CreateRoomScreen } from './screens/CreateRoomScreen'
import { HomeScreen } from './screens/HomeScreen'
import { JoinRoomScreen } from './screens/JoinRoomScreen'
import { LobbyScreen } from './screens/LobbyScreen'
import type { Room } from './types/room'
import './App.css'

type ScreenState =
  | { kind: 'home' }
  | { kind: 'create' }
  | { kind: 'join' }
  | { kind: 'lobby'; room: Room }

function App() {
  const [screen, setScreen] = useState<ScreenState>({ kind: 'home' })
  const identity = useMemo(() => getTelegramIdentity(), [])

  useEffect(() => {
    initTelegramWebApp()
  }, [])

  if (!identity) {
    return <TelegramGate />
  }

  switch (screen.kind) {
    case 'home':
      return (
        <HomeScreen
          onCreate={() => setScreen({ kind: 'create' })}
          onJoin={() => setScreen({ kind: 'join' })}
        />
      )

    case 'create':
      return (
        <CreateRoomScreen
          identity={identity}
          onBack={() => setScreen({ kind: 'home' })}
          onCreated={(room) => setScreen({ kind: 'lobby', room })}
        />
      )

    case 'join':
      return (
        <JoinRoomScreen
          identity={identity}
          onBack={() => setScreen({ kind: 'home' })}
          onJoined={(room) => setScreen({ kind: 'lobby', room })}
        />
      )

    case 'lobby':
      return (
        <LobbyScreen
          identity={identity}
          initialRoom={screen.room}
          onLeave={() => setScreen({ kind: 'home' })}
        />
      )
  }
}

export default App
