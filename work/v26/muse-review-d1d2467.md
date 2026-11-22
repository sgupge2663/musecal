# V2-6 レビュー（Muse、2026-11-23）

対象: ブランチ codex/v26-tabpersist、コミット d1d2467。実装は Codex（gpt-6.1-sol / medium）。レビューは Muse（単純寄りタスクとして実施）。

## 照合結果
- CalendarVisibilityPreferences: 専用の SharedPreferences（calendar_visibility）に、アカウント別の「表示 OFF のカレンダーID集合」だけを保存する。認証情報（サインアウトで消去される側）ともイベントキャッシュとも独立。トークン保存なし。
- OFF 集合だけを持つ設計のため、未知のカレンダーは自動的に表示 ON（仕様の初期値どおり）、一覧から消えたカレンダーの ID が残っても表示に影響しない。
- 復元はアカウントをキーに起動時・切替時に行われ、calendarList 取得前のキャッシュ表示にも適用される。タブ操作時に即保存される。
- CalendarVisibilityPreferencesTest 3件で、保存→復元の往復、ON へ戻す操作、新規・消滅 ID、アカウント分離、認証設定消去からの独立を固定。check.sh 成功は実装記録で確認。

## 判定
受理可。指摘なし。

## 未確認（実機側）
- 実機での再起動後の保持は v2 初回配布時のユーザー確認に含める（QC-12）。
