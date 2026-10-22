import { useEffect, useState } from 'react'
import { initTelegramWebApp } from './lib/telegram'
import './App.css'

function App() {
  const [isJoining, setIsJoining] = useState(false)
  const [roomCode, setRoomCode] = useState('')

  useEffect(() => {
    initTelegramWebApp()
  }, [])

  const handleCreateGame = () => {
    // Игровая логика будет подключена позже, на этом этапе только интерфейс.
  }

  const handleJoinGame = () => {
    // Игровая логика будет подключена позже, на этом этапе только интерфейс.
  }

  return (
    <main className="screen">
      <h1 className="title">Нечто</h1>

      <div className="actions">
        <button type="button" className="btn btn-primary" onClick={handleCreateGame}>
          Создать игру
        </button>

        {isJoining ? (
          <form
            className="join-form"
            onSubmit={(event) => {
              event.preventDefault()
              handleJoinGame()
            }}
          >
            <input
              className="room-code-input"
              type="text"
              inputMode="text"
              autoCapitalize="characters"
              placeholder="Код комнаты"
              value={roomCode}
              onChange={(event) => setRoomCode(event.target.value)}
            />
            <button type="submit" className="btn btn-secondary">
              Присоединиться
            </button>
          </form>
        ) : (
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setIsJoining(true)}
          >
            Присоединиться к игре
          </button>
        )}
      </div>
    </main>
  )
}

export default App
