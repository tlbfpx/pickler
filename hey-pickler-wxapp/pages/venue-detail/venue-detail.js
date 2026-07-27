// Venue detail page (P2 — 连续多格预约)
import request from '../../utils/request'
import util from '../../utils/util'
import auth from '../../utils/auth'

// 与后端 BookingCreateRequest.@Max(8) 对齐:单次最多连订 8 格
const MAX_SLOTS = 8

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
    selectedStarts: [],      // 已选 slot 的 start(ISO 字符串,有序连续)
    selectedCount: 0,
    selectedPriceText: '',
    selectedRangeText: '',
    submitting: false,
    pendingSlot: null,       // 确认弹窗汇总对象
    confirmVisible: false,
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
    // 切换场地/日期或重新加载 → 清空已选(slots 集合变了,旧选择失效)
    this.setData({ loadingSlots: true, slots: [], selectedStarts: [], selectedCount: 0, selectedPriceText: '', selectedRangeText: '' })
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
    this.setData({ selectedCourtId: court.id, selectedStarts: [], selectedCount: 0, selectedPriceText: '', selectedRangeText: '' })
    this.loadSlots(court.id, this.data.selectedDate)
  },

  onSelectDate(e) {
    const date = e.currentTarget.dataset.date
    if (!date || date === this.data.selectedDate) return
    this.setData({ selectedDate: date, selectedStarts: [], selectedCount: 0, selectedPriceText: '', selectedRangeText: '' })
    if (this.data.selectedCourtId) this.loadSlots(this.data.selectedCourtId, date)
  },

  // 点格:已选→截断尾部;未选→仅当与当前块首尾相邻才扩展,否则重置为该格
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

    const slotMinutes = this.currentSlotMinutes()
    const stepMs = slotMinutes * 60 * 1000
    const starts = this.data.selectedStarts.slice()
    const idx = starts.indexOf(slot.start)

    if (idx >= 0) {
      // 已选:从该格起到末尾全部移除,保持剩余前缀连续
      starts.splice(idx)
    } else {
      if (starts.length >= MAX_SLOTS) {
        util.showError('单次最多连订 ' + MAX_SLOTS + ' 格')
        return
      }
      if (starts.length === 0) {
        starts.push(slot.start)
      } else {
        const tapMs = Date.parse(slot.start)
        const earliestMs = Date.parse(starts[0])
        const latestMs = Date.parse(starts[starts.length - 1])
        if (tapMs === earliestMs - stepMs || tapMs === latestMs + stepMs) {
          starts.push(slot.start)
        } else {
          // 不相邻 → 重置选择为该格(开启新块)
          starts.splice(0, starts.length, slot.start)
        }
      }
    }
    starts.sort()
    this.applySelection(starts)
  },

  // 由 starts 同步 slots._selected 标记 + 选择条汇总(价格/区间/计数)
  applySelection(starts) {
    const updatedSlots = this.data.slots.map((s) => ({
      ...s,
      _selected: starts.indexOf(s.start) >= 0
    }))
    const picked = starts
      .map((s) => updatedSlots.find((x) => x.start === s))
      .filter(Boolean)
    if (picked.length === 0) {
      this.setData({
        slots: updatedSlots,
        selectedStarts: [],
        selectedCount: 0,
        selectedPriceText: '',
        selectedRangeText: ''
      })
      return
    }
    const totalPrice = picked.reduce((sum, s) => sum + (Number(s.price) || 0), 0)
    const first = picked[0]
    const last = picked[picked.length - 1]
    const rangeText = util.formatSlotTime(first.start) + '-' +
                      (last.end ? util.formatSlotTime(last.end) : '')
    this.setData({
      slots: updatedSlots,
      selectedStarts: starts,
      selectedCount: picked.length,
      selectedPriceText: util.formatPrice(totalPrice),
      selectedRangeText: rangeText
    })
  },

  clearSelection() {
    if (this.data.selectedStarts.length === 0 && this.data.slots.every((s) => !s._selected)) return
    const updatedSlots = this.data.slots.map((s) => ({ ...s, _selected: false }))
    this.setData({
      slots: updatedSlots,
      selectedStarts: [],
      selectedCount: 0,
      selectedPriceText: '',
      selectedRangeText: ''
    })
  },

  currentSlotMinutes() {
    const c = (this.data.courts || []).find((x) => x.id === this.data.selectedCourtId)
    return c && c.slotMinutes ? c.slotMinutes : 60
  },

  // 点选择条"确认预约"→ 组装 pendingSlot 并弹确认框
  onConfirmBook() {
    const starts = this.data.selectedStarts
    if (!starts || starts.length === 0) return
    const slots = this.data.slots
    const picked = starts.map((s) => slots.find((x) => x.start === s)).filter(Boolean)
    if (picked.length !== starts.length) {
      // slots 已刷新、所选格不见了
      util.showError('所选时段已变化,请重选')
      this.clearSelection()
      return
    }
    const court = (this.data.courts || []).find((c) => c.id === this.data.selectedCourtId)
    const totalPrice = picked.reduce((sum, s) => sum + (Number(s.price) || 0), 0)
    const first = picked[0]
    const last = picked[picked.length - 1]
    this.setData({
      pendingSlot: {
        courtId: this.data.selectedCourtId,
        courtName: court ? court.name : '',
        venueName: this.data.venue ? this.data.venue.name : '',
        date: this.data.selectedDate,
        start: util.formatSlotTime(first.start),
        end: last.end ? util.formatSlotTime(last.end) : '',
        slotsCount: picked.length,
        slotStartIso: first.start,        // 原始 ISO 直传后端,避免字符串拼接出错
        priceText: util.formatPrice(totalPrice)
      },
      confirmVisible: true
    })
  },

  onConfirmCancel() {
    this.setData({ confirmVisible: false, pendingSlot: null })
  },

  // 确认下单(连续 N 格一次 POST)
  async onConfirmOk() {
    const slot = this.data.pendingSlot
    if (!slot || this.data.submitting) return
    this.setData({ submitting: true })
    try {
      const res = await request.post('/bookings', {
        courtId: slot.courtId,
        slotStart: slot.slotStartIso,
        slotsCount: slot.slotsCount
      })
      this.setData({ confirmVisible: false })
      if (res.code === 0) {
        util.showSuccess('预约成功')
        this.clearSelection()
        // 刷新 slots 让已订格立刻变不可订
        this.loadSlots(this.data.selectedCourtId, this.data.selectedDate)
        wx.navigateTo({ url: '/pages/my-bookings/my-bookings?group=upcoming' })
      } else if (res.code === 1012) {
        util.showError('部分时段刚被占用')
        this.clearSelection()
        this.loadSlots(this.data.selectedCourtId, this.data.selectedDate)
      } else if (res.code === 1015) {
        util.showError('您的有效预约数已达上限')
      } else if (res.code === 1011 || res.code === 1006 || res.code === 1013) {
        util.showError(res.message || '所选时段不可预约')
        this.clearSelection()
        this.loadSlots(this.data.selectedCourtId, this.data.selectedDate)
      } else {
        util.showError((res && res.message) || '预约失败')
      }
    } catch (error) {
      console.error('Create booking failed:', error)
      util.showError('网络异常,请重试')
      this.setData({ confirmVisible: false })
    } finally {
      this.setData({ submitting: false, pendingSlot: null })
    }
  }
})
