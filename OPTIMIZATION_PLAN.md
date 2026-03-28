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

- [ ] T1：`GestureAccessibilityService.kt` 編譯無誤
- [ ] T2：`gesture_items` 有 `(combinationId, slotIndex)` 唯一複合索引
- [ ] T3：`SLOT_COUNT`、`EXTRA_COMBINATION_ID`、`EXTRA_COMBINATION_NAME` 常數定義並全面替換
- [ ] T4：`slotButtons` 提升為 field，不再重複建立
- [ ] T5：`onDestroy` 的 catch block 加入 `Log.e`
- [ ] T6：名稱長度驗證 ≤ 50 字元
- [ ] T7：`MIGRATION_2_3` 定義，資料庫版本升至 3，移除 destructive migration
