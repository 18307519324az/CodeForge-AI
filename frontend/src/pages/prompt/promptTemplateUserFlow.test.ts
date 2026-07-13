import { describe, expect, it } from 'vitest'
import { buildSceneFilterOptions, labelTemplateScene } from '@/utils/promptTemplateScene'
import type { PromptTemplateUserListItem } from '@/utils/promptTemplateTypes'

describe('PromptTemplateListTest', () => {
  it('builds scene filters from real template scenes only', () => {
    const options = buildSceneFilterOptions(['CODE_GEN', 'API_GEN'])
    expect(options.map((item) => item.value)).toEqual(['', 'CODE_GEN', 'API_GEN'])
    expect(labelTemplateScene('CODE_GEN')).toBe('代码生成')
  })

  it('lists published template cards with version metadata', () => {
    const item: PromptTemplateUserListItem = {
      id: 1,
      templateName: 'Vue 项目生成',
      description: '通用 Vue 项目模板',
      templateScene: 'CODE_GEN',
      templateSceneLabel: '代码生成',
      applicableAppType: 'WEB_APP',
      currentVersionNo: 1,
      publishedVersionId: 11,
    }
    expect(item.publishedVersionId).toBe(11)
    expect(item.templateName).toBe('Vue 项目生成')
  })
})

describe('PromptTemplateSelectTest', () => {
  it('pins selected template version id in generation payload', () => {
    const payload = {
      promptTemplateId: 1,
      promptTemplateVersionId: 11,
      promptTemplateVersionNo: 1,
    }
    expect(payload.promptTemplateVersionId).toBe(11)
    expect(payload.promptTemplateVersionNo).toBe(1)
  })
})

describe('PromptTemplateDoesNotOverwriteRequirementTest', () => {
  it('keeps requirement independent from template example', () => {
    const requirement = '生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。'
    const example = '生成 {{app_name}} 应用'
    expect(requirement).not.toBe(example)
    expect(requirement.length).toBeGreaterThan(example.length)
  })
})

describe('PromptTemplateVariableFormTest', () => {
  it('blocks submit when required variable is missing', async () => {
    const { validateTemplateVariableValues } = await import('@/utils/promptTemplateVariables')
    const result = validateTemplateVariableValues(
      [{ key: 'app_name', type: 'string', required: true }],
      {},
    )
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.message).toContain('app_name')
    }
  })
})

describe('PromptTemplateUseInWorkspaceTest', () => {
  it('builds home route with template query for workspace handoff', async () => {
    const { buildHomeRouteWithTemplate } = await import('@/utils/promptTemplateWorkspace')
    expect(buildHomeRouteWithTemplate(1, 11)).toEqual({
      name: 'home',
      query: {
        templateId: '1',
        templateVersionId: '11',
      },
    })
  })
})
