// App.js

// === 上线配置（发布前必改）===
// 正式版 / 体验版用生产 HTTPS 域名（微信要求已备案）。
// 开发工具（envVersion=develop）自动回退到 localhost，无需手动切换。
const PROD_BASE_URL = 'https://api.your-domain.com/api/app' // TODO(上线前): 替换为真实生产域名

function resolveBaseUrl() {
  try {
    const envVersion = wx.getAccountInfoSync().miniProgram.envVersion
    // develop=开发工具, trial=体验版, release=正式版
    if (envVersion === 'develop') return 'http://localhost:8080/api/app'
    return PROD_BASE_URL
  } catch (e) {
    return PROD_BASE_URL
  }
}

const dict = require('./utils/dict.js')
const brand = require('./utils/brand.js')

App({
  globalData: {
    userInfo: null,
    token: null,
    baseUrl: resolveBaseUrl()
  },

  onLaunch() {
    // 上线防御：正式/体验版若 baseUrl 仍是占位符或 localhost，立即告警（防发布忘改）
    const url = this.globalData.baseUrl
    if (url.includes('your-domain') || url.includes('localhost')) {
      let env = 'release'
      try { env = wx.getAccountInfoSync().miniProgram.envVersion } catch (e) {}
      if (env !== 'develop') {
        console.error('[上线配置错误] baseUrl=' + url + '，正式/体验版将无法访问后端。请修改 app.js 的 PROD_BASE_URL。')
      }
    }

    // 检查登录状态
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
      this.getUserInfo()
    }

    // 启动拉字典 bundle（匿名，不依赖 token；失败保留 storage 兜底，不阻塞启动）
    dict.refresh(this.globalData.baseUrl)

    // 启动拉品牌配置（匿名）：立即用缓存色设 tabbar，刷新后重设（brand.js 已 storage/fallback 预填）
    brand.applyTabBar()
    brand.refresh(this.globalData.baseUrl).then(() => brand.applyTabBar())

    // 检查更新
    this.checkUpdate()
  },

  // 全局错误捕获
  onError(err) {
    console.error('[App.onError]', typeof err, JSON.stringify(err), err)
  },

  // 未处理的 Promise rejection
  onUnhandledRejection(res) {
    console.error('[App.onUnhandledRejection]', typeof res, JSON.stringify(res), res)
  },

  // 获取用户信息 — app.js 用 this 而非 getApp()
  async getUserInfo() {
    if (!this.globalData.token) return

    try {
      const res = await new Promise((resolve, reject) => {
        wx.request({
          url: `${this.globalData.baseUrl}/user/profile`,
          method: 'GET',
          header: {
            'Authorization': `Bearer ${this.globalData.token}`
          },
          success: resolve,
          fail: reject
        })
      })

      if (res.statusCode === 200 && res.data.code === 0) {
        this.globalData.userInfo = res.data.data
      } else if (res.statusCode === 401 || res.statusCode === 403) {
        // Token 无效或被封禁，清除并跳转登录
        this.globalData.token = null
        this.globalData.userInfo = null
        wx.removeStorageSync('token')
      }
    } catch (error) {
      console.error('获取用户信息失败', error)
    }
  },

  // 检查小程序更新
  checkUpdate() {
    if (wx.canIUse('getUpdateManager')) {
      const updateManager = wx.getUpdateManager()

      updateManager.onCheckForUpdate((res) => {
        if (res.hasUpdate) {
          updateManager.onUpdateReady(() => {
            wx.showModal({
              title: '更新提示',
              content: '新版本已准备好，是否重启应用？',
              success: (res) => {
                if (res.confirm) {
                  updateManager.applyUpdate()
                }
              }
            })
          })

          updateManager.onUpdateFailed(() => {
            wx.showModal({
              title: '更新失败',
              content: '新版本下载失败，请检查网络',
              showCancel: false
            })
          })
        }
      })
    }
  }
})
