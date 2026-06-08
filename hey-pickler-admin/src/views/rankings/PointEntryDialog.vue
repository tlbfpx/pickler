<template>
  <el-dialog
    :model-value="modelValue"
    title="Enter Points"
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
          <span>Record #{{ index + 1 }}</span>
          <el-button
            v-if="records.length > 1"
            type="danger"
            size="small"
            @click="removeRecord(index)"
          >
            Remove
          </el-button>
        </div>
        <el-form :model="record" label-position="top">
          <el-form-item label="User ID">
            <el-input-number
              v-model="record.userId"
              :min="1"
              placeholder="Enter user ID"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="Points">
            <el-input-number
              v-model="record.points"
              placeholder="Enter points"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="Reason">
            <el-input
              v-model="record.reason"
              placeholder="Enter reason"
            />
          </el-form-item>
        </el-form>
      </div>

      <el-button type="dashed" style="width: 100%" @click="addRecord">
        <el-icon><Plus /></el-icon>
        Add Record
      </el-button>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        Submit
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
      ElMessage.error('Please fill in all fields')
      return
    }
  }

  loading.value = true
  try {
    const res = await enterPoints({ records })
    if (res.code === 0) {
      ElMessage.success('Points entered successfully')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || 'Failed to enter points')
    }
  } catch (error) {
    ElMessage.error('Failed to enter points')
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
