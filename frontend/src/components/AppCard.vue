<template>
  <div class="app-card" :class="{ 'app-card--featured': featured }">
    <div class="app-preview">
      <img v-if="app.coverUrl" :src="app.coverUrl" :alt="app.name" />
      <div v-else class="app-placeholder">
        <span>AI</span>
      </div>
      <div class="app-overlay">
        <a-button type="primary" @click="handleViewApp">查看应用</a-button>
      </div>
    </div>
    <div class="app-info">
      <h3 class="app-title">{{ app.name || '未命名应用' }}</h3>
      <p class="app-meta">{{ appTypeLabel }} · {{ app.status }}</p>
      <p class="app-description">{{ app.description || '暂无应用描述' }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AiAppListItemResponse } from '@/api/app'
import type { LongId } from '@/types/id'

interface Props {
  app: AiAppListItemResponse
  featured?: boolean
}

interface Emits {
  (e: 'view-app', appId: LongId): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
})

const emit = defineEmits<Emits>()

const appTypeLabel = computed(() => {
  const typeMap: Record<string, string> = {
    WEB_APP: 'Web 应用',
    BLOG: '博客',
    OFFICIAL_SITE: '官网',
    ECOMMERCE: '电商',
    PORTFOLIO: '作品集',
  }
  return typeMap[props.app.appType] || props.app.appType
})

const handleViewApp = () => {
  emit('view-app', props.app.id)
}
</script>

<style scoped>
.app-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition:
    transform 0.3s,
    box-shadow 0.3s;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.25);
}

.app-preview {
  height: 180px;
  background: linear-gradient(135deg, #e2e8f0, #cbd5e1);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.app-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-placeholder {
  font-size: 40px;
  color: #475569;
  font-weight: 700;
}

.app-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.app-card:hover .app-overlay {
  opacity: 1;
}

.app-info {
  padding: 16px;
}

.app-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px;
  color: #0f172a;
}

.app-meta {
  font-size: 13px;
  color: #475569;
  margin: 0 0 8px;
}

.app-description {
  font-size: 14px;
  color: #64748b;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
