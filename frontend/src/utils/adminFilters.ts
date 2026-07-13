export function hasActiveFilters<T extends Record<string, unknown>>(
  filters: T,
  defaults: T,
): boolean {
  return (Object.keys(filters) as (keyof T)[]).some((key) => {
    const value = filters[key]
    const defaultValue = defaults[key]
    if (value === undefined || value === null || value === '') {
      return false
    }
    return value !== defaultValue
  })
}
