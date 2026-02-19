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

    // 4. 解析 OPF
    const opfFile = zip.file(opfPath);
    if (!opfFile) {
      console.log('[EpubUtils] [DEBUG] OPF file not found:', opfPath);
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
    console.log('[EpubUtils] [DEBUG] Spine length:', spine.length);

    // 5. 提取并清理文本
    const textParts: string[] = [];
    for (const href of spine) {
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

    console.log(`[EpubUtils] [DEBUG] Extraction complete: ${textParts.length} parts`);
    return textParts.join('\n\n');
  } catch (error) {
    console.error('[EpubUtils] [DEBUG] Extraction failed:', error);
    return null;
  }
}
