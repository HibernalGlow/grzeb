import { Button } from '@/components/ui/button';
import { Icon } from '@/components/ui/icon';
import { Text } from '@/components/ui/text';
import { Link, Stack, useRouter } from 'expo-router';
import { MoonStar, Star, Sun, Search, FolderOpen } from 'lucide-react-native';
import { useColorScheme } from 'nativewind';
import * as React from 'react';
import { Image, type ImageStyle, View } from 'react-native';

const LOGO = {
  light: require('@/assets/images/react-native-reusables-light.png'),
  dark: require('@/assets/images/react-native-reusables-dark.png'),
};

const SCREEN_OPTIONS = {
  title: 'Grzeb - 小说全文搜索',
  headerTransparent: true,
  headerRight: () => <ThemeToggle />,
};

const IMAGE_STYLE: ImageStyle = {
  height: 76,
  width: 76,
};

export default function Screen() {
  const { colorScheme } = useColorScheme();
  const router = useRouter();

  return (
    <>
      <Stack.Screen options={SCREEN_OPTIONS} />
      <View className="flex-1 items-center justify-center gap-8 p-4">
        <Image source={LOGO[colorScheme ?? 'light']} style={IMAGE_STYLE} resizeMode="contain" />
        
        <View className="gap-4 p-4">
          <Text className="text-lg font-semibold text-center text-foreground">
            本地小说全文搜索工具
          </Text>
          <Text className="text-sm text-center text-muted-foreground">
            类似 VS Code 的搜索体验，支持上下文预览
          </Text>
        </View>
        
        <View className="flex-row gap-3">
          <Link href="/search" asChild>
            <Button size="lg">
              <Icon as={Search} className="size-5 text-primary-foreground" />
              <Text className="text-primary-foreground font-medium">开始搜索</Text>
            </Button>
          </Link>
        </View>
        
        <View className="gap-2 p-4">
          <Text className="text-xs text-muted-foreground text-center">
            功能特点：
          </Text>
          <Text className="text-xs text-muted-foreground text-center">
            • 全文检索 • 上下文预览 • 正则支持
          </Text>
          <Text className="text-xs text-muted-foreground text-center">
            • 大量文件优化 • 可中断搜索
          </Text>
        </View>
      </View>
    </>
  );
}

const THEME_ICONS = {
  light: Sun,
  dark: MoonStar,
};

function ThemeToggle() {
  const { colorScheme, toggleColorScheme } = useColorScheme();

  return (
    <Button
      onPressIn={toggleColorScheme}
      size="icon"
      variant="ghost"
      className="ios:size-9 rounded-full web:mx-4">
      <Icon as={THEME_ICONS[colorScheme ?? 'light']} className="size-5" />
    </Button>
  );
}