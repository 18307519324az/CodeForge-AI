import { Modal } from 'ant-design-vue'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

export function showConflictModal(content: string, title = adminErrorMessages.conflictTitle) {
  Modal.warning({
    title,
    content,
    okText: adminErrorMessages.conflictOk,
  })
}
