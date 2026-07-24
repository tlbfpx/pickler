// Venue list page (venue booking browse, anonymous)
import request from '../../utils/request'

Page({
  data: {
    venues: [],
    keyword: '',
    loading: false,
    hasMore: true,
    page: 1,
    size: 10
  },

  onLoad() {
    this.loadVenues(true)
  },

  onPullDownRefresh() {
    this.loadVenues(true)
    wx.stopPullDownRefresh()
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadVenues()
    }
  },

  // Load venues (refresh=true resets to page 1)
  async loadVenues(refresh = false) {
    if (this.data.loading) return
    this.setData({ loading: true })

    try {
      const page = refresh ? 1 : this.data.page
      const res = await request.get('/venues', {
        keyword: this.data.keyword,
        page,
        size: this.data.size,
        status: 'ACTIVE'
      })

      if (res.code === 0) {
        const list = (res.data && res.data.list) || []
        const venues = refresh ? list : [...this.data.venues, ...list]
        this.setData({
          venues,
          page: page + 1,
          hasMore: list.length >= this.data.size,
          loading: false
        })
      } else {
        this.setData({ loading: false })
      }
    } catch (error) {
      console.error('Load venues failed:', error)
      this.setData({ loading: false })
    }
  },

  // Search input
  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  // Trigger search
  onSearch() {
    this.setData({ venues: [], page: 1, hasMore: true })
    this.loadVenues(true)
  },

  // Navigate to venue detail
  goDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({
      url: `/pages/venue-detail/venue-detail?id=${id}`
    })
  }
})
