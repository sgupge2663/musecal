# v2 タスク: 書き込み対応

正本は proposal.md。完了条件と検証方法は各タスクに付ける。検証は EM-3 のフロー（エージェント一次判定→ユーザーは最終判定のみ）。

- [ ] V2-1 認可の拡張（calendar.events ＋ calendar.calendarlist.readonly）
  - 内容: アプリの要求スコープを `calendar.readonly` から `calendar.events` ＋ `calendar.calendarlist.readonly` に変更する。同意画面へのスコープ追加は Muse がブラウザで別途実施する（このタスクはアプリ側）。再同意が必要な状態では、V1-5 の許可系エラー＋再試行（サインアウト→ログインし直し）で詰まらないこと
  - 完了条件: 再同意後に読み取りが v1 と同等に動き、書き込み可能なトークンが取得できる。スコープ文字列と同意状態をテストで固定する
  - 検証方法: テスト＋check.sh。実機の再同意フローは配布時にユーザー確認


  - 実装記録（2026-11-22 / Codex、レビュー・実機確認前）:
    - CalendarAuth の単一定義を calendar.events へ変更。認可ゲートウェイとトークン非保存を維持。
    - 再試行を requestCalendarAccess 経由に統一し、取得中の ConsentRequired でも同意画面に戻れるようにした。再認可後は表示窓と失敗窓を再取得する。既存の許可系文言・サインアウト経路は維持。
    - CalendarAuthTest で実際の Scope 一覧、Granted（トークン優先）、ConsentRequired、応答不足の Failed を検証。FetchFailureTest は SecurityException と両 API の 401/403 を網羅。
    - 独立レビュー: 再試行時に画面外の失敗窓が漏れる指摘は修正済み・独立再確認済み。calendarList.list は calendar.events 単独では認可されないため、calendar.calendarlist.readonly 追加は提案側で訂正し対処済み（https://developers.google.com/workspace/calendar/api/v3/reference/calendarList/list#authorization）。
    - 訂正（2026-11-22）: main 最新の proposal.md 決定1を確認し、REQUESTED_SCOPE_URIS を calendar.events ＋ calendar.calendarlist.readonly の2件へ修正。CalendarAuthTest は URI 定義と実際の Scope 一覧がこの2件だけであることを検証する。独立レビューで指摘なし（STATUS.md と制約対象に変更なし）。訂正後も本体の scripts/env.sh を source して ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK、CalendarAuthTest 4件・失敗0・エラー0）。共有 Gradle キャッシュへの書き込みに実行権限拡張を使用。
    - 検証: 本体の scripts/env.sh を source して ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK、CalendarAuthTest 4件・失敗0）。共有 Gradle キャッシュの書き込みに実行権限拡張を使用。PKIX 失敗なし。実機での再同意・読み取り確認は未実施。Google Cloud コンソール・STATUS.md は未変更。

- [ ] V2-2 予定の作成
  - 内容: 新規作成の入口（月表示のボタン）と編集画面（タイトル・開始・終了・終日・カレンダー・色）、events.insert、成功後に該当窓を再取得して表示・キャッシュを一致させる。失敗時は日本語エラー＋再試行、入力内容は保持する
  - 完了条件: アプリで作った予定がサーバーに登録され、再取得後の表示とキャッシュが一致する
  - 検証方法: テスト＋check.sh＋一次判定（スクショ）。Web 側での一致は QC-12（V2-5）

- [ ] V2-3 予定の編集
  - 内容: 既存予定を編集画面で開き、同じ項目を変更して保存する（events.patch/update）。繰り返し予定は「この予定のみ」「すべての予定」の選択肢を出す（制約があれば切り分けて記録）
  - 完了条件: 編集結果がサーバー・表示・キャッシュで一致する。他人の予定（祝日カレンダー等の読み取り専用カレンダー）は編集不可として扱う
  - 検証方法: テスト＋check.sh＋一次判定

- [ ] V2-4 予定の削除
  - 内容: 編集画面または日ポップアップから、確認ダイアログつきで削除する（events.delete）。繰り返し予定は V2-3 と同じ選択肢。成功後は窓を再取得
  - 完了条件: 削除がサーバー・表示・キャッシュで一致し、確認なしに消える経路がない
  - 検証方法: テスト＋check.sh＋一次判定

- [ ] V2-5 仕様 converge と QC-12
  - 内容: REQUIREMENTS.md §8 を書き込み対応の実態に改訂する（「読み取り専用」の記述を更新）。docs/qc.md に QC-12（作成・編集・削除の Web との一致の実機照合）を追加する
  - 完了条件: 正本・STATUS・QC が実装の実態と一致している
  - 検証方法: レビューで記録の整合を確認。実機照合はユーザーの v2 初回配布時に実施
