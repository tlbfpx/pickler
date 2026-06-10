// Login page
import auth from '../../utils/auth'
import util from '../../utils/util'

Page({
  data: {
    loading: false,
    canIGetUserProfile: false,
    needPhone: false
  },

  onLoad() {
    // Check if user info API is available
    this.setData({
      canIGetUserProfile: wx.canIUse('getUserProfile')
    })

    // Auto login if user is already logged in
    if (auth.isLoggedIn()) {
      wx.switchTab({
        url: '/pages/index/index'
      })
    }
  },

  // Handle WeChat login
  async handleLogin() {
    if (this.data.loading) return

    this.setData({
      loading: true
    })

    util.showLoading('登录中...')

    try {
      // Perform login
      const { user } = await auth.login()

      util.hideLoading()
      util.showSuccess('登录成功')

      // Navigate to index
      setTimeout(() => {
        wx.switchTab({
          url: '/pages/index/index'
        })
      }, 1500)
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '登录失败')
      this.setData({
        loading: false
      })
    }
  },

  // Get user profile and bind phone
  async getUserProfile(e) {
    if (this.data.loading) return

    this.setData({
      loading: true
    })

    try {
      // First login with code
      const { user } = await auth.login()

      // If user already has phone, navigate to index
      if (user && user.phone) {
        util.showSuccess('登录成功')
        setTimeout(() => {
          wx.switchTab({
            url: '/pages/index/index'
          })
        }, 1500)
        return
      }

      // Need to bind phone number - show phone button
      this.setData({
        loading: false,
        needPhone: true
      })
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '登录失败')
      this.setData({
        loading: false
      })
    }
  },

  // Bind phone number
  async bindPhoneNumber(e) {
    if (e && e.detail.errMsg !== 'getPhoneNumber:ok') {
      util.showError('需要授权手机号才能继续')
      return
    }

    util.showLoading('绑定手机号...')

    try {
      const { encryptedData, iv } = e.detail
      await auth.bindPhone(encryptedData, iv)

      util.hideLoading()
      util.showSuccess('绑定成功')

      setTimeout(() => {
        wx.switchTab({
          url: '/pages/index/index'
        })
      }, 1500)
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '绑定失败')
    }
  },

  // Old version - get user info (deprecated)
  async getUserInfo(e) {
    if (e.detail.errMsg !== 'getUserInfo:ok') {
      util.showError('需要授权用户信息')
      return
    }

    await this.handleLogin()
  }
})
