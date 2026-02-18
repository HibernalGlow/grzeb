#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use tauri_vite_mobile::AppBuilder;

pub fn main() {
    AppBuilder::new().run();
}