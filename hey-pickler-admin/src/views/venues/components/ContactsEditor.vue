<template>
  <div
    v-loading="loading"
    class="contacts-editor"
  >
    <div class="contacts-toolbar">
      <div class="contacts-hint">
        场馆联系方式：用于小程序「联系场馆」按钮。新增/编辑/删除均为即时保存。
      </div>
      <el-button
        type="primary"
        plain
        @click="handleAdd"
      >
        <el-icon><Plus /></el-icon>
        新增联系方式
      </el-button>
    </div>

    <el-table
      :data="rows"
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column
        label="类型"
        width="160"
      >
        <template #default="{ row }">
          <el-select
            v-model="row.type"
            placeholder="选择类型"
            style="width: 100%"
          >
            <el-option
              label="电话"
              value="PHONE"
            />
            <el-option
              label="微信"
              value="WECHAT"
            />
            <el-option
              label="邮箱"
              value="EMAIL"
            />
            <el-option
              label="其它"
              value="OTHER"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column
        label="内容"
        min-width="220"
      >
        <template #default="{ row }">
          <el-input
            v-model="row.value"
            placeholder="电话号码 / 微信号 / 邮箱"
            maxlength="120"
            show-word-limit
          />
        </template>
      </el-table-column>
      <el-table-column
        label="备注"
        min-width="160"
      >
        <template #default="{ row }">
          <el-input
            v-model="row.label"
            placeholder="例如：客服 / 场馆负责人"
            maxlength="32"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="排序"
        width="120"
      >
        <template #default="{ row }">
          <el-input-number
            v-model="row.sortOrder"
            :min="0"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="160"
        fixed="right"
      >
        <template #default="{ row, $index }">
          <el-button
            type="primary"
            size="small"
            link
            @click="handleSave(row)"
          >
            {{ row.id ? '保存' : '创建' }}
          </el-button>
          <el-button
            type="danger"
            size="small"
            link
            :disabled="!row.id"
            @click="handleDelete(row, $index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="empty-hint">
          暂无联系方式，点击右上角新增
        </div>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addContact,
  updateContact,
  deleteContact
} from '@/api/venues'
import type { VenueContact } from '@/types'

const props = defineProps<{
  venueId: number | null
  contacts: VenueContact[]
}>()

const emit = defineEmits<{
  changed: []
}>()

const loading = ref(false)
const rows = ref<VenueContact[]>([])

interface ContactRow {
  id?: number
  type: string
  value: string
  label?: string
  sortOrder: number
}

const toRow = (c: VenueContact): ContactRow => ({
  id: c.id,
  type: c.type,
  value: c.value,
  label: c.label,
  sortOrder: c.sortOrder ?? 0
})

const buildRows = (source: VenueContact[]) => {
  rows.value = (source || []).slice().sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map(toRow)
}

const handleAdd = () => {
  rows.value.push({
    type: 'PHONE',
    value: '',
    label: '',
    sortOrder: rows.value.length
  })
}

const validateRow = (row: ContactRow): string | null => {
  if (!row.type) return '请选择联系方式类型'
  if (!row.value || !row.value.trim()) return '请输入联系方式内容'
  return null
}

const handleSave = async (row: VenueContact & { id?: number }) => {
  if (props.venueId == null) {
    ElMessage.warning('请先保存场馆基础信息')
    return
  }
  const err = validateRow(row)
  if (err) {
    ElMessage.warning(err)
    return
  }
  loading.value = true
  try {
    const payload = {
      type: row.type,
      value: row.value.trim(),
      label: row.label || undefined,
      sortOrder: row.sortOrder ?? 0
    }
    if (row.id) {
      const res = await updateContact(row.id, payload)
      if (res.code === 0) {
        ElMessage.success('已更新')
        emit('changed')
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } else {
      const res = await addContact(props.venueId, payload)
      if (res.code === 0) {
        ElMessage.success('已创建')
        emit('changed')
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    }
  } catch {

  } finally {
    loading.value = false
  }
}

const handleDelete = async (row: VenueContact & { id?: number }, index: number) => {
  try {
    await ElMessageBox.confirm('确定删除该联系方式？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    if (!row.id) {
      // 尚未持久化的新行直接从 rows 中剔除
      rows.value.splice(index, 1)
      return
    }
    loading.value = true
    const res = await deleteContact(row.id)
    if (res.code === 0) {
      ElMessage.success('已删除')
      emit('changed')
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {

  } finally {
    loading.value = false
  }
}

watch(
  () => props.contacts,
  (val) => {
    buildRows(val)
  },
  { immediate: true }
)
</script>

<style scoped>
.contacts-editor {
  padding-top: 4px;
}

.contacts-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.contacts-hint {
  font-size: 13px;
  color: #6b7280;
}

.empty-hint {
  padding: 16px;
  color: #6b7280;
  font-size: 13px;
}
</style>
