import { Button } from '../components/Button'

interface HomeScreenProps {
  onCreate: () => void
  onJoin: () => void
}

export function HomeScreen({ onCreate, onJoin }: HomeScreenProps) {
  return (
    <main className="screen">
      <h1 className="title">Нечто</h1>

      <div className="actions">
        <Button variant="primary" onClick={onCreate}>
          Создать комнату
        </Button>
        <Button variant="secondary" onClick={onJoin}>
          Присоединиться
        </Button>
      </div>
    </main>
  )
}
