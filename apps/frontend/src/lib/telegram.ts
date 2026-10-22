interface TelegramWebApp {
  ready: () => void
  expand: () => void
  setHeaderColor?: (color: string) => void
  setBackgroundColor?: (color: string) => void
  colorScheme?: 'light' | 'dark'
}

declare global {
  interface Window {
    Telegram?: {
      WebApp?: TelegramWebApp
    }
  }
}

export function getTelegramWebApp(): TelegramWebApp | undefined {
  return window.Telegram?.WebApp
}

export function initTelegramWebApp(): void {
  const webApp = getTelegramWebApp()
  if (!webApp) return

  webApp.ready()
  webApp.expand()
}
