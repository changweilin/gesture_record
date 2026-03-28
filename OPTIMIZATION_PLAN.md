# GestureRecord 優化計畫書

> 建立日期：2026-03-28
> 分支：`claude/optimize-code-review-i8PxK`

---

## 問題總覽

| 優先度 | 編號 | 問題 | 影響檔案 |
|--------|------|------|---------|
| 🔴 高 | T1 | `GestureAccessibilityService.kt` 括號語法錯誤（`playGesture` 函式未正確閉合） | `GestureAccessibilityService.kt` |
| 🔴 高 | T2 | `gesture_items` 缺少複合索引 `(combinationId, slotIndex)` | `Entities.kt` |
| 🟡 中 | T3 | 魔術數字（slot 數量 `9`）與重複字串（Intent extra key）散落各處 | `OverlayService.kt`, `MainActivity.kt` |
| 🟡 中 | T4 | `OverlayService` 中 slot 按鈕列表建立兩次（`setupButtons` & `loadSlots`） | `OverlayService.kt` |
| 🟡 中 | T5 | `onDestroy` 靜默吞掉例外，無任何 logging | `OverlayService.kt` |
| 🟡 中 | T6 | 輸入驗證不足——手勢名稱無長度限制 | `MainActivity.kt` |
| 🟠 中低 | T7 | `fallbackToDestructiveMigration()` 升級時會遺失資料，需正式 Migration | `GestureDatabase.kt` |

---

## 各任務詳細說明

### T1 — 修復括號語法錯誤（GestureAccessibilityService.kt）

**問題**：`playGesture()` 的 `dispatchGesture(...)` 呼叫在第 64 行缺少關閉括號 `}`，
導致 `playSmartClick()` 被意外放在 `playGesture()` 函式體內，編譯時會出錯。

```
// 錯誤：playSmartClick 被包在 playGesture 裡
val success = dispatchGesture(gesture, object : GestureResultCallback() {
    ...
}, null)
                             ← 缺少 } 關閉 playGesture 函式

fun playSmartClick(...) { ... }  ← 實際是在 playGesture 內部
```

**修正**：在 `dispatchGesture(...)` 呼叫後加上正確的閉合括號。

---

### T2 — 新增複合資料庫索引

**問題**：`GestureItem` 的 `indices` 只有 `Index("combinationId")`，
`getItemForSlot(combinationId, slotIndex)` 這個高頻查詢沒有複合索引可用，
導致每次都要掃全部 rows 再篩 `slotIndex`。

**修正**：在 `@Entity` 加入 `Index(value = ["combinationId", "slotIndex"], unique = true)`。
同時需要將資料庫版本從 `2` 升到 `3`（見 T7）。

---

### T3 — 提取常數

**問題**：`9`（slot 總數）、`"COMBINATION_ID"`、`"COMBINATION_NAME"` 等字串/數字
在 `OverlayService.kt` 與 `MainActivity.kt` 中重複出現，修改時容易漏改。

**修正**：在 `OverlayService` companion object 中定義：
```kotlin
const val SLOT_COUNT = 9
const val EXTRA_COMBINATION_ID = "COMBINATION_ID"
const val EXTRA_COMBINATION_NAME = "COMBINATION_NAME"
```

---

### T4 — 消除重複的 slot 按鈕列表

**問題**：`setupButtons()` 和 `loadSlots()` 各自建立一份相同的 `List<Button>`（9 個 `findViewById`），
造成程式碼重複且效能浪費。

**修正**：將按鈕列表提升為 class field `lateinit var slotButtons: List<Button>`，
在 `createOverlayView()` 初始化一次，之後共用。

---

### T5 — 修正 onDestroy 錯誤處理

**問題**：
```kotlin
try { windowManager.removeView(overlayView) } catch (e: Exception) {}
```
靜默吞掉所有例外，問題發生時完全無從得知原因。

**修正**：加入 `Log.e` 記錄例外：
```kotlin
} catch (e: Exception) {
    Log.e(TAG, "移除 overlay view 失敗", e)
}
```

---

### T6 — 新增輸入驗證

**問題**：手勢名稱只檢查 `isNotBlank()`，可輸入超長字串導致 UI 顯示異常。

**修正**：限制名稱最多 50 個字元，並在超過時顯示提示。

---

### T7 — 正式資料庫 Migration

**問題**：`fallbackToDestructiveMigration()` 在 schema 變更時直接刪除所有資料。
T2 加入索引後版本必須從 `2` 升至 `3`，需要定義 Migration。

**修正**：
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_gesture_items_combinationId_slotIndex " +
            "ON gesture_items(combinationId, slotIndex)"
        )
    }
}
```
並將 `fallbackToDestructiveMigration()` 替換為 `.addMigrations(MIGRATION_2_3)`。

---

## 執行順序

```
T1 (語法修復) → T2+T7 (索引+Migration) → T3 (常數) → T4 (重複程式碼) → T5 (logging) → T6 (驗證)
```

T1 必須最先完成，否則專案無法編譯。
T2 與 T7 需一起完成（版本號同步）。

---

## 完成標準

- [x] T1：`GestureAccessibilityService.kt` 編譯無誤
- [x] T2：`gesture_items` 有 `(combinationId, slotIndex)` 唯一複合索引
- [x] T3：`SLOT_COUNT`、`EXTRA_COMBINATION_ID`、`EXTRA_COMBINATION_NAME` 常數定義並全面替換
- [x] T4：`slotButtons` 提升為 field，不再重複建立
- [x] T5：`onDestroy` 的 catch block 加入 `Log.e`
- [x] T6：名稱長度驗證 ≤ 50 字元
- [x] T7：`MIGRATION_2_3` 定義，資料庫版本升至 3，移除 destructive migration

---

## 第二階段：效能輕量化（在其他 App 上運行）

> 目標：減少 GC 壓力、降低 CPU 佔用，讓 overlay 對底層 App 影響最小化。

### P1 — TransparentCaptureView 距離閾值 + 繪製節流

**問題**：
- `ACTION_MOVE` 每次都記錄觸控點，60fps 快速滑動時每秒可產生 200+ 個 `Pair<Float,Float>` 堆疊物件，造成 GC 暫停。
- `invalidate()` 每次觸控事件都觸發整個 View 全量重繪，佔用 UI thread。

**修正**：
```kotlin
private const val MIN_POINT_DISTANCE_SQ = 25f // 5px 距離閾值

// ACTION_MOVE：只有距上一點 ≥ 5px 才記錄並重繪
val dx = x - lastX; val dy = y - lastY
if (dx*dx + dy*dy >= MIN_POINT_DISTANCE_SQ) {
    path.lineTo(x, y); recordedPoints.add(Pair(x, y))
    lastX = x; lastY = y
    postInvalidateOnAnimation() // 與 Choreographer 同步，最多 60fps
}
```

效果：點位數量減少 ~70%，物件分配減少同比，重繪與 Vsync 對齊。

---

### P2 — OverlayService 路徑點位解析快取

**問題**：每次點擊「播放選取槽位」，都對序列化字串做 `split(";")` + `split(",")` + `toFloat()` 全量解析，重複 CPU 計算。

**修正**：加入 `parsedPointsCache: HashMap<Int, List<Pair<Float,Float>>>` 以 `slotIndex` 為 key，
儲存時清除對應 key，播放時 hit cache 直接使用：
```kotlin
val points = parsedPointsCache.getOrPut(item.slotIndex) {
    item.serializedPathData.split(";").mapNotNull { ... }
}
```

---

### P3 — OverlayService 取代 getIdentifier 反射呼叫

**問題**：`resources.getIdentifier("slot$i", "id", packageName)` 在每次 `createOverlayView()` 呼叫時走反射查找，比直接使用 `R.id` 慢。

**修正**：改回明確列舉 `R.id.slot0 .. R.id.slot8`。

---

### 第二階段執行順序

```
P1 (點位節流) → P2 (解析快取) → P3 (移除反射)
```

### 第二階段完成標準

- [x] P1：`TransparentCaptureView` 設 5px 距離閾值，MOVE 事件改用 `postInvalidateOnAnimation()`
- [x] P2：`OverlayService` 新增 `parsedPointsCache`，儲存時清除，播放時命中快取
- [x] P3：`OverlayService.createOverlayView()` 改用直接 `R.id.slotX` 參照
