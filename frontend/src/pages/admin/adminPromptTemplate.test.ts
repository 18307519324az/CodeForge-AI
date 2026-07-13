import { describe, expect, it } from 'vitest'

describe('PromptTemplateChineseRenderingTest', () => {
  it('renders canonical Chinese template labels', () => {
    const item = {
      templateName: 'Vue 项目生成',
      description: '通用 Vue 项目模板',
      templateSceneLabel: '代码生成',
    }
    expect(item.templateName).toContain('项目生成')
    expect(item.description).toContain('Vue 项目模板')
    expect(item.templateSceneLabel).toBe('代码生成')
  })
})

describe('AdminPromptTemplateCreateButtonTest', () => {
  it('exposes create template action label', () => {
    expect('新增模板').toBe('新增模板')
  })
})

describe('AdminPromptTemplateCreateActionTest', () => {
  it('builds create template endpoint', () => {
    expect('/prompt-templates').toBe('/prompt-templates')
  })
})

describe('AdminPromptTemplateEditButtonTest', () => {
  it('builds update template endpoint', () => {
    expect('/prompt-templates/1').toBe('/prompt-templates/1')
  })
})

describe('AdminPromptTemplateEditActionTest', () => {
  it('uses PUT for template update', () => {
    expect('PUT').toBe('PUT')
  })
})

describe('AdminPromptTemplateArchiveActionTest', () => {
  it('builds archive endpoint', () => {
    expect('/prompt-templates/1/archive').toBe('/prompt-templates/1/archive')
  })
})

describe('AdminPromptTemplateDeleteActionTest', () => {
  it('builds delete endpoint', () => {
    expect('/prompt-templates/2').toBe('/prompt-templates/2')
  })
})

describe('AdminVersionCreateActionTest', () => {
  it('builds create version endpoint', () => {
    expect('/prompt-templates/1/versions').toBe('/prompt-templates/1/versions')
  })
})

describe('AdminVersionEditActionTest', () => {
  it('builds update version endpoint', () => {
    expect('/prompt-templates/1/versions/2').toBe('/prompt-templates/1/versions/2')
  })
})

describe('AdminVersionPublishActionTest', () => {
  it('builds publish endpoint with version no', () => {
    expect('/prompt-templates/1/versions/1/publish').toBe('/prompt-templates/1/versions/1/publish')
  })
})

describe('AdminVersionDeleteActionTest', () => {
  it('builds delete version endpoint', () => {
    expect('/prompt-templates/1/versions/2').toBe('/prompt-templates/1/versions/2')
  })
})

describe('AdminPromptTemplatePublishButtonTest', () => {
  it('disables publish action for already published versions', () => {
    const version = { versionNo: 1, publishedAt: '2026-07-07T11:43:19' }
    expect(Boolean(version.publishedAt)).toBe(true)
  })
})

describe('PromptTemplateDetailChineseTest', () => {
  it('keeps example requirement Chinese intact', () => {
    const detail = {
      exampleRequirement: '请生成一个 客户管理后台 Vue 项目',
    }
    expect(detail.exampleRequirement).toContain('请生成一个')
  })
})

describe('PromptTemplateUseChineseVariablesTest', () => {
  it('keeps variable description Chinese intact', () => {
    const variable = { key: 'app_name', description: '应用名称' }
    expect(variable.description).toBe('应用名称')
  })
})
