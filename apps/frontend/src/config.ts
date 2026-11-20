export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/** HTTPS pages must use WSS: browsers reject an insecure websocket from a secure origin. */
export const WS_URL =
  import.meta.env.VITE_WS_URL ?? API_URL.replace(/^https:/, 'wss:').replace(/^http:/, 'ws:')
