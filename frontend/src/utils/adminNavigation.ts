import type { Component } from 'vue'
import {
  ApiOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  FormOutlined,
  LineChartOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { CurrentUserResponse } from '@/api/auth'

export interface AdminNavItem {
  key: string
  label: string
  icon: Component
  path: string
}

export const ADMIN_PROMPT_TEMPLATES_PATH = '/admin/prompt-templates'

export const ADMIN_NAV_ITEMS: AdminNavItem[] = [
  { key: 'adm-dash', label: '管理概览', icon: BarChartOutlined, path: '/admin/dashboard' },
  { key: 'adm-users', label: '用户管理', icon: UserOutlined, path: '/admin/users' },
  { key: 'adm-apps', label: '应用管理', icon: AppstoreOutlined, path: '/admin/apps' },
  { key: 'adm-prov', label: '模型供应商', icon: ApiOutlined, path: '/admin/model-providers' },
  {
    key: 'adm-tpl',
    label: '提示词模板管理',
    icon: FormOutlined,
    path: ADMIN_PROMPT_TEMPLATES_PATH,
  },
  { key: 'adm-logs', label: '调用日志', icon: LineChartOutlined, path: '/admin/model-call-logs' },
]

export function isPlatformAdminUser(
  loginUser: Pick<CurrentUserResponse, 'platformRoles'> | null | undefined,
): boolean {
  return loginUser?.platformRoles?.includes('PLATFORM_ADMIN') ?? false
}

export function getVisibleAdminNavItems(
  loginUser: Pick<CurrentUserResponse, 'platformRoles'> | null | undefined,
): AdminNavItem[] {
  return isPlatformAdminUser(loginUser) ? ADMIN_NAV_ITEMS : []
}

export function getAdminPromptTemplateNavItem(
  loginUser: Pick<CurrentUserResponse, 'platformRoles'> | null | undefined,
): AdminNavItem | undefined {
  return getVisibleAdminNavItems(loginUser).find((item) => item.key === 'adm-tpl')
}
