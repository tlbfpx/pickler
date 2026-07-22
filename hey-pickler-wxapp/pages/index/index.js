// Index page - Home
import request from '../../utils/request'
import auth from '../../utils/auth'
import util from '../../utils/util'
import { TERMS } from '../../utils/terms'
import brand from '../../utils/brand'

Page({
  data: {
    banners: [],
    events: [],
    currentTab: 0, // 0: Star, 1: Party
    tabs: [TERMS.STAR.type, TERMS.PARTY.type],
    loading: false,
    hasMore: true,
    page: 1,
    size: 10
  },

  onLoad() {
    this.loadBanners()
    this.loadEvents()
  },

  onShow() {
    brand.applyChrome()
    // Refresh data when page shows
    if (this.data.events.length > 0) {
      this.loadEvents(true)
    }
  },

  onPullDownRefresh() {
    this.loadBanners()
    this.loadEvents(true)
    wx.stopPullDownRefresh()
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadEvents()
    }
  },

  // Load banners
  async loadBanners() {
    try {
      const res = await request.get('/banners')
      if (res.code === 0) {
        this.setData({
          banners: res.data || []
        })
      }
    } catch (error) {
      console.error('Load banners failed:', error)
    }
  },

  // Load events
  async loadEvents(refresh = false) {
    if (this.data.loading) return

    this.setData({
      loading: true
    })

    try {
      const page = refresh ? 1 : this.data.page
      const type = this.data.currentTab === 0 ? 'STAR' : 'PARTY'

      const res = await request.get('/events', {
        type,
        status: 'OPEN',
        page,
        size: this.data.size
      })

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
      console.error('Load events failed:', error)
      this.setData({
        loading: false
      })
    }
  },

  // Switch tab
  switchTab(e) {
    const index = e.currentTarget.dataset.index
    if (index === this.data.currentTab) return

    this.setData({
      currentTab: index,
      events: [],
      page: 1,
      hasMore: true
    })

    this.loadEvents(true)
  },

  // Navigate to event detail
  navigateToDetail(e) {
    const { event } = e.detail
    wx.navigateTo({
      url: `/pages/event-detail/event-detail?id=${event.id}`
    })
  },

  // Register for event
  handleRegister(e) {
    const { event } = e.detail

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

    // Navigate to event detail for registration
    wx.navigateTo({
      url: `/pages/event-detail/event-detail?id=${event.id}&action=register`
    })
  },

  // Banner tap
  handleBannerTap(e) {
    const { banner } = e.currentTarget.dataset
    if (banner.linkUrl) {
      wx.navigateTo({
        url: banner.linkUrl
      })
    }
  },

  // Banner image failed to load (e.g. image host not whitelisted in the Mini Program) → fallback
  onBannerError(e) {
    const { index } = e.currentTarget.dataset
    if (index === undefined) return
    this.setData({
      [`banners[${index}].imageUrl`]: '/images/default-event.png'
    })
  },

  // Navigate to venue booking list (venue browse)
  goVenues() {
    wx.navigateTo({
      url: '/pages/venue-list/venue-list'
    })
  }
})
