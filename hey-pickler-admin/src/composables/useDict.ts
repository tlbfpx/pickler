import { computed } from 'vue'
import { useDictStore } from '@/stores/dict'

/** 响应式：返回某字典的 {label,value,color,sort}[]，给 el-select/el-radio 用 */
export function useDictOptions(code: string) {
  const store = useDictStore()
  return computed(() => store.options(code))
}

/** 响应式：返回某 (code,key) 的 {label,color}；keyGetter 是返回 key 的函数/ref */
export function useDictItem(code: string, keyGetter: () => string | null | undefined) {
  const store = useDictStore()
  return computed(() => {
    const k = keyGetter()
    if (!k) return { label: '-', color: '#6B7280' }
    return { label: store.label(code, k), color: store.color(code, k) }
  })
}
