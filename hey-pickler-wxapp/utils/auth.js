// Auth utility for login flow and token management

/**
 * WeChat login flow
 * 1. Call wx.login() to get code
 * 2. Send code to backend to exchange for openid and session_key
 * 3. Backend returns JWT token
 * 4. Store token in storage and app.globalData
 */
function login() {
  const app = getApp()
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => {
        if (res.code) {
          // Send code to backend
          wx.request({
            url: `${app.globalData.baseUrl}/auth/login`,
            method: 'POST',
            data: {
              code: res.code
            },
            success: (response) => {
              if (response.data.code === 0) {
                const { token, user } = response.data.data

                // Store token
                wx.setStorageSync('token', token)
                app.globalData.token = token

                // Store user info
                if (user) {
                  app.globalData.userInfo = user
                }

                resolve({ token, user })
              } else {
                reject(new Error(response.data.message || '登录失败'))
              }
            },
            fail: (err) => {
              reject(new Error('登录请求失败: ' + err.errMsg))
            }
          })
        } else {
          reject(new Error('wx.login 失败: ' + res.errMsg))
        }
      },
      fail: (err) => {
        reject(new Error('wx.login 调用失败: ' + err.errMsg))
      }
    })
  })
}

/**
 * Bind phone number
 * @param {string} encryptedData - Encrypted data from WeChat
 * @param {string} iv - Initialization vector
 */
function bindPhone(encryptedData, iv) {
  const app = getApp()
  return new Promise((resolve, reject) => {
    if (!app.globalData.token) {
      reject(new Error('请先登录'))
      return
    }

    wx.request({
      url: `${app.globalData.baseUrl}/auth/phone`,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${app.globalData.token}`
      },
      data: {
        encryptedData,
        iv
      },
      success: (res) => {
        if (res.data.code === 0) {
          resolve(res.data.data)
        } else {
          reject(new Error(res.data.message || '绑定手机号失败'))
        }
      },
      fail: (err) => {
        reject(new Error('绑定手机号请求失败: ' + err.errMsg))
      }
    })
  })
}

/**
 * Logout - Clear token and user info
 */
function logout() {
  const app = getApp()
  wx.removeStorageSync('token')
  app.globalData.token = null
  app.globalData.userInfo = null

  wx.reLaunch({
    url: '/pages/login/login'
  })
}

/**
 * Check if user is logged in
 */
function isLoggedIn() {
  return !!getApp().globalData.token
}

/**
 * Get current user info
 */
function getUserInfo() {
  return getApp().globalData.userInfo
}

/**
 * Update user info in globalData
 */
function updateUserInfo(userInfo) {
  const app = getApp()
  app.globalData.userInfo = {
    ...app.globalData.userInfo,
    ...userInfo
  }
}

export default {
  login,
  bindPhone,
  logout,
  isLoggedIn,
  getUserInfo,
  updateUserInfo
}
