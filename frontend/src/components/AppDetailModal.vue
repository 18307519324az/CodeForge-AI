<template>
  <a-modal v-model:open="visible" title="应用详情" :footer="null" width="560px">
    <div class="app-detail-content">
      <div class="info-item">
        <span class="info-label">应用名称：</span>
        <span>{{ app?.name || '未命名应用' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">应用类型：</span>
        <span>{{ app?.appType || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">状态：</span>
        <span>{{ app?.status || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">可见性：</span>
        <span>{{ app?.visibility || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">工作空间：</span>
        <span>{{ app?.workspaceId || '-' }}</span>
      </div>
      <div class="info-item">
        <span class="info-label">创建时间：</span>
        <span>{{ formatTime(app?.createdAt) }}</span>
      </div>
      <div class="info-item info-item--block">
        <span class="info-label">应用描述：</span>
        <span>{{ app?.description || '暂无应用描述' }}</span>
      </div>

      <div v-if="showActions" class="app-actions">
        <a-space>
          <a-button type="primary" @click="handleEdit">
            <template #icon>
              <EditOutlined />
            </template>
            编辑
          </a-button>
          <a-popconfirm
            title="确定要删除这个应用吗？"
            @confirm="handleDelete"
            ok-text="确定"
            cancel-text="取消"
          >
            <a-button danger>
              <template #icon>
                <DeleteOutlined />
              </template>
              删除
            </a-button>
          </a-popconfirm>
        </a-space>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { formatTime } from '@/utils/time'
import type { LongId } from '@/types/id'

interface AppDetailView {
  id?: LongId
  workspaceId?: LongId
  name?: string
  description?: string
  appType?: string
  status?: string
  visibility?: string
  createdAt?: string
}

interface Props {
  open: boolean
  app?: AppDetailView
  showActions?: boolean
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'edit'): void
  (e: 'delete'): void
}

const props = withDefaults(defineProps<Props>(), {
  showActions: false,
})

const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

const handleEdit = () => {
  emit('edit')
}

const handleDelete = () => {
  emit('delete')
}
</script>

<style scoped>
.app-detail-content {
  padding: 8px 0;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.info-item--block {
  align-items: flex-start;
}

.info-label {
  width: 84px;
  color: #666;
  font-size: 14px;
  flex-shrink: 0;
}

.app-actions {
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
