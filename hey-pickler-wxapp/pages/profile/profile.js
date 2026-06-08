// Profile page
import request from '../../utils/request'
import auth from '../../utils/auth'
import util from '../../utils/util'

Page({
  data: {
    userInfo: null,
    loading: true,
    showPhoneModal: false,
    phone: ''
  },

  onLoad() {
    this.loadUserInfo()
  },

  onShow() {
    this.loadUserInfo()
  },

  onPullDownRefresh() {
    this.loadUserInfo()
    wx.stopPullDownRefresh()
  },

  // Load user info
  async loadUserInfo() {
    this.setData({
      loading: true
    })

    try {
      const res = await request.get('/user/profile')

      if (res.code === 0) {
        this.setData({
          userInfo: res.data,
          loading: false
        })
      } else {
        util.showError(res.message || '加载失败')
        this.setData({
          loading: false
        })
      }
    } catch (error) {
      // Redirect to login if not authenticated
      if (error.code === 401) {
        auth.logout()
      } else {
        util.showError('加载失败')
        this.setData({
          loading: false
        })
      }
    }
  },

  // Navigate to my events
  navigateToMyEvents(type) {
    wx.navigateTo({
      url: `/pages/my-events/my-events?type=${type || 'ALL'}`
    })
  },

  // Navigate to ranking
  navigateToRanking(type) {
    wx.navigateTo({
      url: `/pages/ranking/ranking?type=${type || 'STAR'}`
    })
  },

  // Edit nickname
  handleEditNickname() {
    const userInfo = this.data.userInfo
    wx.showModal({
      title: '修改昵称',
      editable: true,
      placeholderText: userInfo.nickname,
      success: async (res) => {
        if (res.confirm && res.content) {
          await this.updateProfile({ nickname: res.content })
        }
      }
    })
  },

  // Edit city
  handleEditCity() {
    const userInfo = this.data.userInfo
    wx.showModal({
      title: '修改城市',
      editable: true,
      placeholderText: userInfo.city,
      success: async (res) => {
        if (res.confirm && res.content) {
          await this.updateProfile({ city: res.content })
        }
      }
    })
  },

  // Bind phone
  handleBindPhone() {
    this.setData({
      showPhoneModal: true
    })
  },

  // Get phone number
  async getPhoneNumber(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      util.showError('需要授权手机号')
      return
    }

    this.setData({
      showPhoneModal: false
    })

    util.showLoading('绑定中...')

    try {
      const { encryptedData, iv } = e.detail
      await auth.bindPhone(encryptedData, iv)

      util.hideLoading()
      util.showSuccess('绑定成功')

      // Reload user info
      this.loadUserInfo()
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '绑定失败')
    }
  },

  // Close phone modal
  closePhoneModal() {
    this.setData({
      showPhoneModal: false
    })
  },

  // Update profile
  async updateProfile(data) {
    util.showLoading('更新中...')

    try {
      const res = await request.put('/user/profile', data)

      util.hideLoading()

      if (res.code === 0) {
        util.showSuccess('更新成功')

        // Update local state
        this.setData({
          userInfo: {
            ...this.data.userInfo,
            ...data
          }
        })

        // Update global user info
        auth.updateUserInfo(data)
      } else {
        util.showError(res.message || '更新失败')
      }
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '更新失败')
    }
  },

  // Logout
  handleLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          auth.logout()
        }
      }
    })
  }
})
