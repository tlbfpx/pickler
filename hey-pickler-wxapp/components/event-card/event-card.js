// Event card component
import util from '../../utils/util'
import { TERMS, FORMAT } from '../../utils/terms'

Component({
  properties: {
    event: {
      type: Object,
      value: null
    },
    showType: {
      type: Boolean,
      value: true
    },
    showFormat: {
      type: Boolean,
      value: true
    },
    showStatus: {
      type: Boolean,
      value: true
    }
  },

  data: {
    imgSrc: '/images/default-event.png',
    statusText: '',
    statusColor: '#999',
    eventTime: '',
    deadline: '',
    isFull: false,
    isOpen: false,
    typeText: '赛事',
    typeClass: 'type-party',
    formatText: '',
    formatColor: '#9CA3AF',
    remainingTime: ''
  },

  observers: {
    'event': function (event) {
      if (!event) return

      var statusText = util.formatEventStatus(event.status)
      var statusColor = util.getEventStatusColor(event.status)
      var eventTime = util.formatDate(event.eventTime, 'MM-DD HH:mm')
      var deadline = util.formatDate(event.registrationDeadline, 'MM-DD HH:mm')
      var isFull = event.currentParticipants >= event.maxParticipants
      var isOpen = util.isRegistrationOpen(event)

      var typeText = (TERMS[event.type] && TERMS[event.type].type) || '赛事'
      var typeClass = event.type === 'STAR' ? 'type-star' : 'type-party'

      var formatText = ''
      var formatColor = '#9CA3AF'
      if (event.format && FORMAT[event.format]) {
        formatText = FORMAT[event.format].label
        formatColor = FORMAT[event.format].color
      }

      // Reset fallback state when event changes; default to local image if bannerUrl missing
      var imgSrc = event.bannerUrl || '/images/default-event.png'

      // Remaining time
      var remainingTime = ''
      if (event.registrationDeadline) {
        var now = new Date()
        var end = new Date(event.registrationDeadline)
        var diff = end.getTime() - now.getTime()
        if (diff > 0) {
          var days = Math.floor(diff / (1000 * 60 * 60 * 24))
          var hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
          var minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
          if (days > 0) {
            remainingTime = days + '天' + hours + '小时'
          } else if (hours > 0) {
            remainingTime = hours + '小时' + minutes + '分钟'
          } else {
            remainingTime = minutes + '分钟'
          }
        }
      }

      this.setData({
        imgSrc: imgSrc,
        statusText: statusText,
        statusColor: statusColor,
        eventTime: eventTime,
        deadline: deadline,
        isFull: isFull,
        isOpen: isOpen,
        typeText: typeText,
        typeClass: typeClass,
        formatText: formatText,
        formatColor: formatColor,
        remainingTime: remainingTime
      })
    }
  },

  methods: {
    handleTap: function () {
      this.triggerEvent('navigate', {
        event: this.data.event
      })
    },

    handleRegister: function () {
      this.triggerEvent('register', {
        event: this.data.event
      })
    },

    // Event image failed to load → swap to local fallback (idempotent: local file won't re-error)
    onBannerError: function () {
      if (this.data.imgSrc === '/images/default-event.png') return
      this.setData({ imgSrc: '/images/default-event.png' })
    }
  }
})
