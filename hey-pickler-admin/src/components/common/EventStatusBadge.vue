<template>
  <el-popover placement="bottom" :width="160" trigger="click" :visible="visible" @update:visible="visible = $event">
    <template #reference>
      <span class="status-badge clickable" :style="{ backgroundColor: statusColor(status) }" @click="!readonly && (visible = true)">
        {{ formatStatus(status) }} <span v-if="!readonly">▾</span>
      </span>
    </template>
    <div class="status-options">
      <div v-for="t in getAllowedTargets(status)" :key="t" class="status-option" @click="pick(t)">
        <span class="status-dot" :style="{ backgroundColor: statusColor(t) }" /> {{ formatStatus(t) }}
      </div>
      <div v-if="!getAllowedTargets(status).length" class="status-option disabled">无可用转换</div>
    </div>
  </el-popover>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { formatStatus, statusColor, getAllowedTargets, type EventStatus } from '@/constants/eventStatus'
const props = defineProps<{ status: EventStatus; readonly?: boolean }>()
const emit = defineEmits<{ change: [t: EventStatus] }>()
const visible = ref(false)
const pick = (t: EventStatus) => { visible.value = false; emit('change', t) }
</script>
<style scoped>
.status-badge { display: inline-block; padding: 2px 10px; border-radius: 12px; color: #fff; font-size: 12px; }
.status-badge.clickable { cursor: pointer; }
.status-options { display: flex; flex-direction: column; gap: 4px; }
.status-option { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 13px; }
.status-option:hover { background-color: #f5f7fa; }
.status-option.disabled { color: #c0c4cc; cursor: default; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
</style>
