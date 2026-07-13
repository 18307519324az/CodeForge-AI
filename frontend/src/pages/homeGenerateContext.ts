export const DEFAULT_HOME_APP_TYPE = 'WEB_APP'

export const NO_WORKSPACE_MESSAGE = '当前没有可用工作空间，请先创建工作空间'

export function resolveDefaultWorkspaceId<T extends { id: string | number }>(
  workspaces: T[],
): T['id'] | undefined {
  return workspaces[0]?.id
}

export function normalizeWorkspaceId(id: string | number | undefined): string | undefined {
  if (id === undefined || id === null || id === '') {
    return undefined
  }
  return String(id)
}

/** Prefer listed workspaces; otherwise use bootstrap API result. */
export function resolveWorkspaceIdAfterBootstrap(input: {
  listedWorkspaces: { id: string | number }[]
  bootstrappedWorkspaceId?: string | number
}): string | undefined {
  const fromList = normalizeWorkspaceId(resolveDefaultWorkspaceId(input.listedWorkspaces))
  if (fromList) {
    return fromList
  }
  return normalizeWorkspaceId(input.bootstrappedWorkspaceId)
}

export const HOME_WORKSPACE_WARNING_KEY = 'home-workspace-missing'

export function validateHomeGenerationSubmit(input: {
  workspaceId?: string | number
  requirement: string
}): { ok: true } | { ok: false; message: string } {
  const req = input.requirement.trim()
  if (!req) {
    return { ok: false, message: '请输入有效的应用生成需求' }
  }
  if (!input.workspaceId) {
    return { ok: false, message: NO_WORKSPACE_MESSAGE }
  }
  return { ok: true }
}
