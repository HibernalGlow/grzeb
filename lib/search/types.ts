/**
 * 搜索模块类型定义
 */

/** 搜索选项配置 */
export interface SearchOptions {
  /** 搜索根目录 */
  rootDir: string;
  /** 搜索关键词 */
  query: string;
  /** 是否使用正则表达式 */
  isRegex: boolean;
  /** 是否区分大小写 */
  isCaseSensitive: boolean;
  /** 是否全词匹配 */
  isWholeWord: boolean;
  /** 是否搜索文件内容 */
  isSearchInContent: boolean;
  /** 是否只查找第一个匹配 */
  isOnlyFirstMatch: boolean;
  /** 最大搜索深度 */
  maxDepth: number;
  /** 忽略的目录名（支持精确匹配和正则） */
  ignoredDirs: string[];
  /** 包含的文件扩展名（如 ['.txt', '.md']） */
  includeExtensions: string[];
  /** 最大预览长度 */
  maxPreviewLength: number;
  /** 预览上下文行数 */
  contextLines: number;
}

/** 单个匹配项 */
export interface SearchMatch {
  /** 匹配的文本 */
  matchText: string;
  /** 在行中的起始位置 */
  start: number;
  /** 在行中的结束位置 */
  end: number;
  /** 行号（0-based） */
  lineNumber: number;
  /** 预览文本（包含上下文） */
  preview: string;
}

/** 文件搜索结果 */
export interface FileSearchResult {
  /** 文件URI */
  uri: string;
  /** 相对路径 */
  relPath: string;
  /** 是否为目录 */
  isDirectory: boolean;
  /** 文件大小（字节） */
  size?: number;
  /** 匹配项列表 */
  matches: SearchMatch[];
  /** 总匹配数 */
  matchCount: number;
}

/** 搜索进度信息 */
export interface SearchProgress {
  /** 当前正在搜索的文件 */
  currentFile?: string;
  /** 已扫描文件数 */
  scannedFiles: number;
  /** 已找到的匹配数 */
  totalMatches: number;
  /** 当前深度 */
  currentDepth: number;
  /** 待处理的目录数 */
  pendingDirs: number;
  /** 是否已完成 */
  isComplete: boolean;
  /** 错误信息（如果有） */
  error?: string;
}

/** 搜索回调 */
export interface SearchCallbacks {
  /** 进度更新回调 */
  onProgress?: (progress: SearchProgress) => void;
  /** 找到结果回调（增量更新） */
  onResult?: (result: FileSearchResult) => void;
  /** 搜索完成回调 */
  onComplete?: (results: FileSearchResult[]) => void;
  /** 错误回调 */
  onError?: (error: Error) => void;
}

/** 搜索状态 */
export type SearchState = 'idle' | 'searching' | 'cancelled' | 'complete' | 'error';

/** 默认搜索选项 */
export const DEFAULT_SEARCH_OPTIONS: Partial<SearchOptions> = {
  isRegex: false,
  isCaseSensitive: false,
  isWholeWord: false,
  isSearchInContent: true,
  isOnlyFirstMatch: false,
  maxDepth: 10,
  ignoredDirs: ['.git', '.svn', '.hg', 'node_modules', '.expo'],
  includeExtensions: [],
  maxPreviewLength: 100,
  contextLines: 2,
};
