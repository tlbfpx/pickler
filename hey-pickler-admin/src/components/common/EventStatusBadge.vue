<template>
  <el-popover
    placement="bottom"
    :width="160"
    trigger="click"
    :visible="visible"
    @update:visible="visible = $event"
  >
    <template #reference>
      <span
        class="status-badge clickable"
        :style="{ backgroundColor: statusColorValue }"
        @click="!readonly && (visible = true)"
      >
        {{ statusLabel }} <span v-if="!readonly">▾</span>
      </span>
    </template>
    <div class="status-options">
      <div
        v-for="t in getAllowedTargets(status)"
        :key="t"
        class="status-option"
        @click="pick(t)"
      >
        <span
          class="status-dot"
          :style="{ backgroundColor: colorFor(t) }"
        /> {{ labelFor(t) }}
      </div>
      <div
        v-if="!getAllowedTargets(status).length"
        class="status-option disabled"
      >
        无可用转换
      </div>
    </div>
  </el-popover>
</template>
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDictStore } from '@/stores/dict'
import { getAllowedTargets, type EventStatus } from '@/constants/eventStatus'

const props = defineProps<{ status: EventStatus; readonly?: boolean }>()
const emit = defineEmits<{ change: [t: EventStatus] }>()
const visible = ref(false)
const pick = (t: EventStatus) => { visible.value = false; emit('change', t) }

// Chunk 2b — 响应式读取字典（dict store 变更即时更新）。tooltip 仍走 statusTooltip（字典无 tooltip），状态机仍走 getAllowedTargets（业务逻辑零改动）。
const store = useDictStore()
const statusLabel = computed(() => store.label('event_status', props.status))
const statusColorValue = computed(() => store.color('event_status', props.status))
const labelFor = (key: string) => store.label('event_status', key)
const colorFor = (key: string) => store.color('event_status', key)
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
