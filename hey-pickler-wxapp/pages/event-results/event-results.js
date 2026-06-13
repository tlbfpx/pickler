import request from '../../utils/request'
import util from '../../utils/util'

Page({
  data: {
    eventId: null,
    results: [],
    loading: true
  },

  onLoad(options) {
    const { id } = options
    if (!id) {
      util.showError('赛事不存在')
      setTimeout(() => wx.navigateBack(), 1500)
      return
    }
    this.setData({ eventId: id })
    this.loadResults()
  },

  onPullDownRefresh() {
    this.loadResults().finally(() => wx.stopPullDownRefresh())
  },

  async loadResults() {
    this.setData({ loading: true })
    try {
      const res = await request.get(`/events/${this.data.eventId}/results`)
      if (res.code === 0) {
        this.setData({
          results: res.data || [],
          loading: false
        })
      } else {
        util.showError(res.message || '加载失败')
        this.setData({ loading: false })
      }
    } catch (error) {
      util.showError('加载失败')
      this.setData({ loading: false })
    }
  },

  formatMatchType(type) {
    const map = { SINGLES: '单打', DOUBLES: '双打', MIXED: '混双' }
    return map[type] || type
  }
})
