<template>
  <div class="grouping-panel">
    <div class="panel-header">
      <h3>分组管理</h3>
      <div class="status-info">
        <el-tag
          v-if="locked"
          type="danger"
          effect="dark"
        >
          已锁定
        </el-tag>
        <el-tag
          v-else-if="hasGroups"
          type="warning"
          effect="plain"
        >
          未锁定（可调整）
        </el-tag>
        <el-tag
          v-else
          type="info"
          effect="plain"
        >
          未分组
        </el-tag>
      </div>
    </div>

    <div class="controls">
      <el-select
        v-model="strategy"
        :disabled="locked || running"
        placeholder="选择分组策略"
        style="width: 180px"
      >
        <el-option
          label="随机"
          value="RANDOM"
        />
        <el-option
          label="蛇形按排名"
          value="SERPENTINE"
        />
        <el-option
          label="手动"
          value="MANUAL"
        />
      </el-select>
      <el-input-number
        v-model="groupCount"
        :min="1"
        :max="32"
        :disabled="locked || running"
        style="width: 110px"
      />
      <el-button
        type="primary"
        :loading="running"
        :disabled="locked"
        @click="handleGroup"
      >
        {{ hasGroups ? '重新分组' : '开始分组' }}
      </el-button>
      <el-button
        v-if="hasGroups && !locked"
        type="danger"
        plain
        :loading="running"
        @click="handleUnlock"
      >
        解锁并清空
      </el-button>
      <el-button
        v-if="hasGroups && !locked"
        type="success"
        :loading="running"
        @click="handleLock"
      >
        锁定
      </el-button>
    </div>

    <div
      v-if="groups.length === 0"
      class="empty"
    >
      尚未分组。配置策略与组数后点击「开始分组」。
    </div>

    <div
      v-else
      class="group-grid"
    >
      <div
        v-for="g in groups"
        :key="g.id"
        class="group-card"
      >
        <div class="group-header">
          <span class="group-name">{{ g.name }} 组</span>
          <span class="group-count">{{ g.assignments.length }} 人</span>
        </div>
        <ul class="assignment-list">
          <li
            v-for="a in g.assignments"
            :key="a.id"
            class="assignment-row"
          >
            <span class="seed">#{{ a.seed }}</span>
            <span class="display-name">{{ a.displayName || (a.userId ? `用户 ${a.userId}` : `队伍 ${a.teamId}`) }}</span>
            <el-button
              v-if="!locked && hasGroups"
              link
              size="small"
              type="primary"
              @click="openReassign(a)"
            >
              换组
            </el-button>
          </li>
          <li
            v-if="g.assignments.length === 0"
            class="assignment-row empty-slot"
          >
            <em>空组</em>
          </li>
        </ul>
      </div>
    </div>

    <el-dialog
      v-model="reassignOpen"
      title="调整分组"
      width="420px"
    >
      <p class="reassign-hint">
        将 <b>{{ reassignAssignment?.displayName || '该成员' }}</b> 移动到：
      </p>
      <el-select
        v-model="targetGroupId"
        placeholder="选择目标组"
        style="width: 100%"
      >
        <el-option
          v-for="g in groups"
          :key="g.id"
          :label="`${g.name} 组（${g.assignments.length} 人）`"
          :value="g.id"
          :disabled="reassignAssignment && g.id === reassignAssignment.groupId"
        />
      </el-select>
      <template #footer>
        <el-button @click="reassignOpen = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="reassigning"
          @click="handleReassign"
        >
          确认调整
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  groupEvent,
  getGroups,
  reassignParticipant,
  lockGroups,
  unlockGroups,
  type Group,
  type Assignment,
  type GroupingStrategy
} from '@/api/grouping'
import type { Event } from '@/types'

const props = defineProps<{
  event: Event
}>()

const emit = defineEmits<{ changed: [] }>()

const groups = ref<Group[]>([])
const strategy = ref<GroupingStrategy>('SERPENTINE')
const groupCount = ref(2)
const running = ref(false)
const locked = computed(() => props.event.groupingLocked === true)
const hasGroups = computed(() => groups.value.length > 0)

const reassignOpen = ref(false)
const reassignAssignment = ref<Assignment | null>(null)
const targetGroupId = ref<number | null>(null)
const reassigning = ref(false)

const fetchGroups = async () => {
  try {
    const res = await getGroups(props.event.id)
    if (res.code === 0) {
      groups.value = res.data || []
    }
  } catch {
    /* network error -> keep current view */
  }
}

onMounted(fetchGroups)

const handleGroup = async () => {
  if (locked.value) return
  if (hasGroups.value) {
    try {
      await ElMessageBox.confirm(
        '重新分组将清空当前分组结果，确定继续？',
        '重新分组',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  running.value = true
  try {
    const res = await groupEvent(props.event.id, strategy.value, groupCount.value)
    if (res.code === 0) {
      groups.value = res.data || []
      ElMessage.success('分组完成')
      emit('changed')
    } else {
      ElMessage.error(res.message || '分组失败')
    }
  } finally {
    running.value = false
  }
}

const handleLock = async () => {
  running.value = true
  try {
    const res = await lockGroups(props.event.id)
    if (res.code === 0) {
      ElMessage.success('已锁定')
      emit('changed')
    } else {
      ElMessage.error(res.message || '锁定失败')
    }
  } finally {
    running.value = false
  }
}

const handleUnlock = async () => {
  try {
    await ElMessageBox.confirm(
      '解锁将清空当前所有分组，并重新开放报名。继续？',
      '解锁并清空',
      { type: 'warning' }
    )
  } catch {
    return
  }
  running.value = true
  try {
    const res = await unlockGroups(props.event.id)
    if (res.code === 0) {
      groups.value = []
      ElMessage.success('已解锁并清空分组')
      emit('changed')
    } else {
      ElMessage.error(res.message || '解锁失败')
    }
  } finally {
    running.value = false
  }
}

const openReassign = (a: Assignment) => {
  const group = groups.value.find(g => g.assignments.some(x => x.id === a.id))
  reassignAssignment.value = { ...a, groupId: group?.id ?? null } as Assignment & { groupId?: number | null }
  targetGroupId.value = null
  reassignOpen.value = true
}

const handleReassign = async () => {
  if (!reassignAssignment.value || !targetGroupId.value) return
  reassigning.value = true
  try {
    const res = await reassignParticipant(
      props.event.id,
      reassignAssignment.value.id,
      targetGroupId.value
    )
    if (res.code === 0) {
      ElMessage.success('已调整')
      reassignOpen.value = false
      emit('changed')
      await fetchGroups()
    } else {
      ElMessage.error(res.message || '调整失败')
    }
  } finally {
    reassigning.value = false
  }
}
</script>

<style scoped>
.grouping-panel {
  padding: 16px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.controls {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.empty {
  padding: 24px;
  text-align: center;
  color: #9ca3af;
  border: 1px dashed #d1d5db;
  border-radius: 4px;
}
.group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.group-card {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #fafafa;
  overflow: hidden;
}
.group-header {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f3f4f6;
  font-weight: 500;
  border-bottom: 1px solid #e5e7eb;
}
.group-name {
  color: #1f2937;
}
.group-count {
  color: #6b7280;
  font-size: 12px;
}
.assignment-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.assignment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid #f3f4f6;
}
.assignment-row:last-child {
  border-bottom: none;
}
.seed {
  color: #9ca3af;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  min-width: 24px;
}
.display-name {
  flex: 1;
  font-size: 13px;
}
.empty-slot {
  color: #9ca3af;
  font-style: italic;
  justify-content: center;
}
.reassign-hint {
  margin: 0 0 12px;
  color: #4b5563;
}
</style>