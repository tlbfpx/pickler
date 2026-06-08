// Ranking page
import request from '../../utils/request'
import util from '../../utils/util'

Page({
  data: {
    type: 'STAR', // STAR or PARTY
    tier: 'ALL', // ALL, LEGEND, SUPER, SHINING
    tabs: ['Star 排名', 'Party 排名'],
    tierTabs: ['全部', 'Legend', 'Super', 'Shining'],
    currentTab: 0,
    currentTierTab: 0,
    rankings: [],
    loading: false,
    hasMore: true,
    page: 1,
    size: 20
  },

  onLoad(options) {
    const type = options.type || 'STAR'
    this.setData({
      type,
      currentTab: type === 'STAR' ? 0 : 1
    })
    this.loadRankings(true)
  },

  onPullDownRefresh() {
    this.loadRankings(true)
    wx.stopPullDownRefresh()
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadRankings()
    }
  },

  // Switch type tab (Star/Party)
  switchTypeTab(e) {
    const index = e.currentTarget.dataset.index
    if (index === this.data.currentTab) return

    const type = index === 0 ? 'STAR' : 'PARTY'

    this.setData({
      currentTab: index,
      type,
      tier: 'ALL',
      currentTierTab: 0,
      rankings: [],
      page: 1,
      hasMore: true
    })

    this.loadRankings(true)
  },

  // Switch tier tab
  switchTierTab(e) {
    const index = e.currentTarget.dataset.index
    if (index === this.data.currentTierTab) return

    const tierMap = ['ALL', 'LEGEND', 'SUPER', 'SHINING']
    const tier = tierMap[index]

    this.setData({
      currentTierTab: index,
      tier,
      rankings: [],
      page: 1,
      hasMore: true
    })

    this.loadRankings(true)
  },

  // Load rankings
  async loadRankings(refresh = false) {
    if (this.data.loading) return

    this.setData({
      loading: true
    })

    try {
      const page = refresh ? 1 : this.data.page

      const params = {
        type: this.data.type,
        page,
        size: this.data.size
      }

      if (this.data.tier !== 'ALL') {
        params.tier = this.data.tier
      }

      const res = await request.get('/rankings', params)

      if (res.code === 0) {
        const newRankings = res.data.list || []
        const rankings = refresh ? newRankings : [...this.data.rankings, ...newRankings]

        this.setData({
          rankings,
          page: page + 1,
          hasMore: newRankings.length >= this.data.size,
          loading: false
        })
      } else {
        this.setData({
          loading: false
        })
      }
    } catch (error) {
      console.error('Load rankings failed:', error)
      this.setData({
        loading: false
      })
    }
  },

  // Handle ranking item tap
  handleRankingTap(e) {
    const { user, rank } = e.detail
    console.log('Ranking item tapped:', user, rank)
  }
})
