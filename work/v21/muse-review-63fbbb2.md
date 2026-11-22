# V2-1 レビュー（Muse、2026-11-22）

対象: ブランチ codex/v21-scope、コミット 2cc4a27（実装）＋ 63fbbb2（スコープ訂正）。実装は Codex（gpt-6.1-sol / medium）、レビューは Muse（単純寄りタスクとして実施）。

## 照合結果
- 要求スコープは CalendarAuth の単一定義 REQUESTED_SCOPE_URIS に集約され、最終形は calendar.events ＋ calendar.calendarlist.readonly の2つ。calendarList.list が calendar.events では認可されない問題は、Codex の実装時レビューが公式ドキュメントを根拠に発見し、proposal.md 決定1を訂正したうえで本修正に反映済み。
- Google Cloud 同意画面側も同2スコープに更新・保存済み（2026-11-22、Muse のブラウザ作業で再読み込み確認まで完了）。
- トークン非保存、認可ゲートウェイの一元化は維持。再試行は requestCalendarAccess 経由に統一され、取得中の ConsentRequired でも同意フローへ戻れる。再認可後は表示窓と失敗窓を再取得する（V1-5c の失敗窓回収と整合）。
- CalendarAuthTest は実際の Scope 一覧・Granted・ConsentRequired・Failed を固定。check.sh は実装側で成功を確認。

## 判定
受理可。指摘なし。

## 未確認（実機側）
- 端末での再同意フローと、再同意後の読み取り継続は v2 初回配布時のユーザー確認で照合する（QC-12 に含める）。
