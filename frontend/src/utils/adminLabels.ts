export const appTypeLabel = (appType?: string): string => {
  const map: Record<string, string> = {
    WEB_APP: 'Web 应用',
    ADMIN_WEB: '后台管理',
    BLOG: '博客',
    OFFICIAL_SITE: '官网',
  }
  return map[appType ?? ''] ?? appType ?? '-'
}

export const appStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    DEVELOPING: '开发中',
    READY: '就绪',
    ARCHIVED: '已归档',
  }
  return map[status ?? ''] ?? status ?? '-'
}

export const appGenerationStatusLabel = (status?: string, latestTaskId?: string | number | null): string => {
  if (!latestTaskId && status === 'DRAFT') {
    return '未生成'
  }
  const map: Record<string, string> = {
    DRAFT: '待生成',
    DEVELOPING: '生成中',
    READY: '已生成',
    ARCHIVED: '已归档',
  }
  return map[status ?? ''] ?? '未知'
}

export const modelLogSourceLabel = (source?: string): string => {
  const map: Record<string, string> = {
    AI_DIRECT: 'AI 直连',
    RULE_ONLY: '纯规则',
    RULE_FALLBACK: '规则降级',
  }
  return map[source ?? ''] ?? source ?? '-'
}

export const modelLogStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    RUNNING: '进行中',
    TIMEOUT: '超时',
    CANCELED: '已取消',
  }
  return map[status ?? ''] ?? status ?? '-'
}

export const fallbackLabel = (fallback?: boolean): string => {
  if (fallback === true) return '已降级'
  if (fallback === false) return '未降级'
  return '-'
}

export const providerAuthModeLabel = (mode?: string): string => {
  const map: Record<string, string> = {
    API_KEY: 'API Key',
    NONE: '无',
    OPENAI_COMPATIBLE: 'OpenAI 兼容',
  }
  return map[mode ?? ''] ?? mode ?? '-'
}

export const userStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    ACTIVE: '正常',
    DISABLED: '已禁用',
  }
  return map[status ?? ''] ?? status ?? '—'
}

export const userStatusColor = (status?: string): string => {
  if (status === 'ACTIVE') return 'green'
  if (status === 'DISABLED') return 'red'
  return 'default'
}

export const platformRoleLabel = (role?: string): string => {
  const map: Record<string, string> = {
    PLATFORM_ADMIN: '平台管理员',
    USER: '普通用户',
  }
  return map[role ?? ''] ?? role ?? '—'
}

export const quotaUsageTypeLabel = (usageType?: string): string => {
  const map: Record<string, string> = {
    GENERATION: '生成消耗',
    EXPORT: '导出消耗',
    DEPLOY: '部署消耗',
  }
  return map[usageType ?? ''] ?? usageType ?? '—'
}
