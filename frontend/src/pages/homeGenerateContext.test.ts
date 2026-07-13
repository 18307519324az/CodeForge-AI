import { describe, expect, it } from 'vitest'
import {
  DEFAULT_HOME_APP_TYPE,
  HOME_WORKSPACE_WARNING_KEY,
  NO_WORKSPACE_MESSAGE,
  resolveDefaultWorkspaceId,
  resolveWorkspaceIdAfterBootstrap,
  validateHomeGenerationSubmit,
} from './homeGenerateContext'

describe('WorkspaceContextWithoutMiniSelectTest', () => {
  it('uses first readable workspace as implicit context', () => {
    expect(resolveDefaultWorkspaceId([{ id: '1001' }, { id: '1002' }])).toBe('1001')
  })

  it('returns undefined when no workspace exists', () => {
    expect(resolveDefaultWorkspaceId([])).toBeUndefined()
  })
})

describe('DefaultAppTypeWithoutMiniSelectTest', () => {
  it('defaults app type to WEB_APP without mini-select UI', () => {
    expect(DEFAULT_HOME_APP_TYPE).toBe('WEB_APP')
  })
})

describe('NoWorkspaceBlocksGenerationTest', () => {
  it('blocks submit and shows workspace message', () => {
    const result = validateHomeGenerationSubmit({
      workspaceId: undefined,
      requirement: '生成一个博客',
    })
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.message).toBe(NO_WORKSPACE_MESSAGE)
    }
  })
})

describe('HomeGenerateStillWorksTest', () => {
  it('allows submit when workspace and requirement are present', () => {
    const result = validateHomeGenerationSubmit({
      workspaceId: '1001',
      requirement: '生成一个客户管理后台',
    })
    expect(result).toEqual({ ok: true })
  })
})

describe('HomeWorkspaceBootstrapTest', () => {
  it('uses listed workspace when available', () => {
    expect(
      resolveWorkspaceIdAfterBootstrap({
        listedWorkspaces: [{ id: '62' }],
        bootstrappedWorkspaceId: '99',
      }),
    ).toBe('62')
  })

  it('falls back to bootstrap workspace id when list is empty', () => {
    expect(
      resolveWorkspaceIdAfterBootstrap({
        listedWorkspaces: [],
        bootstrappedWorkspaceId: '63',
      }),
    ).toBe('63')
  })

  it('keeps snowflake ids as strings', () => {
    expect(
      resolveWorkspaceIdAfterBootstrap({
        listedWorkspaces: [{ id: '2074742054218625024' }],
      }),
    ).toBe('2074742054218625024')
  })
})

describe('HomeWorkspaceWarningDedupTest', () => {
  it('uses stable toast key for workspace missing feedback', () => {
    expect(HOME_WORKSPACE_WARNING_KEY).toBe('home-workspace-missing')
  })
})
