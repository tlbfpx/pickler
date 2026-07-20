// Request utility with JWT interceptors

function getAppInstance() {
  return getApp()
}

function request(options) {
  return new Promise((resolve, reject) => {
    const app = getAppInstance()
    const header = {
      'Content-Type': 'application/json',
      ...options.header
    }

    // Add Authorization header if token exists
    if (app.globalData.token) {
      header['Authorization'] = `Bearer ${app.globalData.token}`
    }

    // Loop-v19 Phase 2 R3 — 记录请求起始时间用于 http_request 上报延迟
    const startTime = Date.now()

    wx.request({
      url: `${app.globalData.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success: (res) => {
        // 上报 http_request 事件（best-effort，失败仅 warn）
        try {
          const tracker = require('./tracker.js').default
          tracker.trackEvent('http_request', {
            method: options.method || 'GET',
            path: options.url,
            code: res.statusCode,
            latencyMs: Date.now() - startTime
          })
        } catch (e) { /* tracker 不存在或异常不影响主请求 */ }

        // Handle 401 Unauthorized
        if (res.statusCode === 401) {
          handleUnauthorized()
          reject({ code: 401, message: '未授权，请重新登录' })
          return
        }

        // Handle 403 Forbidden (banned user or invalid token)
        if (res.statusCode === 403) {
          redirectToLogin()
          reject({ code: 403, message: res.data?.message || '账号异常，请重新登录' })
          return
        }

        // Handle other error status codes
        if (res.statusCode !== 200) {
          reject({ code: res.statusCode, message: res.data?.message || '请求失败' })
          return
        }

        // Return response data
        resolve(res.data)
      },
      fail: (err) => {
        // 上报失败请求
        try {
          const tracker = require('./tracker.js').default
          tracker.trackEvent('http_request', {
            method: options.method || 'GET',
            path: options.url,
            code: -1,
            latencyMs: Date.now() - startTime
          })
        } catch (e) { /* 忽略 */ }
        console.error('Request failed:', err)
        reject({ code: -1, message: '网络请求失败，请检查网络连接' })
      }
    })
  })
}

// Handle 401 Unauthorized
function handleUnauthorized() {
  const token = wx.getStorageSync('token')
  if (token) {
    // Try to refresh token
    refreshToken().catch(() => {
      // If refresh fails, redirect to login
      redirectToLogin()
    })
  } else {
    // No token, redirect to login
    redirectToLogin()
  }
}

// Refresh token
function refreshToken() {
  const app = getAppInstance()
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.baseUrl}/auth/refresh`,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${wx.getStorageSync('token')}`
      },
      success: (res) => {
        if (res.data.code === 0) {
          const newToken = res.data.data.token
          wx.setStorageSync('token', newToken)
          app.globalData.token = newToken
          resolve(newToken)
        } else {
          reject(res.data)
        }
      },
      fail: reject
    })
  })
}

// Redirect to login page
function redirectToLogin() {
  const app = getAppInstance()
  // Clear token
  wx.removeStorageSync('token')
  app.globalData.token = null
  app.globalData.userInfo = null

  // Redirect to login page
  wx.reLaunch({
    url: '/pages/login/login'
  })
}

// Export convenience methods
export default {
  get(url, data = {}, options = {}) {
    return request({ url, method: 'GET', data, ...options })
  },

  post(url, data = {}, options = {}) {
    return request({ url, method: 'POST', data, ...options })
  },

  put(url, data = {}, options = {}) {
    return request({ url, method: 'PUT', data, ...options })
  },

  delete(url, data = {}, options = {}) {
    return request({ url, method: 'DELETE', data, ...options })
  }
}
