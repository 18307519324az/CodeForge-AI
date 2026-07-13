import type { IdLike } from '@/types/id'

/** Snowflake ID 必须保持 string，禁止 Number() 转换。 */
export function stringifyId(id: IdLike | null | undefined): string | null {
  if (id == null) {
    return null
  }
  const value = String(id).trim()
  return value === '' ? null : value
}
