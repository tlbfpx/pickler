// Venue detail page (venue booking browse — read only, P1)
import request from '../../utils/request'
import util from '../../utils/util'

Page({
  data: {
    venueId: null,
    venue: null,
    businessHours: [],
    courts: [],
    selectedCourtId: null,
    dates: [],          // [{ value:'YYYY-MM-DD', label:'今天'|'MM-DD', weekday:'周一' }]
    selectedDate: '',
    slots: [],
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

  // Build a horizontal strip of the next N days starting today
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

  // Fetch venue detail (venue + businessHours + courts)
  async loadVenue(id) {
    if (this.data.loadingVenue) return
    this.setData({ loadingVenue: true })
    try {
      const res = await request.get(`/venues/${id}`)
      if (res.code === 0 && res.data) {
        const venue = res.data
        const courts = venue.courts || []
        // Auto-select the first ACTIVE court so slots render immediately
        const firstActive = courts.find((c) => c.status === 'ACTIVE')
        const selectedCourtId = firstActive ? firstActive.id : null
        this.setData({
          venue,
          businessHours: venue.businessHours || [],
          courts,
          selectedCourtId,
          loadingVenue: false
        })
        if (selectedCourtId) {
          this.loadSlots(selectedCourtId, this.data.selectedDate)
        }
      } else {
        this.setData({ loadingVenue: false })
        util.showError((res && res.message) || '加载失败')
      }
    } catch (error) {
      console.error('Load venue failed:', error)
      this.setData({ loadingVenue: false })
    }
  },

  // Fetch slots for a court + date
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

  // Court selected (from court-card component)
  onSelectCourt(e) {
    const court = e.detail.court
    if (!court || court.id === this.data.selectedCourtId) return
    this.setData({ selectedCourtId: court.id })
    this.loadSlots(court.id, this.data.selectedDate)
  },

  // Date selected (from date strip)
  onSelectDate(e) {
    const date = e.currentTarget.dataset.date
    if (!date || date === this.data.selectedDate) return
    this.setData({ selectedDate: date })
    if (this.data.selectedCourtId) {
      this.loadSlots(this.data.selectedCourtId, date)
    }
  }
})
