<template>
  <el-dialog
    :model-value="modelValue"
    :title="event ? '编辑赛事' : '新建赛事'"
    width="600px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="赛事类型" prop="type">
        <el-select v-model="formData.type" placeholder="请选择赛事类型" style="width: 100%">
          <el-option label="明星赛" value="STAR" />
          <el-option label="派对赛" value="PARTY" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入赛事标题" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="3"
          placeholder="请输入赛事描述"
        />
      </el-form-item>
      <el-form-item label="地点" prop="location">
        <el-input v-model="formData.location" placeholder="请输入比赛地点" />
      </el-form-item>
      <el-form-item label="比赛时间" prop="eventTime">
        <el-date-picker
          v-model="formData.eventTime"
          type="datetime"
          placeholder="请选择比赛时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="报名截止" prop="registrationDeadline">
        <el-date-picker
          v-model="formData.registrationDeadline"
          type="datetime"
          placeholder="请选择报名截止时间"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="最大人数" prop="maxParticipants">
        <el-input-number
          v-model="formData.maxParticipants"
          :min="1"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="费用 (元)" prop="fee">
        <el-input-number
          v-model="formData.fee"
          :min="0"
          :precision="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item v-if="event" label="状态">
        <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
          <el-option
            v-for="opt in editStatusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ event ? '更新' : '新建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createEvent, updateEvent } from '@/api/events'
import { formatEventStatus } from '@/utils'
import type { Event, CreateEventRequest } from '@/types'

const props = defineProps<{
  modelValue: boolean
  event: Event | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive<CreateEventRequest & { status?: string }>({
  type: 'STAR',
  title: '',
  description: '',
  location: '',
  eventTime: '',
  registrationDeadline: '',
  maxParticipants: 50,
  fee: 0,
  status: undefined
})

const rules: FormRules = {
  type: [{ required: true, message: '请选择赛事类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }],
  eventTime: [{ required: true, message: '请选择比赛时间', trigger: 'change' }],
  registrationDeadline: [{ required: true, message: '请选择报名截止时间', trigger: 'change' }],
  maxParticipants: [{ required: true, message: '请输入最大人数', trigger: 'blur' }],
  fee: [{ required: true, message: '请输入费用', trigger: 'blur' }]
}

const FORM_STATUS_TRANSITIONS: Record<string, string[]> = {
  DRAFT: ['DRAFT', 'OPEN'],
  OPEN: ['OPEN', 'CANCELLED'],
  FULL: ['FULL', 'CANCELLED'],
  IN_PROGRESS: ['IN_PROGRESS', 'COMPLETED', 'CANCELLED'],
  COMPLETED: ['COMPLETED'],
  CANCELLED: ['CANCELLED']
}

const editStatusOptions = computed(() => {
  if (!props.event) return []
  const current = props.event.status
  const allowed = FORM_STATUS_TRANSITIONS[current] || [current]
  return allowed.map(s => ({ value: s, label: formatEventStatus(s) }))
})

watch(() => props.event, (val) => {
  if (val) {
    formData.type = val.type
    formData.title = val.title
    formData.description = val.description
    formData.location = val.location
    formData.eventTime = val.eventTime
    formData.registrationDeadline = val.registrationDeadline
    formData.maxParticipants = val.maxParticipants
    formData.fee = val.fee
    formData.status = val.status
  } else {
    formData.status = undefined
    formRef.value?.resetFields()
  }
})

watch(() => props.modelValue, (val) => {
  if (!val && !props.event) {
    formRef.value?.resetFields()
  }
})

const handleConfirm = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    let res
    if (props.event) {
      res = await updateEvent(props.event.id, formData)
    } else {
      res = await createEvent(formData)
    }

    if (res.code === 0) {
      ElMessage.success(props.event ? '赛事更新成功' : '赛事创建成功')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}
</script>
