<template>
  <el-tag
    :color="color || undefined"
    effect="dark"
    :size="size"
    :type="!color ? 'info' : undefined"
  >
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useDictStore } from '@/stores/dict'

const props = defineProps<{
  dictCode: string
  itemKey: string | null | undefined
  size?: 'small' | 'default' | 'large'
}>()

const store = useDictStore()
const label = computed(() => (props.itemKey ? store.label(props.dictCode, props.itemKey) : '-'))
const color = computed(() =>
  props.itemKey ? store.color(props.dictCode, props.itemKey) : '#6B7280'
)
</script>
