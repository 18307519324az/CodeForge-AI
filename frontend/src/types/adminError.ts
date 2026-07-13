export interface FieldError {
  field: string
  message: string
}

export interface AdminApiErrorBody {
  code: number
  message: string
  errorCode?: string
  data?: unknown
  requestId?: string
  fieldErrors?: FieldError[]
}

export class AdminApiError extends Error {
  readonly code: number
  readonly errorCode: string
  readonly data: unknown
  readonly requestId: string
  readonly fieldErrors?: FieldError[]
  readonly httpStatus?: number

  constructor(body: Partial<AdminApiErrorBody>, httpStatus?: number) {
    super(body.message?.trim() || '操作失败')
    this.name = 'AdminApiError'
    this.code = body.code ?? httpStatus ?? 0
    this.errorCode = body.errorCode || 'UNKNOWN_ERROR'
    this.data = body.data ?? null
    this.requestId = body.requestId || generateClientRequestId()
    this.fieldErrors = body.fieldErrors
    this.httpStatus = httpStatus
  }
}

export function generateClientRequestId(): string {
  const uuid =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().slice(0, 8)
      : `${Date.now()}`
  return `client-${uuid}`
}

export function isTokenExpiredError(error: AdminApiError): boolean {
  if (error.errorCode.startsWith('TOKEN_')) {
    return true
  }
  return error.code === 40100 || error.code === 40101 || error.code === 40102
}

export function isAdminPermissionError(error: AdminApiError): boolean {
  if (error.httpStatus === 403) {
    return true
  }
  return (
    error.errorCode.includes('PERMISSION_REQUIRED') ||
    (error.errorCode.startsWith('ADMIN_') && error.errorCode.includes('PERMISSION'))
  )
}

export function isAuthLoginFailedError(error: AdminApiError): boolean {
  return error.errorCode === 'AUTH_LOGIN_FAILED' || error.code === 40103
}

export function isValidationError(error: AdminApiError): boolean {
  return error.httpStatus === 400 || error.errorCode.includes('VALIDATION')
}

export function isBusinessConflictError(error: AdminApiError): boolean {
  return error.httpStatus === 409 || error.errorCode.endsWith('_CONFLICT')
}

export function isRateLimitError(error: AdminApiError): boolean {
  return error.httpStatus === 429 || error.errorCode === 'SYSTEM_RATE_LIMITED'
}

export function isServerError(error: AdminApiError): boolean {
  return (error.httpStatus ?? 0) >= 500
}

export function isNetworkError(error: AdminApiError): boolean {
  return (
    error.errorCode === 'CLIENT_NETWORK_ERROR' ||
    error.errorCode === 'CLIENT_TIMEOUT'
  )
}
