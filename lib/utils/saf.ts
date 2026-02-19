/**
 * Utility functions for Android Storage Access Framework (SAF) URIs
 */

/**
 * Normalizes and encodes a SAF URI to be canonical and readable by system APIs.
 * Preserves tree/document structure surgicaly.
 */
export function normalizeAndEncodeSafUri(uri: string): string {
  if (!uri || !uri.startsWith('content://')) return uri;
  
  try {
    // 1. Decode first to get the raw structure
    const decoded = decodeURIComponent(uri);
    
    // 2. Extract Authority and Path
    const match = decoded.match(/^content:\/\/([^/]+)(\/.+)$/);
    if (!match) return uri;
    
    const authority = match[1];
    const path = match[2];
    
    const treeToken = '/tree/';
    const docToken = '/document/';
    
    const treeIdx = path.indexOf(treeToken);
    const docIdx = path.indexOf(docToken);
    
    let result = `content://${authority}`;
    
    if (treeIdx !== -1) {
      if (docIdx !== -1 && docIdx > treeIdx) {
        // Format: tree/[TREE_ID]/document/[DOC_ID]
        const treeId = path.substring(treeIdx + treeToken.length, docIdx);
        const docId = path.substring(docIdx + docToken.length);
        result += `${treeToken}${encodeURIComponent(treeId)}${docToken}${encodeURIComponent(docId)}`;
      } else {
        // Format: tree/[TREE_ID]
        const treeId = path.substring(treeIdx + treeToken.length);
        result += `${treeToken}${encodeURIComponent(treeId)}`;
      }
    } else if (docIdx !== -1) {
      // Format: document/[DOC_ID]
      const docId = path.substring(docIdx + docToken.length);
      result += `${docToken}${encodeURIComponent(docId)}`;
    } else {
      return uri;
    }
    
    return result;
  } catch (e) {
    console.error('[SAF Utils] URI Normalization failed:', e);
    return uri;
  }
}

/**
 * Extracts a human-readable name from a SAF URI.
 */
export function getSafDisplayName(uri: string): string {
  if (!uri) return '';
  if (!uri.startsWith('content://')) {
    return uri.split('/').pop() || uri;
  }

  try {
    const decoded = decodeURIComponent(uri);
    const parts = decoded.split('/');
    
    // Look for the last meaningful part that is not a reserved keyword
    for (let i = parts.length - 1; i >= 0; i--) {
      const part = parts[i];
      if (
        part && 
        part !== 'tree' && 
        part !== 'document' && 
        !part.startsWith('com.android') && 
        !part.includes('.documents')
      ) {
        // Extract part after last colon (e.g., primary:Documents -> Documents)
        const colonIndex = part.lastIndexOf(':');
        if (colonIndex >= 0) {
          return part.slice(colonIndex + 1);
        }
        return part;
      }
    }
    
    // Fallback: use the very last part of the URI
    const lastPart = uri.split('/').pop() || uri;
    return decodeURIComponent(lastPart);
  } catch {
    return uri.split('/').pop() || uri;
  }
}
