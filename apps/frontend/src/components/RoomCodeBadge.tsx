import { useState } from 'react'

interface RoomCodeBadgeProps {
  code: string
}

async function copyToClipboard(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // Fall through to the legacy fallback below.
  }

  try {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    return true
  } catch {
    return false
  }
}

export function RoomCodeBadge({ code }: RoomCodeBadgeProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    void copyToClipboard(code).then((success) => {
      if (!success) return
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <div className="room-code-badge">
      <span className="room-code-badge-label">Код комнаты</span>
      <div className="room-code-badge-row">
        <span className="room-code-badge-value">{code}</span>
        <button type="button" className="btn btn-ghost btn-small" onClick={handleCopy}>
          {copied ? 'Скопировано' : 'Копировать'}
        </button>
      </div>
    </div>
  )
}
