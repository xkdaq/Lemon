import { ref } from 'vue'
import { getContentTree, type TreeNode } from '@/api/content'

/** 题库目录树，全局共享一份；结构有变化（新建章节、导入自动建章节）后调用 reload */
const tree = ref<TreeNode[]>([])
let loading: Promise<void> | null = null

function reload(): Promise<void> {
  loading = getContentTree()
    .then((t) => {
      tree.value = t
    })
    .finally(() => {
      loading = null
    })
  return loading
}

export function useContentTree() {
  if (!tree.value.length && !loading) {
    reload()
  }
  return { tree, reload }
}

/** 在树中查找章节，返回 [bank, course?, chapter] 节点路径 */
export function findChapterPath(nodes: TreeNode[], chapterId: number): TreeNode[] | null {
  for (const bank of nodes) {
    for (const child of bank.children ?? []) {
      if (child.kind === 'chapter' && child.id === chapterId) {
        return [bank, child]
      }
      const chapter = child.children?.find((c) => c.id === chapterId)
      if (chapter) {
        return [bank, child, chapter]
      }
    }
  }
  return null
}
