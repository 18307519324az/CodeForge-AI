import { describe, expect, it } from 'vitest'
import {
  applyWorkspacePreset,
  deriveAppDisplayName,
  isGenerationRequirementValid,
  type WorkspacePreset,
} from './deriveAppDisplayName'

describe('deriveAppDisplayName', () => {
  it('derives CRM title from full requirement', () => {
    expect(
      deriveAppDisplayName('生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。'),
    ).toBe('客户管理后台')
  })

  it('derives blog title from full requirement', () => {
    expect(
      deriveAppDisplayName('生成一个个人博客系统，包含文章列表、文章详情和分类标签功能。'),
    ).toBe('个人博客系统')
  })

  it('derives ticket system title', () => {
    expect(
      deriveAppDisplayName('创建一个工单管理系统，支持工单提交、流转和状态看板。'),
    ).toBe('工单管理系统')
  })

  it('derives short mall title', () => {
    expect(deriveAppDisplayName('帮我做一个商城')).toBe('商城')
  })

  it('falls back by app type when requirement is empty after trim', () => {
    expect(deriveAppDisplayName('   ', 'ADMIN_WEB')).toBe('新建管理后台')
  })
})

describe('isGenerationRequirementValid', () => {
  it('rejects greeting-only input', () => {
    expect(isGenerationRequirementValid('你好')).toBe(false)
  })

  it('rejects blank input', () => {
    expect(isGenerationRequirementValid('   ')).toBe(false)
  })

  it('accepts meaningful requirement', () => {
    expect(isGenerationRequirementValid('生成一个客户管理后台，包含客户列表。')).toBe(true)
  })
})

describe('applyWorkspacePreset', () => {
  const preset: WorkspacePreset = {
    key: 'crm',
    label: '客户管理后台',
    prompt: '生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。',
    appType: 'ADMIN_WEB',
  }

  it('only updates workspace context without creating app', () => {
    const result = applyWorkspacePreset(preset)
    expect(result).toEqual({
      requirement: preset.prompt,
      appType: 'ADMIN_WEB',
      selectedPresetKey: 'crm',
    })
  })
})
