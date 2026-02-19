/**
 * 全文搜索引擎核心实现
 * 参考 Markor 的 FileSearchEngine 优化策略
 * 支持 Android StorageAccessFramework (content:// URI)
 */

import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';
import { 
  SearchOptions, 
  SearchMatch, 
  FileSearchResult, 
  SearchProgress, 
  SearchCallbacks,
  SearchState,
  DEFAULT_SEARCH_OPTIONS 
} from './types';

// SAF 别名
const SAF = FileSystem.StorageAccessFramework;

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
    
    console.log('[SearchEngine] [DEBUG] Constructor:');
    console.log('  - Platform:', Platform.OS);
    console.log('  - Root dir:', this.options.rootDir);
    console.log('  - Is SAF:', this.isAndroidSaf);
    console.log('  - Query:', this.options.query);
    console.log('  - Regex valid:', !!this.regex);
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
          line = (subStart > 0 ? '...' : '') + line.slice(subStart, subEnd) + (subEnd < line.length ? '...' : '');
        } else {
          line = line.slice(0, maxPreviewLength) + (line.length > maxPreviewLength ? '...' : '');
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

  /** 读取文件内容 */
  private async readFileContent(uri: string): Promise<string | null> {
    try {
      if (this.isAndroidSaf) {
        return await FileSystem.readAsStringAsync(uri);
      }
      return null;
    } catch (error) {
      console.error('[SearchEngine] [DEBUG] Read file error:', error);
      return null;
    }
  }

  /** 读取目录内容 */
  private async readDirectory(uri: string): Promise<string[]> {
    try {
      console.log('[SearchEngine] [DEBUG] Reading directory URI:', uri);
      // readDirectoryAsync 返回的是 full content:// URI 列表
      const entries = await SAF.readDirectoryAsync(uri);
      console.log('[SearchEngine] [DEBUG] Found entries:', entries.length);
      return entries;
    } catch (error) {
      console.error('[SearchEngine] [DEBUG] Read directory error:', error);
      return [];
    }
  }

  /** 检查 URI 是否为目录 */
  private async isDirectory(uri: string): Promise<boolean> {
    try {
      const info = await FileSystem.getInfoAsync(uri);
      return info.isDirectory;
    } catch {
      try {
        await SAF.readDirectoryAsync(uri);
        return true;
      } catch {
        return false;
      }
    }
  }

  /** 从 SAF URI 提取文件名 */
  private extractFileName(uri: string): string {
    // SAF URI 格式: content://com.android.externalstorage.documents/tree/primary%3ADocuments
    // 或: content://com.android.externalstorage.documents/document/primary%3ADocuments%2Ftest.txt
    try {
      const decoded = decodeURIComponent(uri);
      const parts = decoded.split('/');
      // 尝试找到最后一个有意义部分
      for (let i = parts.length - 1; i >= 0; i--) {
        const part = parts[i];
        if (part && part !== 'tree' && part !== 'document' && !part.startsWith('com.')) {
          // 提取 %3A 后面的部分 (primary%3ADocuments -> Documents)
          const colonIndex = part.lastIndexOf('%3A');
          if (colonIndex >= 0) {
            return part.slice(colonIndex + 3);
          }
          return part;
        }
      }
      return uri.split('/').pop() || uri;
    } catch {
      return uri.split('/').pop() || uri;
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
      const content = await this.readFileContent(uri);
      if (!content) {
        return null;
      }
      
      const lines = content.split('\n');

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

        const startLine = Math.max(0, lineNumber - this.options.contextLines);
        const preview = this.extractContext(
          content,
          lines,
          match.index,
          match.index + match[0].length,
          lineNumber
        );

        const indexInLine = this.findMatchPositionInLine(content, match.index, lineNumber, lines);
        
        result.matches.push({
          matchText: match[0],
          start: match.index,
          end: match.index + match[0].length,
          lineNumber,
          preview,
          previewStartLine: startLine,
          indexInLine,
        });

        result.matchCount++;

        if (this.options.isOnlyFirstMatch) {
          break;
        }
      }
    } catch (error) {
      console.error('[SearchEngine] Search in file error:', error);
      return null;
    }

    return result.matches.length > 0 ? result : null;
  }

  /** 遍历目录 */
  private async walkDirectory(
    dirUri: string,
    depth: number
  ): Promise<void> {
    if (this.isCancelled || depth > this.options.maxDepth) {
      console.log('[SearchEngine] Walk cancelled or max depth reached:', depth);
      return;
    }

    console.log('[SearchEngine] Walking directory, depth:', depth);
    this.progress.currentDepth = depth;

    try {
      const entries = await this.readDirectory(dirUri);
      this.progress.pendingDirs = entries.length;

      for (const entryUri of entries) {
        if (this.isCancelled) break;

        const entryName = this.extractFileName(entryUri);
        console.log('[SearchEngine] Processing:', entryName);

        // 检查是否忽略
        if (this.shouldIgnore(entryName)) {
          console.log('[SearchEngine] Ignored:', entryName);
          continue;
        }

        try {
          const isDir = await this.isDirectory(entryUri);
          console.log('[SearchEngine] Is directory:', isDir);
          
          if (isDir) {
            await this.walkDirectory(entryUri, depth + 1);
          } else if (this.isTextFile(entryName)) {
            this.progress.currentFile = entryName;
            this.progress.scannedFiles++;
            
            console.log('[SearchEngine] Searching in file:', entryName);
            const result = await this.searchInFile(entryUri, entryName);
            
            if (result) {
              this.results.push(result);
              this.progress.totalMatches += result.matchCount;
              console.log('[SearchEngine] Found matches:', result.matchCount);
              
              if (this.callbacks.onResult) {
                this.callbacks.onResult(result);
              }
            }

            if (this.callbacks.onProgress) {
              this.callbacks.onProgress({ ...this.progress });
            }

            // 让出事件循环
            await new Promise(resolve => setTimeout(resolve, 10));
          }
        } catch (error) {
          console.error('[SearchEngine] Process entry error:', error);
        }
      }
    } catch (error) {
      console.error('[SearchEngine] Walk directory error:', error);
    }
  }

  /** 开始搜索 */
  async start(): Promise<FileSearchResult[]> {
    if (this.state === 'searching') {
      return this.results;
    }

    if (!this.regex) {
      console.log('[SearchEngine] No valid regex, aborting');
      return [];
    }

    if (!this.isAndroidSaf) {
      console.log('[SearchEngine] Only SAF mode supported on Android');
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

    console.log('[SearchEngine] Starting search...');

    try {
      await this.walkDirectory(this.options.rootDir, 0);
    } catch (error) {
      this.state = 'error';
      console.error('[SearchEngine] Search error:', error);
      if (this.callbacks.onError) {
        this.callbacks.onError(error as Error);
      }
      return this.results;
    }

    this.state = this.isCancelled ? 'cancelled' : 'complete';
    this.progress.isComplete = true;
    console.log('[SearchEngine] Search complete. Results:', this.results.length, 'Matches:', this.progress.totalMatches);

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
    console.log('[SearchEngine] Cancelling search...');
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
