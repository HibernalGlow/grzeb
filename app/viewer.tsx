import { useLocalSearchParams, Stack } from 'expo-router';
import * as React from 'react';
import { View, ActivityIndicator, Alert, TextInput, Pressable, Platform, Modal, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '@/components/ui/text';
import { Icon } from '@/components/ui/icon';
import { Search, Settings, X, ChevronUp, ChevronDown, List, Type } from 'lucide-react-native';
import { WebView } from 'react-native-webview';
import * as FileSystem from 'expo-file-system/legacy';
import { normalizeAndEncodeSafUri } from '@/lib/utils/saf';
import { useSettings } from '@/lib/store/settings';
import { useColorScheme } from 'nativewind';

// HTML Template (Professional Universal Reader with Monaco)
const READER_HTML_TEMPLATE = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <style>
    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #fff; }
    #loading { position: fixed; top:0; left:0; width:100%; height:100%; display:flex; flex-direction: column; justify-content:center; align-items:center; background:#fff; z-index:1000; font-family:sans-serif; }
    #epub-viewer, #monaco-viewer { width: 100%; height: 100%; display: none; }
    body.dark { background: #1a1a1a; color: #eee; }
    body.dark #loading { background: #1a1a1a; color: #eee; }
    .status { margin-top: 10px; font-size: 12px; color: #888; }
  </style>
</head>
<body class="light">
  <div id="loading">
    <div>正在初始化阅读引擎...</div>
    <div id="status" class="status">准备中...</div>
  </div>
  <div id="epub-viewer"></div>
  <div id="monaco-viewer"></div>

  <script>
    const send = (type, data) => {
      if (window.ReactNativeWebView) {
        window.ReactNativeWebView.postMessage(JSON.stringify({type, ...data}));
      }
    };

    // First, let RN know we are alive
    let bridgeReady = false;
    const checkBridge = setInterval(() => {
      if (window.ReactNativeWebView) {
        send('mounted', { ts: Date.now() });
        bridgeReady = true;
        clearInterval(checkBridge);
      }
    }, 100);

    // Capture logs immediately
    window.console.log = (...args) => send('log', { message: args.join(' ') });
    window.onerror = (msg, url, line) => send('error', { message: msg + ' at ' + url + ':' + line });

    let book, rendition, editor, mode;

    function loadScript(src) {
      return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = src;
        script.onload = resolve;
        script.onerror = () => reject(new Error('Failed to load script: ' + src));
        document.head.appendChild(script);
      });
    }

    async function initEpub(d) {
      document.getElementById('status').innerText = '正在加载图书引擎...';
      try {
        if (!window.ePub) {
          await loadScript('https://cdnjs.cloudflare.com/ajax/libs/epub.js/0.3.88/epub.min.js');
        }
        document.getElementById('epub-viewer').style.display = 'block';
        book = ePub(d.content, { encoding: 'base64' });
        rendition = book.renderTo("epub-viewer", { width: "100%", height: "100%", flow: "scrolled", manager: "continuous" });
        rendition.display().then(() => {
          document.getElementById('loading').style.display = 'none';
          send('ready');
        }).catch(e => send('error', {message: 'EPUB display error: ' + e.message}));
        book.getNavigation().then(n => send('toc', {toc: n.toc}));
      } catch (err) {
        send('error', { message: 'EPUB init error: ' + err.message });
      }
    }

    async function initMonaco(d) {
      document.getElementById('status').innerText = '正在加载代码引擎...';
      try {
        if (!window.require) {
          await loadScript('https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.0/min/vs/loader.min.js');
        }
        require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.0/min/vs' }});
        
        require(['vs/editor/editor.main'], function() {
          document.getElementById('monaco-viewer').style.display = 'block';
          if (!editor) {
            editor = monaco.editor.create(document.getElementById('monaco-viewer'), {
              value: d.content,
              language: 'plaintext',
              readOnly: true,
              automaticLayout: true,
              minimap: { enabled: true },
              fontSize: d.settings?.fontSize || 18,
              theme: d.settings?.theme === 'dark' ? 'vs-dark' : 'vs',
              wordWrap: 'on',
              lineNumbers: 'on',
              renderLineHighlight: 'all',
              scrollBeyondLastLine: false,
            });
          } else {
            editor.setValue(d.content);
          }

          if (d.targetLine) {
            editor.revealLineInCenter(d.targetLine + 1);
            editor.setPosition({ lineNumber: d.targetLine + 1, column: 1 });
          }
          document.getElementById('loading').style.display = 'none';
          send('ready');
        }, function(err) {
          send('error', { message: 'Monaco require error: ' + err.message });
        });
      } catch (err) {
        send('error', { message: 'Monaco init error: ' + err.message });
      }
    }

    window.addEventListener('message', (e) => {
      try {
        const d = JSON.parse(e.data);
        if (d.type === 'load') {
          mode = d.mode;
          applySettings(d.settings);
          if (mode === 'epub') initEpub(d); else initMonaco(d);
        } else if (d.type === 'search') {
            if (editor) try { editor.getAction('actions.find').run(); } catch(e) {}
        } else if (d.type === 'settings') {
            applySettings(d.settings);
        } else if (d.type === 'goto') {
            if (editor) {
              editor.revealLineInCenter(d.line + 1);
              editor.setPosition({ lineNumber: d.line + 1, column: 1 });
            } else if (rendition) rendition.display(d.cfi);
        }
      } catch (err) {
        send('error', { message: 'Message handling error: ' + err.message });
      }
    });

    function applySettings(s) {
      if (!s) return;
      document.body.className = s.theme || 'light';
      if (editor) {
        editor.updateOptions({
          fontSize: s.fontSize || 18,
          theme: s.theme === 'dark' ? 'vs-dark' : 'vs',
          wordWrap: s.wrap !== false ? 'on' : 'off'
        });
      }
      if (rendition) {
        rendition.themes.fontSize((s.fontSize || 18) + 'px');
        const color = (s.theme === 'dark') ? '#eee' : '#333';
        rendition.themes.default({ body: { color: color + ' !important' } });
      }
    }
    
    // Fallback for manually triggered load
    window.initManually = (d) => {
      mode = d.mode;
      if (mode === 'epub') initEpub(d); else initMonaco(d);
    };
  </script>
</body>
</html>
`;

export default function ViewerScreen() {
  const { uri: encodedUri, line } = useLocalSearchParams<{ uri: string; line?: string }>();
  const uri = encodedUri || '';
  const targetLine = React.useMemo(() => line ? parseInt(line, 10) : 0, [line]);
  const { colorScheme } = useColorScheme();

  const [loading, setLoading] = React.useState(true);
  const [showSearch, setShowSearch] = React.useState(false);
  const [showTOC, setShowTOC] = React.useState(false);
  const [toc, setToc] = React.useState<any[]>([]);
  const [searchQuery, setSearchQuery] = React.useState('');
  const webViewRef = React.useRef<WebView>(null);

  const [showSettings, setShowSettings] = React.useState(false);
  const [fontSize, setFontSize] = React.useState(18);
  const [theme, setTheme] = React.useState<'light' | 'dark' | 'sepia'>(colorScheme || 'light');

  const loadingTimeoutRef = React.useRef<any>(null);
  const hasSentLoadRef = React.useRef(false);

  React.useEffect(() => {
    // 25s 超时处理，Monaco 比较大
    loadingTimeoutRef.current = setTimeout(() => {
      if (loading) {
        setLoading(false);
        Alert.alert('加载超时', '资源加载过慢（CDN 响应慢）或网络中断，请检查网络或重试。');
      }
    }, 25000);

    return () => {
      if (loadingTimeoutRef.current) clearTimeout(loadingTimeoutRef.current);
    };
  }, [loading]);

  const updateDisplaySettings = (newFontSize: number, newTheme: 'light' | 'dark' | 'sepia') => {
    setFontSize(newFontSize);
    setTheme(newTheme);
    webViewRef.current?.postMessage(JSON.stringify({
      type: 'settings',
      settings: { fontSize: newFontSize, theme: newTheme, wrap: true }
    }));
  };

  const loadFileInWebView = async () => {
    if (hasSentLoadRef.current) return;
    try {
      console.log('[Viewer] Loading file:', uri);
      const isEpub = uri.toLowerCase().endsWith('.epub');
      const normalizedUri = uri.startsWith('content://') ? normalizeAndEncodeSafUri(uri) : uri;
      
      let content = '';
      if (isEpub) {
        content = await FileSystem.readAsStringAsync(normalizedUri, { encoding: FileSystem.EncodingType.Base64 });
      } else {
        content = uri.startsWith('content://') 
          ? await FileSystem.StorageAccessFramework.readAsStringAsync(normalizedUri)
          : await FileSystem.readAsStringAsync(normalizedUri);
      }

      console.log('[Viewer] Content read success (' + content.length + ' chars), sending to WebView...');
      hasSentLoadRef.current = true;
      webViewRef.current?.postMessage(JSON.stringify({
        type: 'load',
        mode: isEpub ? 'epub' : 'text',
        content,
        targetLine,
        settings: {
          fontSize,
          theme,
          wrap: true,
        }
      }));
    } catch (err) {
      console.error('[Viewer] Load error:', err);
      Alert.alert('错误', '无法读取文件内容: ' + (err as Error).message);
      setLoading(false);
    }
  };

  const handleMessage = (event: any) => {
    try {
      const data = JSON.parse(event.nativeEvent.data);
      if (data.type === 'mounted') {
        console.log('[Viewer] Bridge connected, triggered at: ' + new Date(data.ts).toLocaleTimeString());
        loadFileInWebView();
      } else if (data.type === 'ready') {
        console.log('[Viewer] Reader engine initialized successfully');
        setLoading(false);
        if (loadingTimeoutRef.current) clearTimeout(loadingTimeoutRef.current);
      } else if (data.type === 'toc') {
        setToc(data.toc || []);
      } else if (data.type === 'log') {
        console.log('[WebView Log]', data.message);
      } else if (data.type === 'error') {
        console.error('[WebView Error]', data.message);
        Alert.alert('查看器错误', data.message);
        setLoading(false);
      }
    } catch (e) {
      console.error('[Viewer] Bridge parse error:', e);
    }
  };

  const handleWebViewLoadEnd = () => {
    console.log('[Viewer] WebView onLoadEnd triggered');
    // 如果 2秒 后还没收到 mounted 信号，说明桥接可能挂了，尝试手动触发
    setTimeout(() => {
      if (!hasSentLoadRef.current) {
        console.log('[Viewer] Falling back to manual load trigger...');
        loadFileInWebView();
      }
    }, 2000);
  };

  const handleInternalSearch = () => {
    webViewRef.current?.postMessage(JSON.stringify({
      type: 'search',
      query: searchQuery
    }));
  };

  const goToCfi = (cfi: string) => {
    webViewRef.current?.postMessage(JSON.stringify({ type: 'goto', cfi }));
    setShowTOC(false);
  };

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top']}>
      <Stack.Screen options={{ title: uri.split('/').pop() || '查看器', headerShown: !showSearch }} />
      
      {/* 顶部搜索栏 */}
      {showSearch && (
        <View className="flex-row items-center p-2 bg-background border-b border-border">
          <Pressable onPress={() => setShowSearch(false)} className="p-2">
            <Icon as={X} size={20} />
          </Pressable>
          <TextInput
            className="flex-1 h-10 px-3 bg-muted rounded-md text-foreground"
            placeholder="搜索当前文件..."
            value={searchQuery}
            onChangeText={setSearchQuery}
            onSubmitEditing={handleInternalSearch}
            autoFocus
          />
          <Pressable onPress={handleInternalSearch} className="p-2">
            <Icon as={Search} size={20} className="text-primary" />
          </Pressable>
        </View>
      )}

      {/* WebView 查看器 */}
      <View className="flex-1 relative">
        <WebView
          ref={webViewRef}
          originWhitelist={['*']}
          source={{ html: READER_HTML_TEMPLATE }}
          onMessage={handleMessage}
          onLoadEnd={handleWebViewLoadEnd}
          javaScriptEnabled={true}
          domStorageEnabled={true}
          allowFileAccess={true}
          mixedContentMode="always"
          style={{ backgroundColor: 'transparent' }}
        />
        
        {loading && (
          <View className="absolute inset-0 items-center justify-center bg-background">
            <ActivityIndicator size="large" color="rgb(34, 197, 94)" />
            <Text className="mt-4 text-muted-foreground font-medium text-center px-10">
              正在初始化阅读引擎...{"\n"}
              如果是第一次加载，可能需要从网络下载组件
            </Text>
          </View>
        )}
      </View>

      {/* 底部悬浮按钮组 */}
      {!loading && !showSearch && (
        <View className="absolute bottom-6 right-6 gap-3">
          <Pressable 
            onPress={() => setShowSettings(true)}
            className="w-12 h-12 rounded-full bg-secondary items-center justify-center shadow-lg"
          >
            <Icon as={Type} size={20} className="text-secondary-foreground" />
          </Pressable>
          {toc.length > 0 && (
            <Pressable 
              onPress={() => setShowTOC(true)}
              className="w-12 h-12 rounded-full bg-secondary items-center justify-center shadow-lg"
            >
              <Icon as={List} size={20} className="text-secondary-foreground" />
            </Pressable>
          )}
          <Pressable 
            onPress={() => setShowSearch(true)}
            className="w-12 h-12 rounded-full bg-primary items-center justify-center shadow-lg"
          >
            <Icon as={Search} size={20} className="text-primary-foreground" />
          </Pressable>
        </View>
      )}

      {/* 目录 Modal */}
      <Modal visible={showTOC} animationType="slide" transparent={true} onRequestClose={() => setShowTOC(false)}>
        <View className="flex-1 bg-black/50 justify-end">
          <View className="bg-background h-2/3 rounded-t-3xl p-4">
            <View className="flex-row justify-between items-center mb-4">
              <Text className="text-xl font-bold">目录</Text>
              <Pressable onPress={() => setShowTOC(false)} className="p-2">
                <Icon as={X} size={24} />
              </Pressable>
            </View>
            <ScrollView className="flex-1">
              {toc.map((item, index) => (
                <Pressable 
                  key={index} 
                  onPress={() => goToCfi(item.href)}
                  className="py-3 border-b border-border"
                >
                  <Text className="text-base">{item.label}</Text>
                </Pressable>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>

      {/* 设置 Modal */}
      <Modal visible={showSettings} animationType="fade" transparent={true} onRequestClose={() => setShowSettings(false)}>
        <Pressable className="flex-1 bg-black/20" onPress={() => setShowSettings(false)}>
          <View className="absolute bottom-24 right-6 bg-background p-4 rounded-2xl shadow-xl w-64 border border-border">
            <Text className="font-bold mb-3">显示设置</Text>
            
            <View className="flex-row items-center justify-between mb-4">
              <Text>字号 ({fontSize})</Text>
              <View className="flex-row gap-2">
                <Pressable onPress={() => updateDisplaySettings(Math.max(12, fontSize - 2), theme)} className="bg-muted p-2 rounded-md">
                  <Text>-</Text>
                </Pressable>
                <Pressable onPress={() => updateDisplaySettings(Math.min(32, fontSize + 2), theme)} className="bg-muted p-2 rounded-md">
                  <Text>+</Text>
                </Pressable>
              </View>
            </View>

            <View className="flex-row justify-between">
              {(['light', 'dark', 'sepia'] as const).map((t) => (
                <Pressable 
                  key={t}
                  onPress={() => updateDisplaySettings(fontSize, t)}
                  className={`px-3 py-2 rounded-md border ${theme === t ? 'border-primary bg-primary/10' : 'border-border'}`}
                >
                  <Text className={`capitalize ${theme === t ? 'text-primary' : 'text-foreground'}`}>{t}</Text>
                </Pressable>
              ))}
            </View>
          </View>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}
