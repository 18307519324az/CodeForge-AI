const LEADING_PREFIX =
  /^(?:生成|创建|开发|设计)(?:一个|一套|一页)?|帮我(?:生成|创建|做(?:一个|一套|一页)?)/u

const BOUNDARY = /[，,。]|(?:包含|需要|支持|具有)/u

const GREETING_ONLY = /^(?:你好|您好|hi|hello|hey)[\s!?.，。]*$/iu

const MAX_NAME_LENGTH = 32

const APP_TYPE_FALLBACK: Record<string, string> = {
  WEB_APP: '新建 Web 应用',
  ADMIN_WEB: '新建管理后台',
  BLOG: '新建博客',
  OFFICIAL_SITE: '新建官网',
}

export function isGenerationRequirementValid(requirement: string): boolean {
  const text = requirement.trim()
  if (!text) {
    return false
  }
  if (GREETING_ONLY.test(text)) {
    return false
  }
  if (text.length < 4) {
    return false
  }
  return true
}

export function deriveAppDisplayName(requirement: string, appType = 'WEB_APP'): string {
  let text = requirement.trim()
  if (!text) {
    return fallbackName(appType)
  }

  text = text.replace(LEADING_PREFIX, '').trim()
  if (!text) {
    return fallbackName(appType)
  }

  const boundaryIndex = text.search(BOUNDARY)
  if (boundaryIndex >= 0) {
    text = text.slice(0, boundaryIndex).trim()
  }

  text = text.replace(/[，,。；;：:!！?？]+$/u, '').trim()
  if (!text) {
    return fallbackName(appType)
  }

  const codePoints = [...text]
  if (codePoints.length > MAX_NAME_LENGTH) {
    text = codePoints.slice(0, MAX_NAME_LENGTH).join('')
  }

  return text || fallbackName(appType)
}

function fallbackName(appType: string): string {
  return APP_TYPE_FALLBACK[appType] ?? APP_TYPE_FALLBACK.WEB_APP
}

export type WorkspacePreset = {
  key: string
  label: string
  prompt: string
  appType: string
}

export function applyWorkspacePreset(
  preset: WorkspacePreset,
): { requirement: string; appType: string; selectedPresetKey: string } {
  return {
    requirement: preset.prompt,
    appType: preset.appType,
    selectedPresetKey: preset.key,
  }
}
