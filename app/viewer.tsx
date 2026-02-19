import { useLocalSearchParams, Stack } from 'expo-router';
import * as React from 'react';
import { View, FlatList, ActivityIndicator, Alert, Dimensions } from 'react-native';
import { Text } from '@/components/ui/text';
import { WebView } from 'react-native-webview';
import * as FileSystem from 'expo-file-system/legacy';

/**
 * 极简文件 & 电子书查看器
 */
export default function ViewerScreen() {
  const { uri: encodedUri, line } = useLocalSearchParams<{ uri: string; line?: string }>();
  const uri = React.useMemo(() => encodedUri ? decodeURIComponent(encodedUri) : '', [encodedUri]);
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

        if (lowerUri.endsWith('.epub')) {
          setFileType('epub');
          setLoading(false);
          return;
        }

        // 默认作为文本处理
        setFileType('text');
        const text = await FileSystem.readAsStringAsync(uri);
        const lines = text.split('\n');
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
        Alert.alert('错误', '无法读取文件内容');
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
          headerShown: fileType === 'text', // EPUB 可能需要自己的全屏逻辑
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
          getItemLayout={(_, index) => ({
            length: 24, // 估算行高
            offset: 24 * index,
            index,
          })}
          onScrollToIndexFailed={(info) => {
            // 如果跳转失败（可能还没加载），尝试滚动到底部或忽略
            listRef.current?.scrollToOffset({ offset: info.averageItemLength * info.index, animated: true });
          }}
          renderItem={({ item, index }) => {
            const isTarget = index === targetLine;
            return (
              <View 
                className={`px-4 py-0.5 flex-row ${isTarget ? 'bg-yellow-500/20' : ''}`}
                style={{ height: 24 }}
              >
                <Text className="text-[10px] text-muted-foreground w-10 text-right pr-2 select-none" style={{ lineHeight: 20 }}>
                  {index + 1}
                </Text>
                <Text className={`text-sm flex-1 ${isTarget ? 'text-foreground font-medium' : 'text-muted-foreground'}`} style={{ lineHeight: 20 }}>
                  {item}
                </Text>
              </View>
            );
          }}
        />
      )}

      {fileType === 'epub' && (
        <EpubViewer uri={uri} />
      )}

      {fileType === 'unknown' && (
        <View className="flex-1 items-center justify-center p-8">
          <Text className="text-center text-muted-foreground">不支持的文件格式</Text>
        </View>
      )}
    </View>
  );
}

/**
 * 简易 EPUB 阅读器组件
 * 使用 WebView + epub.js (CDN)
 */
function EpubViewer({ uri }: { uri: string }) {
  // 注意：在打包应用中最好将 epub.js 资源内置
  // 这里先使用临时方案通过 WebView 加载
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <script src="https://cdnjs.cloudflare.com/ajax/libs/epub.js/0.3.88/epub.min.js"></script>
      <style>
        body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
        #viewer { width: 100vw; height: 100vh; }
      </style>
    </head>
    <body>
      <div id="viewer"></div>
      <script>
        var book = ePub("${uri}");
        var rendition = book.renderTo("viewer", {
          width: "100%",
          height: "100%",
          flow: "paginated",
          manager: "default"
        });
        var display = rendition.display();

        // 监听点击翻页
        document.addEventListener('click', function(e) {
          const width = window.innerWidth;
          if (e.clientX < width / 3) rendition.prev();
          else if (e.clientX > width * 2 / 3) rendition.next();
        });

        // 通信
        window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'ready' }));
      </script>
    </body>
    </html>
  `;

  return (
    <WebView
      source={{ html }}
      style={{ flex: 1, backgroundColor: 'transparent' }}
      originWhitelist={['*']}
      allowFileAccess={true}
      allowFileAccessFromFileURLs={true}
      allowUniversalAccessFromFileURLs={true}
    />
  );
}
