import React, { useState } from 'react';
import { Pressable, ActivityIndicator, Alert, Platform } from 'react-native';
import { Icon } from '@/components/ui/icon';
import { ExternalLink } from 'lucide-react-native';
import * as Sharing from 'expo-sharing';
import * as IntentLauncher from 'expo-intent-launcher';
import * as FileSystem from 'expo-file-system/legacy';
import { copySafToCache } from '@/lib/utils/saf';
import { useSettings } from '@/lib/store/settings';

interface ExternalOpenButtonProps {
  uri: string;
  filename?: string;
  className?: string;
}

/**
 * 根据后缀名获取 MIME Type
 */
const getMimeType = (uri: string) => {
  const ext = uri.toLowerCase().split('.').pop();
  switch (ext) {
    case 'epub': return 'application/epub+zip';
    case 'txt': return 'text/plain';
    case 'pdf': return 'application/pdf';
    case 'html': case 'htm': return 'text/html';
    case 'jpg': case 'jpeg': return 'image/jpeg';
    case 'png': return 'image/png';
    case 'gif': return 'image/gif';
    default: return '*/*';
  }
};

export function ExternalOpenButton({ uri, filename, className }: ExternalOpenButtonProps) {
  const [loading, setLoading] = useState(false);
  const { settings, setDefaultApp } = useSettings();

  const handleOpen = async (isLongPress: boolean) => {
    if (loading) return;
    
    setLoading(true);
    try {
      // 1. 复制到缓存 (SAF URI 外部应用无法直接打开)
      const cachedUri = await copySafToCache(uri, filename);
      if (!cachedUri) throw new Error('无法准备临时文件');

      // 获取文件后缀和 MIME Type
      const ext = uri.split('.').pop()?.toLowerCase() || '';
      const mimeType = getMimeType(uri);
      
      // 这里的 cachedUri 是 file:// 路径
      // Android Intent Launcher 需要 content:// (FileProvider) 或者 file://
      // expo-file-system 的 getContentUriAsync 可以获取 content:// URI
      const contentUri = Platform.OS === 'android' 
        ? await FileSystem.getContentUriAsync(cachedUri)
        : cachedUri;

      if (Platform.OS === 'android') {
        const defaultPackage = settings.defaultApps[ext];

        if (isLongPress || !defaultPackage) {
          // 长按或未设置默认应用：使用系统选择器
          // 注意：IntentLauncher 不直接支持选择器并返回结果，
          // 我们使用 Sharing.shareAsync 或者发送不带包名的 Intent
          if (isLongPress) {
            // 提供清除默认设置的选项
            Alert.alert(
              '打开方式',
              `当前后缀 (${ext}) 的默认应用: ${defaultPackage || '未设置'}`,
              [
                { text: '取消', style: 'cancel' },
                { 
                  text: '清除默认应用', 
                  onPress: () => setDefaultApp(ext, null),
                  style: 'destructive' 
                },
                { 
                  text: '使用系统选择器', 
                  onPress: () => Sharing.shareAsync(cachedUri) 
                }
              ]
            );
          } else {
            // 单击且无默认：直接调用分享/打开对话框
            await Sharing.shareAsync(cachedUri);
          }
        } else {
          // 使用记忆的默认包名打开
          try {
            await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
              data: contentUri,
              type: mimeType,
              packageName: defaultPackage,
              flags: 1, // FLAG_GRANT_READ_URI_PERMISSION
            });
          } catch (e) {
            console.warn('[ExternalOpen] Default app failed, falling back to picker', e);
            await Sharing.shareAsync(cachedUri);
          }
        }
      } else {
        // iOS 统一使用 Sharing
        await Sharing.shareAsync(cachedUri);
      }
    } catch (error) {
      console.error('[ExternalOpen] Error:', error);
      Alert.alert('打开失败', (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Pressable 
      onPress={() => handleOpen(false)}
      onLongPress={() => handleOpen(true)}
      className={`p-2 active:bg-muted rounded-md ${className}`}
      disabled={loading}
    >
      {loading ? (
        <ActivityIndicator size="small" color="#666" />
      ) : (
        <Icon as={ExternalLink} className="size-4 text-muted-foreground" />
      )}
    </Pressable>
  );
}
