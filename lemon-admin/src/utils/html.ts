/** 富文本转纯文本（列表展示用），图片显示为 [图片] */
export function htmlToText(html?: string | null): string {
  if (!html) {
    return ''
  }
  const doc = new DOMParser().parseFromString(html.replace(/<img[^>]*>/gi, '[图片]'), 'text/html')
  return (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim()
}
