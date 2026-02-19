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

        if (lowerUri.endsWith('.epub')) {
          setFileType('epub');
          setLoading(false);
          return;
        }

        // 默认作为文本处理
        setFileType('text');
        
        let contentText = '';
        if (uri.startsWith('content://')) {
          // Expo Router decodes params, but SAF API needs correctly encoded IDs
          const decoded = decodeURIComponent(uri);
          const match = decoded.match(/^content:\/\/([^/]+)(\/.+)$/);
          let fixedUri = uri;
          
          if (match) {
            const authority = match[1];
            const path = match[2];
            const treeToken = '/tree/';
            const docToken = '/document/';
            const treeIdx = path.indexOf(treeToken);
            const docIdx = path.indexOf(docToken);
            
            fixedUri = `content://${authority}`;
            if (treeIdx !== -1) {
              if (docIdx !== -1 && docIdx > treeIdx) {
                const treeId = path.substring(treeIdx + treeToken.length, docIdx);
                const docId = path.substring(docIdx + docToken.length);
                fixedUri += `${treeToken}${encodeURIComponent(treeId)}${docToken}${encodeURIComponent(docId)}`;
              } else {
                const treeId = path.substring(treeIdx + treeToken.length);
                fixedUri += `${treeToken}${encodeURIComponent(treeId)}`;
              }
            } else if (docIdx !== -1) {
              const docId = path.substring(docIdx + docToken.length);
              fixedUri += `${docToken}${encodeURIComponent(docId)}`;
            }
          }

          console.log('[Viewer] [DEBUG] Reading SAF URI:', fixedUri);
          contentText = await FileSystem.StorageAccessFramework.readAsStringAsync(fixedUri);
        } else {
          contentText = await FileSystem.readAsStringAsync(uri);
        }

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
          getItemLayout={(_, index) => ({
            length: 24, // 估算行高
            offset: 24 * index,
            index,
          })}
          onScrollToIndexFailed={(info) => {
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
        <EpubViewer uri={uri} initialQuery={query} />
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
 */
function EpubViewer({ uri, initialQuery }: { uri: string; initialQuery?: string }) {
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <script src="https://cdnjs.cloudflare.com/ajax/libs/epub.js/0.3.88/epub.min.js"></script>
      <style>
        body { margin: 0; padding: 0; background: transparent; overflow: hidden; font-family: sans-serif; }
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

        book.ready.then(function() {
          // 如果有初始搜索词，尝试定位
          if ("${initialQuery || ''}") {
            return book.find("${initialQuery || ''}");
          }
        }).then(function(results) {
          if (results && results.length > 0) {
            rendition.display(results[0].cfi);
            // 这里可以添加高亮逻辑
            results.forEach(result => {
              rendition.annotations.add("highlight", result.cfi, {}, (e) => {
                console.log("annotation clicked", e);
              }, "hl");
            });
          } else {
            rendition.display();
          }
        }).catch(function(err) {
          rendition.display();
          console.error("Epub display error:", err);
        });

        // 监听点击翻页
        document.addEventListener('click', function(e) {
          const width = window.innerWidth;
          if (e.clientX < width / 3) rendition.prev();
          else if (e.clientX > width * 2 / 3) rendition.next();
        });

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
