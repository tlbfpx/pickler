<template>
  <el-dialog
    :model-value="modelValue"
    title="录入积分"
    width="600px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="points-entry">
      <div
        v-for="(record, index) in records"
        :key="index"
        class="record-item"
      >
        <div class="record-header">
          <span>记录 #{{ index + 1 }}</span>
          <el-button
            v-if="records.length > 1"
            type="danger"
            size="small"
            @click="removeRecord(index)"
          >
            删除
          </el-button>
        </div>
        <el-form :model="record" label-position="top">
          <el-form-item label="用户ID">
            <el-input-number
              v-model="record.userId"
              :min="1"
              placeholder="请输入用户ID"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="积分">
            <el-input-number
              v-model="record.points"
              placeholder="请输入积分"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="原因">
            <el-input
              v-model="record.reason"
              placeholder="请输入原因"
            />
          </el-form-item>
        </el-form>
      </div>

      <el-button type="dashed" style="width: 100%" @click="addRecord">
        <el-icon><Plus /></el-icon>
        添加记录
      </el-button>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        提交
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { enterPoints } from '@/api/rankings'
import type { PointEntryRecord } from '@/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const loading = ref(false)
const records = reactive<PointEntryRecord[]>([
  { userId: 0, points: 0, reason: '' }
])

const addRecord = () => {
  records.push({ userId: 0, points: 0, reason: '' })
}

const removeRecord = (index: number) => {
  if (records.length > 1) {
    records.splice(index, 1)
  }
}

watch(() => props.modelValue, (val) => {
  if (!val) {
    records.length = 0
    records.push({ userId: 0, points: 0, reason: '' })
  }
})

const handleConfirm = async () => {
  // Validate records
  for (const record of records) {
    if (!record.userId || record.points === 0 || !record.reason) {
      ElMessage.error('请填写所有字段')
      return
    }
  }

  loading.value = true
  try {
    const res = await enterPoints({ records })
    if (res.code === 0) {
      ElMessage.success('积分录入成功')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '积分录入失败')
    }
  } catch (error) {
    ElMessage.error('积分录入失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.points-entry {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.record-item {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
}
</style>
