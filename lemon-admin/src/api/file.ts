import { upload } from '@/utils/request'

/** 上传图片，返回可访问的 URL（如 /uploads/images/2026/11/xxx.png） */
export const uploadImage = (file: File) => upload<{ url: string }>('/files/upload', file).then((r) => r.url)
