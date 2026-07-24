// Venue detail page (P2 — adds one-tap booking confirmation)
import request from '../../utils/request'
import util from '../../utils/util'
import auth from '../../utils/auth'

Page({
  data: {
    venueId: null,
    venue: null,
    businessHours: [],
    courts: [],
    selectedCourtId: null,
    dates: [],
    selectedDate: '',
    slots: [],
    pendingSlot: null,        // 当前选中的格(slot 对象),展开 confirm 显示
    confirmVisible: false,    // 控制 wx.showModal 的 hidden(模拟弹窗)
    loadingVenue: false,
    loadingSlots: false
  },

  onLoad(options) {
    const id = options && options.id
    if (!id) {
      util.showError('缺少场馆参数')
      return
    }
    this.setData({
      venueId: id,
      dates: this.buildDateStrip(7),
      selectedDate: util.formatDate(new Date(), 'YYYY-MM-DD')
    })
    this.loadVenue(id)
  },

  buildDateStrip(days) {
    const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    const result = []
    for (let i = 0; i < days; i++) {
      const d = new Date()
      d.setDate(d.getDate() + i)
      const value = util.formatDate(d, 'YYYY-MM-DD')
      result.push({
        value,
        label: i === 0 ? '今天' : (i === 1 ? '明天' : util.formatDate(d, 'MM-DD')),
        weekday: weekdays[d.getDay()]
      })
    }
    return result
  },

  async loadVenue(id) {
    if (this.data.loadingVenue) return
    this.setData({ loadingVenue: true })
    try {
      const res = await request.get(`/venues/${id}`)
      if (res.code === 0 && res.data) {
        const venue = res.data
        const courts = venue.courts || []
        const firstActive = courts.find((c) => c.status === 'ACTIVE')
        const selectedCourtId = firstActive ? firstActive.id : null
        this.setData({ venue, businessHours: venue.businessHours || [], courts, selectedCourtId, loadingVenue: false })
        if (selectedCourtId) this.loadSlots(selectedCourtId, this.data.selectedDate)
      } else {
        this.setData({ loadingVenue: false })
        util.showError((res && res.message) || '加载失败')
      }
    } catch (error) {
      console.error('Load venue failed:', error)
      this.setData({ loadingVenue: false })
    }
  },

  async loadSlots(courtId, date) {
    if (!courtId || !date) return
    this.setData({ loadingSlots: true, slots: [] })
    try {
      const res = await request.get(`/courts/${courtId}/slots`, { date })
      if (res.code === 0) {
        this.setData({ slots: res.data || [], loadingSlots: false })
      } else {
        this.setData({ loadingSlots: false })
      }
    } catch (error) {
      console.error('Load slots failed:', error)
      this.setData({ loadingSlots: false })
    }
  },

  onSelectCourt(e) {
    const court = e.detail.court
    if (!court || court.id === this.data.selectedCourtId) return
    this.setData({ selectedCourtId: court.id })
    this.loadSlots(court.id, this.data.selectedDate)
  },

  onSelectDate(e) {
    const date = e.currentTarget.dataset.date
    if (!date || date === this.data.selectedDate) return
    this.setData({ selectedDate: date })
    if (this.data.selectedCourtId) this.loadSlots(this.data.selectedCourtId, date)
  },

  // P2:点可订格 → 一键确认弹窗
  onSlotTap(e) {
    if (!auth.isLoggedIn()) {
      util.showConfirm({
        title: '提示',
        content: '请先登录后再预约',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) wx.navigateTo({ url: '/pages/login/login' })
        }
      })
      return
    }
    const slot = e.currentTarget.dataset.slot
    if (!slot || slot.available !== true) return
    const court = (this.data.courts || []).find((c) => c.id === this.data.selectedCourtId)
    this.setData({
      pendingSlot: {
        courtId: this.data.selectedCourtId,
        courtName: court ? court.name : '',
        venueName: this.data.venue ? this.data.venue.name : '',
        start: slot.start.slice(11, 16),
        end: slot.end ? slot.end.slice(11, 16) : '',
        date: this.data.selectedDate,
        priceText: util.formatPrice(slot.price),
        price: Number(slot.price)
      },
      confirmVisible: true
    })
  },

  // 取消弹窗
  onConfirmCancel() {
    this.setData({ confirmVisible: false, pendingSlot: null })
  },

  // 确认下单
  async onConfirmOk() {
    const slot = this.data.pendingSlot
    if (!slot) return
    try {
      const res = await request.post('/bookings', {
        courtId: slot.courtId,
        slotStart: slot.date + 'T' + slot.start + ':00',
        slotsCount: 1
      })
      // 关闭弹窗
      this.setData({ confirmVisible: false })
      if (res.code === 0) {
        util.showSuccess('预约成功')
        // 刷新 slots 让该格立刻变不可订
        this.loadSlots(this.data.selectedCourtId, this.data.selectedDate)
        wx.navigateTo({ url: '/pages/my-bookings/my-bookings?group=upcoming' })
      } else if (res.code === 1012) {
        util.showError('该时段刚被占用')
        this.loadSlots(this.data.selectedCourtId, this.data.selectedDate)
      } else if (res.code === 1015) {
        util.showError('您的有效预约数已达上限')
      } else if (res.code === 1011 || res.code === 1006) {
        util.showError(res.message || '该时段不可预约')
      } else {
        util.showError((res && res.message) || '预约失败')
      }
    } catch (error) {
      console.error('Create booking failed:', error)
      util.showError('网络异常,请重试')
      this.setData({ confirmVisible: false })
    } finally {
      this.setData({ pendingSlot: null })
    }
  }
})
