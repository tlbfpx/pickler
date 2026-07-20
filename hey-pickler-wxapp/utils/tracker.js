// utils/tracker.js — Loop-v19 Dashboard Phase 2 R3
// 小程序客户端自定义事件上报通道。
//
// 设计原则（best-effort 上报，不影响主流程）：
//  - wx.request 失败仅 console.warn，不抛
//  - props 体积限制 2KB（避免大对象塞 access_log）
//  - did（device id）首次启动生成并存 wx.getStorageSync，后续沿用
//  - export 两个 API：trackEvent(name, props) / trackError(msg, stack)

const PROPS_MAX_KEYS = 50
const PROPS_MAX_VALUE_LEN = 512

/**
 * 持久化设备 id：首次启动生成 `did-<timestamp>-<random>` 存 wx.getStorageSync。
 * @returns {string}
 */
function ensureDid() {
  let did = ''
  try {
    did = wx.getStorageSync('did') || ''
  } catch (e) {
    did = ''
  }
  if (!did) {
    const ts = Date.now().toString(36)
    const rnd = Math.random().toString(36).slice(2, 10)
    did = `did-${ts}-${rnd}`
    try {
      wx.setStorageSync('did', did)
    } catch (e) {
      // storage 写失败不抛（罕见）
    }
  }
  return did
}

/**
 * 裁剪 props：超过 50 个 key 或单 value > 512 字符直接截断（best-effort 防御）。
 */
function sanitizeProps(props) {
  if (!props || typeof props !== 'object') return {}
  const out = {}
  const keys = Object.keys(props).slice(0, PROPS_MAX_KEYS)
  for (const k of keys) {
    let v = props[k]
    if (v == null) { out[k] = v; continue }
    if (typeof v === 'string') {
      v = v.length > PROPS_MAX_VALUE_LEN ? v.slice(0, PROPS_MAX_VALUE_LEN) : v
    } else if (typeof v === 'number' || typeof v === 'boolean') {
      // 原样
    } else {
      // 对象/数组 → JSON 后截断
      try {
        const s = JSON.stringify(v)
        v = s && s.length > PROPS_MAX_VALUE_LEN ? s.slice(0, PROPS_MAX_VALUE_LEN) : v
      } catch (e) {
        v = String(v).slice(0, PROPS_MAX_VALUE_LEN)
      }
    }
    out[k] = v
  }
  return out
}

/**
 * 上报一个自定义事件。失败仅 warn，不抛。
 * @param {string} name 事件名（必填，≤ 64 字符）
 * @param {object} [props] 事件属性（可选）
 */
function trackEvent(name, props) {
  if (!name || typeof name !== 'string') return
  if (name.length > 64) name = name.slice(0, 64)

  try {
    const app = getApp()
    const baseUrl = app && app.globalData && app.globalData.baseUrl
    if (!baseUrl) return // 没 baseUrl 时跳过（dev mode 启动早期）

    const payload = {
      name,
      props: sanitizeProps(props),
      did: ensureDid(),
      ts: Date.now()
    }

    wx.request({
      url: `${baseUrl}/track/event`,
      method: 'POST',
      data: payload,
      // 显式不传 Authorization：track endpoint 接受匿名 + 鉴权后 userId 由 AppAuthFilter 自动绑
      header: { 'Content-Type': 'application/json' },
      success: () => {},
      fail: (e) => {
        console.warn('[tracker] event failed:', name, e && e.errMsg)
      }
    })
  } catch (e) {
    console.warn('[tracker] trackEvent exception:', name, e && e.message)
  }
}

/**
 * 上报一个 JS 错误（onError / onUnhandledRejection 钩入）。
 * 自动截断 msg / stack 长度，避免大异常塞 access_log。
 */
function trackError(msg, stack) {
  const safeMsg = msg == null ? '' : String(msg).slice(0, 200)
  const safeStack = stack == null ? '' : String(stack).slice(0, 500)
  trackEvent('js_error', { msg: safeMsg, stack: safeStack })
}

export default {
  trackEvent,
  trackError,
  // 仅供测试 / 调试用：暴露 ensureDid 让单测可断言
  _ensureDid: ensureDid
}