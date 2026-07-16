import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBrand, type BrandVO } from '@/api/brand'
import { applyTheme } from '@/utils/color'

const CACHE_KEY = 'brand_config'

const FALLBACK: BrandVO = {
  appName: 'Hey Pickler',
  slogan: '',
  logoUrl: '',
  primaryColor: '#4CAF50'
}

type CachedBrand = BrandVO & { ts: number }

function readCache(): CachedBrand | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? (JSON.parse(raw) as CachedBrand) : null
  } catch {
    return null
  }
}

function writeCache(b: BrandVO) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ ...b, ts: Date.now() }))
  } catch {
    /* quota */
  }
}

/** 设 favicon（无则不动，保留内置 /favicon.svg）。 */
function setFavicon(url: string) {
  if (!url) return
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.href = url
}

export const useBrandStore = defineStore('brand', () => {
  const appName = ref(FALLBACK.appName)
  const slogan = ref<string>(FALLBACK.slogan || '')
  const logoUrl = ref<string>(FALLBACK.logoUrl || '')
  const primaryColor = ref(FALLBACK.primaryColor)
  const loaded = ref(false)

  function apply(b: BrandVO) {
    appName.value = b.appName || FALLBACK.appName
    slogan.value = b.slogan || ''
    logoUrl.value = b.logoUrl || ''
    primaryColor.value = b.primaryColor || FALLBACK.primaryColor
  }

  // 同步 DOM：浏览器标签 title / favicon / Element Plus 主色主题
  function syncDom() {
    document.title = `${appName.value} 管理后台`
    setFavicon(logoUrl.value)
    applyTheme(primaryColor.value)
  }

  // 模块加载即用缓存预填 + 立即应用主题，首屏不闪默认蓝
  const cached = readCache()
  if (cached) apply(cached)
  syncDom()

  async function load() {
    try {
      const res = await getBrand()
      if (res.code === 0 && res.data) {
        apply(res.data)
        writeCache(res.data)
      }
    } catch {
      /* 保留缓存兜底 */
    } finally {
      syncDom()
      loaded.value = true
    }
  }

  // 编辑后无条件重拉并重新应用
  const refresh = () => load()

  return { appName, slogan, logoUrl, primaryColor, loaded, load, refresh }
})
