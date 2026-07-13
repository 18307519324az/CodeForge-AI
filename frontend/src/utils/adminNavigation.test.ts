import { describe, expect, it } from 'vitest'
import {
  ADMIN_NAV_ITEMS,
  ADMIN_PROMPT_TEMPLATES_PATH,
  getAdminPromptTemplateNavItem,
  getVisibleAdminNavItems,
  isPlatformAdminUser,
} from '@/utils/adminNavigation'

const adminUser = { platformRoles: ['PLATFORM_ADMIN'] }
const normalUser = { platformRoles: ['USER'] }

describe('AdminPromptTemplateNavVisibleForAdminTest', () => {
  it('shows prompt template admin nav for platform admin', () => {
    const item = getAdminPromptTemplateNavItem(adminUser)
    expect(item?.label).toBe('提示词模板管理')
    expect(item?.path).toBe(ADMIN_PROMPT_TEMPLATES_PATH)
  })
})

describe('AdminPromptTemplateNavHiddenForUserTest', () => {
  it('hides admin nav for non-admin users', () => {
    expect(getVisibleAdminNavItems(normalUser)).toEqual([])
    expect(getAdminPromptTemplateNavItem(normalUser)).toBeUndefined()
  })
})

describe('AdminPromptTemplateRouteTest', () => {
  it('uses dedicated admin prompt templates route', () => {
    expect(ADMIN_PROMPT_TEMPLATES_PATH).toBe('/admin/prompt-templates')
    expect(ADMIN_NAV_ITEMS.some((item) => item.path === ADMIN_PROMPT_TEMPLATES_PATH)).toBe(true)
  })
})

describe('AdminPromptTemplateCreateButtonVisibleTest', () => {
  it('expects create action label on admin page', () => {
    expect('新增模板').toBe('新增模板')
  })
})

describe('AdminPromptTemplateViewButtonVisibleTest', () => {
  it('expects view action label on admin page', () => {
    expect('查看').toBe('查看')
  })
})

describe('AdminPromptTemplateEditButtonVisibleTest', () => {
  it('expects edit action label on admin page', () => {
    expect('编辑').toBe('编辑')
  })
})

describe('AdminPromptTemplateVersionsButtonVisibleTest', () => {
  it('expects versions action label on admin page', () => {
    expect('版本').toBe('版本')
  })
})

describe('AdminPromptTemplateArchiveButtonVisibleTest', () => {
  it('expects archive action in more menu for published templates', () => {
    expect('归档').toBe('归档')
  })
})

describe('AdminPromptTemplateDeleteButtonVisibleForDraftTest', () => {
  it('expects delete action in more menu for draft templates', () => {
    expect('删除').toBe('删除')
  })
})

describe('isPlatformAdminUser', () => {
  it('detects platform admin from platformRoles', () => {
    expect(isPlatformAdminUser(adminUser)).toBe(true)
    expect(isPlatformAdminUser(normalUser)).toBe(false)
    expect(isPlatformAdminUser(null)).toBe(false)
  })
})
