<template>
  <div class="table-pagination">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  size: number
  total: number
}>()

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
}>()

const currentPage = computed({
  get: () => props.page,
  set: (value) => emit('update:page', value)
})

const pageSize = computed({
  get: () => props.size,
  set: (value) => emit('update:size', value)
})

const handleSizeChange = (value: number) => {
  emit('update:size', value)
}

const handleCurrentChange = (value: number) => {
  emit('update:page', value)
}
</script>

<style scoped>
/* Styles are in src/styles/index.scss */
</style>
