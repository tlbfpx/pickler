<template>
  <div class="filter-bar">
    <el-input v-model="local.keyword" placeholder="搜索标题" clearable style="width: 220px" @keyup.enter="emitFilter" @clear="emitFilter" />
    <el-select v-model="local.type" placeholder="按类型筛选" clearable style="width: 150px" @change="emitFilter">
      <el-option :label="TERMS.STAR.type" value="STAR" />
      <el-option :label="TERMS.PARTY.type" value="PARTY" />
    </el-select>
    <el-select v-model="local.status" placeholder="按状态筛选" clearable style="width: 150px" @change="emitFilter">
      <el-option label="草稿" value="DRAFT" />
      <el-option label="报名中" value="OPEN" />
      <el-option label="名额已满" value="FULL" />
      <el-option label="进行中" value="IN_PROGRESS" />
      <el-option label="已结束" value="COMPLETED" />
      <el-option label="已取消" value="CANCELLED" />
    </el-select>
    <el-button type="primary" @click="emitFilter">查询</el-button>
    <el-button @click="emitReset">重置</el-button>
    <div class="quick-chips">
      <el-tag v-for="c in chips" :key="c.value" :type="local.status === c.value ? 'primary' : 'info'" effect="plain"
        style="cursor: pointer; margin-left: 8px" @click="quick(c.value)">{{ c.label }}</el-tag>
    </div>
  </div>
</template>
<script setup lang="ts">
import { reactive } from 'vue'
import { TERMS } from '@/constants/terms'
const props = defineProps<{ keyword: string; type: string; status: string }>()
const emit = defineEmits<{ filter: [{ keyword: string; type: string; status: string }]; reset: [] }>()
const local = reactive({ keyword: props.keyword, type: props.type, status: props.status })
const chips = [
  { label: '草稿', value: 'DRAFT' },
  { label: '报名中', value: 'OPEN' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '已结束', value: 'COMPLETED' }
]
const emitFilter = () => emit('filter', { keyword: local.keyword, type: local.type, status: local.status })
const emitReset = () => { local.keyword = ''; local.type = ''; local.status = ''; emit('reset') }
const quick = (v: string) => { local.status = local.status === v ? '' : v; emitFilter() }
</script>
<style scoped>
.filter-bar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.quick-chips { display: inline-flex; gap: 4px; margin-left: 8px; }
</style>
