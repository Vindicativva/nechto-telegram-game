export interface TelegramWebAppUser {
  id: number
  username?: string
  first_name: string
  last_name?: string
}

interface TelegramWebApp {
  ready: () => void
  expand: () => void
  initDataUnsafe?: {
    user?: TelegramWebAppUser
  }
}

declare global {
  interface Window {
    Telegram?: {
      WebApp?: TelegramWebApp
    }
  }
}

export interface TelegramIdentity {
  telegramId: string
  username: string | null
  displayName: string
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

export function getTelegramIdentity(): TelegramIdentity | undefined {
  const user = getTelegramWebApp()?.initDataUnsafe?.user
  if (!user) return undefined

  return {
    telegramId: String(user.id),
    username: user.username ?? null,
    displayName: [user.first_name, user.last_name].filter(Boolean).join(' '),
  }
}
