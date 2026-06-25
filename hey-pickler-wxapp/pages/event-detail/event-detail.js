// Event detail page
import request from '../../utils/request'
import auth from '../../utils/auth'
import util from '../../utils/util'
import { FORMAT } from '../../utils/terms'

Page({
  data: {
    eventId: null,
    event: null,
    loading: true,
    isRegistered: false,
    registration: null,
    action: null, // 'register' from navigation
    isRegistrationOpen: false,
    insufficientPoints: false,
    pointsGap: 0,
    myTeam: null,
    isInvitee: false,
    formatText: '',
    formatColor: '#9CA3AF'
  },

  onLoad(options) {
    const { id, action } = options
    if (id) {
      this.setData({
        eventId: id,
        action: action || null
      })
      this.loadEventDetail()
    } else {
      util.showError('赛事不存在')
      setTimeout(() => {
        wx.navigateBack()
      }, 1500)
    }
  },

  onShow() {
    // Refresh when page shows
    if (this.data.eventId) {
      this.loadEventDetail()
    }
  },

  onPullDownRefresh() {
    this.loadEventDetail()
    wx.stopPullDownRefresh()
  },

  // Load event detail
  async loadEventDetail() {
    this.setData({
      loading: true
    })

    try {
      const res = await request.get(`/events/${this.data.eventId}`)

      if (res.code === 0) {
        const event = res.data
        const isRegistrationOpen = util.isRegistrationOpen(event)
        const formatText = event.format && FORMAT[event.format] ? FORMAT[event.format].label : ''
        const formatColor = event.format && FORMAT[event.format] ? FORMAT[event.format].color : '#9CA3AF'

        this.setData({
          event,
          loading: false,
          isRegistrationOpen,
          formatText,
          formatColor
        })

        // Check if user is registered
        if (auth.isLoggedIn()) {
          await this.checkRegistration()
          await this.checkPointsEligibility(event)
          await this.checkMyTeam(event)
        }

        // Handle action from navigation
        if (this.data.action === 'register' && isRegistrationOpen && !this.data.isRegistered) {
          this.handleRegister()
        }
      } else {
        util.showError(res.message || '加载失败')
        this.setData({
          loading: false
        })
      }
    } catch (error) {
      util.showError('加载失败')
      this.setData({
        loading: false
      })
    }
  },

  // Check if user is registered
  async checkRegistration() {
    try {
      const res = await request.get('/user/events', { page: 1, size: 100 })

      if (res.code === 0 && res.data.list) {
        const registration = res.data.list.find(r => r.id == this.data.eventId)
        if (registration) {
          this.setData({
            isRegistered: true,
            registration
          })
        }
      }
    } catch (error) {
      console.error('Check registration failed:', error)
    }
  },

  // Load caller's team in this event (for doubles/mixed) and determine whether
  // they're the invited partner (member2) so they can accept/decline.
  async checkMyTeam(event) {
    if (!event || event.format === 'SINGLES') {
      this.setData({ myTeam: null, isInvitee: false })
      return
    }
    try {
      const res = await request.get(`/events/${this.data.eventId}/my-team`)
      if (res.code === 0 && res.data) {
        const team = res.data
        const userId = auth.getUserId()
        this.setData({
          myTeam: team,
          isInvitee: userId && team.member2UserId === userId && team.status === 'PENDING'
        })
      } else {
        this.setData({ myTeam: null, isInvitee: false })
      }
    } catch (error) {
      console.error('Check my-team failed:', error)
    }
  },

  // Check if user has enough points to register
  async checkPointsEligibility(event) {
    const minPoints = event.minPoints || 0
    if (minPoints <= 0) {
      this.setData({ insufficientPoints: false, pointsGap: 0 })
      return
    }
    try {
      const res = await request.get('/user/profile')
      if (res.code === 0 && res.data) {
        const userPoints = event.type === 'STAR'
          ? (res.data.starPoints || 0)
          : (res.data.partyPoints || 0)
        const insufficient = userPoints < minPoints
        this.setData({
          insufficientPoints: insufficient,
          pointsGap: insufficient ? (minPoints - userPoints) : 0
        })
      }
    } catch (error) {
      console.error('Check points eligibility failed:', error)
    }
  },

  // Register for event
  async handleRegister() {
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

    // Check if already registered
    if (this.data.isRegistered) {
      util.showError('您已报名该赛事')
      return
    }

    // Check if registration is open
    if (!this.data.isRegistrationOpen) {
      util.showError('报名已截止或已满员')
      return
    }

    // Format-driven flow: server forces matchType = event.format.
    // SINGLES: direct. DOUBLES/MIXED: ask for teammate userId (captain initiates).
    const format = this.data.event.format || 'SINGLES'

    let body = {}
    if (format === 'DOUBLES' || format === 'MIXED') {
      const partnerUserId = await new Promise((resolve) => {
        wx.showModal({
          title: '输入队友 userId',
          content: `${format === 'DOUBLES' ? '双打' : '混打'}需要 2 人组队。请输入队友的用户 id（您将作为队长发起邀请，队友确认后成队）`,
          editable: true,
          placeholderText: '队友 userId',
          success: (r) => {
            if (!r.confirm) return resolve(null)
            const id = parseInt(r.content, 10)
            if (!id || isNaN(id)) return resolve(null)
            resolve(id)
          },
          fail: () => resolve(null)
        })
      })
      if (!partnerUserId) {
        util.showError('请输入有效的队友 userId')
        return
      }
      body = { partnerUserId }
    } else {
      body = { matchType: 'SINGLES' }
    }

    // Confirm registration
    const confirmed = await util.showConfirm({
      title: '确认报名',
      content: `确定要报名参加「${this.data.event.title}」吗？`
    })

    if (!confirmed) return

    util.showLoading('报名中...')

    try {
      const res = await request.post(`/events/${this.data.eventId}/register`, body)

      util.hideLoading()

      if (res.code === 0) {
        if (format === 'DOUBLES' || format === 'MIXED') {
          util.showSuccess('组队邀请已发出，等待队友确认')
        } else {
          util.showSuccess('报名成功')
        }

        // Update state
        this.setData({
          isRegistered: true,
          registration: res.data,
          event: {
            ...this.data.event,
            currentParticipants: this.data.event.currentParticipants + 1
          }
        })
        // Re-fetch my-team so the team card shows PENDING state
        this.checkMyTeam(this.data.event)

        // Check if event is now full
        if (this.data.event.currentParticipants >= this.data.event.maxParticipants) {
          this.setData({
            isRegistrationOpen: false,
            event: {
              ...this.data.event,
              status: 'FULL'
            }
          })
        }
      } else {
        util.showError(res.message || '报名失败')
      }
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '报名失败')
    }
  },

  // Partner accepts a pending team invite — registers as team member2.
  async handleConfirmTeam() {
    if (!this.data.myTeam) return
    const confirmed = await util.showConfirm({
      title: '接受邀请',
      content: `确认加入「${this.data.myTeam.name || '该队伍'}」吗？`
    })
    if (!confirmed) return
    util.showLoading('处理中...')
    try {
      const res = await request.post(`/events/${this.data.eventId}/register`, {
        teamId: this.data.myTeam.id
      })
      util.hideLoading()
      if (res.code === 0) {
        util.showSuccess('已加入队伍')
        await this.loadEventDetail()
      } else {
        util.showError(res.message || '加入失败')
      }
    } catch (e) {
      util.hideLoading()
      util.showError(e.message || '加入失败')
    }
  },

  // Partner declines a pending team invite — server deletes the team + captain reg.
  async handleDeclineTeam() {
    if (!this.data.myTeam) return
    const confirmed = await util.showConfirm({
      title: '拒绝邀请',
      content: '拒绝后将解散该队伍（队长需重新组队），是否继续？'
    })
    if (!confirmed) return
    util.showLoading('处理中...')
    try {
      const res = await request.post(`/teams/${this.data.myTeam.id}/decline`)
      util.hideLoading()
      if (res.code === 0) {
        util.showSuccess('已拒绝')
        this.setData({ myTeam: null, isInvitee: false })
        await this.loadEventDetail()
      } else {
        util.showError(res.message || '操作失败')
      }
    } catch (e) {
      util.hideLoading()
      util.showError(e.message || '操作失败')
    }
  },

  // Cancel registration
  async handleCancel() {
    // Check if can cancel
    const now = new Date()
    const deadline = new Date(this.data.event.registrationDeadline)

    if (now >= deadline) {
      util.showError('报名截止后无法取消，请联系管理员')
      return
    }

    // Confirm cancel
    const confirmed = await util.showConfirm({
      title: '取消报名',
      content: '确定要取消报名吗？'
    })

    if (!confirmed) return

    util.showLoading('取消中...')

    try {
      const res = await request.post(`/events/${this.data.eventId}/cancel`)

      util.hideLoading()

      if (res.code === 0) {
        util.showSuccess('已取消报名')

        // Update state
        this.setData({
          isRegistered: false,
          registration: null,
          event: {
            ...this.data.event,
            currentParticipants: this.data.event.currentParticipants - 1
          }
        })
      } else {
        util.showError(res.message || '取消失败')
      }
    } catch (error) {
      util.hideLoading()
      util.showError(error.message || '取消失败')
    }
  },

  // View event results
  handleViewResults() {
    wx.navigateTo({
      url: `/pages/event-results/event-results?id=${this.data.eventId}`
    })
  },

  // Share event
  onShareAppMessage() {
    const event = this.data.event
    return {
      title: event.title,
      path: `/pages/event-detail/event-detail?id=${event.id}`,
      imageUrl: event.bannerUrl
    }
  }
})
