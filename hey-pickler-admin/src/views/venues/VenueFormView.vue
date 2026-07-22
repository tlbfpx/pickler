<template>
  <div
    v-loading="loading"
    class="venue-form"
  >
    <div class="page-header">
      <el-button
        link
        type="primary"
        @click="goBack"
      >
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h1>{{ isNew ? '新建场馆' : '编辑场馆' }}</h1>
    </div>

    <div class="card">
      <el-tabs v-model="activeTab">
        <el-tab-pane
          label="基础信息"
          name="basic"
        >
          <el-form
            ref="formRef"
            :model="formData"
            :rules="rules"
            label-position="top"
            style="max-width: 720px"
          >
            <el-form-item
              label="场馆名称"
              prop="name"
            >
              <el-input
                v-model="formData.name"
                placeholder="例如：xx 体育公园"
                maxlength="64"
                show-word-limit
              />
            </el-form-item>
            <el-form-item
              label="地址"
              prop="address"
            >
              <el-input
                v-model="formData.address"
                placeholder="详细地址"
                maxlength="128"
              />
            </el-form-item>
            <el-form-item label="封面图">
              <ImageUpload v-model="formData.coverUrl" />
            </el-form-item>
            <el-form-item label="简介">
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="3"
                placeholder="场馆介绍（选填）"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
            <el-form-item
              label="状态"
              prop="status"
            >
              <el-select
                v-model="formData.status"
                style="width: 200px"
              >
                <el-option
                  label="启用"
                  value="ACTIVE"
                />
                <el-option
                  label="停用"
                  value="INACTIVE"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              label="可订窗口(天)"
              prop="bookingLeadDays"
            >
              <el-input-number
                v-model="formData.bookingLeadDays"
                :min="0"
                :max="90"
                :step="1"
              />
              <span class="form-hint">0 = 当天可订；N = 至少提前 N 天</span>
            </el-form-item>
            <el-form-item label="经纬度（选填）">
              <div class="geo-row">
                <el-input
                  v-model="formData.latitude"
                  placeholder="纬度 latitude"
                  style="width: 180px"
                />
                <el-input
                  v-model="formData.longitude"
                  placeholder="经度 longitude"
                  style="width: 180px"
                />
              </div>
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving"
                @click="handleSaveBasic"
              >
                {{ isNew ? '创建场馆' : '保存基础信息' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane
          label="营业时间"
          name="hours"
          :disabled="isNew"
        >
          <BusinessHoursEditor
            :venue-id="venueId"
            :hours="detail?.businessHours || []"
            @saved="reloadDetail"
          />
        </el-tab-pane>

        <el-tab-pane
          label="联系方式"
          name="contacts"
          :disabled="isNew"
        >
          <ContactsEditor
            :venue-id="venueId"
            :contacts="detail?.contacts || []"
            @changed="reloadDetail"
          />
        </el-tab-pane>

        <el-tab-pane
          label="场地"
          name="courts"
          :disabled="isNew"
        >
          <CourtsEditor
            :venue-id="venueId"
            :courts="detail?.courts || []"
            @changed="reloadDetail"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getVenueDetail, createVenue, updateVenue } from '@/api/venues'
import ImageUpload from '@/components/common/ImageUpload.vue'
import BusinessHoursEditor from './components/BusinessHoursEditor.vue'
import ContactsEditor from './components/ContactsEditor.vue'
import CourtsEditor from './components/CourtsEditor.vue'
import type { VenueDetail, CreateVenueRequest } from '@/types'

const route = useRoute()
const router = useRouter()

const idParam = computed(() => String(route.params.id ?? '0'))
const isNew = computed(() => idParam.value === '0')
const venueId = computed(() => (isNew.value ? null : Number(idParam.value)))

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('basic')
const detail = ref<VenueDetail | null>(null)

const formRef = ref<FormInstance>()

const formData = reactive<CreateVenueRequest>({
  name: '',
  address: '',
  latitude: '',
  longitude: '',
  coverUrl: '',
  description: '',
  status: 'ACTIVE',
  bookingLeadDays: 0
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入场馆名称', trigger: 'blur' },
    { max: 64, message: '名称不超过 64 个字符', trigger: 'blur' }
  ],
  address: [
    { required: true, message: '请输入地址', trigger: 'blur' },
    { max: 128, message: '地址不超过 128 个字符', trigger: 'blur' }
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  bookingLeadDays: [
    {
      required: true,
      type: 'number',
      min: 0,
      max: 90,
      message: '可订窗口需在 0..90 之间',
      trigger: 'blur'
    }
  ]
}

const goBack = () => {
  router.push('/venues')
}

const fillForm = (d: VenueDetail) => {
  formData.name = d.name
  formData.address = d.address
  formData.latitude = d.latitude ?? ''
  formData.longitude = d.longitude ?? ''
  formData.coverUrl = d.coverUrl ?? ''
  formData.description = d.description ?? ''
  formData.status = d.status
  formData.bookingLeadDays = d.bookingLeadDays
}

const loadDetail = async () => {
  if (isNew.value) return
  loading.value = true
  try {
    const res = await getVenueDetail(venueId.value as number)
    if (res.code === 0) {
      detail.value = res.data
      fillForm(res.data)
    } else {
      ElMessage.error(res.message || '加载场馆失败')
    }
  } catch {

  } finally {
    loading.value = false
  }
}

const reloadDetail = async () => {
  if (isNew.value) return
  try {
    const res = await getVenueDetail(venueId.value as number)
    if (res.code === 0) {
      detail.value = res.data
    }
  } catch {

  }
}

const handleSaveBasic = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const payload: CreateVenueRequest = {
    name: formData.name.trim(),
    address: formData.address.trim(),
    latitude: formData.latitude || undefined,
    longitude: formData.longitude || undefined,
    coverUrl: formData.coverUrl || undefined,
    description: formData.description || undefined,
    status: formData.status,
    bookingLeadDays: formData.bookingLeadDays
  }

  saving.value = true
  try {
    if (isNew.value) {
      const res = await createVenue(payload)
      if (res.code === 0) {
        ElMessage.success('场馆已创建，可继续完善营业时间/联系方式/场地')
        router.replace(`/venues/${res.data.id}`)
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    } else {
      const res = await updateVenue(venueId.value as number, payload)
      if (res.code === 0) {
        ElMessage.success('基础信息已保存')
        await reloadDetail()
      } else {
        ElMessage.error(res.message || '保存失败')
      }
    }
  } catch {

  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.venue-form {
  position: relative;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header h1 {
  margin: 0;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.geo-row {
  display: flex;
  gap: 12px;
}
</style>
