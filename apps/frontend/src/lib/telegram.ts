export interface TelegramWebAppUser {
  id: number
  username?: string
  first_name: string
  last_name?: string
}

interface TelegramWebApp {
  ready: () => void
  expand: () => void
  /** Signed, opaque data which must be verified by the backend with the bot token. */
  initData?: string
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

export interface TelegramAuthentication {
  identity: TelegramIdentity
  /** Do not parse or trust this on the client; pass it to the backend for verification. */
  initData: string
}

export function getTelegramWebApp(): TelegramWebApp | undefined {
  return typeof window === 'undefined' ? undefined : window.Telegram?.WebApp
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

/**
 * Returns the display identity together with Telegram's original signed
 * `initData`. `initDataUnsafe` is used only to render a name; authorization
 * must always be based on `initData` verified by the server.
 */
export function getTelegramAuthentication(): TelegramAuthentication | undefined {
  const identity = getTelegramIdentity()
  const initData = getTelegramWebApp()?.initData

  if (!identity || !initData) return undefined

  return { identity, initData }
}
