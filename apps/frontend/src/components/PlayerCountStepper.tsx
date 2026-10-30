import { MAX_PLAYERS, MIN_PLAYERS } from '../types/room'

interface PlayerCountStepperProps {
  value: number
  onChange: (value: number) => void
  disabled?: boolean
}

export function PlayerCountStepper({ value, onChange, disabled = false }: PlayerCountStepperProps) {
  const canDecrement = value > MIN_PLAYERS
  const canIncrement = value < MAX_PLAYERS

  return (
    <div className="stepper">
      <button
        type="button"
        className="stepper-btn"
        onClick={() => onChange(Math.max(MIN_PLAYERS, value - 1))}
        disabled={disabled || !canDecrement}
        aria-label="Уменьшить количество участников"
      >
        −
      </button>
      <output className="stepper-value" aria-live="polite">
        {value}
      </output>
      <button
        type="button"
        className="stepper-btn"
        onClick={() => onChange(Math.min(MAX_PLAYERS, value + 1))}
        disabled={disabled || !canIncrement}
        aria-label="Увеличить количество участников"
      >
        +
      </button>
    </div>
  )
}
