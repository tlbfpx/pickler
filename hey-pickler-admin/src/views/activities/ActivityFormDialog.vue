<template>
  <el-dialog
    :model-value="modelValue"
    :title="event ? '编辑活动' : '新建活动'"
    width="600px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入活动标题" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="3"
          placeholder="请输入活动描述"
        />
      </el-form-item>
      <el-form-item label="地点" prop="location">
        <el-input v-model="formData.location" placeholder="请输入活动地点" />
      </el-form-item>
      <el-form-item label="Banner图">
        <ImageUpload v-model="formData.bannerUrl" />
      </el-form-item>
      <el-form-item label="活动时间" prop="eventTime">
        <el-date-picker
          v-model="formData.eventTime"
          type="datetime"
          placeholder="请选择活动时间"
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
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createEvent, updateEvent } from '@/api/events'
import ImageUpload from '@/components/common/ImageUpload.vue'
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

const formData = reactive<CreateEventRequest>({
  type: 'PARTY',
  title: '',
  description: '',
  bannerUrl: '',
  location: '',
  eventTime: '',
  registrationDeadline: '',
  maxParticipants: 30,
  fee: 0
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入活动描述', trigger: 'blur' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }],
  eventTime: [{ required: true, message: '请选择活动时间', trigger: 'change' }],
  registrationDeadline: [{ required: true, message: '请选择报名截止时间', trigger: 'change' }],
  maxParticipants: [{ required: true, message: '请输入最大人数', trigger: 'blur' }],
  fee: [{ required: true, message: '请输入费用', trigger: 'blur' }]
}

watch(() => props.event, (val) => {
  if (val) {
    formData.type = 'PARTY'
    formData.title = val.title
    formData.description = (val as any).description || ''
    formData.bannerUrl = val.bannerUrl || ''
    formData.location = val.location || ''
    formData.eventTime = val.eventTime || ''
    formData.registrationDeadline = val.registrationDeadline || ''
    formData.maxParticipants = val.maxParticipants || 30
    formData.fee = val.fee || 0
  } else {
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
    const data = { ...formData, type: 'PARTY' as const }
    let res
    if (props.event) {
      res = await updateEvent(props.event.id, data)
    } else {
      res = await createEvent(data)
    }

    if (res.code === 0) {
      ElMessage.success(props.event ? '活动更新成功' : '活动创建成功')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    loading.value = false
  }
}
</script>
