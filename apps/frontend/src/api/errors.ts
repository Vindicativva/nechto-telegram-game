export interface ApiErrorBody {
  code: string
  message: string
  details?: { field: string; issue: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: { field: string; issue: string }[]

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.details = body.details ?? []
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Не удалось подключиться к серверу. Проверьте соединение и попробуйте снова.')
    this.name = 'NetworkError'
  }
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError || error instanceof NetworkError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Неизвестная ошибка'
}
