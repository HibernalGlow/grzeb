/**
 * 全文搜索引擎核心实现
 * 参考 Markor 的 FileSearchEngine 优化策略
 * 支持 Android StorageAccessFramework (content:// URI)
 */

import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';

// 从 legacy 模块获取 StorageAccessFramework
const SAF = FileSystem.StorageAccessFramework;
import { 
  SearchOptions, 
  SearchMatch, 
  FileSearchResult, 
  SearchProgress, 
  SearchCallbacks,
  SearchState,
  DEFAULT_SEARCH_OPTIONS 
} from './types';

/** 常见文本文件扩展名 */
const TEXT_EXTENSIONS = new Set([
  '.txt', '.md', '.markdown', '.json', '.xml', '.html', '.htm',
  '.css', '.js', '.ts', '.jsx', '.tsx', '.vue', '.svelte',
  '.py', '.java', '.c', '.cpp', '.h', '.hpp', '.cs',
  '.go', '.rs', '.rb', '.php', '.swift', '.kt', '.scala',
  '.sh', '.bash', '.zsh', '.ps1', '.bat', '.cmd',
  '.yaml', '.yml', '.toml', '.ini', '.cfg', '.conf',
  '.log', '.csv', '.tsv', '.sql', '.r', '.lua',
  '.org', '.adoc', '.rst', '.tex', '.bib',
  // 小说常见格式
  '.epub', '.mobi', '.fb2', '.rtf',
]);

/** 默认忽略的目录正则 */
const DEFAULT_IGNORED_PATTERNS = [
  /^\.git$/i,
  /^\.svn$/i,
  /^\.hg$/i,
  /^node_modules$/i,
  /^\.expo$/i,
  /^\.cache$/i,
  /^\.tmp$/i,
  /^thumbs$/i,
];

/** 检查是否为 Android SAF URI */
function isSafUri(uri: string): boolean {
  return uri.startsWith('content://');
}

/** 检查是否为隐藏目录 */
function isHiddenDir(name: string): boolean {
  return name.startsWith('.');
}

export class SearchEngine {
  private options: SearchOptions;
  private callbacks: SearchCallbacks;
  private isCancelled: boolean = false;
  private state: SearchState = 'idle';
  private results: FileSearchResult[] = [];
  private progress: SearchProgress = {
    scannedFiles: 0,
    totalMatches: 0,
    currentDepth: 0,
    pendingDirs: 0,
    isComplete: false,
  };
  private regex: RegExp | null = null;
  private ignoredPatterns: RegExp[] = [];
  private ignoredExact: Set<string> = new Set();
  private isAndroidSaf: boolean = false;

  constructor(options: Partial<SearchOptions>, callbacks: SearchCallbacks = {}) {
    this.options = { ...DEFAULT_SEARCH_OPTIONS, ...options } as SearchOptions;
    this.callbacks = callbacks;
    this.parseIgnoredDirs();
    this.buildRegex();
    this.isAndroidSaf = Platform.OS === 'android' && isSafUri(this.options.rootDir);
  }

  /** 解析忽略目录配置 */
  private parseIgnoredDirs(): void {
    this.ignoredPatterns = [...DEFAULT_IGNORED_PATTERNS];
    this.ignoredExact = new Set();

    for (const pattern of this.options.ignoredDirs) {
      if (pattern.startsWith('^') || pattern.endsWith('$') || pattern.includes('*')) {
        try {
          this.ignoredPatterns.push(new RegExp(pattern, 'i'));
        } catch {
          // 忽略无效正则
        }
      } else {
        this.ignoredExact.add(pattern.toLowerCase());
      }
    }
  }

  /** 构建搜索正则表达式 */
  private buildRegex(): void {
    const { query, isRegex, isCaseSensitive, isWholeWord } = this.options;
    
    if (!query) {
      this.regex = null;
      return;
    }

    let pattern = query;
    let flags = isCaseSensitive ? 'gm' : 'gim';

    if (!isRegex) {
      pattern = pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    if (isWholeWord) {
      pattern = `\\b${pattern}\\b`;
    }

    try {
      this.regex = new RegExp(pattern, flags);
    } catch {
      this.regex = null;
    }
  }

  /** 检查目录是否应被忽略 */
  private shouldIgnore(name: string): boolean {
    const lowerName = name.toLowerCase();
    
    if (this.ignoredExact.has(lowerName)) {
      return true;
    }

    for (const pattern of this.ignoredPatterns) {
      if (pattern.test(name)) {
        return true;
      }
    }

    // 跳过隐藏目录
    if (isHiddenDir(name)) {
      return true;
    }

    return false;
  }

  /** 检查是否为文本文件 */
  private isTextFile(filename: string): boolean {
    const dotIndex = filename.lastIndexOf('.');
    if (dotIndex === -1) return false;
    const ext = filename.toLowerCase().slice(dotIndex);
    return TEXT_EXTENSIONS.has(ext) || this.options.includeExtensions.includes(ext);
  }

  /** 提取匹配的上下文预览 */
  private extractContext(
    content: string, 
    lines: string[], 
    matchStart: number, 
    matchEnd: number,
    matchedLine: number
  ): string {
    const { contextLines, maxPreviewLength } = this.options;
    
    const startLine = Math.max(0, matchedLine - contextLines);
    const endLine = Math.min(lines.length - 1, matchedLine + contextLines);
    
    const contextParts: string[] = [];
    
    for (let i = startLine; i <= endLine; i++) {
      let line = lines[i];
      
      if (line.length > maxPreviewLength) {
        if (i === matchedLine) {
          const matchPosInLine = this.findMatchPositionInLine(content, matchStart, matchedLine, lines);
          const offset = Math.floor((maxPreviewLength - (matchEnd - matchStart)) / 2);
          const subStart = Math.max(0, matchPosInLine - offset);
          const subEnd = Math.min(line.length, matchPosInLine + (matchEnd - matchStart) + offset);
          line = (subStart > 0 ? '…' : '') + line.slice(subStart, subEnd) + (subEnd < line.length ? '…' : '');
        } else {
          line = line.slice(0, maxPreviewLength) + (line.length > maxPreviewLength ? '…' : '');
        }
      }
      
      contextParts.push(line);
    }
    
    return contextParts.join('\n');
  }

  /** 找到匹配在行中的位置 */
  private findMatchPositionInLine(
    content: string, 
    matchStart: number, 
    matchedLine: number,
    lines: string[]
  ): number {
    let pos = 0;
    for (let i = 0; i < matchedLine; i++) {
      pos += lines[i].length + 1;
    }
    return matchStart - pos;
  }

  /** 读取文件内容（支持 SAF 和普通文件系统） */
  private async readFileContent(uri: string): Promise<string | null> {
    try {
      if (this.isAndroidSaf) {
        // Android SAF 方式读取
        return await SAF.readAsStringAsync(uri);
      } else {
        // 普通文件系统读取
        return await FileSystem.readAsStringAsync(uri);
      }
    } catch {
      return null;
    }
  }

  /** 读取目录内容（支持 SAF 和普通文件系统） */
  private async readDirectory(uri: string): Promise<string[]> {
    try {
      if (this.isAndroidSaf) {
        // Android SAF 方式
        return await SAF.readDirectoryAsync(uri);
      } else {
        // 普通文件系统
        return await FileSystem.readDirectoryAsync(uri);
      }
    } catch {
      return [];
    }
  }

  /** 获取文件信息 */
  private async getFileInfo(uri: string): Promise<{ exists: boolean; isDirectory: boolean; size?: number }> {
    try {
      if (this.isAndroidSaf) {
        // SAF 没有 getInfoAsync，需要通过尝试读取来判断
        // 先尝试作为目录读取
        try {
          await SAF.readDirectoryAsync(uri);
          return { exists: true, isDirectory: true };
        } catch {
          // 不是目录，尝试作为文件
          try {
            const content = await SAF.readAsStringAsync(uri);
            return { exists: true, isDirectory: false, size: content.length };
          } catch {
            return { exists: false, isDirectory: false };
          }
        }
      } else {
        const info = await FileSystem.getInfoAsync(uri);
        if ('exists' in info) {
          return { 
            exists: info.exists, 
            isDirectory: 'isDirectory' in info ? info.isDirectory : false,
            size: 'size' in info ? info.size : undefined,
          };
        }
        return { exists: false, isDirectory: false };
      }
    } catch {
      return { exists: false, isDirectory: false };
    }
  }

  /** 在单个文件中搜索 */
  private async searchInFile(
    uri: string,
    relPath: string
  ): Promise<FileSearchResult | null> {
    if (this.isCancelled || !this.regex) {
      return null;
    }

    const result: FileSearchResult = {
      uri,
      relPath,
      isDirectory: false,
      matches: [],
      matchCount: 0,
    };

    try {
      // 读取文件内容
      const content = await this.readFileContent(uri);
      if (!content) {
        return null;
      }
      
      result.size = content.length;
      const lines = content.split('\n');

      // 搜索匹配
      let match: RegExpExecArray | null;
      const seenPositions = new Set<number>();

      while ((match = this.regex.exec(content)) !== null) {
        if (this.isCancelled) break;

        if (seenPositions.has(match.index)) {
          this.regex.lastIndex++;
          continue;
        }
        seenPositions.add(match.index);

        // 计算行号
        let lineNumber = 0;
        let charCount = 0;
        for (let i = 0; i < lines.length; i++) {
          if (charCount + lines[i].length >= match.index) {
            lineNumber = i;
            break;
          }
          charCount += lines[i].length + 1;
        }

        const preview = this.extractContext(
          content,
          lines,
          match.index,
          match.index + match[0].length,
          lineNumber
        );

        result.matches.push({
          matchText: match[0],
          start: match.index,
          end: match.index + match[0].length,
          lineNumber,
          preview,
        });

        result.matchCount++;

        if (this.options.isOnlyFirstMatch) {
          break;
        }
      }
    } catch {
      return null;
    }

    return result.matches.length > 0 ? result : null;
  }

  /** 构建 URI */
  private buildUri(dirUri: string, entry: string): string {
    if (this.isAndroidSaf) {
      // Android SAF URI 格式
      return dirUri.endsWith('/') ? dirUri + encodeURIComponent(entry) : dirUri + '/' + encodeURIComponent(entry);
    } else {
      return dirUri.endsWith('/') ? dirUri + entry : dirUri + '/' + entry;
    }
  }

  /** 遍历目录 */
  private async walkDirectory(
    dirUri: string,
    depth: number,
    rootLength: number
  ): Promise<void> {
    if (this.isCancelled || depth > this.options.maxDepth) {
      return;
    }

    this.progress.currentDepth = depth;

    try {
      const entries = await this.readDirectory(dirUri);
      this.progress.pendingDirs = entries.length;

      for (const entry of entries) {
        if (this.isCancelled) break;

        // 解码 entry 名称（SAF 可能编码）
        const entryName = decodeURIComponent(entry.split('/').pop() || entry);
        const entryUri = this.buildUri(dirUri, entryName);
        
        // 计算相对路径
        const relPath = this.isAndroidSaf 
          ? entryName 
          : entryUri.slice(rootLength);

        // 检查是否忽略
        if (this.shouldIgnore(entryName)) {
          continue;
        }

        try {
          const info = await this.getFileInfo(entryUri);
          
          if (info.isDirectory) {
            await this.walkDirectory(entryUri, depth + 1, rootLength);
          } else if (info.exists && this.isTextFile(entryName)) {
            this.progress.currentFile = relPath;
            this.progress.scannedFiles++;
            
            const result = await this.searchInFile(entryUri, relPath);
            
            if (result) {
              this.results.push(result);
              this.progress.totalMatches += result.matchCount;
              
              if (this.callbacks.onResult) {
                this.callbacks.onResult(result);
              }
            }

            if (this.callbacks.onProgress) {
              this.callbacks.onProgress({ ...this.progress });
            }

            await new Promise(resolve => setTimeout(resolve, 0));
          }
        } catch {
          // 忽略单个条目的错误
        }
      }
    } catch {
      // 目录读取失败
    }
  }

  /** 开始搜索 */
  async start(): Promise<FileSearchResult[]> {
    if (this.state === 'searching') {
      return this.results;
    }

    if (!this.regex) {
      return [];
    }

    this.state = 'searching';
    this.isCancelled = false;
    this.results = [];
    this.progress = {
      scannedFiles: 0,
      totalMatches: 0,
      currentDepth: 0,
      pendingDirs: 0,
      isComplete: false,
    };

    const rootLength = this.options.rootDir.length + 1;

    try {
      await this.walkDirectory(this.options.rootDir, 0, rootLength);
    } catch (error) {
      this.state = 'error';
      if (this.callbacks.onError) {
        this.callbacks.onError(error as Error);
      }
      return this.results;
    }

    this.state = this.isCancelled ? 'cancelled' : 'complete';
    this.progress.isComplete = true;

    if (this.callbacks.onProgress) {
      this.callbacks.onProgress({ ...this.progress });
    }

    if (!this.isCancelled && this.callbacks.onComplete) {
      this.callbacks.onComplete(this.results);
    }

    return this.results;
  }

  /** 取消搜索 */
  cancel(): void {
    this.isCancelled = true;
    this.state = 'cancelled';
    this.progress.isComplete = true;
    
    if (this.callbacks.onProgress) {
      this.callbacks.onProgress({ ...this.progress });
    }
  }

  /** 获取当前状态 */
  getState(): SearchState {
    return this.state;
  }

  /** 获取当前进度 */
  getProgress(): SearchProgress {
    return { ...this.progress };
  }

  /** 获取当前结果 */
  getResults(): FileSearchResult[] {
    return [...this.results];
  }

  /** 是否已取消 */
  isSearchCancelled(): boolean {
    return this.isCancelled;
  }
}

/** 创建搜索引擎实例 */
export function createSearchEngine(
  options: Partial<SearchOptions>,
  callbacks?: SearchCallbacks
): SearchEngine {
  return new SearchEngine(options, callbacks);
}