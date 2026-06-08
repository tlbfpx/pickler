// Event card component
import util from '../../utils/util'

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
    showStatus: {
      type: Boolean,
      value: true
    }
  },

  computed: {
    statusText() {
      return util.formatEventStatus(this.data.event?.status)
    },

    statusColor() {
      return util.getEventStatusColor(this.data.event?.status)
    },

    eventTime() {
      return util.formatDate(this.data.event?.event_time, 'MM-DD HH:mm')
    },

    deadline() {
      return util.formatDate(this.data.event?.registration_deadline, 'MM-DD HH:mm')
    },

    isFull() {
      const event = this.data.event
      return event?.current_participants >= event?.max_participants
    },

    isOpen() {
      return util.isRegistrationOpen(this.data.event)
    },

    typeText() {
      const typeMap = {
        STAR: '赛事',
        PARTY: '活动'
      }
      return typeMap[this.data.event?.type] || '赛事'
    },

    typeClass() {
      return this.data.event?.type === 'STAR' ? 'type-star' : 'type-party'
    },

    remainingTime() {
      return util.getRemainingTime(this.data.event?.registration_deadline)
    }
  },

  methods: {
    handleTap() {
      this.triggerEvent('tap', {
        event: this.data.event
      })
    },

    handleRegister(e) {
      e.stopPropagation()
      this.triggerEvent('register', {
        event: this.data.event
      })
    }
  }
})
