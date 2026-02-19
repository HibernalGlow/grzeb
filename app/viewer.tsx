import { useLocalSearchParams, Stack } from 'expo-router';
import * as React from 'react';
import { View, FlatList, ActivityIndicator, Alert, Dimensions } from 'react-native';
import { Text } from '@/components/ui/text';
import * as FileSystem from 'expo-file-system/legacy';
import { normalizeAndEncodeSafUri } from '@/lib/utils/saf';
import { extractEpubText } from '@/lib/utils/epub';

/**
 * 极简文件 & 电子书查看器
 */
export default function ViewerScreen() {
  const { uri: encodedUri, line, query } = useLocalSearchParams<{ uri: string; line?: string; query?: string }>();
  // Expo Router 自动处理解码，无需手动 decodeURIComponent
  const uri = encodedUri || '';
  const targetLine = React.useMemo(() => line ? parseInt(line, 10) : -1, [line]);

  const [content, setContent] = React.useState<string | string[] | null>(null);
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

        if (lowerUri.endsWith('.epub')) {
          setFileType('text'); // Treat as text
          console.log('[Viewer] [DEBUG] Extracting text from EPUB:', normalizedUri);
          const epubText = await extractEpubText(normalizedUri);
          if (epubText) {
            setContent(epubText.split('\n'));
          } else {
            throw new Error('EPUB 提取失败');
          }
          setLoading(false);
          return;
        }

        // 默认作为文本处理
        setFileType('text');
        
        console.log('[Viewer] [DEBUG] Reading text file:', normalizedUri);
        const contentText = isSaf 
          ? await FileSystem.StorageAccessFramework.readAsStringAsync(normalizedUri)
          : await FileSystem.readAsStringAsync(normalizedUri);

        const lines = contentText.split('\n');
        setContent(lines);
        setLoading(false);

        // 延迟跳转到目标行
        if (targetLine >= 0) {
          setTimeout(() => {
            listRef.current?.scrollToIndex({
              index: targetLine,
              animated: true,
              viewPosition: 0.5,
            });
          }, 500);
        }
      } catch (error) {
        console.error('Failed to load file:', error);
        Alert.alert('错误', '无法读取文件内容: ' + (error as Error).message);
        setLoading(false);
      }
    };

    loadFile();
  }, [uri, targetLine]);

  if (loading) {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator size="large" color="rgb(34, 197, 94)" />
        <Text className="mt-4 text-muted-foreground">加载中...</Text>
      </View>
    );
  }

  return (
    <View className="flex-1 bg-background">
      <Stack.Screen 
        options={{ 
          title: uri.split('/').pop() || '查看器',
          headerShown: fileType === 'text',
        }} 
      />
      
      {fileType === 'text' && Array.isArray(content) && (
        <FlatList
          ref={listRef}
          data={content}
          keyExtractor={(_, index) => index.toString()}
          initialNumToRender={50}
          maxToRenderPerBatch={50}
          windowSize={10}
          onScrollToIndexFailed={(info) => {
            listRef.current?.scrollToOffset({ offset: info.averageItemLength * info.index, animated: true });
          }}
          renderItem={({ item, index }) => {
            const isTarget = index === targetLine;
            // 跳过空行显示或仅显示结构
            if (!item.trim() && !isTarget) {
              return <View style={{ height: 10 }} />;
            }

            return (
              <View 
                className={`px-4 py-1 flex-row ${isTarget ? 'bg-yellow-500/20' : ''}`}
              >
                <Text className="text-[10px] text-muted-foreground w-10 text-right pr-2 select-none" style={{ marginTop: 4 }}>
                  {index + 1}
                </Text>
                <Text className={`text-base flex-1 ${isTarget ? 'text-foreground font-medium' : 'text-muted-foreground'}`}>
                  {item}
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
