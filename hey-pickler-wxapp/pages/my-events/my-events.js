// My events page
import request from '../../utils/request'
import auth from '../../utils/auth'
import util from '../../utils/util'
import { TERMS } from '../../utils/terms'

Page({
  data: {
    type: 'ALL', // ALL, STAR, PARTY
    tabs: ['全部', TERMS.STAR.type, TERMS.PARTY.type],
    currentTab: 0,
    events: [],
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
      page: 1,
      hasMore: true
    })

    this.loadMyEvents(true)
  },

  // Load my events
  async loadMyEvents(refresh = false) {
    if (this.data.loading) return

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
        const newEvents = res.data.list || []
        const events = refresh ? newEvents : [...this.data.events, ...newEvents]

        this.setData({
          events,
          page: page + 1,
          hasMore: newEvents.length >= this.data.size,
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
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/event-detail/event-detail?id=${id}`
    })
  }
})
