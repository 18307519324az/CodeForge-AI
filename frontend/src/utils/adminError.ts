import type { AxiosError } from 'axios'
import {
  AdminApiError,
  type AdminApiErrorBody,
  type FieldError,
  generateClientRequestId,
} from '@/types/adminError'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

function normalizeBody(raw: unknown, httpStatus?: number): AdminApiErrorBody {
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    const fieldErrors = Array.isArray(obj.fieldErrors)
      ? (obj.fieldErrors as FieldError[])
      : undefined
    return {
      code: typeof obj.code === 'number' ? obj.code : (httpStatus ?? 0),
      message: typeof obj.message === 'string' ? obj.message : adminErrorMessages.fallback,
      errorCode: typeof obj.errorCode === 'string' ? obj.errorCode : undefined,
      data: obj.data ?? null,
      requestId: typeof obj.requestId === 'string' ? obj.requestId : undefined,
      fieldErrors,
    }
  }
  return {
    code: httpStatus ?? 0,
    message: adminErrorMessages.fallback,
    data: null,
  }
}

export function mapAxiosErrorToAdminApiError(error: AxiosError): AdminApiError {
  if (!error.response) {
    const isTimeout = error.code === 'ECONNABORTED'
    return new AdminApiError(
      {
        code: 0,
        message: isTimeout ? adminErrorMessages.timeout : adminErrorMessages.network,
        errorCode: isTimeout ? 'CLIENT_TIMEOUT' : 'CLIENT_NETWORK_ERROR',
        data: null,
        requestId: generateClientRequestId(),
      },
      undefined,
    )
  }

  const { status, data } = error.response
  const body = normalizeBody(data, status)
  if (!body.errorCode) {
    if (status === 403) body.errorCode = 'ADMIN_PERMISSION_REQUIRED'
    else if (status === 404) body.errorCode = 'ADMIN_RESOURCE_NOT_FOUND'
    else if (status === 409) body.errorCode = 'BUSINESS_CONFLICT'
    else if (status === 429) body.errorCode = 'SYSTEM_RATE_LIMITED'
    else if (status >= 500) body.errorCode = 'SYSTEM_INTERNAL_ERROR'
    else if (status === 400) body.errorCode = 'VALIDATION_FAILED'
    else if (status === 401) body.errorCode = 'TOKEN_EXPIRED'
  }
  if (status === 403 && body.message.includes('登录超时')) {
    body.message = adminErrorMessages.forbiddenTitle
  }
  return new AdminApiError(body, status)
}

export function mapResponseBodyError(data: AdminApiErrorBody): AdminApiError {
  return new AdminApiError(data)
}

export { AdminApiError }
