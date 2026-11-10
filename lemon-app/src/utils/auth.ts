const TOKEN_KEY = 'lemon_token'

export const getToken = (): string => uni.getStorageSync(TOKEN_KEY) || ''
export const setToken = (token: string) => uni.setStorageSync(TOKEN_KEY, token)
export const removeToken = () => uni.removeStorageSync(TOKEN_KEY)
export const isLoggedIn = () => !!getToken()
