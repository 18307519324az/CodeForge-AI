<template>
  <div class="user-info">
    <a-avatar :src="userAvatar" :size="size">
      {{ userDisplayName.charAt(0) }}
    </a-avatar>
    <span v-if="showName" class="user-name">{{ userDisplayName }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
interface UserInfoView {
  displayName?: string
  account?: string
  avatarUrl?: string
  userName?: string
  userAvatar?: string
}

interface Props {
  user?: UserInfoView | null
  size?: number | 'small' | 'default' | 'large'
  showName?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default',
  showName: true,
})

const userDisplayName = computed(() => {
  return props.user?.displayName || props.user?.account || props.user?.userName || '未知用户'
})

const userAvatar = computed(() => {
  return props.user?.avatarUrl || props.user?.userAvatar
})
</script>

<style scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 14px;
  color: #1a1a1a;
}
</style>
