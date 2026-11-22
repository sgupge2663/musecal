# v2 タスク: 書き込み対応

正本は proposal.md。完了条件と検証方法は各タスクに付ける。検証は EM-3 のフロー（エージェント一次判定→ユーザーは最終判定のみ）。

- [x] V2-1 認可の拡張（calendar.events ＋ calendar.calendarlist.readonly）
  - 内容: アプリの要求スコープを `calendar.readonly` から `calendar.events` ＋ `calendar.calendarlist.readonly` に変更する。同意画面へのスコープ追加は Muse がブラウザで別途実施する（このタスクはアプリ側）。再同意が必要な状態では、V1-5 の許可系エラー＋再試行（サインアウト→ログインし直し）で詰まらないこと
  - 完了条件: 再同意後に読み取りが v1 と同等に動き、書き込み可能なトークンが取得できる。スコープ文字列と同意状態をテストで固定する
  - 検証方法: テスト＋check.sh。実機の再同意フローは配布時にユーザー確認
  - 統合: 2026-11-22、Muse がレビュー（work/v21/muse-review-63fbbb2.md、受理可）のうえ main へ統合。Google Cloud 同意画面側の2スコープ化も同日完了


  - 実装記録（2026-11-22 / Codex、レビュー・実機確認前）:
    - CalendarAuth の単一定義を calendar.events へ変更。認可ゲートウェイとトークン非保存を維持。
    - 再試行を requestCalendarAccess 経由に統一し、取得中の ConsentRequired でも同意画面に戻れるようにした。再認可後は表示窓と失敗窓を再取得する。既存の許可系文言・サインアウト経路は維持。
    - CalendarAuthTest で実際の Scope 一覧、Granted（トークン優先）、ConsentRequired、応答不足の Failed を検証。FetchFailureTest は SecurityException と両 API の 401/403 を網羅。
    - 独立レビュー: 再試行時に画面外の失敗窓が漏れる指摘は修正済み・独立再確認済み。calendarList.list は calendar.events 単独では認可されないため、calendar.calendarlist.readonly 追加は提案側で訂正し対処済み（https://developers.google.com/workspace/calendar/api/v3/reference/calendarList/list#authorization）。
    - 訂正（2026-11-22）: main 最新の proposal.md 決定1を確認し、REQUESTED_SCOPE_URIS を calendar.events ＋ calendar.calendarlist.readonly の2件へ修正。CalendarAuthTest は URI 定義と実際の Scope 一覧がこの2件だけであることを検証する。独立レビューで指摘なし（STATUS.md と制約対象に変更なし）。訂正後も本体の scripts/env.sh を source して ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK、CalendarAuthTest 4件・失敗0・エラー0）。共有 Gradle キャッシュへの書き込みに実行権限拡張を使用。
    - 検証: 本体の scripts/env.sh を source して ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK、CalendarAuthTest 4件・失敗0）。共有 Gradle キャッシュの書き込みに実行権限拡張を使用。PKIX 失敗なし。実機での再同意・読み取り確認は未実施。Google Cloud コンソール・STATUS.md は未変更。

- [x] V2-2 予定の作成
  - 内容: 新規作成の入口（月表示のボタン）と編集画面（タイトル・開始・終了・終日・カレンダー・色・場所・説明）、events.insert、成功後に該当窓を再取得して表示・キャッシュを一致させる。失敗時は日本語エラー＋再試行、入力内容は保持する
  - 完了条件: アプリで作った予定がサーバーに登録され、再取得後の表示とキャッシュが一致する
  - 検証方法: テスト＋check.sh＋一次判定（スクショ）。Web 側での一致は QC-12（V2-5）
  - 統合: 2026-11-22、Muse がレビュー（work/v22/muse-review-14c1cce.md、受理可）のうえ main へ統合

  - 実装計画（V2-2）: 既存の認可・同期を再利用し、トークン非保存・依存追加なしで作成する。最新 calendarList の accessRole が owner/writer のものだけを選択可能にする。順序はリクエスト変換と単体テスト→共用フォームと月表示入口→insert と同期接続→スクショ・check.sh→独立レビュー。成功基準は変換テスト・画面基準・既存チェック成功。実サーバー照合と見た目の最終受理はユーザー確認待ち。編集・削除・STATUS 更新は対象外。未解決: 実機照合は V2-5 へ引き継ぐ。

- [x] V2-3 予定の編集
  - 内容: 既存予定を編集画面で開き、同じ項目を変更して保存する（events.patch/update）。編集画面は予定の詳細表示を兼ね、場所・説明もそこで見られるようにする（現状はアプリ内で内容が一切表示されない）。繰り返し予定は「この予定のみ」「すべての予定」の選択肢を出す（制約があれば切り分けて記録）
  - 完了条件: 編集結果がサーバー・表示・キャッシュで一致する。他人の予定（祝日カレンダー等の読み取り専用カレンダー）は編集不可として扱う
  - 検証方法: テスト＋check.sh＋一次判定
  - 統合: 2026-11-22、Muse がレビュー（work/v23/muse-review-89b8bf1.md、切り分け2件を既知の制約として条件つき受理可）のうえ main へ統合

  - 実装計画（V2-3）: 詳細を読めない問題を共用フォームで解消する。取得・キャッシュに説明と繰り返しIDを保持→初期値変換→更新API→日ポップアップ接続→変換テスト・編集画面基準→check.sh の順。成功基準は入力保持、読み取り専用の保存禁止、更新後の既存同期経路での再取得。依存は既存認可・EventEditor、トークン保存・新規依存・削除は対象外。シリーズ全体はマスター取得と日時編集の検証を切り分け、今回は単体とこの回の編集を優先する。実サーバー照合・独立レビュー・ユーザー受理は未確認。

  - 実装記録（2026-11-22 / Codex）:
    - 日ポップアップから共用 EventEditor を編集・詳細モードで開く。説明・繰り返しIDを取得・キャッシュに保持し、旧形式キャッシュは version 2 で無効化。owner/writer 以外は全項目・保存を無効化。アカウント切替時は選択待ち状態も消去。
    - GET + events.update（PUT）で既存の未編集項目を保持。createEventBody の日時変換を共用し、色なしは PUT 本文から colorId を除去。ETag の If-Match で取得後の競合を検出。成功後は元・移動先の日付窓を syncNow で再取得。失敗時は入力を保持し日本語エラーと再試行。
    - 単体予定のカレンダー変更は events.move → PUT。移動後の更新失敗は移動済みと表示し、移動先IDで再試行する。制約: move のサーバー成功後に応答が失われた場合の自動回復は未対応（再取得して移動先から開き直す必要あり）。
    - 繰り返しは「この予定のみ」をインスタンスIDへ更新して例外化。「すべての予定」は無効表示。マスター取得・シリーズ日時変換・例外との整合検証を後続へ切り分ける。インスタンスのカレンダー移動も無効（API move の制約）。
    - EventEditTest: 終日・午前0時終了・色・場所説明・繰り返しID・更新時の未編集項目保持を固定。ScreenshotTest に編集基準と読取専用の保存禁止を追加。基準画像の一次目視で全項目表示を確認。ユーザーの見た目受理と実サーバー照合は未確認。
    - 検証: 本体 scripts/env.sh を source し ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK）。EventEditTest 3件、ScreenshotTest 12件、失敗・エラー0。PKIX失敗なし。共有キャッシュ書込に権限拡張を使用。重複実行のロックタイムアウトは先行実行の正常終了で解消。
    - 独立レビュー: キャッシュ旧形式・アカウント切替状態・移動後再試行の指摘を修正して再確認。移動応答喪失の制約は上記に記録。

- [x] V2-4 予定の削除
  - 内容: 編集画面または日ポップアップから、確認ダイアログつきで削除する（events.delete）。繰り返し予定は V2-3 と同じ選択肢。成功後は窓を再取得
  - 完了条件: 削除がサーバー・表示・キャッシュで一致し、確認なしに消える経路がない
  - 検証方法: テスト＋check.sh＋一次判定
  - 統合: 2026-11-23、Muse がレビュー（work/v24/muse-review-a251f1c.md、受理可）のうえ main へ統合

  - 実装計画（V2-4）: 共用フォームに確認ダイアログと削除範囲選択を追加→権限・確認つき対象決定→DELETE API→成功時 syncNow／失敗時入力保持→対象決定テスト・check.sh・独立レビュー。シリーズ削除はマスターIDを直接指定でき、V2-3 の日時編集制約には依存しない。トークン保存・依存追加・STATUS変更は対象外。実サーバー照合と画面受理は未確認として引き継ぐ。

  - 実装記録（2026-11-23 / Codex、受理待ち）:
    - 編集モードだけに削除ボタンを追加。サーバー上の予定タイトルと「削除は取り消せません」を表示し、確認時だけ DELETE を実行。再試行も同じ確認を通す。読み取り専用はボタン非表示・呼び出し側でも権限を再検証。
    - 単体／この予定のみは eventId、すべての予定は recurringEventId を使用。V2-3 のシリーズ日時編集の制約とは独立して両削除範囲を有効化。根拠: https://developers.google.com/workspace/calendar/api/v3/reference/events/delete 、https://developers.google.com/workspace/calendar/api/guides/recurringevents 。
    - 成功時は画面を閉じ、全取得済み窓と元の週を syncNow で再取得（画面外のシリーズや複数週予定も対象）。失敗時は入力を保持し writeFailureMessage による日本語理由と削除の再試行案内。
    - EventEditTest で対象IDと権限・未確認・ID不明の禁止を固定。ScreenshotTest で確認前0回・キャンセル0回・確認1回と読取専用ボタン非表示を固定。
    - 独立レビュー: 画面外キャッシュ残存の指摘を全取得済み窓の再取得で修正し、再確認で追加指摘なし。トークン保存・新規依存・STATUS変更なし。
    - 未確認: 実API削除とWeb一致・操作感は QC-12 / V2-5、見た目の最終判定はユーザー受理待ち。

- [ ] V2-5 仕様 converge と QC-12
  - 内容: REQUIREMENTS.md §8 を書き込み対応の実態に改訂する（「読み取り専用」の記述を更新）。docs/qc.md に QC-12（作成・編集・削除の Web との一致の実機照合）を追加する
  - 完了条件: 正本・STATUS・QC が実装の実態と一致している
  - 検証方法: レビューで記録の整合を確認。実機照合はユーザーの v2 初回配布時に実施

- [x] V2-6 カレンダー表示選択の固定（2026-11-22 ユーザー追加指示）
  - 内容: 下部タブのカレンダー表示 ON/OFF の選択をアカウントごとに端末へ保存し、起動時に復元する。次回開いても前回選択したカレンダーの予定だけが表示される状態にする。キャッシュ（V1-4）の保存形式と衝突しない形で持つ
  - 完了条件: タブを OFF にして再起動しても OFF のままであることがテストで固定され、新規カレンダーが現れた場合の初期値（表示 ON）も定義されている
  - 検証方法: テスト＋check.sh。実機の再起動確認は配布時のユーザー確認に含める
  - 統合: 2026-11-23、Muse がレビュー（work/v26/muse-review-d1d2467.md、受理可）のうえ main へ統合

  - 実装計画（V2-6）: 再起動で失われる表示選択を、専用 SharedPreferences のアカウント別 OFF-ID 集合として保存する。既存キャッシュ・認証保存とは分離し、トークン・依存追加・書き込み機能・STATUS変更は対象外。順序は保存クラス→アカウントをキーにした画面復元とタブ操作時保存→往復・新規ID・分離テスト→check.sh→独立レビュー。成功基準は指定テストと既存チェック成功。実機再起動は配布時確認、未解決の仕様質問なし。

  - 実装記録（2026-11-23 / Codex、受理待ち）:
    - CalendarVisibilityPreferences の専用 SharedPreferences にアカウント別の OFF-ID 集合を保存。認証情報とイベントキャッシュから分離し、トークン保存・依存追加なし。
    - MuseCalApp はアカウントをキーに起動時・切替時に復元し、キャッシュ表示にも適用。タブ操作時に即保存。未知カレンダーは ON、消滅IDの設定は他のIDに影響しない。
    - CalendarVisibilityPreferencesTest 3件で保存→再生成→復元、ONへ戻す操作、新規ID・消滅ID、アカウント分離、認証設定消去からの独立を固定。
    - 検証: 本体 scripts/env.sh を source して ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK）。共有 Gradle キャッシュ書き込みに権限拡張を使用。PKIX失敗なし。独立レビューで修正必須の指摘なし。
    - 未確認: 実機再起動と操作感・ユーザー受理は配布時確認。チェック欄は未完了のまま。STATUS.md と V2-2〜V2-4 実装は変更なし。
