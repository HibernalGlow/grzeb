/**
 * 搜索结果项组件 - 显示单个文件的搜索结果
 * 支持折叠展开查看所有匹配项
 */

import { Card, CardContent } from '@/components/ui/card';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Icon } from '@/components/ui/icon';
import { Text } from '@/components/ui/text';
import { ChevronDown, ChevronRight, FileText, Folder } from 'lucide-react-native';
import * as React from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import type { FileSearchResult, SearchMatch } from '@/lib/search/types';

interface SearchResultItemProps {
  result: FileSearchResult;
  onPress?: (result: FileSearchResult, match?: SearchMatch) => void;
  defaultExpanded?: boolean;
}

/** 高亮文本组件 */
function HighlightedText({ 
  text, 
  matchText, 
  lineNumber,
  previewStartLine
}: { 
  text: string; 
  matchText: string;
  lineNumber: number;
  previewStartLine: number;
}) {
  // 按行分割
  const lines = text.split('\n');
  
  const elements: React.ReactNode[] = [];
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const currentAbsoluteLine = previewStartLine + i;
    const isMatchLine = currentAbsoluteLine === lineNumber;
    
    // 在匹配行中高亮关键词
    if (isMatchLine) {
      const lowerLine = line.toLowerCase();
      const lowerMatch = matchText.toLowerCase();
      const matchIndex = lowerLine.indexOf(lowerMatch);
      
      if (matchIndex >= 0) {
        const before = line.slice(0, matchIndex);
        const matched = line.slice(matchIndex, matchIndex + matchText.length);
        const after = line.slice(matchIndex + matchText.length);
        
        elements.push(
          <View key={i} className="flex-row flex-wrap py-0.5">
            <Text className="text-xs text-muted-foreground">{`${currentAbsoluteLine + 1}: `}</Text>
            <Text className="text-xs text-foreground" numberOfLines={1}>{before}</Text>
            <Text className="text-xs bg-yellow-500/30 text-yellow-700 dark:text-yellow-300 font-semibold" numberOfLines={1}>{matched}</Text>
            <Text className="text-xs text-foreground" numberOfLines={1}>{after}</Text>
          </View>
        );
        continue;
      }
    }
    
    // 普通行（带起始行号）
    elements.push(
      <Text key={i} className="text-xs text-muted-foreground py-0.5" numberOfLines={1}>
        {`${currentAbsoluteLine + 1}: ${line}`}
      </Text>
    );
  }
  
  return <View className="gap-0.5">{elements}</View>;
}

/** 单个匹配项预览 */
function MatchPreview({ 
  match, 
  onPress 
}: { 
  match: SearchMatch;
  onPress?: () => void;
}) {
  return (
    <Pressable onPress={onPress} className="active:opacity-70">
      <View className="bg-muted/50 rounded-md p-2 my-1 border-l-2 border-primary">
        <HighlightedText 
          text={match.preview} 
          matchText={match.matchText}
          lineNumber={match.lineNumber}
          previewStartLine={match.previewStartLine}
        />
      </View>
    </Pressable>
  );
}

export function SearchResultItem({ 
  result, 
  onPress,
  defaultExpanded = false 
}: SearchResultItemProps) {
  const [isOpen, setIsOpen] = React.useState(defaultExpanded);
  
  const hasMultipleMatches = result.matches.length > 1;
  const matchCount = result.matchCount;
  
  return (
    <Card className="mb-2 overflow-hidden">
      <Collapsible open={isOpen} onOpenChange={setIsOpen}>
        <CollapsibleTrigger asChild>
          <Pressable className="flex-row items-center p-3 active:bg-muted/50">
            {/* 展开/折叠图标 */}
            {hasMultipleMatches && (
              <Icon 
                as={isOpen ? ChevronDown : ChevronRight} 
                className="size-4 text-muted-foreground mr-2"
              />
            )}
            
            {/* 文件图标 */}
            <Icon 
              as={result.isDirectory ? Folder : FileText} 
              className="size-4 text-muted-foreground mr-2"
            />
            
            {/* 文件信息 */}
            <View className="flex-1 flex-row items-center gap-2">
              <Text className="text-sm font-medium text-foreground flex-1" numberOfLines={1}>
                {result.relPath}
              </Text>
              {matchCount > 0 && (
                <View className="bg-primary/10 px-2 py-0.5 rounded-full">
                  <Text className="text-xs text-primary font-medium">
                    {matchCount > 999 ? '999+' : matchCount}
                  </Text>
                </View>
              )}
            </View>
          </Pressable>
        </CollapsibleTrigger>
        
        {/* 匹配内容列表 */}
        {result.matches.length > 0 && (
          <CollapsibleContent>
            <CardContent className="pt-0 pb-3 px-3">
              <ScrollView 
                horizontal={false}
                nestedScrollEnabled
                className="max-h-64"
              >
                {result.matches.map((match, index) => (
                  <MatchPreview 
                    key={`${match.start}-${index}`}
                    match={match}
                    onPress={() => onPress?.(result, match)}
                  />
                ))}
              </ScrollView>
            </CardContent>
          </CollapsibleContent>
        )}
        
        {/* 单个匹配时直接显示 */}
        {result.matches.length === 1 && !isOpen && (
          <View className="px-3 pb-3">
            <MatchPreview 
              match={result.matches[0]}
              onPress={() => onPress?.(result, result.matches[0])}
            />
          </View>
        )}
      </Collapsible>
    </Card>
  );
}

/** 搜索结果列表 */
interface SearchResultListProps {
  results: FileSearchResult[];
  onItemClick?: (result: FileSearchResult, match?: SearchMatch) => void;
  isLoading?: boolean;
}

export function SearchResultList({ 
  results, 
  onItemClick,
  isLoading 
}: SearchResultListProps) {
  if (isLoading) {
    return (
      <View className="flex-1 items-center justify-center p-8">
        <Text className="text-muted-foreground">搜索中...</Text>
      </View>
    );
  }
  
  if (results.length === 0) {
    return (
      <View className="flex-1 items-center justify-center p-8">
        <Text className="text-muted-foreground text-center">
          没有找到匹配的结果
        </Text>
      </View>
    );
  }
  
  return (
    <View className="flex-1">
      {results.map((result, index) => (
        <SearchResultItem
          key={`${result.uri}-${index}`}
          result={result}
          onPress={onItemClick}
          defaultExpanded={result.matches.length <= 3}
        />
      ))}
    </View>
  );
}
