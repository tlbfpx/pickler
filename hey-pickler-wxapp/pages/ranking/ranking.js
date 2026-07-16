// Ranking page
import request from '../../utils/request'
import util from '../../utils/util'
import { TERMS, TIER_NAME } from '../../utils/terms'
import brand from '../../utils/brand'

Page({
  data: {
    type: 'STAR', // STAR or PARTY
    tier: 'ALL', // ALL, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER
    tabs: [TERMS.STAR.type, TERMS.PARTY.type],
    tierTabs: ['全部', TIER_NAME.BRONZE, TIER_NAME.SILVER, TIER_NAME.GOLD, TIER_NAME.PLATINUM, TIER_NAME.DIAMOND, TIER_NAME.MASTER],
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

  onShow() {
    brand.applyChrome()
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

    const tierMap = ['ALL', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER']
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
        const data = res.data || {}
        const newRankings = data.list || []
        const rankings = refresh ? newRankings : [...this.data.rankings, ...newRankings]

        const next = {
          rankings,
          page: page + 1,
          hasMore: newRankings.length >= this.data.size,
          loading: false
        }

        // 段位筛选 tab：后端 tierNameMap（per-track 双轨）刷新标签，顺序 BRONZE..MASTER
        // 与 switchTierTab 的 index↔tier_code 映射一致；缺失（旧后端/离线）回退初始 STAR 标签
        const tierNameMap = data.tierNameMap
        if (tierNameMap && tierNameMap.BRONZE && tierNameMap.MASTER) {
          next.tierTabs = ['全部',
            tierNameMap.BRONZE, tierNameMap.SILVER, tierNameMap.GOLD,
            tierNameMap.PLATINUM, tierNameMap.DIAMOND, tierNameMap.MASTER]
        }

        this.setData(next)
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
