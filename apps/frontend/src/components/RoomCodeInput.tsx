interface RoomCodeInputProps {
  value: string
  onChange: (value: string) => void
  disabled?: boolean
}

export function RoomCodeInput({ value, onChange, disabled = false }: RoomCodeInputProps) {
  return (
    <input
      className="room-code-input"
      type="text"
      inputMode="text"
      autoCapitalize="characters"
      autoComplete="off"
      placeholder="Код комнаты"
      maxLength={12}
      value={value}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
    />
  )
}
