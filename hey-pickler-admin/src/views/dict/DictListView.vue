<template>
  <div>
    <div class="page-header">
      <h1>字典管理</h1>
    </div>
    <div class="dict-layout">
      <!-- 左：字典目录 -->
      <el-card class="dict-nav">
        <el-table
          v-loading="dictLoading"
          :data="dicts"
          highlight-current-row
          size="small"
          @current-change="onPickDict"
        >
          <el-table-column
            prop="dictName"
            label="字典"
          />
        </el-table>
      </el-card>
      <!-- 右：item 编辑 -->
      <el-card class="dict-items">
        <template v-if="currentDict">
          <div class="dict-toolbar">
            <span class="dict-title">
              {{ currentDict.dictName }}
              <el-tag
                size="small"
                type="info"
              >item_key 不可改，仅可改展示名/颜色/排序/启停</el-tag>
            </span>
            <el-button
              type="primary"
              :loading="saving"
              @click="handleSave"
            >
              保存
            </el-button>
          </div>
          <el-table
            v-loading="itemLoading"
            :data="rows"
            size="small"
          >
            <el-table-column
              prop="itemKey"
              label="key"
              width="160"
            />
            <el-table-column
              label="展示名"
              width="160"
            >
              <template #default="{ row }">
                <el-input
                  v-model="row.itemLabel"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="颜色"
              width="160"
            >
              <template #default="{ row }">
                <el-color-picker
                  v-model="row.itemColor"
                  size="small"
                />
                <el-tag
                  :color="row.itemColor || undefined"
                  effect="dark"
                  size="small"
                  class="color-preview"
                >
                  {{ row.itemLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="排序"
              width="100"
            >
              <template #default="{ row }">
                <el-input-number
                  v-model="row.sort"
                  :min="0"
                  controls-position="right"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="启用"
              width="80"
            >
              <template #default="{ row }">
                <el-switch
                  v-model="row.status"
                  active-value="ENABLED"
                  inactive-value="DISABLED"
                />
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty
          v-else
          description="请选择左侧字典"
        />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getDictList,
  getDictItems,
  updateDictItems,
  type SysDictVO,
  type SysDictItemVO,
  type DictItemUpdateRequest
} from '@/api/dict'

const dicts = ref<SysDictVO[]>([])
const currentDict = ref<SysDictVO | null>(null)
const rows = ref<DictItemUpdateRequest[]>([])

const dictLoading = ref(false)
const itemLoading = ref(false)
const saving = ref(false)

const fetchDicts = async () => {
  dictLoading.value = true
  try {
    const res = await getDictList()
    if (res.code === 0) {
      dicts.value = res.data || []
    } else {
      ElMessage.error(res.message || '字典目录加载失败')
    }
  } catch {
    ElMessage.error('字典目录加载失败')
  } finally {
    dictLoading.value = false
  }
}

const onPickDict = async (d: SysDictVO | null) => {
  if (!d) return
  currentDict.value = d
  itemLoading.value = true
  try {
    const res = await getDictItems(d.dictCode)
    if (res.code === 0) {
      const items: SysDictItemVO[] = res.data || []
      rows.value = items.map((it) => ({
        itemKey: it.itemKey,
        itemLabel: it.itemLabel,
        itemColor: it.itemColor,
        sort: it.sort,
        status: it.status
      }))
    } else {
      ElMessage.error(res.message || '字典项加载失败')
    }
  } catch {
    ElMessage.error('字典项加载失败')
  } finally {
    itemLoading.value = false
  }
}

const handleSave = async () => {
  if (!currentDict.value) return
  saving.value = true
  try {
    const res = await updateDictItems(currentDict.value.dictCode, rows.value)
    if (res.code === 0) {
      ElMessage.success('已保存')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchDicts()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0;
  font-size: 20px;
}

.dict-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.dict-nav {
  width: 240px;
  flex-shrink: 0;
}

.dict-items {
  flex: 1;
}

.dict-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.dict-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.color-preview {
  margin-left: 6px;
  color: #fff;
  border: none;
}

.dict-items :deep(.el-table .cell) {
  display: flex;
  align-items: center;
}
</style>
