<template>
  <el-drawer
    v-model="visible"
    :title="`用户详情 - ${user?.nickname || 'ID: ' + userId}`"
    size="680px"
    destroy-on-close
  >
    <div
      v-loading="loading"
      class="detail-content"
    >
      <!-- 基本信息 -->
      <div class="section">
        <div class="section-title">
          基本信息
        </div>
        <div class="info-grid">
          <div class="info-row">
            <el-avatar
              :src="user?.avatarUrl || undefined"
              :size="56"
            />
            <div class="info-main">
              <div class="info-name">
                {{ user?.nickname || '-' }}
              </div>
              <div class="info-sub">
                ID: {{ user?.id }} · {{ user?.city || '未设置城市' }}
              </div>
            </div>
          </div>
          <div class="info-items">
            <div class="info-item">
              <span class="label">手机号</span>
              <span class="value">{{ maskPhone(user?.phone) }}</span>
            </div>
            <div class="info-item">
              <span class="label">状态</span>
              <el-tag
                :type="user?.status === 'BANNED' ? 'danger' : 'success'"
                size="small"
              >
                {{ user?.status === 'BANNED' ? '禁赛' : '正常' }}
              </el-tag>
            </div>
            <div class="info-item">
              <span class="label">注册时间</span>
              <span class="value">{{ formatDate(user?.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 积分概览 -->
      <div class="section">
        <div class="section-title">
          积分概览
        </div>
        <div class="points-overview">
          <div class="points-card star">
            <div class="points-label">
              明星积分
            </div>
            <div class="points-value">
              {{ user?.starPoints ?? 0 }}
            </div>
            <div class="points-tier">
              <span
                class="tier-badge"
                :style="{ backgroundColor: getTierColor(user?.starTier) }"
              >
                {{ formatTier(user?.starTier) }}
              </span>
            </div>
          </div>
          <div class="points-card party">
            <div class="points-label">
              派对积分
            </div>
            <div class="points-value">
              {{ user?.partyPoints ?? 0 }}
            </div>
            <div class="points-tier">
              <span
                class="tier-badge"
                :style="{ backgroundColor: getTierColor(user?.partyTier) }"
              >
                {{ formatTier(user?.partyTier) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 积分明细 Tabs -->
      <div class="section">
        <el-tabs v-model="activeTab">
          <el-tab-pane
            label="明星积分明细"
            name="star-points"
          >
            <el-table
              v-loading="pointsLoading"
              :data="starPoints"
              size="small"
              style="width: 100%"
            >
              <el-table-column
                prop="id"
                label="ID"
                width="60"
              />
              <el-table-column
                label="赛事"
                min-width="140"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.eventTitle || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                label="积分"
                width="80"
              >
                <template #default="{ row }">
                  <span :class="row.points > 0 ? 'text-green' : 'text-red'">
                    {{ row.points > 0 ? '+' : '' }}{{ row.points }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                prop="reason"
                label="原因"
                min-width="120"
                show-overflow-tooltip
              />
              <el-table-column
                label="时间"
                width="150"
              >
                <template #default="{ row }">
                  {{ formatDate(row.createdAt) }}
                </template>
              </el-table-column>
            </el-table>
            <div
              v-if="starPointsTotal > starPoints.length"
              class="load-more"
            >
              <el-button
                link
                type="primary"
                @click="loadMorePoints('STAR')"
              >
                加载更多 ({{ starPoints.length }}/{{ starPointsTotal }})
              </el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane
            label="派对积分明细"
            name="party-points"
          >
            <el-table
              v-loading="pointsLoading"
              :data="partyPoints"
              size="small"
              style="width: 100%"
            >
              <el-table-column
                prop="id"
                label="ID"
                width="60"
              />
              <el-table-column
                label="赛事"
                min-width="140"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.eventTitle || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                label="积分"
                width="80"
              >
                <template #default="{ row }">
                  <span :class="row.points > 0 ? 'text-green' : 'text-red'">
                    {{ row.points > 0 ? '+' : '' }}{{ row.points }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                prop="reason"
                label="原因"
                min-width="120"
                show-overflow-tooltip
              />
              <el-table-column
                label="时间"
                width="150"
              >
                <template #default="{ row }">
                  {{ formatDate(row.createdAt) }}
                </template>
              </el-table-column>
            </el-table>
            <div
              v-if="partyPointsTotal > partyPoints.length"
              class="load-more"
            >
              <el-button
                link
                type="primary"
                @click="loadMorePoints('PARTY')"
              >
                加载更多 ({{ partyPoints.length }}/{{ partyPointsTotal }})
              </el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane
            label="赛事记录"
            name="star-events"
          >
            <el-table
              v-loading="eventsLoading"
              :data="starEvents"
              size="small"
              style="width: 100%"
            >
              <el-table-column
                prop="id"
                label="ID"
                width="60"
              />
              <el-table-column
                prop="title"
                label="赛事名称"
                min-width="160"
                show-overflow-tooltip
              />
              <el-table-column
                label="时间"
                width="150"
              >
                <template #default="{ row }">
                  {{ formatDate(row.eventTime) }}
                </template>
              </el-table-column>
              <el-table-column
                prop="location"
                label="地点"
                width="120"
                show-overflow-tooltip
              />
              <el-table-column
                label="报名状态"
                width="90"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="regTagType(row.registrationStatus)"
                  >
                    {{ formatRegStatus(row.registrationStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div
              v-if="starEventsTotal > starEvents.length"
              class="load-more"
            >
              <el-button
                link
                type="primary"
                @click="loadMoreEvents('STAR')"
              >
                加载更多 ({{ starEvents.length }}/{{ starEventsTotal }})
              </el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane
            label="活动记录"
            name="party-events"
          >
            <el-table
              v-loading="eventsLoading"
              :data="partyEvents"
              size="small"
              style="width: 100%"
            >
              <el-table-column
                prop="id"
                label="ID"
                width="60"
              />
              <el-table-column
                prop="title"
                label="活动名称"
                min-width="160"
                show-overflow-tooltip
              />
              <el-table-column
                label="时间"
                width="150"
              >
                <template #default="{ row }">
                  {{ formatDate(row.eventTime) }}
                </template>
              </el-table-column>
              <el-table-column
                prop="location"
                label="地点"
                width="120"
                show-overflow-tooltip
              />
              <el-table-column
                label="报名状态"
                width="90"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="regTagType(row.registrationStatus)"
                  >
                    {{ formatRegStatus(row.registrationStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div
              v-if="partyEventsTotal > partyEvents.length"
              class="load-more"
            >
              <el-button
                link
                type="primary"
                @click="loadMoreEvents('PARTY')"
              >
                加载更多 ({{ partyEvents.length }}/{{ partyEventsTotal }})
              </el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getUserDetail, getUserPoints, getUserEvents } from '@/api/users'
import type { User, PointRecord, EventRecord } from '@/api/users'
import { formatDate, formatTier, getTierColor } from '@/utils'

const props = defineProps<{
  modelValue: boolean
  userId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
}>()

const visible = ref(false)
const loading = ref(false)
const pointsLoading = ref(false)
const eventsLoading = ref(false)
const activeTab = ref('star-points')
const user = ref<User | null>(null)

const starPoints = ref<PointRecord[]>([])
const partyPoints = ref<PointRecord[]>([])
const starPointsTotal = ref(0)
const partyPointsTotal = ref(0)

const starEvents = ref<EventRecord[]>([])
const partyEvents = ref<EventRecord[]>([])
const starEventsTotal = ref(0)
const partyEventsTotal = ref(0)

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:modelValue', val) })

watch(() => props.userId, (id) => {
  if (id && visible.value) loadAll(id)
})

watch(visible, (val) => {
  if (val && props.userId) loadAll(props.userId)
})

async function loadAll(id: number) {
  loading.value = true
  try {
    const res = await getUserDetail(id)
    if (res.code === 0) user.value = res.data
  } catch { /* ignore */ }
  loading.value = false
  loadPoints('STAR', 1)
  loadPoints('PARTY', 1)
  loadEvents('STAR', 1)
  loadEvents('PARTY', 1)
}

async function loadPoints(type: string, page: number) {
  if (!props.userId) return
  pointsLoading.value = true
  try {
    const res = await getUserPoints(props.userId, { type, page, size: 20 })
    if (res.code === 0) {
      const list = res.data.list || []
      if (page === 1) {
        if (type === 'STAR') { starPoints.value = list; starPointsTotal.value = res.data.total }
        else { partyPoints.value = list; partyPointsTotal.value = res.data.total }
      } else {
        if (type === 'STAR') { starPoints.value.push(...list) }
        else { partyPoints.value.push(...list) }
      }
    }
  } catch {  }
  pointsLoading.value = false
}

async function loadEvents(type: string, page: number) {
  if (!props.userId) return
  eventsLoading.value = true
  try {
    const res = await getUserEvents(props.userId, { type, page, size: 20 })
    if (res.code === 0) {
      const list = res.data.list || []
      if (page === 1) {
        if (type === 'STAR') { starEvents.value = list; starEventsTotal.value = res.data.total }
        else { partyEvents.value = list; partyEventsTotal.value = res.data.total }
      } else {
        if (type === 'STAR') { starEvents.value.push(...list) }
        else { partyEvents.value.push(...list) }
      }
    }
  } catch {  }
  eventsLoading.value = false
}

function loadMorePoints(type: string) {
  const current = type === 'STAR' ? starPoints.value.length : partyPoints.value.length
  loadPoints(type, Math.floor(current / 20) + 1)
}

function loadMoreEvents(type: string) {
  const current = type === 'STAR' ? starEvents.value.length : partyEvents.value.length
  loadEvents(type, Math.floor(current / 20) + 1)
}

function regTagType(status: string) {
  const map: Record<string, string> = { REGISTERED: 'primary', CHECKED_IN: 'success', WITHDRAWN: 'info' }
  return map[status] || 'info'
}

function formatRegStatus(status: string) {
  const map: Record<string, string> = { REGISTERED: '已报名', CHECKED_IN: '已签到', WITHDRAWN: '已退赛' }
  return map[status] || status
}

function maskPhone(phone: string | null | undefined) {
  if (!phone || phone.length < 7) return phone || '-'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}
</script>

<style scoped>
.detail-content {
  padding: 0 4px;
}

.section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.info-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.info-sub {
  font-size: 12px;
  color: #9ca3af;
}

.info-items {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item .label {
  font-size: 12px;
  color: #9ca3af;
}

.info-item .value {
  font-size: 13px;
  color: #374151;
}

.points-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.points-card {
  border-radius: 10px;
  padding: 16px;
  color: #fff;
}

.points-card.star {
  background: linear-gradient(135deg, #f59e0b, #f97316);
}

.points-card.party {
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
}

.points-label {
  font-size: 12px;
  opacity: 0.9;
}

.points-value {
  font-size: 28px;
  font-weight: 700;
  margin: 4px 0;
}

.tier-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(255,255,255,0.3);
}

.load-more {
  text-align: center;
  padding: 8px;
}

.text-green { color: #16a34a; font-weight: 600; }
.text-red { color: #dc2626; font-weight: 600; }
</style>
