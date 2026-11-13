import { useEffect } from 'react'

interface ErrorBannerProps {
  message: string
  onDismiss?: () => void
}

/** A short-lived, non-blocking notification for recoverable API errors. */
export function ErrorBanner({ message, onDismiss }: ErrorBannerProps) {
  useEffect(() => {
    if (!onDismiss) return undefined
    const timeout = window.setTimeout(onDismiss, 5_000)
    return () => window.clearTimeout(timeout)
  }, [message, onDismiss])

  return (
    <div className="toast toast-error" role="alert">
      <span>{message}</span>
      {onDismiss && (
        <button className="toast-close" type="button" onClick={onDismiss} aria-label="Закрыть уведомление">
          ×
        </button>
      )}
    </div>
  )
}
