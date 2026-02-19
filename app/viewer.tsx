import { useLocalSearchParams, Stack } from 'expo-router';
import * as React from 'react';
import { View, FlatList, ActivityIndicator, Alert, Dimensions } from 'react-native';
import { Text } from '@/components/ui/text';
import { WebView } from 'react-native-webview';
import * as FileSystem from 'expo-file-system/legacy';
import { normalizeAndEncodeSafUri } from '@/lib/utils/saf';
import { extractEpubText } from '@/lib/utils/epub';
import { useSettings } from '@/lib/store/settings';
import { EPUBJS_SOURCE } from '@/lib/utils/epubjs-src';

/**
 * 极简文件 & 电子书查看器
 */
export default function ViewerScreen() {
  const { uri: encodedUri, line, query } = useLocalSearchParams<{ uri: string; line?: string; query?: string }>();
  const { settings } = useSettings();
  
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
          if (settings.epubMode === 'simple') {
            setFileType('text'); // Treat as text
            console.log('[Viewer] [DEBUG] Extracting text from EPUB (Simple Mode):', normalizedUri);
            const epubText = await extractEpubText(normalizedUri);
            if (epubText) {
              setContent(epubText.split('\n'));
            } else {
              throw new Error('EPUB 提取失败');
            }
          } else {
            setFileType('epub');
            let finalEpubUri = normalizedUri;
            
            if (isSaf) {
              // WebView cannot access content:// directly. Copy to cache.
              const cacheFile = `${FileSystem.cacheDirectory}preview.epub`;
              console.log('[Viewer] [DEBUG] Copying SAF EPUB to cache:', cacheFile);
              
              const base64 = await FileSystem.StorageAccessFramework.readAsStringAsync(normalizedUri, {
                encoding: FileSystem.EncodingType.Base64
              });
              await FileSystem.writeAsStringAsync(cacheFile, base64, {
                encoding: FileSystem.EncodingType.Base64
              });
              finalEpubUri = cacheFile;
            }
            setContent(finalEpubUri); // Store the file:// or raw URI for EPUB
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
  }, [uri, targetLine, settings.epubMode]);

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

      {fileType === 'epub' && content && (
        <EpubViewer uri={content as string} initialQuery={query} />
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
  const epubPath = uri.startsWith('file://') ? uri : `file://${uri}`;
  
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <style>
        body { margin: 0; padding: 0; background: #fff; overflow-hidden; font-family: sans-serif; }
        #viewer { width: 100vw; height: 100vh; }
        #loading { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #666; font-size: 14px; }
      </style>
    </head>
    <body>
      <div id="loading">正在准备阅读器...</div>
      <div id="viewer"></div>
      <script>
        // Injecting EpubJS source locally
        ${EPUBJS_SOURCE}
        
        function log(msg) {
          window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'log', message: msg }));
        }
        function error(msg) {
          window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'error', message: msg }));
        }

        window.onerror = function(m, s, l, c, e) {
          error("JS Error: " + m + " at " + s + ":" + l);
          return false;
        };

        try {
          log("Initializing book with URI: ${epubPath}");
          var book = ePub("${epubPath}");
          var rendition = book.renderTo("viewer", {
            width: "100%",
            height: "100%",
            flow: "paginated",
            manager: "default"
          });

          book.ready.then(function() {
            log("Book ready");
            document.getElementById('loading').style.display = 'none';
            if ("${initialQuery || ''}") {
              return book.find("${initialQuery || ''}");
            }
          }).then(function(results) {
            if (results && results.length > 0) {
              log("Found match, displaying...");
              rendition.display(results[0].cfi);
              results.forEach(result => {
                rendition.annotations.add("highlight", result.cfi, {}, null, "hl");
              });
            } else {
              rendition.display();
            }
          }).catch(function(err) {
            error("EpubJS Error: " + err.message);
            rendition.display();
          });

          document.addEventListener('click', function(e) {
            const width = window.innerWidth;
            if (e.clientX < width / 3) rendition.prev();
            else if (e.clientX > width * 2 / 3) rendition.next();
          });
        } catch (e) {
          error("Init Error: " + e.message);
        }
      </script>
    </body>
    </html>
  `;

  return (
    <WebView
      source={{ html }}
      style={{ flex: 1, backgroundColor: 'white' }}
      originWhitelist={['*']}
      allowFileAccess={true}
      allowFileAccessFromFileURLs={true}
      allowUniversalAccessFromFileURLs={true}
      onMessage={(event) => {
        try {
          const data = JSON.parse(event.nativeEvent.data);
          if (data.type === 'log') console.log('[WebView Log]', data.message);
          if (data.type === 'error') {
            console.error('[WebView Error]', data.message);
            Alert.alert('阅读器错误', data.message);
          }
        } catch (e) {
          console.log('[WebView Raw]', event.nativeEvent.data);
        }
      }}
    />
  );
}
