import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDictBundle, getDictVersion, type SysDictItemVO } from '@/api/dict'

const CACHE_KEY = 'dict_bundle'

interface CachedBundle {
  version: number
  dicts: Record<string, SysDictItemVO[]>
  ts: number
}

function readCache(): CachedBundle | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? (JSON.parse(raw) as CachedBundle) : null
  } catch {
    return null
  }
}

function writeCache(c: CachedBundle) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(c))
  } catch {
    /* quota */
  }
}

export const useDictStore = defineStore('dict', () => {
  const bundle = ref<Record<string, SysDictItemVO[]>>({})
  const version = ref<number>(0)
  const loaded = ref(false)

  // 模块加载即同步预填缓存，避免首屏 fallback
  const cached = readCache()
  if (cached) {
    bundle.value = cached.dicts
    version.value = cached.version
  }

  async function load() {
    try {
      const vRes = await getDictVersion()
      const serverVersion = vRes.data?.version ?? 0
      if (serverVersion > version.value || !loaded.value) {
        const bRes = await getDictBundle()
        if (bRes.code === 0 && bRes.data) {
          bundle.value = bRes.data.dicts
          version.value = bRes.data.version
          writeCache({ version: version.value, dicts: bundle.value, ts: Date.now() })
        }
      }
    } catch {
      /* 保持缓存，不阻塞业务 */
    } finally {
      loaded.value = true
    }
  }

  async function refresh() {
    try {
      const bRes = await getDictBundle()
      if (bRes.code === 0 && bRes.data) {
        bundle.value = bRes.data.dicts
        version.value = bRes.data.version
        writeCache({ version: version.value, dicts: bundle.value, ts: Date.now() })
      }
    } catch {
      /* ignore */
    }
  }

  function items(code: string): SysDictItemVO[] {
    return (bundle.value[code] || [])
      .filter((i) => i.status === 'ENABLED')
      .sort((a, b) => a.sort - b.sort)
  }

  function item(code: string, key: string): SysDictItemVO | undefined {
    return (bundle.value[code] || []).find((i) => i.itemKey === key && i.status === 'ENABLED')
  }

  const label = (code: string, key: string): string => item(code, key)?.itemLabel || key
  const color = (code: string, key: string): string => item(code, key)?.itemColor || '#6B7280'

  function meta(code: string, key: string): Record<string, unknown> | null {
    const raw = item(code, key)?.extraJson
    if (!raw) return null
    try {
      return JSON.parse(raw) as Record<string, unknown>
    } catch {
      return null
    }
  }

  const options = (code: string) =>
    items(code).map((i) => ({
      label: i.itemLabel,
      value: i.itemKey,
      color: i.itemColor || '#6B7280',
      sort: i.sort
    }))

  return { bundle, version, loaded, load, refresh, items, item, label, color, meta, options }
})
