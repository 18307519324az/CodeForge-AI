/** 统一解析 API code，兼容 Jackson 将部分数值序列化为字符串的情况。 */
export function coerceApiCode(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return null
}

export function isApiSuccessCode(value: unknown): boolean {
  return coerceApiCode(value) === 0
}
