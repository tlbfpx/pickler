// My events page
import request from '../../utils/request'
import auth from '../../utils/auth'
import util from '../../utils/util'

Page({
  data: {
    type: 'ALL', // ALL, STAR, PARTY
    tabs: ['全部', '明星赛事', '派对活动'],
    currentTab: 0,
    events: [],
    registrations: [],
    loading: false,
    hasMore: true,
    page: 1,
    size: 10
  },

  onLoad(options) {
    const type = options.type || 'ALL'
    this.setData({
      type,
      currentTab: type === 'STAR' ? 1 : type === 'PARTY' ? 2 : 0
    })
    this.loadMyEvents(true)
  },

  onShow() {
    // Refresh when page shows
    if (this.data.events.length > 0) {
      this.loadMyEvents(true)
    }
  },

  onPullDownRefresh() {
    this.loadMyEvents(true)
    wx.stopPullDownRefresh()
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadMyEvents()
    }
  },

  // Switch tab
  switchTab(e) {
    const index = e.currentTarget.dataset.index
    if (index === this.data.currentTab) return

    const typeMap = ['ALL', 'STAR', 'PARTY']
    const type = typeMap[index]

    this.setData({
      currentTab: index,
      type,
      events: [],
      registrations: [],
      page: 1,
      hasMore: true
    })

    this.loadMyEvents(true)
  },

  // Load my events
  async loadMyEvents(refresh = false) {
    if (this.data.loading) return

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

    this.setData({
      loading: true
    })

    try {
      const page = refresh ? 1 : this.data.page

      const params = {
        page,
        size: this.data.size
      }

      if (this.data.type !== 'ALL') {
        params.type = this.data.type
      }

      const res = await request.get('/user/events', params)

      if (res.code === 0) {
        const newRegistrations = res.data.list || []
        const registrations = refresh ? newRegistrations : [...this.data.registrations, ...newRegistrations]

        // Extract events from registrations
        const events = registrations.map(reg => reg.event).filter(e => e)

        this.setData({
          registrations,
          events,
          page: page + 1,
          hasMore: newRegistrations.length >= this.data.size,
          loading: false
        })
      } else {
        this.setData({
          loading: false
        })
      }
    } catch (error) {
      console.error('Load my events failed:', error)
      this.setData({
        loading: false
      })
    }
  },

  // Navigate to event detail
  navigateToDetail(e) {
    const { event } = e.detail
    wx.navigateTo({
      url: `/pages/event-detail/event-detail?id=${event.id}`
    })
  },

  // Get registration status text
  getRegistrationStatus(registration) {
    if (!registration) return ''

    const statusMap = {
      REGISTERED: '已报名',
      CHECKED_IN: '已签到',
      WITHDRAWN: '已取消'
    }
    return statusMap[registration.status] || registration.status
  },

  // Get registration status color
  getRegistrationStatusColor(registration) {
    if (!registration) return '#999'

    const colorMap = {
      REGISTERED: '#4CAF50',
      CHECKED_IN: '#2196F3',
      WITHDRAWN: '#999'
    }
    return colorMap[registration.status] || '#999'
  }
})
