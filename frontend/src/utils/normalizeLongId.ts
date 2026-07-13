/** Snowflake / long ID must remain decimal string; reject floats and Number coercion. */
export function normalizeLongId(value: unknown): string | null {
  if (value == null) {
    return null
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return /^\d+$/.test(trimmed) ? trimmed : null
  }
  return null
}
