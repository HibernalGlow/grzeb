/**
 * 全文搜索引擎核心实现
 * 参考 Markor 的 FileSearchEngine 优化策略
 * 支持 Android StorageAccessFramework (content:// URI)
 */

import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';
import JSZip from 'jszip';
import { Buffer } from 'buffer';
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
      if (uri.toLowerCase().endsWith('.epub')) {
        return await this.readEpubContent(uri);
      }

      if (this.isAndroidSaf) {
        return await FileSystem.readAsStringAsync(uri);
      }
      return null;
    } catch (error) {
      console.error('[SearchEngine] [DEBUG] Read file error:', error);
      return null;
    }
  }

  /** 读取 EPUB 内容并提取文本（简易版） */
  private async readEpubContent(uri: string): Promise<string | null> {
    try {
      console.log('[SearchEngine] [DEBUG] Start extracting EPUB:', uri);
      
      const base64 = await FileSystem.readAsStringAsync(uri, { 
        encoding: FileSystem.EncodingType.Base64 
      });
      console.log('[SearchEngine] [DEBUG] EPUB Base64 read, length:', base64.length);

      const zip = await JSZip.loadAsync(Buffer.from(base64, 'base64'));
      console.log('[SearchEngine] [DEBUG] EPUB Zip loaded');

      // 1. 获取 OPF 路径
      const containerFile = zip.file('META-INF/container.xml');
      if (!containerFile) {
        console.log('[SearchEngine] [DEBUG] EPUB has no container.xml');
        return null;
      }
      
      const containerXml = await containerFile.async('text');
      const opfPathMatch = containerXml.match(/full-path="([^"]+)"/);
      const opfPath = opfPathMatch ? opfPathMatch[1] : 'OEBPS/content.opf';
      const rootDir = opfPath.includes('/') ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : '';
      console.log('[SearchEngine] [DEBUG] OPF path:', opfPath);

      // 2. 解析 OPF
      const opfFile = zip.file(opfPath);
      if (!opfFile) {
        console.log('[SearchEngine] [DEBUG] OPF file not found:', opfPath);
        return null;
      }

      const opfText = await opfFile.async('text');
      const itemMap: Record<string, string> = {};
      const itemRegex = /<item[^>]+id="([^"]+)"[^>]+href="([^"]+)"/g;
      let m;
      while ((m = itemRegex.exec(opfText)) !== null) {
        itemMap[m[1]] = m[2];
      }

      const spine: string[] = [];
      const itemRefRegex = /<itemref[^>]+idref="([^"]+)"/g;
      while ((m = itemRefRegex.exec(opfText)) !== null) {
        const idref = m[1];
        if (itemMap[idref]) {
          spine.push(itemMap[idref]);
        }
      }
      console.log('[SearchEngine] [DEBUG] Spine length:', spine.length);

      // 3. 提取文本
      const textParts: string[] = [];
      for (const href of spine) {
        if (this.isCancelled) break;
        const decodedHref = decodeURIComponent(href);
        const filePath = rootDir + decodedHref;
        const file = zip.file(filePath);
        if (file) {
          const html = await file.async('text');
          const text = html
            .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
            .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
            .replace(/<[^>]+>/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
          textParts.push(text);
        }
      }

      console.log(`[SearchEngine] [DEBUG] EPUB extraction complete, length: ${textParts.length} parts`);
      return textParts.join('\n\n');
    } catch (error) {
      console.error('[SearchEngine] [DEBUG] EPUB processing failed:', error);
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

  /** 递归扫描目录 */
  private async walkDirectory(uri: string, depth: number): Promise<void> {
    if (this.isCancelled || depth > this.options.maxDepth) {
      console.log(`[SearchEngine] [DEBUG] Skipping scan: cancelled=${this.isCancelled}, depth=${depth}`);
      return;
    }

    console.log(`[SearchEngine] [DEBUG] Scanning directory: ${uri} (depth: ${depth})`);
    this.progress.currentDepth = depth;
    this.callbacks.onProgress?.(this.progress);

    try {
      const entries = await this.readDirectory(uri);
      this.progress.pendingDirs += entries.length;
      this.callbacks.onProgress?.(this.progress);

      for (const entryUri of entries) {
        if (this.isCancelled) break;
        
        try {
          const isDir = await this.isDirectory(entryUri);
          const name = this.extractFileName(entryUri);
          
          if (isDir) {
            if (!this.shouldIgnore(name)) {
              await this.walkDirectory(entryUri, depth + 1);
            }
          } else {
            if (this.isTextFile(name)) {
              this.progress.scannedFiles++;
              this.progress.currentFile = name;
              if (this.callbacks.onProgress) {
                this.callbacks.onProgress({ ...this.progress });
              }

              const result = await this.searchInFile(entryUri, name);
              if (result) {
                this.results.push(result);
                this.progress.totalMatches += result.matchCount;
                if (this.callbacks.onResult) {
                  this.callbacks.onResult(result);
                }
              }
            }
          }
          
          // 给 UI 线程喘息机会
          if (this.progress.scannedFiles % 5 === 0) {
            await new Promise(resolve => setTimeout(resolve, 0));
          }
        } catch (error) {
          console.error('[SearchEngine] [DEBUG] Entry processing failed:', entryUri, error);
        }
      }
    } catch (error) {
      console.error('[SearchEngine] [DEBUG] Walk directory failed:', uri, error);
    }
  }

  /** 开始搜索 */
  async start(): Promise<FileSearchResult[]> {
    if (this.state === 'searching') {
      return this.results;
    }

    if (!this.regex) {
      console.log('[SearchEngine] [DEBUG] No valid regex, aborting');
      return [];
    }

    if (!this.isAndroidSaf) {
      console.log('[SearchEngine] [DEBUG] Only SAF mode supported on Android');
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

    console.log('[SearchEngine] [DEBUG] Starting search...');

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
