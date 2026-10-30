import { useState, type FormEvent } from 'react'
import { createRoom } from '../api/rooms'
import { getErrorMessage } from '../api/errors'
import { Button } from '../components/Button'
import { ErrorBanner } from '../components/ErrorBanner'
import { PlayerCountStepper } from '../components/PlayerCountStepper'
import type { TelegramIdentity } from '../lib/telegram'
import type { Room } from '../types/room'

interface CreateRoomScreenProps {
  identity: TelegramIdentity
  onBack: () => void
  onCreated: (room: Room) => void
}

const ROOM_NAME_MAX_LENGTH = 32

export function CreateRoomScreen({ identity, onBack, onCreated }: CreateRoomScreenProps) {
  const [name, setName] = useState('')
  const [maxPlayers, setMaxPlayers] = useState(4)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmedName = name.trim()
  const isNameValid = trimmedName.length > 0 && trimmedName.length <= ROOM_NAME_MAX_LENGTH

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (isSubmitting) return

    if (!isNameValid) {
      setError('Название комнаты должно содержать от 1 до 32 символов')
      return
    }

    setError(null)
    setIsSubmitting(true)
    createRoom(identity, { name: trimmedName, maxPlayers })
      .then(onCreated)
      .catch((submitError: unknown) => setError(getErrorMessage(submitError)))
      .finally(() => setIsSubmitting(false))
  }

  return (
    <main className="screen">
      <h1 className="title title-small">Создать комнату</h1>

      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span className="field-label">Название комнаты</span>
          <input
            className="text-input"
            type="text"
            maxLength={ROOM_NAME_MAX_LENGTH}
            value={name}
            disabled={isSubmitting}
            onChange={(event) => setName(event.target.value)}
            placeholder="Вечерняя партия"
          />
        </label>

        <label className="field">
          <span className="field-label">Количество участников</span>
          <PlayerCountStepper value={maxPlayers} onChange={setMaxPlayers} disabled={isSubmitting} />
        </label>

        {error && <ErrorBanner message={error} />}

        <div className="actions">
          <Button type="submit" variant="primary" isLoading={isSubmitting}>
            Создать
          </Button>
          <Button type="button" variant="ghost" onClick={onBack} disabled={isSubmitting}>
            Назад
          </Button>
        </div>
      </form>
    </main>
  )
}
