import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import {
  buildGeneratedFilesBrowseTarget,
  MISSING_GENERATION_VERSION_MESSAGE,
  resolveBrowseVersionId,
} from '@/pages/app/generationBrowseNavigation'

describe('SuccessfulTaskBrowseFilesButtonIsClickableTest', () => {
  it('produces a navigable browse target when task succeeded with files', () => {
    const versionId = resolveBrowseVersionId({
      resolvedVersionId: '88',
      currentVersionId: '88',
      events: [],
    })
    const target = buildGeneratedFilesBrowseTarget('2074836281539653632', versionId)
    expect(target).toBe('/apps/2074836281539653632/files?versionId=88')
    expect(target).not.toBeNull()
  })
})

describe('SuccessfulTaskUsesCreatedVersionIdForBrowseTest', () => {
  it('prefers resolvedVersionId from successful file load', () => {
    const versionId = resolveBrowseVersionId({
      resolvedVersionId: '88',
      currentVersionId: '99',
      events: [{ data: { versionId: '77' } }],
    })
    expect(versionId).toBe('88')
  })

  it('falls back to app currentVersionId when resolvedVersionId is missing', () => {
    const versionId = resolveBrowseVersionId({
      resolvedVersionId: null,
      currentVersionId: '88',
      events: [{ data: { versionId: '77' } }],
    })
    expect(versionId).toBe('88')
  })
})

describe('BrowseFilesNavigatesToCorrectRouteTest', () => {
  it('matches app-files route contract', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/apps/:appId/files',
          name: 'app-files',
          component: { template: '<div />' },
        },
      ],
    })

    const target = buildGeneratedFilesBrowseTarget('2074836281539653632', '88')
    expect(target).not.toBeNull()
    const resolved = router.resolve(target!)
    expect(resolved.matched.length).toBeGreaterThan(0)
    expect(resolved.name).toBe('app-files')
    expect(resolved.params.appId).toBe('2074836281539653632')
    expect(resolved.query.versionId).toBe('88')
  })
})

describe('SnowflakeVersionIdRemainsStringTest', () => {
  it('keeps snowflake version ids as strings in browse target', () => {
    const snowflake = '2074836281539653632'
    const versionId = resolveBrowseVersionId({
      resolvedVersionId: snowflake,
      currentVersionId: snowflake,
      events: [],
    })
    expect(typeof versionId).toBe('string')
    expect(versionId).toBe(snowflake)
    const target = buildGeneratedFilesBrowseTarget('2074836281539653632', versionId)
    expect(target).toContain(`versionId=${snowflake}`)
  })

  it('rejects numeric version ids', () => {
    expect(buildGeneratedFilesBrowseTarget('1', 88)).toBeNull()
  })
})

describe('MissingVersionIdShowsActionableErrorTest', () => {
  it('returns null when version id cannot be resolved', () => {
    const versionId = resolveBrowseVersionId({
      resolvedVersionId: null,
      currentVersionId: null,
      events: [{ data: { versionNo: 1 } }],
    })
    expect(versionId).toBeNull()
    expect(buildGeneratedFilesBrowseTarget('2074836281539653632', versionId)).toBeNull()
  })

  it('exposes actionable error message for missing version', () => {
    expect(MISSING_GENERATION_VERSION_MESSAGE).toContain('无法定位生成版本')
  })
})
