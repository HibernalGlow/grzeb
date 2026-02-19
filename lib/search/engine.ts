/**
 * 全文搜索引擎核心实现
 * 参考 Markor 的 FileSearchEngine 优化策略
 */

import * as FileSystem from 'expo-file-system';
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
  /^\./, // 隐藏目录
];

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

  constructor(options: Partial<SearchOptions>, callbacks: SearchCallbacks = {}) {
    this.options = { ...DEFAULT_SEARCH_OPTIONS, ...options } as SearchOptions;
    this.callbacks = callbacks;
    this.parseIgnoredDirs();
    this.buildRegex();
  }

  /** 解析忽略目录配置 */
  private parseIgnoredDirs(): void {
    this.ignoredPatterns = [...DEFAULT_IGNORED_PATTERNS];
    this.ignoredExact = new Set();

    for (const pattern of this.options.ignoredDirs) {
      if (pattern.startsWith('^') || pattern.endsWith('$') || pattern.includes('*')) {
        // 正则模式
        try {
          this.ignoredPatterns.push(new RegExp(pattern, 'i'));
        } catch {
          // 忽略无效正则
        }
      } else {
        // 精确匹配
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
      // 转义特殊字符
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
    
    // 精确匹配
    if (this.ignoredExact.has(lowerName)) {
      return true;
    }

    // 正则匹配
    for (const pattern of this.ignoredPatterns) {
      if (pattern.test(name)) {
        return true;
      }
    }

    return false;
  }

  /** 检查是否为文本文件 */
  private isTextFile(filename: string): boolean {
    const ext = filename.toLowerCase().slice(filename.lastIndexOf('.'));
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
    
    // 获取上下文行
    const startLine = Math.max(0, matchedLine - contextLines);
    const endLine = Math.min(lines.length - 1, matchedLine + contextLines);
    
    const contextParts: string[] = [];
    
    for (let i = startLine; i <= endLine; i++) {
      let line = lines[i];
      
      // 截断过长的行
      if (line.length > maxPreviewLength) {
        if (i === matchedLine) {
          // 匹配行：显示匹配位置附近
          const matchPosInLine = this.findMatchPositionInLine(content, matchStart, matchedLine, lines);
          const offset = Math.floor((maxPreviewLength - (matchEnd - matchStart)) / 2);
          const subStart = Math.max(0, matchPosInLine - offset);
          const subEnd = Math.min(line.length, matchPosInLine + (matchEnd - matchStart) + offset);
          line = (subStart > 0 ? '…' : '') + line.slice(subStart, subEnd) + (subEnd < line.length ? '…' : '');
        } else {
          // 非匹配行：截断
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
      pos += lines[i].length + 1; // +1 for newline
    }
    return matchStart - pos;
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
      // 获取文件信息
      const info = await FileSystem.getInfoAsync(uri);
      if (!info.exists || info.isDirectory) {
        return null;
      }
      result.size = 'size' in info ? info.size : undefined;

      // 读取文件内容
      const content = await FileSystem.readAsStringAsync(uri);
      const lines = content.split('\n');

      // 搜索匹配
      let match: RegExpExecArray | null;
      const seenPositions = new Set<number>();

      while ((match = this.regex.exec(content)) !== null) {
        if (this.isCancelled) break;

        // 避免无限循环（零宽匹配）
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

        // 提取上下文
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

        // 如果只找第一个匹配
        if (this.options.isOnlyFirstMatch) {
          break;
        }
      }
    } catch (error) {
      // 文件读取失败，静默忽略
      return null;
    }

    return result.matches.length > 0 ? result : null;
  }

  /** 遍历目录 */
  private async walkDirectory(
    dirUri: string,
    depth: number,
    trimLength: number
  ): Promise<void> {
    if (this.isCancelled || depth > this.options.maxDepth) {
      return;
    }

    this.progress.currentDepth = depth;

    try {
      const entries = await FileSystem.readDirectoryAsync(dirUri);
      this.progress.pendingDirs = entries.length;

      for (const entry of entries) {
        if (this.isCancelled) break;

        const entryUri = dirUri.endsWith('/') 
          ? dirUri + entry 
          : dirUri + '/' + entry;
        const relPath = entryUri.slice(trimLength);

        // 检查是否忽略
        if (this.shouldIgnore(entry)) {
          continue;
        }

        try {
          const info = await FileSystem.getInfoAsync(entryUri);
          
          if (info.isDirectory) {
            // 递归目录
            await this.walkDirectory(entryUri, depth + 1, trimLength);
          } else if (info.exists && this.isTextFile(entry)) {
            // 搜索文件内容
            this.progress.currentFile = relPath;
            this.progress.scannedFiles++;
            
            const result = await this.searchInFile(entryUri, relPath);
            
            if (result) {
              this.results.push(result);
              this.progress.totalMatches += result.matchCount;
              
              // 增量回调
              if (this.callbacks.onResult) {
                this.callbacks.onResult(result);
              }
            }

            // 进度回调
            if (this.callbacks.onProgress) {
              this.callbacks.onProgress({ ...this.progress });
            }

            // 让出事件循环，避免阻塞UI
            await new Promise(resolve => setTimeout(resolve, 0));
          }
        } catch {
          // 忽略单个文件的错误
        }
      }
    } catch (error) {
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

    const trimLength = this.options.rootDir.length + 1;

    try {
      await this.walkDirectory(this.options.rootDir, 0, trimLength);
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
