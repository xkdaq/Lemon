<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import { Placeholder } from '@tiptap/extensions'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@/api/file'

/**
 * 题干 / 解析用的富文本编辑器（Tiptap）。
 * 只提供后端白名单允许的格式：粗体、斜体、下划线、删除线、列表、引用、图片；不提供链接和自定义样式。
 * 图片支持按钮选择、粘贴、拖入，都会先上传再插入 URL（不存 base64）。
 */
const props = withDefaults(defineProps<{ modelValue?: string; placeholder?: string; minHeight?: number }>(), {
  modelValue: '',
  placeholder: '',
  minHeight: 120,
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit.configure({ heading: false, codeBlock: false, code: false, link: false, horizontalRule: false }),
    Image,
    Placeholder.configure({ placeholder: props.placeholder }),
  ],
  editorProps: {
    handlePaste: (_view, event) => insertImageFiles(event.clipboardData?.files),
    handleDrop: (_view, event) => insertImageFiles((event as DragEvent).dataTransfer?.files),
  },
  onUpdate: ({ editor }) => emit('update:modelValue', editor.isEmpty ? '' : editor.getHTML()),
})

watch(
  () => props.modelValue,
  (value) => {
    const e = editor.value
    if (e && value !== (e.isEmpty ? '' : e.getHTML())) {
      e.commands.setContent(value || '', { emitUpdate: false })
    }
  },
)

onBeforeUnmount(() => editor.value?.destroy())

/** 有图片文件就接管粘贴 / 拖入，返回 true 表示已处理 */
function insertImageFiles(files?: FileList | null): boolean {
  const images = Array.from(files ?? []).filter((f) => f.type.startsWith('image/'))
  if (!images.length) {
    return false
  }
  images.forEach(uploadAndInsert)
  return true
}

async function uploadAndInsert(file: File) {
  uploading.value = true
  try {
    const url = await uploadImage(file)
    editor.value?.chain().focus().setImage({ src: url }).run()
  } catch {
    ElMessage.error(`图片 ${file.name} 上传失败`)
  } finally {
    uploading.value = false
  }
}

function onPickFile(e: Event) {
  const input = e.target as HTMLInputElement
  Array.from(input.files ?? []).forEach(uploadAndInsert)
  input.value = ''
}

type Action = { icon: string; title: string; active?: string; run: () => void }
const actions: Action[] = [
  { icon: 'B', title: '粗体', active: 'bold', run: () => editor.value?.chain().focus().toggleBold().run() },
  { icon: 'I', title: '斜体', active: 'italic', run: () => editor.value?.chain().focus().toggleItalic().run() },
  { icon: 'U', title: '下划线', active: 'underline', run: () => editor.value?.chain().focus().toggleUnderline().run() },
  { icon: 'S', title: '删除线', active: 'strike', run: () => editor.value?.chain().focus().toggleStrike().run() },
  { icon: '•', title: '无序列表', active: 'bulletList', run: () => editor.value?.chain().focus().toggleBulletList().run() },
  { icon: '1.', title: '有序列表', active: 'orderedList', run: () => editor.value?.chain().focus().toggleOrderedList().run() },
  { icon: '❝', title: '引用（材料题的材料）', active: 'blockquote', run: () => editor.value?.chain().focus().toggleBlockquote().run() },
  { icon: '⌫', title: '清除格式', run: () => editor.value?.chain().focus().unsetAllMarks().clearNodes().run() },
  { icon: '↶', title: '撤销', run: () => editor.value?.chain().focus().undo().run() },
  { icon: '↷', title: '重做', run: () => editor.value?.chain().focus().redo().run() },
]
</script>

<template>
  <div class="rich-editor">
    <div class="toolbar">
      <button
        v-for="a in actions"
        :key="a.title"
        type="button"
        :title="a.title"
        :class="{ active: a.active && editor?.isActive(a.active) }"
        @click="a.run"
      >{{ a.icon }}</button>
      <button type="button" title="插入图片（也可以直接粘贴或拖入）" :disabled="uploading" @click="fileInput?.click()">
        <el-icon v-if="uploading" class="is-loading"><Loading /></el-icon>
        <el-icon v-else><Picture /></el-icon>
      </button>
      <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp" multiple hidden @change="onPickFile" />
    </div>
    <EditorContent :editor="editor" class="content" :style="{ minHeight: `${minHeight}px` }" />
  </div>
</template>

<style scoped>
.rich-editor {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  line-height: 1.6;
}
.rich-editor:focus-within {
  border-color: var(--el-color-primary);
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 4px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}
.toolbar button {
  min-width: 28px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.toolbar button:hover {
  background: var(--el-fill-color);
}
.toolbar button.active {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}
.content {
  padding: 8px 12px;
  cursor: text;
}
.content :deep(.ProseMirror) {
  outline: none;
  min-height: inherit;
}
.content :deep(.ProseMirror p) {
  margin: 0 0 6px;
}
.content :deep(.ProseMirror img) {
  max-width: 100%;
  max-height: 320px;
}
.content :deep(.ProseMirror img.ProseMirror-selectednode) {
  outline: 2px solid var(--el-color-primary);
}
.content :deep(.ProseMirror blockquote) {
  margin: 4px 0;
  padding-left: 10px;
  border-left: 3px solid var(--el-border-color);
  color: var(--el-text-color-regular);
}
.content :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: var(--el-text-color-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
}
</style>
