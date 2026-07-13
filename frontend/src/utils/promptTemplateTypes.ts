export interface PromptTemplateVariableItem {
  key: string
  type: string
  required: boolean
  description?: string
}

export interface PromptTemplatePublishedVersion {
  id: number
  versionNo: number
  publishedAt?: string
}

export interface PromptTemplateUserListItem {
  id: number
  templateName: string
  description?: string
  templateScene: string
  templateSceneLabel: string
  applicableAppType: string
  currentVersionNo: number
  publishedVersionId?: number
  updatedAt?: string
}

export interface PromptTemplateUserDetail {
  id: number
  templateName: string
  description?: string
  templateScene: string
  templateSceneLabel: string
  applicableAppType: string
  exampleRequirement?: string
  variables: PromptTemplateVariableItem[]
  publishedVersion: PromptTemplatePublishedVersion
}

export interface PublishedPromptTemplateQuery {
  keyword?: string
  templateScene?: string
  pageNo?: number
  pageSize?: number
}

export interface SelectedPromptTemplate {
  templateId: number
  templateVersionId: number
  templateName: string
  versionNo: number
  applicableAppType?: string
  variables: PromptTemplateVariableItem[]
  variableValues: Record<string, string>
}

export const EMPTY_SELECTED_TEMPLATE = (): SelectedPromptTemplate | null => null
