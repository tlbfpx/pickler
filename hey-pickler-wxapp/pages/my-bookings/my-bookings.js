// 我的预约(P2)
import request from '../../utils/request'
import util from '../../utils/util'

const STATUS_LABEL = {
  CONFIRMED: '待履约',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
  NO_SHOW: '未到'
}
const STATUS_COLOR = {
  CONFIRMED: '#4CAF50',
  CANCELLED: '#999',
  COMPLETED: '#2196F3',
  NO_SHOW: '#FF9800'
}

Page({
  data: {
    group: 'upcoming',          // upcoming | history
    list: [],
    page: 1,
    size: 10,
    hasMore: false,
    loading: false,
    refreshing: false,
    cancelingId: null
  },

  onLoad(options) {
    const group = (options && options.group) || 'upcoming'
    if (group !== 'upcoming' && group !== 'history') {
      this.setData({ group: 'upcoming' })
    } else {
      this.setData({ group })
    }
    this.load(true)
  },

  onShow() {
    // 回到我的预约页时刷新(下单/取消成功后回到此页)
    this.load(true)
  },

  onPullDownRefresh() {
    this.setData({ refreshing: true })
    this.load(true).finally(() => {
      this.setData({ refreshing: false })
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.load(false)
  },

  switchGroup(e) {
    const g = e.currentTarget.dataset.group
    if (g === this.data.group) return
    this.setData({ group: g, list: [], page: 1, hasMore: false })
    this.load(true)
  },

  async load(refresh) {
    if (this.data.loading) return
    this.setData({ loading: true })
    const page = refresh ? 1 : this.data.page
    try {
      const res = await request.get('/bookings/my', {
        group: this.data.group,
        page,
        size: this.data.size
      })
      if (res.code === 0) {
        const items = (res.data.list || []).map((b) => ({
          ...b,
          _statusLabel: STATUS_LABEL[b.status] || b.status,
          _statusColor: STATUS_COLOR[b.status] || '#999',
          _priceText: util.formatPrice(b.priceSnapshot),
          _startText: (b.slotStart || '').slice(11, 16),
          _endText: (b.slotEnd || '').slice(11, 16)
        }))
        const newList = refresh ? items : [...this.data.list, ...items]
        this.setData({
          list: newList,
          page: page + 1,
          hasMore: items.length >= this.data.size,
          loading: false
        })
      } else {
        this.setData({ loading: false })
        util.showError(res.message || '加载失败')
      }
    } catch (error) {
      console.error('Load my bookings failed:', error)
      this.setData({ loading: false })
    }
  },

  onCancelBooking(e) {
    const id = e.currentTarget.dataset.id
    const no = e.currentTarget.dataset.no
    util.showConfirm({
      title: '取消预约',
      content: '确认取消订单「' + no + '」?截止前可免费取消',
      confirmText: '确认取消',
      success: async (res) => {
        if (!res.confirm) return
        this.setData({ cancelingId: id })
        try {
          const r = await request.post('/bookings/' + id + '/cancel')
          if (r.code === 0) {
            util.showSuccess('已取消')
            this.load(true)
          } else {
            util.showError(r.message || '取消失败')
          }
        } catch (err) {
          console.error('Cancel failed:', err)
          util.showError('网络异常')
        } finally {
          this.setData({ cancelingId: null })
        }
      }
    })
  },

  onTapVenue() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
