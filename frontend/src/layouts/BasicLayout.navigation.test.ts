import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { computed, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { ADMIN_NAV_ITEMS, isPlatformAdminUser } from '@/utils/adminNavigation'

const layoutSource = readFileSync(resolve(__dirname, 'BasicLayout.vue'), 'utf8')

describe('AdminNavigationRuntimeSourceTest', () => {
  it('BasicLayout consumes adminNavigation as the admin sidebar source', () => {
    expect(layoutSource).toContain("from '@/utils/adminNavigation'")
    expect(layoutSource).toContain('ADMIN_NAV_ITEMS')
    expect(layoutSource).toContain('v-for="item in adminItems"')
    expect(layoutSource).not.toContain('cf-nav-spacer')
    expect(layoutSource).toContain('min-height: 0')
  })
})

describe('AdminPromptTemplateNavVisibleForPlatformAdminTest', () => {
  it('includes prompt template admin item for platform admin', () => {
    const adminItems = ADMIN_NAV_ITEMS
    const item = adminItems.find((entry) => entry.key === 'adm-tpl')
    expect(item?.label).toBe('提示词模板管理')
    expect(item?.path).toBe('/admin/prompt-templates')
  })
})

describe('AdminPromptTemplateNavAppearsAfterAsyncUserLoadTest', () => {
  it('reveals admin items after async user load', () => {
    const loginUser = ref<{ platformRoles: string[] } | null>(null)
    const adminItems = computed(() => (isPlatformAdminUser(loginUser.value) ? ADMIN_NAV_ITEMS : []))

    expect(adminItems.value.some((item) => item.label === '提示词模板管理')).toBe(false)

    loginUser.value = { platformRoles: ['PLATFORM_ADMIN'] }

    expect(adminItems.value.some((item) => item.label === '提示词模板管理')).toBe(true)
  })
})

describe('AdminPromptTemplateNavHiddenForRegularUserTest', () => {
  it('keeps admin items empty for regular users', () => {
    const loginUser = ref<{ platformRoles: string[] } | null>({ platformRoles: ['USER'] })
    const adminItems = computed(() => (isPlatformAdminUser(loginUser.value) ? ADMIN_NAV_ITEMS : []))
    expect(adminItems.value).toEqual([])
  })
})

describe('UserPromptTemplatePageDoesNotShowCrudTest', () => {
  it('keeps user prompt page actions read-only', () => {
    const userPageSource = readFileSync(resolve(__dirname, '../pages/prompt/PromptTemplateListPage.vue'), 'utf8')
    expect(userPageSource).toContain('查看详情')
    expect(userPageSource).toContain('使用模板')
    expect(userPageSource).not.toContain('新增模板')
    expect(userPageSource).not.toContain('/admin/prompt-templates')
  })
})

describe('AdminPromptTemplateCrudDomVisibleTest', () => {
  it('keeps admin CRUD labels on admin page source', () => {
    const adminPageSource = readFileSync(resolve(__dirname, '../pages/admin/PromptTemplateManagePage.vue'), 'utf8')
    expect(adminPageSource).toContain('新增模板')
    expect(adminPageSource).toContain('查看')
    expect(adminPageSource).toContain('编辑')
    expect(adminPageSource).toContain('版本')
    expect(adminPageSource).toContain('归档')
    expect(adminPageSource).toContain('删除')
  })
})
