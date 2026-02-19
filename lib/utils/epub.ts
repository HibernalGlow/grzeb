import * as FileSystem from 'expo-file-system/legacy';
import JSZip from 'jszip';
import { Buffer } from 'buffer';

const SAF = FileSystem.StorageAccessFramework;

/**
 * 从 EPUB 文件中提取纯文本
 * @param uri 文件的 URI (可以是 SAF content:// URI)
 * @returns 提取出的纯文本内容，如果失败则返回 null
 */
export async function extractEpubText(uri: string): Promise<string | null> {
  try {
    console.log('[EpubUtils] [DEBUG] Extracting text from EPUB:', uri);
    
    // 1. 读取 Base64
    let base64: string;
    if (uri.startsWith('content://')) {
      base64 = await SAF.readAsStringAsync(uri, { 
        encoding: FileSystem.EncodingType.Base64 
      });
    } else {
      base64 = await FileSystem.readAsStringAsync(uri, { 
        encoding: FileSystem.EncodingType.Base64 
      });
    }
    
    if (!base64) return null;

    // 2. 加载 Zip
    const zip = await JSZip.loadAsync(Buffer.from(base64, 'base64'));

    // 3. 获取 OPF 路径
    const containerFile = zip.file('META-INF/container.xml');
    if (!containerFile) {
      console.log('[EpubUtils] [DEBUG] EPUB has no container.xml');
      return null;
    }
    
    const containerXml = await containerFile.async('text');
    const opfPathMatch = containerXml.match(/full-path="([^"]+)"/);
    const opfPath = opfPathMatch ? opfPathMatch[1] : 'OEBPS/content.opf';
    const rootDir = opfPath.includes('/') ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : '';

    // 4. 解析 OPF 获取 Manifest 和 Spine
    const opfFile = zip.file(opfPath);
    if (!opfFile) {
      console.log('[EpubUtils] [DEBUG] OPF file not found:', opfPath);
      return null;
    }

    const opfText = await opfFile.async('text');
    
    // 建立 ID -> href 和 ID -> properties 的映射
    const itemMap: Record<string, { href: string; props: string }> = {};
    const itemRegex = /<item\s+[^>]*?id="([^"]+)"\s+[^>]*?href="([^"]+)"(?:[^>]*?properties="([^"]*)")?/g;
    let m;
    while ((m = itemRegex.exec(opfText)) !== null) {
      itemMap[m[1]] = { href: m[2], props: m[3] || '' };
    }

    // 解析 Spine 并过滤导航/冗余文件
    const spine: string[] = [];
    const itemRefRegex = /<itemref\s+[^>]*?idref="([^"]+)"/g;
    while ((m = itemRefRegex.exec(opfText)) !== null) {
      const idref = m[1];
      const item = itemMap[idref];
      if (item) {
        const lowerHref = item.href.toLowerCase();
        const lowerProps = item.props.toLowerCase();
        
        // 过滤条件：
        // 1. properties 包含 nav
        // 2. 名字包含常见的目录/封面/样式关键字
        // 3. 后缀不是 html/xhtml (有些 mobi 转 epub 会带奇怪文件)
        if (
          lowerProps.includes('nav') || 
          lowerHref.includes('nav.') || 
          lowerHref.includes('toc.') || 
          lowerHref.includes('content.opf') ||
          lowerHref.includes('cover') ||
          lowerHref.includes('titlepage') ||
          (!lowerHref.endsWith('.html') && !lowerHref.endsWith('.xhtml') && !lowerHref.endsWith('.htm'))
        ) {
          console.log('[EpubUtils] [DEBUG] Skipping non-content file:', item.href);
          continue;
        }
        spine.push(item.href);
      }
    }
    console.log('[EpubUtils] [DEBUG] Final spine length:', spine.length);

    // 6. 提取并清理文本
    const textParts: string[] = [];
    for (const href of spine) {
      const decodedHref = decodeURIComponent(href);
      const filePath = rootDir + decodedHref;
      const file = zip.file(filePath);
      if (file) {
        let html = await file.async('text');
        
        // a. 移除不可见标签
        html = html
          .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
          .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
          .replace(/<head[^>]*>[\s\S]*?<\/head>/gi, '');

        // b. 将块级标签替换为换行符
        // 之前：直接替换为 \n 可能会导致内容中间夹杂过多 \n
        // 现在：处理为有规律的换行，避免合并后产生过多空行
        const blockTags = /<\/?(p|div|h[1-6]|li|tr|section|article)[^>]*>/gi;
        let text = html.replace(blockTags, (tag) => {
          return tag.startsWith('</') || tag.toLowerCase().startsWith('<br') ? '\n' : '';
        });

        // c. 移除所有剩余标签
        text = text.replace(/<[^>]+>/g, ' ');

        // d. 清理多余空白
        // 处理逻辑：合并水平空白，将连续换行限制在最多两个
        text = text
          .replace(/[ \t]+/g, ' ') 
          .replace(/\n\s*\n/g, '\n\n')
          .split('\n')
          .map(line => line.trim())
          .filter(line => line.length > 0)
          .join('\n');

        if (text) {
          textParts.push(text);
        }
      }
    }

    console.log(`[EpubUtils] [DEBUG] Extraction complete: ${textParts.length} segments`);
    // 最终各分段（章节/文件）之间用双换行隔开
    return textParts.join('\n\n');
  } catch (error) {
    console.error('[EpubUtils] [DEBUG] Extraction failed:', error);
    return null;
  }
}
