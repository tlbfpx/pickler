<template>
  <div v-loading="loading" class="matches-panel">
    <div class="panel-header">
      <h3>对阵 / 比赛</h3>
      <div>
        <el-button v-if="!hasMatches" type="primary" :loading="genLoading"
          :disabled="!event.groupingLocked" @click="handleGenerate">生成对阵</el-button>
        <el-button v-if="hasMatches" type="success" :loading="standingLoading" @click="fetchStandings">刷新排名</el-button>
      </div>
    </div>
    <el-alert v-if="!event.groupingLocked" type="info" :closable="false" title="需先锁定分组后才能生成对阵" />
    <el-empty v-else-if="!hasMatches && !genLoading" description="尚未生成对阵" />

    <div v-for="(group, gi) in matches" :key="gi" class="group-block">
      <div class="group-title">第 {{ gi + 1 }} 组</div>
      <el-table :data="group" size="small">
        <el-table-column label="A" min-width="120"><template #default="{ row }">{{ row.slotADisplayName || '-' }}</template></el-table-column>
        <el-table-column label="比分" width="120"><template #default="{ row }">{{ scoreText(row) }}</template></el-table-column>
        <el-table-column label="B" min-width="120"><template #default="{ row }">{{ row.slotBDisplayName || '-' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }">
          <el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'warning' : 'info'">{{ statusLabel(row.status) }}</el-tag>
        </template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }">
          <el-button link type="primary" size="small" @click="openScore(row)">代录</el-button>
          <el-button v-if="row.status !== 'SCHEDULED'" link type="danger" size="small" @click="handleReset(row)">重置</el-button>
        </template></el-table-column>
      </el-table>
      <div v-if="standings[gi]?.length" class="standings">
        <span class="standings-title">本组排名：</span>
        <span v-for="s in standings[gi]" :key="s.participantKey ?? ''" class="standing-item">
          #{{ s.rank ?? '-' }} {{ s.displayName ?? '-' }} ({{ s.wins ?? 0 }}胜{{ s.losses ?? 0 }}负)
        </span>
      </div>
    </div>

    <el-dialog v-model="scoreOpen" title="代录比分（三局两胜）" width="520px">
      <div v-for="(g, idx) in scoreForm" :key="idx" class="score-row">
        <span>第{{ idx + 1 }}局</span>
        <el-input-number v-model="g.a" :min="0" :max="30" />
        <el-input-number v-model="g.b" :min="0" :max="30" />
      </div>
      <div class="hint">规则：21 分起，净胜 2 分，单局 ≤30</div>
      <template #footer>
        <el-button @click="scoreOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitScore">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateMatches, getEventMatches, getEventStandings, submitMatchScore, resetMatch } from '@/api/matches'
import type { Event, MatchItem, StandingRow, GameScore } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()

const loading = ref(false); const genLoading = ref(false)
const matches = ref<MatchItem[][]>([])
const standings = ref<StandingRow[][]>([])
const standingLoading = ref(false)
const hasMatches = computed(() => matches.value.some(g => g.length))

const fetchMatches = async () => {
  loading.value = true
  try { const r = await getEventMatches(props.event.id); if (r.code === 0) matches.value = r.data || [] } finally { loading.value = false }
}
const fetchStandings = async () => {
  standingLoading.value = true
  try { const r = await getEventStandings(props.event.id); if (r.code === 0) standings.value = r.data || [] } finally { standingLoading.value = false }
}
const handleGenerate = async () => {
  genLoading.value = true
  try {
    const r = await generateMatches(props.event.id)
    if (r.code === 0) { ElMessage.success('对阵已生成'); emit('changed'); await fetchMatches(); await fetchStandings() }
    else ElMessage.error(r.message || '生成失败')
  } finally { genLoading.value = false }
}

const scoreOpen = ref(false); const submitting = ref(false)
const scoreForm = ref<GameScore[]>([{ game: 1, a: 0, b: 0 }, { game: 2, a: 0, b: 0 }, { game: 3, a: 0, b: 0 }])
const currentMatch = ref<MatchItem | null>(null)
const openScore = (m: MatchItem) => {
  currentMatch.value = m
  scoreForm.value = (m.games?.length ? m.games : [{ game: 1, a: 0, b: 0 }, { game: 2, a: 0, b: 0 }, { game: 3, a: 0, b: 0 }])
    .map(g => ({ game: g.game, a: g.a, b: g.b }))
  scoreOpen.value = true
}
const handleSubmitScore = async () => {
  if (!currentMatch.value) return
  submitting.value = true
  try {
    const r = await submitMatchScore(currentMatch.value.id, scoreForm.value)
    if (r.code === 0) { ElMessage.success('已录入'); scoreOpen.value = false; emit('changed'); await fetchMatches(); await fetchStandings() }
    else ElMessage.error(r.message || '录入失败')
  } finally { submitting.value = false }
}
const handleReset = async (m: MatchItem) => {
  try { await ElMessageBox.confirm('确定重置该场比分？', '重置', { type: 'warning' }) } catch { return }
  const r = await resetMatch(m.id)
  if (r.code === 0) { ElMessage.success('已重置'); emit('changed'); await fetchMatches(); await fetchStandings() } else ElMessage.error(r.message)
}

const scoreText = (m: MatchItem) => m.games?.length ? m.games.map(g => `${g.a}:${g.b}`).join(' ') : (m.gamesWonA != null ? `${m.gamesWonA}:${m.gamesWonB}` : '-')
const statusLabel = (s: string) => ({ SCHEDULED: '待打', IN_PROGRESS: '进行中', COMPLETED: '已完成' } as any)[s] || s

onMounted(fetchMatches)
</script>

<style scoped>
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.group-block { margin-bottom: 20px; }
.group-title { font-weight: 600; margin-bottom: 8px; color: #374151; }
.standings { margin-top: 8px; font-size: 13px; color: #6b7280; }
.standings-title { font-weight: 600; color: #374151; }
.standing-item { margin-right: 12px; }
.score-row { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.hint { color: #9ca3af; font-size: 12px; }
</style>
