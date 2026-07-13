import { describe, expect, it } from 'vitest'
import type { PromptTemplateUserDetail, PromptTemplateUserListItem } from '@/utils/promptTemplateTypes'

const EXPECTED_NAME = 'Vue 项目生成'
const EXPECTED_DESC = '通用 Vue 项目模板'

function assertChineseMetadata(name: string | undefined, description: string | undefined) {
  expect(name).toBe(EXPECTED_NAME)
  expect(description).toBe(EXPECTED_DESC)
  expect(name).toContain('项目')
  expect(description).toContain('模板')
  expect(name).not.toMatch(/[?椤]/)
  expect(description).not.toMatch(/[?椤]/)
}

describe('PromptTemplateChineseTitleRenderingTest', () => {
  it('renders published template title without mojibake markers', () => {
    const item: PromptTemplateUserListItem = {
      id: 1,
      templateName: EXPECTED_NAME,
      description: EXPECTED_DESC,
      templateScene: 'CODE_GEN',
      templateSceneLabel: '代码生成',
      applicableAppType: 'WEB_APP',
      currentVersionNo: 1,
      publishedVersionId: 11,
    }
    assertChineseMetadata(item.templateName, item.description)
  })
})

describe('PromptTemplateChineseDescriptionRenderingTest', () => {
  it('keeps description readable Chinese text in card model', () => {
    const item: PromptTemplateUserListItem = {
      id: 1,
      templateName: EXPECTED_NAME,
      description: EXPECTED_DESC,
      templateScene: 'CODE_GEN',
      templateSceneLabel: '代码生成',
      applicableAppType: 'WEB_APP',
      currentVersionNo: 1,
      publishedVersionId: 11,
    }
    expect(item.description).toBe('通用 Vue 项目模板')
  })
})

describe('PromptTemplateDetailChineseRenderingTest', () => {
  it('renders detail fields in Chinese without exposing system prompt', () => {
    const detail: PromptTemplateUserDetail = {
      id: 1,
      templateName: EXPECTED_NAME,
      description: EXPECTED_DESC,
      templateScene: 'CODE_GEN',
      templateSceneLabel: '代码生成',
      applicableAppType: 'WEB_APP',
      exampleRequirement: '请生成一个 {{app_name}} Vue 项目',
      variables: [{ key: 'app_name', type: 'string', required: true, description: '应用名称' }],
      publishedVersion: { id: 11, versionNo: 1, publishedAt: '2026-07-07T12:00:00' },
    }
    assertChineseMetadata(detail.templateName, detail.description)
    expect(detail.exampleRequirement).toContain('请生成一个')
    expect(detail.variables[0]?.description).toBe('应用名称')
    expect((detail as unknown as Record<string, unknown>).systemPrompt).toBeUndefined()
  })
})

describe('PromptTemplateUseVariablesUtf8Test', () => {
  it('keeps variable descriptions in Chinese for workspace handoff', () => {
    const variables = [{ key: 'app_name', type: 'string', required: true, description: '应用名称' }]
    expect(variables[0]?.description).toBe('应用名称')
    expect(variables[0]?.description).not.toMatch(/[?]/)
  })
})
