// App.js
App({
  globalData: {
    userInfo: null,
    token: null,
    baseUrl: 'http://localhost:8080/api/app'
  },

  onLaunch() {
    // 检查登录状态
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
      this.getUserInfo()
    }

    // 检查更新
    this.checkUpdate()
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
