//! The desktop companion's native side (docs/08): one tray icon per drop, the tray click that opens the
//! panel, and the toast window. Everything else lives in the web pages under `../src`.

use tauri::image::Image;
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager};

/// The tray shows at most three drops (design/Masaustu.dc.html).
const TRAY_ASSETS: usize = 3;

/// Where the clicked tray icon is, in physical pixels; the panel opens next to it.
#[derive(Clone, serde::Serialize)]
struct TrayClick {
    x: f64,
    y: f64,
    width: f64,
    height: f64,
}

fn tray_id(index: usize) -> String {
    format!("asset{index}")
}

/// Draws drop [index] in the tray: the core window renders it (RGBA) with the shared gauge painter.
#[tauri::command]
fn set_tray(app: AppHandle, index: usize, rgba: Vec<u8>, width: u32, height: u32, tooltip: String) -> Result<(), String> {
    let id = tray_id(index);
    let tray = match app.tray_by_id(&id) {
        Some(tray) => tray,
        None => TrayIconBuilder::with_id(&id)
            .on_tray_icon_event(|tray, event| on_tray(tray.app_handle(), event))
            .build(&app)
            .map_err(|e| e.to_string())?,
    };
    tray.set_icon(Some(Image::new_owned(rgba, width, height))).map_err(|e| e.to_string())?;
    tray.set_tooltip(Some(tooltip)).map_err(|e| e.to_string())
}

/// Removes the tray drops from [from] on, when fewer assets are shown.
#[tauri::command]
fn clear_trays(app: AppHandle, from: usize) {
    for index in from..TRAY_ASSETS {
        let _ = app.remove_tray_by_id(&tray_id(index));
    }
}

#[tauri::command]
fn show_toast(app: AppHandle) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("toast") {
        window.show().map_err(|e| e.to_string())?;
    }
    Ok(())
}

fn on_tray(app: &AppHandle, event: TrayIconEvent) {
    if let TrayIconEvent::Click { rect, button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
        let scale = app
            .get_webview_window("panel")
            .and_then(|window| window.scale_factor().ok())
            .unwrap_or(1.0);
        let position = rect.position.to_physical::<f64>(scale);
        let size = rect.size.to_physical::<f64>(scale);
        let click = TrayClick { x: position.x, y: position.y, width: size.width, height: size.height };
        let _ = app.emit_to("panel", "damla:tray", click);
    }
}

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![set_tray, clear_trays, show_toast])
        .setup(|_app| {
            // A menu bar app on macOS: no Dock icon.
            #[cfg(target_os = "macos")]
            _app.set_activation_policy(tauri::ActivationPolicy::Accessory);
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("Damla could not start");
}
