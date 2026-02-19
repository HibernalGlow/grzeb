/**
 * 全文搜索页面
 * 类似 VS Code 的搜索侧边栏体验
 */

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Icon } from '@/components/ui/icon';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Separator } from '@/components/ui/separator';
import { Text } from '@/components/ui/text';
import { SearchResultList } from '@/components/search/SearchResultItem';
import { createSearchEngine, SearchEngine, type SearchOptions, type FileSearchResult, type SearchMatch, type SearchProgress } from '@/lib/search';
import { FolderOpen, Search, X, Settings, ChevronDown, ChevronUp } from 'lucide-react-native';
import * as React from 'react';
import { 
  View, 
  ScrollView, 
  Pressable, 
  KeyboardAvoidingView, 
  Platform,
  Alert,
} from 'react-native';
import { Stack, useRouter } from 'expo-router';
import * as FileSystem from 'expo-file-system/legacy';
import { getSafDisplayName } from '@/lib/utils/saf';

// SAF 别名
const SAF = FileSystem.StorageAccessFramework;

/** 搜索选项状态 */
interface SearchOptionsState {
  isCaseSensitive: boolean;
  isRegex: boolean;
  isWholeWord: boolean;
  isOnlyFirstMatch: boolean;
}

export default function SearchScreen() {
  const router = useRouter();

  // 搜索状态
  const [query, setQuery] = React.useState('');
  const [selectedDir, setSelectedDir] = React.useState<string | null>(null);
  const [results, setResults] = React.useState<FileSearchResult[]>([]);
  const [isSearching, setIsSearching] = React.useState(false);
  const [progress, setProgress] = React.useState<SearchProgress | null>(null);
  const [showOptions, setShowOptions] = React.useState(false);
  
  // 搜索选项
  const [searchOptions, setSearchOptions] = React.useState<SearchOptionsState>({
    isCaseSensitive: false,
    isRegex: false,
    isWholeWord: false,
    isOnlyFirstMatch: false,
  });
  
  // 搜索引擎引用
  const engineRef = React.useRef<SearchEngine | null>(null);
  
  // 搜索历史
  const [searchHistory, setSearchHistory] = React.useState<string[]>([]);
  
  /** 选择搜索目录 */
  const pickDirectory = async () => {
    try {
      // Android 使用 StorageAccessFramework 获取目录权限
      if (Platform.OS === 'android') {
        const permissions = await SAF.requestDirectoryPermissionsAsync();
        
        if (permissions.granted) {
          // permissions.directoryUri 是 content:// 格式的 URI
          setSelectedDir(permissions.directoryUri);
        } else {
          // 用户拒绝了权限请求
          Alert.alert('权限被拒绝', '需要目录访问权限才能搜索文件');
        }
      } else if (Platform.OS === 'ios') {
        // iOS 使用 document directory
        const docDir = FileSystem.documentDirectory;
        if (docDir) {
          setSelectedDir(docDir);
        }
      } else {
        // Web 或其他平台 - 使用示例目录
        const docDir = FileSystem.documentDirectory;
        if (docDir) {
          setSelectedDir(docDir);
        }
      }
    } catch (error) {
      console.error('选择目录失败:', error);
      Alert.alert('错误', '选择目录失败，请重试');
    }
  };
  
  /** 执行搜索 */
  const performSearch = async () => {
    if (!query.trim()) {
      return;
    }
    
    if (!selectedDir) {
      Alert.alert('提示', '请先选择要搜索的目录');
      return;
    }
    
    // 保存搜索历史
    if (!searchHistory.includes(query)) {
      setSearchHistory(prev => [query, ...prev.slice(0, 19)]);
    }
    
    // 取消之前的搜索
    if (engineRef.current) {
      engineRef.current.cancel();
    }
    
    setIsSearching(true);
    setResults([]);
    setProgress(null);
    
    const options: Partial<SearchOptions> = {
      rootDir: selectedDir,
      query: query.trim(),
      ...searchOptions,
      maxDepth: 15,
      contextLines: 2,
    };
    
    const engine = createSearchEngine(options, {
      onProgress: (p) => {
        setProgress(p);
      },
      onResult: (result) => {
        // 增量更新结果
        setResults(prev => [...prev, result]);
      },
      onComplete: (finalResults) => {
        setIsSearching(false);
        setProgress(null);
      },
      onError: (error) => {
        setIsSearching(false);
        setProgress(null);
        Alert.alert('搜索错误', error.message);
      },
    });
    
    engineRef.current = engine;
    await engine.start();
  };
  
  /** 取消搜索 */
  const cancelSearch = () => {
    if (engineRef.current) {
      engineRef.current.cancel();
    }
    setIsSearching(false);
    setProgress(null);
  };
  
  /** 清空结果 */
  const clearResults = () => {
    setResults([]);
    setProgress(null);
  };
  
  /** 点击搜索结果 */
  const handleResultPress = (result: FileSearchResult, match?: SearchMatch) => {
    router.push({
      pathname: '/viewer',
      params: {
        uri: result.uri,
        line: match?.lineNumber?.toString() || '-1',
        query: query.trim(),
      },
    });
  };
  
  /** 计算总匹配数 */
  const totalMatches = results.reduce((sum, r) => sum + r.matchCount, 0);
  
  return (
    <>
      <Stack.Screen 
        options={{
          title: '全文搜索',
          headerRight: () => (
            <Button 
              variant="ghost" 
              size="icon"
              onPress={() => setShowOptions(!showOptions)}
            >
              <Icon as={Settings} className="size-5 text-muted-foreground" />
            </Button>
          ),
        }}
      />
      
      <KeyboardAvoidingView 
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1"
      >
        <View className="flex-1 bg-background">
          {/* 搜索输入区域 */}
          <View className="p-3 gap-3">
            {/* 目录选择 */}
            <Pressable 
              onPress={pickDirectory}
              className="flex-row items-center gap-2 p-3 rounded-md border border-input bg-muted/30"
            >
              <Icon as={FolderOpen} className="size-4 text-muted-foreground" />
              <Text 
                className="flex-1 text-sm text-foreground" 
                numberOfLines={1}
              >
                {selectedDir ? getSafDisplayName(selectedDir) : '点击选择搜索目录...'}
              </Text>
              {selectedDir && (
                <Pressable onPress={() => setSelectedDir(null)}>
                  <Icon as={X} className="size-4 text-muted-foreground" />
                </Pressable>
              )}
            </Pressable>
            
            {/* 搜索输入框 */}
            <View className="flex-row gap-2">
              <View className="flex-1 flex-row items-center border border-input rounded-md bg-background overflow-hidden">
                <Input
                  value={query}
                  onChangeText={setQuery}
                  placeholder="输入搜索关键词..."
                  className="flex-1 border-0"
                  returnKeyType="search"
                  onSubmitEditing={performSearch}
                  editable={!isSearching}
                />
                {query.length > 0 && !isSearching && (
                  <Pressable onPress={() => setQuery('')} className="px-2">
                    <Icon as={X} className="size-4 text-muted-foreground" />
                  </Pressable>
                )}
              </View>
              
              <Button
                onPress={isSearching ? cancelSearch : performSearch}
                disabled={!selectedDir || (!isSearching && !query.trim())}
              >
                <Icon as={isSearching ? X : Search} className="size-4 text-primary-foreground" />
                <Text className="text-primary-foreground">
                  {isSearching ? '取消' : '搜索'}
                </Text>
              </Button>
            </View>
            
            {/* 搜索选项（可折叠） */}
            <CollapsibleSection 
              isOpen={showOptions} 
              onToggle={() => setShowOptions(!showOptions)}
              title="搜索选项"
            >
              <View className="flex-row flex-wrap gap-4 pt-2">
                <OptionCheckbox
                  label="区分大小写"
                  checked={searchOptions.isCaseSensitive}
                  onCheckedChange={(checked) => 
                    setSearchOptions(prev => ({ ...prev, isCaseSensitive: checked }))
                  }
                />
                <OptionCheckbox
                  label="正则表达式"
                  checked={searchOptions.isRegex}
                  onCheckedChange={(checked) => 
                    setSearchOptions(prev => ({ ...prev, isRegex: checked }))
                  }
                />
                <OptionCheckbox
                  label="全词匹配"
                  checked={searchOptions.isWholeWord}
                  onCheckedChange={(checked) => 
                    setSearchOptions(prev => ({ ...prev, isWholeWord: checked }))
                  }
                />
                <OptionCheckbox
                  label="每文件首个"
                  checked={searchOptions.isOnlyFirstMatch}
                  onCheckedChange={(checked) => 
                    setSearchOptions(prev => ({ ...prev, isOnlyFirstMatch: checked }))
                  }
                />
              </View>
            </CollapsibleSection>
          </View>
          
          <Separator />
          
          {/* 搜索进度 */}
          {progress && (
            <View className="px-3 py-2 gap-1">
              <Progress 
                value={progress.isComplete ? 100 : 50} 
                className="h-1"
              />
              <View className="flex-row justify-between">
                <Text className="text-xs text-muted-foreground">
                  {progress.currentFile ? `正在搜索: ${progress.currentFile}` : '准备中...'}
                </Text>
                <Text className="text-xs text-muted-foreground">
                  {progress.scannedFiles} 文件 | {progress.totalMatches} 匹配
                </Text>
              </View>
            </View>
          )}
          
          {/* 结果统计 */}
          {results.length > 0 && !isSearching && (
            <View className="px-3 py-2 flex-row justify-between items-center bg-muted/30">
              <Text className="text-sm text-muted-foreground">
                找到 {results.length} 个文件，共 {totalMatches} 处匹配
              </Text>
              <Button variant="ghost" size="sm" onPress={clearResults}>
                <Text className="text-xs text-muted-foreground">清空</Text>
              </Button>
            </View>
          )}
          
          {/* 搜索结果列表 */}
          <ScrollView 
            className="flex-1 px-3"
            contentContainerStyle={{ paddingBottom: 20 }}
            keyboardShouldPersistTaps="handled"
          >
            <SearchResultList 
              results={results}
              onItemClick={handleResultPress}
              isLoading={isSearching && results.length === 0}
            />
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </>
  );
}

/** 可折叠区域组件 */
function CollapsibleSection({ 
  isOpen, 
  onToggle, 
  title, 
  children 
}: { 
  isOpen: boolean; 
  onToggle: () => void;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <View>
      <Pressable 
        onPress={onToggle}
        className="flex-row items-center gap-1"
      >
        <Icon 
          as={isOpen ? ChevronUp : ChevronDown} 
          className="size-4 text-muted-foreground"
        />
        <Text className="text-sm text-muted-foreground">{title}</Text>
      </Pressable>
      {isOpen && children}
    </View>
  );
}

/** 选项复选框 */
function OptionCheckbox({
  label,
  checked,
  onCheckedChange,
}: {
  label: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}) {
  return (
    <Pressable 
      onPress={() => onCheckedChange(!checked)}
      className="flex-row items-center gap-2"
    >
      <Checkbox checked={checked} onCheckedChange={onCheckedChange} />
      <Text className="text-sm text-foreground">{label}</Text>
    </Pressable>
  );
}
