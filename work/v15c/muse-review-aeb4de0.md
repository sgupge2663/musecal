# V1-5c レビュー記録（対象: aeb4de0、著者 Codex、レビュアー Muse）

## 確認内容
1. **対象範囲**: `windowsToRefresh(visibleWeekStarts, failedWindows)` が表示窓の計算を終えた後に失敗窓を重複なく追加する。再試行・今すぐ同期の両経路がこの共通ロジックを通る（MainActivity の syncNow が `fetchErrors.keys.filterNotNull()` を渡す）。
2. **エラーの意味論**: エラーキーを `EventWindow?` に変更（null = アクセス許可エラー）。`withoutSuccessfulWindows` で成功した窓のエラーだけを消し、再失敗・破棄・他窓・アクセスエラーは保持される。帯の表示・再試行の振り分け（access 系は requestCalendarAccess）も null キーに追随して正しく移行している。
3. **回帰テスト**: GoogleEventsTest に、表示外の失敗窓が同期対象に含まれること、表示窓だけの成功や表示外窓の再失敗では帯が維持されること、その窓の成功で消えること、重複排除・表示窓なし・他窓/アクセスエラーの保持を検証するケースが追加された。
4. **check.sh / CI**: 実装側で check.sh 成功を記録。スコープは calendar.readonly のみ、トークン保存なし、新規ライブラリ・権限なし。

## Verdict
**受理可、指摘なし。** 実機での最終確認（帯が再試行で消えること）は v0.9 でユーザー判定。
