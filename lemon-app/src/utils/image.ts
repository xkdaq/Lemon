/**
 * 上传前压缩图片：长边缩到 maxSide 以内，转成 JPEG（质量 quality）。
 * 已经很小的图片（< 300KB）原样返回。H5 用 canvas，小程序用 uni.compressImage。
 */
export async function compressImage(path: string, size: number, maxSide = 1600, quality = 0.8): Promise<string> {
  if (size > 0 && size < 300 * 1024) return path
  // #ifdef H5
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const i = new Image()
      i.onload = () => resolve(i)
      i.onerror = reject
      i.src = path
    })
    const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(img.width * scale)
    canvas.height = Math.round(img.height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) return path
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    return blob ? URL.createObjectURL(blob) : path
  } catch {
    return path
  }
  // #endif
  // #ifndef H5
  try {
    const r = await uni.compressImage({ src: path, quality: Math.round(quality * 100) })
    return r.tempFilePath
  } catch {
    return path
  }
  // #endif
}
