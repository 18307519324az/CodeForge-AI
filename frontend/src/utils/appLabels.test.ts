import { describe, expect, it } from 'vitest'
import { formatVersionChangeSummary } from './appLabels'

describe('formatVersionChangeSummary', () => {
  it('returns plain text summary when not json', () => {
    expect(formatVersionChangeSummary('调整页面布局')).toBe('调整页面布局')
  })

  it('parses FULL_GENERATE json with requirement', () => {
    const raw = JSON.stringify({
      workspaceId: 2,
      appId: 1,
      taskType: 'FULL_GENERATE',
      requirement: '生成一个简洁的待办清单页面',
    })
    expect(formatVersionChangeSummary(raw)).toBe('首次完整生成：生成一个简洁的待办清单页面')
  })

  it('maps RULE_FALLBACK without requirement', () => {
    const raw = JSON.stringify({ taskType: 'RULE_FALLBACK', appId: 1 })
    expect(formatVersionChangeSummary(raw)).toBe('规则降级生成')
  })

  it('falls back when json invalid', () => {
    expect(formatVersionChangeSummary('{bad json')).toBe('版本更新')
  })

  it('returns default for empty input', () => {
    expect(formatVersionChangeSummary('')).toBe('版本更新')
  })
})
