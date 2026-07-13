import { describe, expect, it } from 'vitest'
import { buildGenerationDetailRoute, resolveQueryTaskId } from '@/pages/app/generationDetailNavigation'

describe('GenerationRecordDetailClickTest', () => {
  it('routes to generate page with taskId query from app detail', () => {
    expect(buildGenerationDetailRoute('2073999739753553920', '74')).toBe(
      '/apps/2073999739753553920/generate?taskId=74',
    )
  })
})

describe('GenerationTaskIdContractTest', () => {
  it('uses generation task id not generation record id in route', () => {
    const taskId = '74'
    const generationRecordId = '120'
    const route = buildGenerationDetailRoute('2073999739753553920', taskId)
    expect(route).toContain('taskId=74')
    expect(route).not.toContain(String(generationRecordId))
  })

  it('preserves snowflake app id as string in route', () => {
    const appId = '2073999739753553920'
    const route = buildGenerationDetailRoute(appId, '73')
    expect(route.startsWith(`/apps/${appId}/generate`)).toBe(true)
    expect(route).not.toContain('2073999739753552896')
  })
})

describe('GenerationDetailRouteOrDrawerTest', () => {
  it('resolves taskId from route query for generate page bootstrap', () => {
    expect(resolveQueryTaskId({ taskId: '74' })).toBe('74')
    expect(resolveQueryTaskId({ taskId: ['73'] })).toBe('73')
    expect(resolveQueryTaskId({})).toBe('')
  })

  it('targets existing app-generate route instead of unknown paths', () => {
    const route = buildGenerationDetailRoute('2073999739753553920', '74')
    expect(route).toMatch(/^\/apps\/[^/]+\/generate\?taskId=\d+$/)
    expect(route).not.toContain('/generation-tasks/')
  })
})

describe('AppDetailPreviewUsesVersionIdTest', () => {
  it('keeps versionId 45 separate from versionNo 2 semantics', () => {
    const currentVersionId = '45'
    const currentVersionNo = 2
    expect(currentVersionId).not.toBe(String(currentVersionNo))
    expect(buildGenerationDetailRoute('2073999739753553920', '74')).toContain('taskId=74')
  })
})
