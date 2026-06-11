// Event detail page
import request from '../../utils/request'
import auth from '../../utils/auth'
import util from '../../utils/util'

Page({
  data: {
    eventId: null,
    event: null,
    loading: true,
    isRegistered: false,
    registration: null,
    action: null, // 'register' from navigation
    isRegistrationOpen: false
  },

  onLoad(options) {
    const { id, action } = options
    if (id) {
      this.setData({
        eventId: id,
        action: action || null
      })
      this.loadEventDetail()
    } else {
      util.showError('赛事不存在')
      setTimeout(() => {
        wx.navigateBack()
      }, 1500)
    }
  },

  onShow() {
    // Refresh when page shows
    if (this.data.eventId) {
      this.loadEventDetail()
    }
  },

  onPullDownRefresh() {
    this.loadEventDetail()
    wx.stopPullDownRefresh()
  },

  // Load event detail
  async loadEventDetail() {
    this.setData({
      loading: true
    })

    try {
      const res = await request.get(`/events/${this.data.eventId}`)

      if (res.code === 0) {
        const event = res.data
        const isRegistrationOpen = util.isRegistrationOpen(event)

        this.setData({
          event,
          loading: false,
          isRegistrationOpen
        })

        // Check if user is registered
        if (auth.isLoggedIn()) {
          await this.checkRegistration()
        }

        // Handle action from navigation
        if (this.data.action === 'register' && isRegistrationOpen && !this.data.isRegistered) {
          this.handleRegister()
        }
      } else {
        util.showError(res.message || '加载失败')
        this.setData({
          loading: false
        })
      }
    } catch (error) {
      util.showError('加载失败')
      this.setData({
        loading: false
      })
    }
  },

  // Check if user is registered
  async checkRegistration() {
    try {
      const res = await request.get('/user/events', { page: 1, size: 100 })

      if (res.code === 0 && res.data.list) {
        const registration = res.data.list.find(r => r.id == this.data.eventId)
        if (registration) {
          this.setData({
            isRegistered: true,
            registration
          })
        }
      }
    } catch (error) {
      console.error('Check registration failed:', error)
    }
  },

  // Register for event
  async handleRegister() {
    // Check login status
    if (!auth.isLoggedIn()) {
      wx.showModal({
        title: '提示',
        content: '请先登录',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({
              url: '/pages/login/login'
            })
          }
        }
      })
      return
    }

    // Check if already registered
    if (this.data.isRegistered) {
      util.showError('您已报名该赛事')
      return
    }

    // Check if registration is open
    if (!this.data.isRegistrationOpen) {
      util.showError('报名已截止或已满员')
      return
    }

    // Select match type
    const matchType = await new Promise((resolve) => {
      wx.showActionSheet({
        itemList: ['单打', '双打', '混双'],
        success: (res) => {
          const types = ['SINGLES', 'DOUBLES', 'MIXED']
          resolve(types[res.tapIndex])
        },
        fail: () => {
          resolve(null)
        }
      })
    })

    if (!matchType) return

    // Confirm registration
    const confirmed = await util.showConfirm({
      title: '确认报名',
      content: `确定要报名参加「${this.data.event.title}」吗？`
    })

    if (!confirmed) return

    util.showLoading('报名中...')

    try {
      const res = await request.post(`/events/${this.data.eventId}/register`, {
        matchType
      })

      util.hideLoading()

      if (res.code === 0) {
        util.showSuccess('报名成功')

        // Update state
        this.setData({
          isRegistered: true,
          registration: res.data,
          event: {
            ...this.data.event,
            currentParticipants: this.data.event.currentParticipants + 1
          }
        })

        // Check if event is now full
        if (this.data.event.currentParticipants >= this.data.event.maxParticipants) {
          this.setData({
            isRegistrationOpen: false,
            event: {
              ...this.data.event,
              status: 'FULL'
            }
          })
        }
      } else {
        util.showError(res.message || '报名失败')
      }
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '报名失败')
    }
  },

  // Cancel registration
  async handleCancel() {
    // Check if can cancel
    const now = new Date()
    const deadline = new Date(this.data.event.registrationDeadline)

    if (now >= deadline) {
      util.showError('报名截止后无法取消，请联系管理员')
      return
    }

    // Confirm cancel
    const confirmed = await util.showConfirm({
      title: '取消报名',
      content: '确定要取消报名吗？'
    })

    if (!confirmed) return

    util.showLoading('取消中...')

    try {
      const res = await request.post(`/events/${this.data.eventId}/cancel`)

      util.hideLoading()

      if (res.code === 0) {
        util.showSuccess('已取消报名')

        // Update state
        this.setData({
          isRegistered: false,
          registration: null,
          event: {
            ...this.data.event,
            currentParticipants: this.data.event.currentParticipants - 1
          }
        })
      } else {
        util.showError(res.message || '取消失败')
      }
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '取消失败')
    }
  },

  // Navigate to ranking
  navigateToRanking() {
    const type = this.data.event.type
    wx.navigateTo({
      url: `/pages/ranking/ranking?type=${type}`
    })
  },

  // Share event
  onShareAppMessage() {
    const event = this.data.event
    return {
      title: event.title,
      path: `/pages/event-detail/event-detail?id=${event.id}`,
      imageUrl: event.bannerUrl
    }
  }
})
