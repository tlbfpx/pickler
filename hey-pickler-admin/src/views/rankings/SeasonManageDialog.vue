<template>
  <el-dialog
    :model-value="modelValue"
    :title="`赛季管理（${TERMS[type].points}）`"
    width="760px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="season-dialog">
      <div class="dialog-toolbar">
        <el-button
          type="primary"
          size="small"
          @click="openCreate"
        >
          新建赛季
        </el-button>
        <span class="hint">新建默认为「已归档」，需「设为当前」才生效；切换当前会自动归档同类型旧当前赛季并给新赛季播种排名。</span>
      </div>

      <el-table
        v-loading="loading"
        :data="seasons"
        size="small"
        style="margin-top: 12px"
      >
        <el-table-column
          prop="code"
          label="代号"
          width="120"
        />
        <el-table-column
          prop="name"
          label="名称"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="时间"
          width="200"
        >
          <template #default="{ row }">
            {{ row.startDate || '?' }} ~ {{ row.endDate || '?' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'CURRENT' ? 'success' : 'info'"
            >
              {{ row.status === 'CURRENT' ? '当前' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'CURRENT'"
              link
              type="primary"
              size="small"
              @click="handleActivate(row)"
            >
              设为当前
            </el-button>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建表单（内嵌） -->
    <el-dialog
      v-model="createVisible"
      title="新建赛季"
      width="460px"
      append-to-body
    >
      <el-form
        :model="form"
        label-width="80px"
      >
        <el-form-item label="类型">
          <el-select
            v-model="form.type"
            disabled
            style="width: 100%"
          >
            <el-option
              :label="TERMS.STAR.points"
              value="STAR"
            />
            <el-option
              :label="TERMS.PARTY.points"
              value="PARTY"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="代号">
          <el-input
            v-model="form.code"
            placeholder="如 2026-Q4"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="form.name"
            placeholder="如 2026 第四季度"
          />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="creating"
          @click="handleCreate"
        >
          创建
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSeasons, createSeason, activateSeason } from '@/api/seasons'
import { TERMS } from '@/constants/terms'
import type { Season } from '@/types'

const props = defineProps<{
  modelValue: boolean
  type: 'STAR' | 'PARTY'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const loading = ref(false)
const seasons = ref<Season[]>([])
const createVisible = ref(false)
const creating = ref(false)
const form = reactive({
  type: 'STAR' as 'STAR' | 'PARTY',
  code: '',
  name: '',
  startDate: '',
  endDate: ''
})

const load = async () => {
  loading.value = true
  try {
    const res = await listSeasons(props.type)
    if (res.code === 0) {
      seasons.value = res.data || []
    }
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

const openCreate = () => {
  form.type = props.type
  form.code = ''
  form.name = ''
  form.startDate = ''
  form.endDate = ''
  createVisible.value = true
}

const handleCreate = async () => {
  if (!form.code.trim()) {
    ElMessage.error('请填写赛季代号')
    return
  }
  if (!form.startDate || !form.endDate) {
    ElMessage.error('请选择起止日期')
    return
  }
  creating.value = true
  try {
    const res = await createSeason({
      type: form.type,
      code: form.code.trim(),
      name: form.name.trim(),
      startDate: form.startDate,
      endDate: form.endDate
    })
    if (res.code === 0) {
      ElMessage.success('赛季已创建（默认已归档，需「设为当前」生效）')
      createVisible.value = false
      await load()
      emit('changed')
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch { /* ignore */ } finally {
    creating.value = false
  }
}

const handleActivate = async (row: Season) => {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.name || row.code}」设为当前赛季？同类型旧当前赛季将自动归档，并按当前积分给新赛季播种排名。`,
      '切换当前赛季',
      { type: 'warning', confirmButtonText: '设为当前', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const res = await activateSeason(row.id)
    if (res.code === 0) {
      ElMessage.success('已设为当前赛季')
      await load()
      emit('changed')
    } else {
      ElMessage.error(res.message || '切换失败')
    }
  } catch { /* ignore */ }
}

watch(() => props.modelValue, (val) => {
  if (val) load()
})

watch(() => props.type, () => {
  if (props.modelValue) load()
})
</script>

<style scoped>
.dialog-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hint {
  font-size: 12px;
  color: #9ca3af;
}

.muted {
  color: #d1d5db;
}
</style>
