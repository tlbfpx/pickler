<template>
  <el-dialog
    :model-value="modelValue"
    title="录入积分"
    width="750px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <!-- Step 1: 选择赛事/活动 -->
    <div class="step-section">
      <div class="step-title">关联赛事/活动</div>
      <el-form label-position="top">
        <el-form-item label="积分类型">
          <el-radio-group v-model="entryMode" @change="handleModeChange">
            <el-radio value="STAR">关联赛事</el-radio>
            <el-radio value="PARTY">关联活动</el-radio>
            <el-radio value="MANUAL">手动录入</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="entryMode !== 'MANUAL'" label="选择赛事/活动">
          <el-select
            v-model="selectedEventId"
            placeholder="请选择"
            filterable
            style="width: 100%"
            :loading="eventsLoading"
            @change="handleEventChange"
          >
            <el-option
              v-for="evt in events"
              :key="evt.id"
              :label="`${evt.title} (${formatDate(evt.eventTime)})`"
              :value="evt.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="entryMode === 'MANUAL'" label="积分类型">
          <el-select v-model="manualType" style="width: 200px">
            <el-option label="明星积分" value="STAR" />
            <el-option label="派对积分" value="PARTY" />
          </el-select>
        </el-form-item>

        <!-- 选中赛事信息 -->
        <div v-if="selectedEvent" class="event-info-card">
          <div class="event-info-row">
            <span class="event-info-label">{{ entryMode === 'STAR' ? '赛事' : '活动' }}名称</span>
            <span>{{ selectedEvent.title }}</span>
          </div>
          <div class="event-info-row">
            <span class="event-info-label">时间</span>
            <span>{{ formatDate(selectedEvent.eventTime) }}</span>
          </div>
          <div class="event-info-row">
            <span class="event-info-label">地点</span>
            <span>{{ selectedEvent.location }}</span>
          </div>
          <div class="event-info-row">
            <span class="event-info-label">报名人数</span>
            <span>{{ participants.length }} 人</span>
          </div>
        </div>
      </el-form>
    </div>

    <!-- Step 2: 录入积分明细 -->
    <div class="step-section" style="margin-top: 16px">
      <div class="step-title">
        积分明细
        <span v-if="records.length" class="step-sub">（共 {{ records.length }} 人）</span>
      </div>

      <div v-if="participantsLoading" style="text-align: center; padding: 20px; color: #9ca3af;">
        加载参赛者中...
      </div>

      <div v-else-if="entryMode !== 'MANUAL' && selectedEventId && records.length === 0" style="text-align: center; padding: 20px; color: #9ca3af;">
        该赛事暂无报名人员
      </div>

      <div v-else-if="records.length > 0" class="records-list">
        <div
          v-for="(record, index) in records"
          :key="index"
          class="record-item"
        >
          <div class="record-fields">
            <!-- 手动模式：用户搜索选择器 -->
            <template v-if="entryMode === 'MANUAL' && editingIndex === index">
              <el-select
                v-model="selectValue"
                placeholder="搜索用户（昵称/手机号）"
                filterable
                remote
                :remote-method="searchUsers"
                :loading="userSearchLoading"
                style="width: 180px; flex-shrink: 0"
                @change="(uid: any) => handleUserSelect(Number(uid), index)"
              >
                <el-option
                  v-for="u in userSearchResults"
                  :key="u.id"
                  :label="u.nickname || ('用户 ' + u.id)"
                  :value="u.id"
                >
                  <div class="user-option">
                    <el-avatar :src="u.avatarUrl || undefined" :size="24" />
                    <span>{{ u.nickname || ('用户 ' + u.id) }}</span>
                    <span class="user-option-id">ID: {{ u.id }}</span>
                  </div>
                </el-option>
              </el-select>
            </template>

            <!-- 已选用户 或 赛事模式：显示用户信息 -->
            <template v-else-if="record.userId">
              <div class="user-cell" @click="startEdit(index)">
                <el-avatar :src="record.avatarUrl || undefined" :size="32" />
                <div class="user-info">
                  <div class="user-name">{{ record.nickname || ('用户 ' + record.userId) }}</div>
                  <div class="user-id">ID: {{ record.userId }}</div>
                </div>
              </div>
            </template>

            <!-- 手动模式未选用户时显示占位 -->
            <template v-else-if="entryMode === 'MANUAL'">
              <el-button size="small" @click="startEdit(index)">
                <el-icon><User /></el-icon>
                选择用户
              </el-button>
            </template>

            <el-input-number
              v-model="record.points"
              placeholder="积分"
              style="width: 110px"
            />
            <el-input
              v-model="record.reason"
              :placeholder="reasonPlaceholder"
              style="flex: 1"
            />

            <el-button
              v-if="records.length > 1"
              type="danger"
              size="small"
              link
              @click="removeRecord(index)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>

      <!-- 添加按钮 -->
      <el-button
        type="primary"
        plain
        style="width: 100%; margin-top: 12px"
        @click="addRecord"
      >
        <el-icon><Plus /></el-icon>
        添加一条
      </el-button>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        提交录入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { enterPoints } from '@/api/rankings'
import { getEventList, getEventParticipants } from '@/api/events'
import type { EventParticipant } from '@/api/events'
import { getUserList } from '@/api/users'
import { formatDate } from '@/utils'
import type { Event, User } from '@/types'

interface RecordItem {
  userId: number
  nickname: string | null
  avatarUrl: string | null
  points: number
  reason: string
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const loading = ref(false)
const eventsLoading = ref(false)
const participantsLoading = ref(false)
const userSearchLoading = ref(false)
const entryMode = ref<'STAR' | 'PARTY' | 'MANUAL'>('STAR')
const manualType = ref<'STAR' | 'PARTY'>('STAR')
const selectedEventId = ref<number | null>(null)
const events = ref<Event[]>([])
const participants = ref<EventParticipant[]>([])
const userSearchResults = ref<User[]>([])
const records = reactive<RecordItem[]>([])
const editingIndex = ref(-1)
const selectValue = ref<number | null>(null)

const selectedEvent = computed(() => {
  if (!selectedEventId.value) return null
  return events.value.find(e => e.id === selectedEventId.value) || null
})

const reasonPlaceholder = computed(() => {
  if (entryMode.value === 'MANUAL') return '请输入积分变动原因'
  return '例如：冠军、亚军、季军、参与分'
})

watch(() => props.modelValue, (val) => {
  if (val) {
    entryMode.value = 'STAR'
    manualType.value = 'STAR'
    selectedEventId.value = null
    events.value = []
    participants.value = []
    records.length = 0
    fetchEvents()
  }
})

const fetchEvents = async () => {
  if (entryMode.value === 'MANUAL') {
    records.length = 0
    records.push(emptyRecord())
    return
  }
  eventsLoading.value = true
  try {
    const res = await getEventList({ type: entryMode.value, page: 1, size: 100 })
    if (res.code === 0) {
      events.value = res.data.list || []
    }
  } catch { /* ignore */ }
  eventsLoading.value = false
}

const fetchParticipants = async (eventId: number) => {
  participantsLoading.value = true
  try {
    const res = await getEventParticipants(eventId)
    if (res.code === 0) {
      participants.value = res.data || []
      records.length = 0
      for (const p of participants.value) {
        records.push({
          userId: p.userId,
          nickname: p.nickname,
          avatarUrl: p.avatarUrl,
          points: 0,
          reason: ''
        })
      }
    }
  } catch { /* ignore */ }
  participantsLoading.value = false
}

const startEdit = (index: number) => {
  editingIndex.value = index
  selectValue.value = null
  userSearchResults.value = []
  // clear previous user for this slot
  records[index].userId = 0
  records[index].nickname = null
  records[index].avatarUrl = null
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
const searchUsers = (query: string) => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!query) { userSearchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    userSearchLoading.value = true
    try {
      const res = await getUserList({ keyword: query, page: 1, size: 20 })
      if (res.code === 0) {
        userSearchResults.value = res.data.list || []
      }
    } catch { /* ignore */ }
    userSearchLoading.value = false
  }, 300)
}

const handleUserSelect = (userId: number, index: number) => {
  const user = userSearchResults.value.find(u => u.id === userId)
  if (user) {
    records[index].userId = user.id
    records[index].nickname = user.nickname
    records[index].avatarUrl = user.avatarUrl
  }
  editingIndex.value = -1
  selectValue.value = null
}

const handleModeChange = () => {
  selectedEventId.value = null
  participants.value = []
  editingIndex.value = -1
  records.length = 0
  fetchEvents()
}

const handleEventChange = (eventId: number) => {
  if (eventId) {
    fetchParticipants(eventId)
  } else {
    participants.value = []
    records.length = 0
  }
}

const emptyRecord = (): RecordItem => ({ userId: 0, nickname: null, avatarUrl: null, points: 0, reason: '' })

const addRecord = () => {
  records.push(emptyRecord())
}

const removeRecord = (index: number) => {
  records.splice(index, 1)
}

const handleConfirm = async () => {
  if (entryMode.value !== 'MANUAL' && !selectedEventId.value) {
    ElMessage.error('请选择赛事/活动')
    return
  }

  if (records.length === 0) {
    ElMessage.error('请添加至少一条积分记录')
    return
  }

  for (let i = 0; i < records.length; i++) {
    const r = records[i]
    if (!r.userId || r.userId <= 0) {
      ElMessage.error(`第 ${i + 1} 条记录：请选择用户`)
      return
    }
    if (r.points === 0 || r.points == null) {
      ElMessage.error(`第 ${i + 1} 条记录：积分不能为0`)
      return
    }
    if (!r.reason?.trim()) {
      ElMessage.error(`第 ${i + 1} 条记录：请填写积分原因`)
      return
    }
  }

  loading.value = true
  try {
    const data: any = {
      records: validRecords.map(r => ({
        userId: r.userId,
        points: r.points,
        reason: r.reason.trim()
      }))
    }

    if (entryMode.value === 'MANUAL') {
      data.type = manualType.value
    } else {
      data.eventId = selectedEventId.value
    }

    const res = await enterPoints(data)
    if (res.code === 0) {
      ElMessage.success('积分录入成功')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '积分录入失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.step-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.step-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}

.step-sub {
  font-weight: 400;
  color: #9ca3af;
  font-size: 12px;
}

.event-info-card {
  background: #f9fafb;
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.event-info-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
}

.event-info-label {
  color: #9ca3af;
  width: 70px;
  flex-shrink: 0;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}

.record-item {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.record-fields {
  display: flex;
  gap: 10px;
  align-items: center;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 160px;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100px;
}

.user-id {
  font-size: 11px;
  color: #9ca3af;
}

.user-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-option-id {
  color: #9ca3af;
  font-size: 12px;
  margin-left: auto;
}
</style>
