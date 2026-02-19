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

      const ext = uri.split('.').pop()?.toLowerCase() || '';
      const mimeType = getMimeType(uri);
      
      // Android 需要 content:// (FileProvider)
      const contentUri = Platform.OS === 'android' 
        ? await FileSystem.getContentUriAsync(cachedUri)
        : cachedUri;

      if (Platform.OS === 'android') {
        if (isLongPress) {
          const defaultPackage = settings.defaultApps[ext];
          Alert.alert(
            '外部打开管理',
            `后缀: .${ext}\n当前内部默认: ${defaultPackage || '无'}`,
            [
              { text: '取消', style: 'cancel' },
              { 
                text: '清除内部默认', 
                onPress: () => setDefaultApp(ext, null),
                style: 'destructive' 
              },
              { 
                text: '系统默认应用设置', 
                onPress: () => {
                   IntentLauncher.startActivityAsync('android.settings.MANAGE_DEFAULT_APPS_SETTINGS')
                   .catch(() => {
                     // Fallback to general settings if the specific one fails
                     IntentLauncher.startActivityAsync('android.settings.SETTINGS');
                   });
                }
              },
              { 
                text: '尝试重新选择应用', 
                onPress: async () => {
                  try {
                    await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
                      data: contentUri,
                      type: mimeType,
                      flags: 1, // FLAG_GRANT_READ_URI_PERMISSION
                    });
                  } catch (e) {
                    Alert.alert('打开失败', '找不到支持该格式的应用');
                  }
                } 
              }
            ]
          );
        } else {
          // 单击：尝试使用内部记录的默认包名，或者发送通用 VIEW Intent
          const defaultPackage = settings.defaultApps[ext];
          
          try {
            const intentParams: IntentLauncher.IntentLauncherParams = {
              data: contentUri,
              type: mimeType,
              flags: 1, // FLAG_GRANT_READ_URI_PERMISSION
            };
            
            if (defaultPackage) {
              intentParams.packageName = defaultPackage;
            }

            await IntentLauncher.startActivityAsync('android.intent.action.VIEW', intentParams);
          } catch (e) {
            console.warn('[ExternalOpen] View intent failed', e);
            // 如果带包名失败了，可能是应用卸载了，回退到通用选择
            if (defaultPackage) {
               await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
                data: contentUri,
                type: mimeType,
                flags: 1,
              });
            } else {
              Alert.alert('提示', '没有找到能处理该文件的应用');
            }
          }
        }
      } else {
        // iOS 仍然推荐使用 Sharing，因为它内置了系统选择器和各种预览/打开操作
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
