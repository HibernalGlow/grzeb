import { useLocalSearchParams, Stack } from 'expo-router';
import * as React from 'react';
import { View, FlatList, ActivityIndicator, Alert, Dimensions } from 'react-native';
import { Text } from '@/components/ui/text';
import * as FileSystem from 'expo-file-system/legacy';
import { normalizeAndEncodeSafUri } from '@/lib/utils/saf';
import { extractEpubText } from '@/lib/utils/epub';
import { useSettings } from '@/lib/store/settings';

/**
 * 极简文件 & 电子书查看器
 */
export default function ViewerScreen() {
  const { uri: encodedUri, line, query } = useLocalSearchParams<{ uri: string; line?: string; query?: string }>();
  // Expo Router 自动处理解码，无需手动 decodeURIComponent
  const uri = encodedUri || '';
  const targetLine = React.useMemo(() => line ? parseInt(line, 10) : -1, [line]);

  const [content, setContent] = React.useState<string[] | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [fileType, setFileType] = React.useState<'text' | 'epub' | 'unknown'>('unknown');

  const listRef = React.useRef<FlatList>(null);

  React.useEffect(() => {
    if (!uri) return;

    const loadFile = async () => {
      try {
        setLoading(true);
        const lowerUri = uri.toLowerCase();
        const isSaf = uri.startsWith('content://');
        const normalizedUri = isSaf ? normalizeAndEncodeSafUri(uri) : uri;

        let contentText = '';
        if (lowerUri.endsWith('.epub')) {
          setFileType('epub');
          const extracted = await extractEpubText(normalizedUri);
          if (extracted) {
            contentText = extracted;
          } else {
            throw new Error('EPUB 提取失败');
          }
        } else {
          setFileType('text');
          contentText = isSaf 
            ? await FileSystem.StorageAccessFramework.readAsStringAsync(normalizedUri)
            : await FileSystem.readAsStringAsync(normalizedUri);
        }

        const lines = contentText.split('\n');
        setContent(lines);
        setLoading(false);
      } catch (error) {
        console.error('Failed to load file:', error);
        Alert.alert('错误', '无法读取文件内容: ' + (error as Error).message);
        setLoading(false);
      }
    };

    loadFile();
  }, [uri]);

  if (loading) {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator size="large" color="rgb(34, 197, 94)" />
        <Text className="mt-4 text-muted-foreground">加载中...</Text>
      </View>
    );
  }

  // 计算初始滚动位置。
  // 注意：FlatList 的 initialScrollIndex 在渲染大型列表时非常高效，
  // 但它要求必须提供 getItemLayout。
  const initialIndex = targetLine >= 0 && content && targetLine < content.length ? targetLine : undefined;

  return (
    <View className="flex-1 bg-background">
      <Stack.Screen 
        options={{ 
          title: uri.split('/').pop() || '查看器',
          headerShown: true,
        }} 
      />
      
      {(fileType === 'text' || fileType === 'epub') && content && (
        <FlatList
          ref={listRef}
          data={content}
          keyExtractor={(_, index) => index.toString()}
          // 关键性能与定位配置
          initialScrollIndex={initialIndex}
          initialNumToRender={50}
          onScrollToIndexFailed={(info) => {
            // 如果 initialScrollIndex 失败，尝试平滑补偿
            setTimeout(() => {
              if (listRef.current) {
                listRef.current.scrollToIndex({ index: info.index, animated: true, viewPosition: 0.5 });
              }
            }, 500);
          }}
          renderItem={({ item, index }) => {
            const isTarget = index === targetLine;
            return (
              <View 
                className={`px-4 flex-row ${isTarget ? 'bg-yellow-500/20' : ''}`}
                style={{ minHeight: 28, paddingVertical: 4 }}
              >
                <Text 
                  className="text-[10px] text-muted-foreground w-10 text-right pr-2 pt-1 select-none font-mono"
                >
                  {index + 1}
                </Text>
                <Text 
                  className={`text-base flex-1 ${isTarget ? 'text-foreground font-medium' : 'text-muted-foreground'}`}
                  style={{ lineHeight: 22 }}
                >
                  {item || ' '}
                </Text>
              </View>
            );
          }}
        />
      )}

      {fileType === 'unknown' && (
        <View className="flex-1 items-center justify-center p-8">
          <Text className="text-center text-muted-foreground">不支持的文件格式</Text>
        </View>
      )}
    </View>
  );
}
