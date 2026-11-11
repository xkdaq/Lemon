import { getToken } from './auth'

/**
 * 下载需要登录的文件（带 token）。
 * H5：取回文件后触发浏览器下载；小程序：下载到临时文件后用系统预览打开（可转发 / 保存）。
 * 接口出错时后端返回 JSON，这里读出提示信息。
 */
export function downloadFile(url: string, filename: string, fileType = 'docx'): Promise<void> {
  const header = { Authorization: `Bearer ${getToken()}` }
  // #ifdef H5
  return fetch(url, { headers: header }).then(async (res) => {
    const type = res.headers.get('content-type') ?? ''
    if (!res.ok || type.includes('application/json')) {
      const body = await res.json().catch(() => null)
      throw new Error(body?.msg || '下载失败')
    }
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 10000)
  })
  // #endif
  // #ifndef H5
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      header,
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error('下载失败'))
          return
        }
        uni.openDocument({ filePath: res.tempFilePath, fileType, showMenu: true, success: () => resolve(), fail: () => reject(new Error('打开文件失败')) })
      },
      fail: () => reject(new Error('下载失败')),
    })
  })
  // #endif
}
