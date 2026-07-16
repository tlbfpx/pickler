<template>
  <div
    v-loading="loading"
    class="brand-view"
  >
    <el-card>
      <template #header>
        品牌配置
      </template>
      <el-form
        :model="form"
        label-width="100px"
        style="max-width: 560px"
      >
        <el-form-item label="App 名称">
          <el-input
            v-model="form.appName"
            maxlength="64"
            show-word-limit
            placeholder="如：Hey Pickler"
          />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input
            v-model="form.slogan"
            maxlength="128"
            placeholder="如：匹克球赛事活动管理平台（留空隐藏）"
          />
        </el-form-item>
        <el-form-item label="Logo URL">
          <el-input
            v-model="form.logoUrl"
            placeholder="https://... （留空用内置 favicon）"
          />
          <el-image
            v-if="form.logoUrl"
            :src="form.logoUrl"
            class="logo-preview"
            fit="cover"
          />
        </el-form-item>
        <el-form-item label="主题色">
          <el-color-picker v-model="form.primaryColor" />
          <span class="color-hex">{{ form.primaryColor }}</span>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBrand, updateBrand, type BrandUpdateRequest } from '@/api/brand'
import { useBrandStore } from '@/stores/brand'

const brandStore = useBrandStore()
const loading = ref(false)
const saving = ref(false)

const form = reactive<BrandUpdateRequest>({
  appName: '',
  slogan: '',
  logoUrl: '',
  primaryColor: '#4CAF50'
})

const load = async () => {
  loading.value = true
  try {
    const res = await getBrand()
    if (res.code === 0 && res.data) {
      form.appName = res.data.appName
      form.slogan = res.data.slogan || ''
      form.logoUrl = res.data.logoUrl || ''
      form.primaryColor = res.data.primaryColor
    }
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!form.appName.trim()) {
    ElMessage.error('App 名称不能为空')
    return
  }
  saving.value = true
  try {
    const res = await updateBrand({ ...form })
    if (res.code === 0) {
      ElMessage.success('品牌配置已保存')
      // 立即重新拉取并应用（title / favicon / 主题色 / 各处文案）
      await brandStore.refresh()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.brand-view {
  padding: 16px;
}

.logo-preview {
  width: 56px;
  height: 56px;
  margin-top: 8px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.color-hex {
  margin-left: 12px;
  color: #6b7280;
  font-size: 13px;
}
</style>
