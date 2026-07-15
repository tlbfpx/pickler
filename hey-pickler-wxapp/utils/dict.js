// 字典 bundle 缓存与翻译（启动拉取 + storage 兜底）
//
// 后端匿名端点 GET /api/app/dict/bundle → { code:0, data:{ version, dicts:{ dictCode: [items] } } }
// wxapp globalData.baseUrl 已含 /api/app 前缀，故本模块拼接 ${baseUrl}/dict/bundle。
// 启动 onLaunch 即调用 refresh（fire-and-forget），失败保留 storage 兜底，不阻塞启动。

const STORAGE_KEY = 'dict_bundle'

// 同步预填：模块加载即读 storage，避免首屏空（wx.getStorageSync 同步 API）
function loadFromStorage() {
  try {
    const raw = wx.getStorageSync(STORAGE_KEY)
    return raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : null
  } catch (e) {
    return null
  }
}

const _seed = loadFromStorage()
let bundle = (_seed && _seed.dicts) || {} // { dictCode: [items] }
let version = (_seed && _seed.version) || 0

function save(data) {
  bundle = (data && data.dicts) || {}
  version = (data && data.version) || 0
  try {
    wx.setStorageSync(STORAGE_KEY, { dicts: bundle, version, ts: Date.now() })
  } catch (e) {
    // storage 写入失败不阻塞业务
  }
}

// 启动拉取（匿名，不依赖 token）。失败 resolve(null) 保留 storage 兜底。
function refresh(baseUrl) {
  return new Promise((resolve) => {
    wx.request({
      url: `${baseUrl}/dict/bundle`,
      method: 'GET',
      success: (res) => {
        const body = (res && res.data) || {}
        if (body.code === 0 && body.data) {
          save(body.data)
        }
        resolve(body.data || null)
      },
      fail: () => resolve(null)
    })
  })
}

// === 翻译 helper（读当前 bundle 快照）===

// 取某字典下全部启用项，按 sort 升序
function items(code) {
  return (bundle[code] || [])
    .filter((i) => i && i.status === 'ENABLED')
    .sort((a, b) => (a.sort || 0) - (b.sort || 0))
}

// 取单个启用项
function item(code, key) {
  return (bundle[code] || []).find((i) => i && i.itemKey === key && i.status === 'ENABLED')
}

// itemKey → itemLabel；未命中回退原 key（避免空白）
function label(code, key) {
  const it = item(code, key)
  return it ? it.itemLabel : key
}

// itemKey → itemColor；未命中回退中性灰
function color(code, key) {
  const it = item(code, key)
  return it && it.itemColor ? it.itemColor : '#6B7280'
}

// 解析 extraJson（含 tier 阈值、track_term 术语映射等）；失败/缺失返回 null
function meta(code, key) {
  const it = item(code, key)
  if (!it || !it.extraJson) return null
  try {
    return JSON.parse(it.extraJson)
  } catch (e) {
    return null
  }
}

// track_term 术语：{ type, points, tier, ranking }
// 优先用 bundle 的 track_term.{STAR|PARTY}.extraJson；缺失回退历史硬编码（与 utils/terms.js 对齐）
function terms(track) {
  const m = meta('track_term', track)
  if (m) {
    return {
      type: label('event_type', track),
      points: m.pointsName,
      tier: m.tierName,
      ranking: m.rankingName
    }
  }
  const fb = {
    STAR: { type: '竞技赛事', points: '战力', tier: '战力段位', ranking: '战力排名' },
    PARTY: { type: '社交活动', points: '活力', tier: '活力段位', ranking: '活力排名' }
  }
  return fb[track] || { type: track, points: track, tier: track, ranking: track }
}

module.exports = {
  refresh,
  save,
  items,
  item,
  label,
  color,
  meta,
  terms,
  getBundle: () => bundle,
  getVersion: () => version
}
