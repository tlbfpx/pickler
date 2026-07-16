// 品牌配置缓存与应用（启动拉取 + storage 兜底 + 离线 fallback）
//
// 后端匿名端点 GET /api/app/brand → { code:0, data:{ appName, slogan, logoUrl, primaryColor } }
// wxapp globalData.baseUrl 已含 /api/app 前缀，故本模块拼接 ${baseUrl}/brand。
// 启动 onLaunch 即 refresh（fire-and-forget），失败保留 storage/fallback，不阻塞启动。

var STORAGE_KEY = 'brand_config'

// 离线/首启 fallback（与 brand_config seed 一致）
var FALLBACK = {
  appName: 'Hey Pickler',
  slogan: '匹克球赛事活动管理平台',
  logoUrl: '/images/logo.png',
  primaryColor: '#4CAF50'
}

function loadFromStorage() {
  try {
    var raw = wx.getStorageSync(STORAGE_KEY)
    return raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : null
  } catch (e) {
    return null
  }
}

// 模块加载即同步预填：storage → fallback，避免首屏空
var brand = merge(FALLBACK, loadFromStorage() || {})

function merge(base, over) {
  return Object.assign({}, base, over)
}

function save(data) {
  brand = merge(FALLBACK, data || {})
  try {
    wx.setStorageSync(STORAGE_KEY, data)
  } catch (e) {
    // storage 写失败不阻塞
  }
}

// 启动拉取（匿名，不依赖 token）。失败 resolve(null) 保留 storage 兜底。
function refresh(baseUrl) {
  return new Promise(function (resolve) {
    wx.request({
      url: baseUrl + '/brand',
      method: 'GET',
      success: function (res) {
        var body = (res && res.data) || {}
        if (body.code === 0 && body.data) {
          save(body.data)
        }
        resolve(body.data || null)
      },
      fail: function () { resolve(null) }
    })
  })
}

// === getter（带 fallback，空值回退默认）===
function appName() { return brand.appName || FALLBACK.appName }
function slogan() { return brand.slogan || FALLBACK.slogan }
function logoUrl() { return brand.logoUrl || FALLBACK.logoUrl }
function primaryColor() { return brand.primaryColor || FALLBACK.primaryColor }

function get() {
  return {
    appName: appName(),
    slogan: slogan(),
    logoUrl: logoUrl(),
    primaryColor: primaryColor()
  }
}

// === 主题应用 ===

// hex 向黑色混合（weight 0..1）产生深色端；非法 hex 原样返回
function shadeHex(hex, weight) {
  var m = /^#?([0-9a-fA-F]{6})$/.exec((hex || '').trim())
  if (!m) return hex
  var v = m[1]
  var r = parseInt(v.slice(0, 2), 16)
  var g = parseInt(v.slice(2, 4), 16)
  var b = parseInt(v.slice(4, 6), 16)
  var w = Math.max(0, Math.min(1, weight))
  function byte(n) { return Math.round(n * (1 - w)) }
  function hex2(n) { return ('0' + Math.max(0, Math.min(255, byte(n))).toString(16)).slice(-2) }
  return '#' + hex2(r) + hex2(g) + hex2(b)
}

// 主色 → 加深 12% 的两段渐变（login 等品牌大面用）
function gradient(color) {
  var c = color || primaryColor()
  return 'linear-gradient(135deg,' + c + ' 0%,' + shadeHex(c, 0.12) + ' 100%)'
}

// 导航栏底色跟随品牌主色（白字）。每个页面 onShow 调一次（tab 切换会重置回 page JSON 默认）。
function applyChrome() {
  try {
    wx.setNavigationBarColor({
      frontColor: '#ffffff',
      backgroundColor: primaryColor(),
      animation: { duration: 0 }
    })
  } catch (e) {
    // 部分页面/基础库不支持，忽略
  }
}

// tabbar 选中色跟随品牌主色（启动 + 刷新后各调一次；仅 tab 页生效）
function applyTabBar() {
  try {
    wx.setTabBarStyle({ selectedColor: primaryColor() })
  } catch (e) {
    // 忽略
  }
}

module.exports = {
  refresh: refresh,
  save: save,
  get: get,
  appName: appName,
  slogan: slogan,
  logoUrl: logoUrl,
  primaryColor: primaryColor,
  gradient: gradient,
  shadeHex: shadeHex,
  applyChrome: applyChrome,
  applyTabBar: applyTabBar
}
