import { useState, useEffect } from 'react';
import * as FileSystem from 'expo-file-system/legacy';
import { Platform } from 'react-native';

export type EpubMode = 'standard' | 'simple';

export interface AppSettings {
  epubMode: EpubMode;
}

const DEFAULT_SETTINGS: AppSettings = {
  epubMode: 'simple', // 默认极简模式
};

const SETTINGS_FILE = `${FileSystem.documentDirectory}settings.json`;

class SettingsStore {
  private settings: AppSettings = { ...DEFAULT_SETTINGS };
  private listeners: Set<(settings: AppSettings) => void> = new Set();
  private isLoaded: boolean = false;

  constructor() {
    this.load();
  }

  async load() {
    try {
      if (Platform.OS === 'web') {
        this.isLoaded = true;
        return;
      }
      
      const info = await FileSystem.getInfoAsync(SETTINGS_FILE);
      if (info.exists) {
        const content = await FileSystem.readAsStringAsync(SETTINGS_FILE);
        const parsed = JSON.parse(content);
        this.settings = { ...DEFAULT_SETTINGS, ...parsed };
      }
    } catch (error) {
      console.error('[SettingsStore] Failed to load settings:', error);
    } finally {
      this.isLoaded = true;
      this.notify();
    }
  }

  async save() {
    try {
      if (Platform.OS === 'web') return;
      await FileSystem.writeAsStringAsync(SETTINGS_FILE, JSON.stringify(this.settings));
    } catch (error) {
      console.error('[SettingsStore] Failed to save settings:', error);
    }
  }

  getSettings(): AppSettings {
    return this.settings;
  }

  async updateSettings(updates: Partial<AppSettings>) {
    this.settings = { ...this.settings, ...updates };
    this.notify();
    await this.save();
  }

  subscribe(listener: (settings: AppSettings) => void) {
    this.listeners.add(listener);
    if (this.isLoaded) {
      listener(this.settings);
    }
    return () => this.listeners.delete(listener);
  }

  private notify() {
    this.listeners.forEach(l => l(this.settings));
  }
}

export const settingsStore = new SettingsStore();

export function useSettings() {
  const [settings, setSettings] = useState<AppSettings>(settingsStore.getSettings());

  useEffect(() => {
    const unsubscribe = settingsStore.subscribe(setSettings);
    return () => {
      unsubscribe();
    };
  }, []);

  return {
    settings,
    updateSettings: (updates: Partial<AppSettings>) => settingsStore.updateSettings(updates),
  };
}
