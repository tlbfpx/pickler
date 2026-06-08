<template>
  <el-dialog
    :model-value="modelValue"
    :title="event ? 'Edit Event' : 'Create Event'"
    width="600px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="Event Type" prop="type">
        <el-select v-model="formData.type" placeholder="Select event type" style="width: 100%">
          <el-option label="Star Event" value="STAR" />
          <el-option label="Party Event" value="PARTY" />
        </el-select>
      </el-form-item>
      <el-form-item label="Title" prop="title">
        <el-input v-model="formData.title" placeholder="Enter event title" />
      </el-form-item>
      <el-form-item label="Description" prop="description">
        <el-input
          v-model="formData.description"
          type="textarea"
          :rows="3"
          placeholder="Enter event description"
        />
      </el-form-item>
      <el-form-item label="Location" prop="location">
        <el-input v-model="formData.location" placeholder="Enter event location" />
      </el-form-item>
      <el-form-item label="Event Date" prop="eventDate">
        <el-date-picker
          v-model="formData.eventDate"
          type="datetime"
          placeholder="Select event date"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="Registration Deadline" prop="registrationDeadline">
        <el-date-picker
          v-model="formData.registrationDeadline"
          type="datetime"
          placeholder="Select registration deadline"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="Max Participants" prop="maxParticipants">
        <el-input-number
          v-model="formData.maxParticipants"
          :min="1"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="Fee (CNY)" prop="fee">
        <el-input-number
          v-model="formData.fee"
          :min="0"
          :precision="0"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ event ? 'Update' : 'Create' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createEvent, updateEvent } from '@/api/events'
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
  type: 'STAR',
  title: '',
  description: '',
  location: '',
  eventDate: '',
  registrationDeadline: '',
  maxParticipants: 50,
  fee: 0
})

const rules: FormRules = {
  type: [{ required: true, message: 'Please select event type', trigger: 'change' }],
  title: [{ required: true, message: 'Please enter title', trigger: 'blur' }],
  description: [{ required: true, message: 'Please enter description', trigger: 'blur' }],
  location: [{ required: true, message: 'Please enter location', trigger: 'blur' }],
  eventDate: [{ required: true, message: 'Please select event date', trigger: 'change' }],
  registrationDeadline: [{ required: true, message: 'Please select deadline', trigger: 'change' }],
  maxParticipants: [{ required: true, message: 'Please enter max participants', trigger: 'blur' }],
  fee: [{ required: true, message: 'Please enter fee', trigger: 'blur' }]
}

watch(() => props.event, (val) => {
  if (val) {
    formData.type = val.type
    formData.title = val.title
    formData.description = val.description
    formData.location = val.location
    formData.eventDate = val.eventDate
    formData.registrationDeadline = val.registrationDeadline
    formData.maxParticipants = val.maxParticipants
    formData.fee = val.fee
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

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      let res
      if (props.event) {
        res = await updateEvent(props.event.id, formData)
      } else {
        res = await createEvent(formData)
      }

      if (res.code === 0) {
        ElMessage.success(props.event ? 'Event updated successfully' : 'Event created successfully')
        emit('success')
        emit('update:modelValue', false)
      } else {
        ElMessage.error(res.message || 'Operation failed')
      }
    } catch (error) {
      ElMessage.error('Operation failed')
    } finally {
      loading.value = false
    }
  })
}
</script>
