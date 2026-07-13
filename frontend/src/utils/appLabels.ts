export const appTypeLabel = (appType?: string): string => {
  const map: Record<string, string> = {
    WEB_APP: 'Web 应用',
    ADMIN_WEB: '后台管理',
    BLOG: '博客',
    OFFICIAL_SITE: '官网',
  }
  return map[appType ?? ''] ?? appType ?? '—'
}

export const appStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    DEVELOPING: '开发中',
    READY: '就绪',
    READY_TO_RELEASE: '可发布',
    RELEASED: '已发布',
    ARCHIVED: '已归档',
  }
  return map[status ?? ''] ?? status ?? '—'
}

export const displayStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    GENERATING: '生成中',
    READY: '已就绪',
    FAILED: '生成失败',
    DRAFT: '草稿',
    ARCHIVED: '已归档',
    PUBLISHED: '已发布',
  }
  return map[status ?? ''] ?? status ?? '—'
}

export const displayStatusColor = (status?: string): string => {
  const map: Record<string, string> = {
    GENERATING: 'processing',
    READY: 'success',
    FAILED: 'error',
    DRAFT: 'default',
    ARCHIVED: 'red',
    PUBLISHED: 'green',
  }
  return map[status ?? ''] ?? 'default'
}

export const versionSourceLabel = (source?: string): string => {
  const map: Record<string, string> = {
    AI_DIRECT: 'AI 直连',
    RULE_FALLBACK: '规则降级',
    RULE_ONLY: '纯规则',
  }
  return map[source ?? ''] ?? source ?? '—'
}

export const formatVersionNoFromNumber = (versionNo?: number | null): string => {
  if (versionNo == null) return '暂无版本'
  return `v${versionNo}`
}

export const appStatusColor = (status?: string): string => {
  const map: Record<string, string> = {
    DRAFT: 'default',
    DEVELOPING: 'processing',
    READY: 'success',
    READY_TO_RELEASE: 'orange',
    RELEASED: 'green',
    ARCHIVED: 'red',
  }
  return map[status ?? ''] ?? 'default'
}

export const taskStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '运行中',
    GENERATING: '生成中',
    PERSISTING: '保存中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return map[status ?? ''] ?? status ?? '未知'
}

export const taskStatusColor = (status?: string): string => {
  if (status === 'SUCCESS') return 'green'
  if (status === 'FAILED') return 'red'
  if (status === 'CANCELLED') return 'default'
  if (status === 'QUEUED' || status === 'RUNNING' || status === 'GENERATING' || status === 'PERSISTING') {
    return 'processing'
  }
  return 'default'
}

export const generationSourceLabel = (record: {
  modelName?: string
  taskType?: string
}): string => {
  if (record.modelName) return 'AI 直连'
  const map: Record<string, string> = {
    RULE_GENERATION: '规则生成',
    AI_GENERATION: 'AI 生成',
    CHAT_GENERATION: '对话生成',
  }
  return map[record.taskType ?? ''] ?? '规则生成'
}

export const exportStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    READY: '已就绪',
    PROCESSING: '处理中',
    CREATED: '处理中',
    FAILED: '失败',
    PENDING: '等待中',
  }
  if (!status) return '暂无导出'
  return map[status] ?? status
}

export const exportStatusColor = (status?: string): string => {
  if (status === 'READY') return 'green'
  if (status === 'FAILED') return 'red'
  if (status === 'PROCESSING') return 'processing'
  return 'default'
}

export const packageTypeLabel = (type?: string): string => {
  const map: Record<string, string> = {
    SOURCE_ZIP: '源码包',
    ZIP: '压缩包',
  }
  return map[type ?? ''] ?? type ?? '—'
}

export const formatVersionNo = (versionId?: string | number | null): string => {
  if (versionId == null) return '—'
  return `v${versionId}`
}

export const workspaceLabel = (workspaceId?: string | number | null): string => {
  if (workspaceId == null) return '—'
  return `工作空间 #${workspaceId}`
}

const VERSION_TASK_TYPE_LABELS: Record<string, string> = {
  FULL_GENERATE: '首次完整生成',
  REGENERATE: '重新生成',
  RULE_GENERATION: '规则生成',
  RULE_FALLBACK: '规则降级生成',
  AI_GENERATION: 'AI 生成',
  CHAT_GENERATION: '对话生成',
}

export const versionStatusLabel = (status?: string): string => {
  const map: Record<string, string> = {
    READY: '已就绪',
    BUILDING: '构建中',
    PENDING: '等待中',
    FAILED: '失败',
    ARCHIVED: '已归档',
    ACTIVE: '当前版本',
    SUPERSEDED: '历史版本',
  }
  return map[status ?? ''] ?? status ?? '—'
}

export const versionStatusColor = (status?: string): string => {
  const map: Record<string, string> = {
    READY: 'green',
    BUILDING: 'processing',
    PENDING: 'default',
    FAILED: 'red',
    ARCHIVED: 'default',
    ACTIVE: 'blue',
    SUPERSEDED: 'default',
  }
  return map[status ?? ''] ?? 'default'
}

export const formatVersionChangeSummary = (raw?: string | null, maxLen = 100): string => {
  if (!raw?.trim()) return '版本更新'
  const text = raw.trim()
  if (!text.startsWith('{')) {
    return text.length > maxLen ? `${text.slice(0, maxLen)}…` : text
  }
  try {
    const obj = JSON.parse(text) as Record<string, unknown>
    const taskType = typeof obj.taskType === 'string' ? obj.taskType : undefined
    const requirement = typeof obj.requirement === 'string' ? obj.requirement.trim() : ''
    const prefix = taskType ? (VERSION_TASK_TYPE_LABELS[taskType] ?? '版本更新') : '版本更新'
    if (requirement) {
      const snippet =
        requirement.length > maxLen ? `${requirement.slice(0, maxLen)}…` : requirement
      return `${prefix}：${snippet}`
    }
    return prefix
  } catch {
    return '版本更新'
  }
}
