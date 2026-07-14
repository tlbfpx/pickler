<template>
  <div class="filter-bar">
    <el-input
      v-model="local.keyword"
      placeholder="搜索 (标题 / 描述，多个用空格分隔)"
      clearable
      style="width: 280px"
      @keyup.enter="emitFilter"
      @clear="emitFilter"
    />
    <el-select
      v-model="local.type"
      placeholder="按类型筛选"
      clearable
      style="width: 150px"
      @change="emitFilter"
    >
      <el-option
        :label="getTerms('STAR').type"
        value="STAR"
      />
      <el-option
        :label="getTerms('PARTY').type"
        value="PARTY"
      />
    </el-select>
    <el-select
      v-model="local.status"
      placeholder="按状态筛选"
      clearable
      style="width: 150px"
      @change="emitFilter"
    >
      <el-option
        v-for="o in statusOptions"
        :key="o.value"
        :label="o.label"
        :value="o.value"
      />
    </el-select>
    <!-- Loop-v18 — sort selector (multi-keyword backend support). -->
    <el-select
      v-model="local.sort"
      placeholder="排序"
      style="width: 160px"
      @change="emitFilter"
    >
      <el-option
        label="开赛时间 ↓"
        value="event_time_desc"
      />
      <el-option
        label="开赛时间 ↑"
        value="event_time_asc"
      />
      <el-option
        label="创建时间 ↓"
        value="created_at_desc"
      />
      <el-option
        label="报名数 ↓"
        value="current_participants_desc"
      />
    </el-select>
    <el-button
      type="primary"
      @click="emitFilter"
    >
      查询
    </el-button>
    <el-button @click="emitReset">
      重置
    </el-button>
    <div class="quick-chips">
      <el-tag
        v-for="c in chips"
        :key="c.value"
        :type="local.status === c.value ? 'primary' : 'info'"
        effect="plain"
        style="cursor: pointer; margin-left: 8px"
        @click="quick(c.value)"
      >
        {{ c.label }}
      </el-tag>
    </div>
  </div>
</template>
<script setup lang="ts">
import { reactive, computed } from 'vue'
import { getTerms } from '@/constants/terms'
import { useDictOptions } from '@/composables/useDict'
import { useDictStore } from '@/stores/dict'

interface FilterPayload {
  keyword: string
  type: string
  status: string
  sort: string
}

const props = defineProps<{
  keyword: string
  type: string
  status: string
  sort: string
}>()

const emit = defineEmits<{
  filter: [FilterPayload]
  reset: []
}>()

const local = reactive({
  keyword: props.keyword,
  type: props.type,
  status: props.status,
  sort: props.sort
})

// Chunk 2b — 状态下拉走字典（响应式，字典变更即时更新）。
const statusOptions = useDictOptions('event_status')

// chips 是 quick filter 子集：保留固定 4 个 key，label 走字典（响应式）。
// 不用 statusOptions.value.slice(0,4) 是因为字典排序变化会意外改变 quick filter 的含义。
const QUICK_KEYS = ['DRAFT', 'OPEN', 'IN_PROGRESS', 'COMPLETED'] as const
const store = useDictStore()
const chips = computed(() =>
  QUICK_KEYS.map(k => ({ label: store.label('event_status', k), value: k }))
)

const emitFilter = () =>
  emit('filter', {
    keyword: local.keyword,
    type: local.type,
    status: local.status,
    sort: local.sort
  })

const emitReset = () => {
  local.keyword = ''
  local.type = ''
  local.status = ''
  local.sort = 'event_time_desc'
  emit('reset')
}

const quick = (v: string) => {
  local.status = local.status === v ? '' : v
  emitFilter()
}
</script>

<style scoped>
.filter-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.quick-chips { display: inline-flex; gap: 4px; margin-left: 8px; }
</style>
