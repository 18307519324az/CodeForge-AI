const SCENE_LABELS: Record<string, string> = {
  CODE_GEN: '代码生成',
  API_GEN: 'API 接口',
  PAGE_GENERATION: '页面生成',
  APP_GENERATION: '应用生成',
  ADMIN_WEB: '后台管理',
  CONTENT: '内容创作',
  EFFICIENCY: '效率工具',
  DASHBOARD: '数据看板',
  ECOMMERCE: '电商',
}

export function labelTemplateScene(scene?: string) {
  if (!scene) return '其他'
  return SCENE_LABELS[scene] || scene
}

export function buildSceneFilterOptions(scenes: string[]) {
  const unique = Array.from(new Set(scenes.filter(Boolean)))
  return [
    { label: '全部', value: '' },
    ...unique.map((scene) => ({ label: labelTemplateScene(scene), value: scene })),
  ]
}

export function formatTemplateVersionNo(versionNo?: number) {
  if (!versionNo) return '-'
  return `v${versionNo}`
}
