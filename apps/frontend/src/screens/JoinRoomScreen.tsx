import { useState, type FormEvent } from 'react'
import { getErrorMessage } from '../api/errors'
import { joinRoom } from '../api/rooms'
import { Button } from '../components/Button'
import { ErrorBanner } from '../components/ErrorBanner'
import { RoomCodeInput } from '../components/RoomCodeInput'
import type { TelegramIdentity } from '../lib/telegram'
import { ROOM_CODE_PATTERN, type Room } from '../types/room'

interface JoinRoomScreenProps {
  identity: TelegramIdentity
  onBack: () => void
  onJoined: (room: Room) => void
}

export function JoinRoomScreen({ identity, onBack, onJoined }: JoinRoomScreenProps) {
  const [code, setCode] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (isSubmitting) return

    if (!ROOM_CODE_PATTERN.test(code)) {
      setError('Код комнаты должен содержать от 4 до 12 символов (A-Z, 0-9)')
      return
    }

    setError(null)
    setIsSubmitting(true)
    joinRoom(identity, code)
      .then(onJoined)
      .catch((submitError: unknown) => setError(getErrorMessage(submitError)))
      .finally(() => setIsSubmitting(false))
  }

  return (
    <main className="screen">
      <h1 className="title title-small">Присоединиться к игре</h1>

      <form className="form" onSubmit={handleSubmit}>
        <RoomCodeInput value={code} onChange={setCode} disabled={isSubmitting} />

        {error && <ErrorBanner message={error} />}

        <div className="actions">
          <Button type="submit" variant="primary" isLoading={isSubmitting}>
            Присоединиться
          </Button>
          <Button type="button" variant="ghost" onClick={onBack} disabled={isSubmitting}>
            Назад
          </Button>
        </div>
      </form>
    </main>
  )
}
