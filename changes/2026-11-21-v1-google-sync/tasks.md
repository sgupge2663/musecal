# v1 タスク（接頭辞 V1-）

- [x] V1-1 ログイン基盤 — 完了（2026-11-21）
  - 内容: Google Sign-In の導線（未ログイン時の案内画面＋ログインボタン）、ログイン状態の保持と表示、ログアウト
  - 完了条件: 実機で本人のアカウントでログインでき、再起動後もログイン状態が保たれる。権限は calendar.readonly のみ要求する
  - 検証方法: 実機スクショ＋ユーザーの確認。要求 scope はコードと同意画面で照合
  - 状態: 実装済み（ブランチ `claude/v11-signin`、worktree `.worktrees/claude-v11`）。方式は Credential Manager（GetGoogleIdOption）＋ AuthorizationClient。Codex レビューは条件付き受理可 → 条件対応済み: (1) テスト強化 — 要求 scope の集合を `CalendarAuth.REQUESTED_SCOPE_URIS` に集約し（認可要求は必ずこの一覧から組み立てる）、CalendarAuthTest がその一覧そのもの（AuthorizationClient へ渡す scope の集合。Scope オブジェクトへの変換は1対1のラップのみ）が calendar.readonly 1件のみであることを検証する（従来は scope 定数の文字列のみの検証だった）。(2) Web クライアント ID 実値差し替え済み — Google Cloud プロジェクト「musecal」の Web クライアント ID を `R.string.google_web_client_id` に設定（Android クライアントも作成済み）。`scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug、単体テスト 8件全通過）。**実機検証は未実施**（ユーザーの実機でログイン試行待ち）。完了条件の「実機でログインできる」「再起動後の保持」は実機で確認する

  - 完了記録（2026-11-21）: main へ統合済み（1614531、ブランチ CI 成功を確認してから統合）。Codex レビュー条件対応済み。実機検証はユーザーの実機で 22:55 に確認（v0.4 をインストール → アカウント選択で kou1995yurin530@gmail.com を選択 → 未確認アプリ警告を続行 → サインイン済みの月表示にメール帯＋サインアウト表示。スクショ5枚が証拠）。再起動後の保持とサインアウト→案内画面への復帰は明示確認なし（未確認。次回起動時に自然に確認される）
- [x] V1-2 カレンダー一覧とタブ対応 — main 統合済み（2026-11-22、d8cacb6）。完了条件の実機分は V1-3 後の実データ版でまとめて確認（未確認）
  - 内容: calendarList 取得。既存タブ（メイン / AI木曜会 / 日本の祝日）を実カレンダーに対応づけ、タブの色線はカレンダー色を使う
  - 完了条件: 実機で3カレンダーが正しく切り替わり、各タブにそのカレンダーの予定だけが出る
  - 検証方法: 実機スクショと Google Calendar（Web）との目視照合
  - 状態（2026-11-21、Claude Code）: 実装済み（ブランチ `claude/v12-calendar-list`、worktree `.worktrees/claude-v12`）。Codex レビュー条件1対応済み、実機確認待ち
    - 取得: サインイン済みアカウントが決まるたび（サインイン直後・再起動時）に CalendarAuth.authorizeCalendar() でトークンを得て、Calendar API v3 `users/me/calendarList` を REST で直接取得（`Calendars.kt` の CalendarListApi。HttpURLConnection＋org.json、pageToken で全ページ）。google-api-client は入れない（GET 1種だけのため）。トークンは保存しない。Manifest に INTERNET 権限を追加
    - 対応づけ（`mapCalendarTabs`）: 既存3タブは KNOWN_TABS（V1-3 欄の calendarList 実物が正本）と calendarId の**完全一致**でのみ対応。タブ順は メイン / AI木曜会 / 日本の祝日 で固定。色線は calendarList の backgroundColor（読めないときだけ KNOWN_TABS の色）。未知のカレンダーは既存タブの後ろに calendarList の順で追加（ラベル summaryOverride→summary→id、色が無ければ灰）。calendarList に無い既存タブは出さない。重複 id は先頭の1件
    - 土台: CalEvent に calendarId を追加し、タブの表示 ON/OFF を calendarId で判定するよう変更（従来は calendarName）。V1-3 は events.list の結果に calendarId を付ければタブ単位でフィルタできる。月表示の予定は当面サンプルのまま（サンプルには calendarId が無いため、既存タブ名から KNOWN_TABS の calendarId を引いて付与）
    - 取得前・取得失敗時: タブは空（サンプルのタブで実データを装わない）。失敗時は理由をトーストで表示。キャッシュは V1-4
    - 検証: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug、単体テスト 21件全通過。うち CalendarsTest 10件: 完全一致の対応、返却順に依らないタブ順、色の取得とフォールバック、大文字・空白・同名の不一致、未知カレンダーの追加順とラベル、既存タブ欠落、重複 id、JSON 読み取り）。Roborazzi 3画面はベースライン不変（recordRoborazziDebug で再記録しても PNG に差分なし。サンプルの色と calendarList 実物の色が同じため）
    - Codex レビュー（2026-11-21）: 条件付き受理可。条件1「取得中にサインアウト→別アカウントへサインインすると、遅れて返った前アカウントの結果が新アカウントのタブを上書きしうる」（重要度：中）に対応（2026-11-21、Claude Code）:
      - `Calendars.kt` に `AccountRequestGate` を追加。アカウントが決まるたびに札（アカウント＋連番）を出し、サインアウトで全札を無効化。結果は「札が最新」かつ「札のアカウント＝反映時点のサインイン中アカウント」のときだけ反映し、それ以外は成功・失敗とも捨てる（トーストも出さない）。同一アカウントへのサインインし直しでも前回の結果は捨てる
      - `MainActivity.kt` の MuseCalRoot: 認可の戻り・同意画面の戻り・calendarList の戻りの3か所で札を確かめる。同意画面を出す間は札を保持し、戻ったらその札で判定
      - 検証: `AccountRequestGateTest` 6件（A 取得中にサインアウト→B サインイン、A の結果が B の前後どちらで返っても捨てる／サインアウト後の結果を捨てる／同一アカウント再サインイン／アカウント不一致／begin による旧札の無効化）。`scripts/check.sh` 成功（単体テスト 27件全通過）、`verifyRoborazziDebug` 一致
      - 条件2「実機証拠とユーザー判定」は計画どおり V1-3 後の実データ版でまとめて行う（未確認のまま）
    - **未確認（実機確認は V1-3 後）**: 完了条件「実機で3カレンダーが正しく切り替わり、各タブにそのカレンダーの予定だけが出る」は、V1-3 の実データ版でまとめて実機確認する。実 API からの calendarList 取得も実機では未確認
  - 完了記録（2026-11-22）: main へ統合済み（d8cacb6。ブランチ CI run 44915206193・44916406090 の成功を確認してから統合）。Codex レビュー条件1（競合）は対応済みで、Codex の再確認でもクリア判定。条件2（実機証拠とユーザー判定）は V1-3 後の実データ版でまとめて行う

- [ ] V1-3 予定の取得と描画への接続
  - 内容: 表示ウィンドウ（6週）の events.list 取得（singleEvents=true）。既存モデルへ変換し、サンプルデータと差し替える。colorId→パレット変換を含む
  - 完了条件: 実機の月表示が Web 版 Google Calendar と一致する（件数・タイトル・色・終日/時間の別）
  - 検証方法: 2026年12月で照合し、差異があれば QC に記録。正解データは `hatch_gws_cli calendar events list`（Muse のコネクタ CLI、アプリと同じ API・同じ形の JSON）の出力を使う。calendarList の実物（2026-11-21 取得）: メイン=kou1995yurin530@gmail.com (#9fe1e7)、AI木曜会=mokuyoukai.ai@gmail.com (#fad165)、日本の祝日=ja.japanese#holiday@group.v.calendar.google.com (#9a9cff) — タブの色線・帯色の対応はこれが正本

- [ ] V1-4 キャッシュと更新
  - 内容: 取得結果を端末にキャッシュ。起動時はキャッシュ即表示→裏で再取得。手動リフレッシュ導線。取得失敗時はキャッシュ表示＋鮮度（最終取得時刻）の表示
  - 完了条件: 機内モードでも前回分が表示され、古いことが画面で分かる。通常時は起動後に最新化される
  - 検証方法: 実機で機内モード ON/OFF を切り替えて確認

- [ ] V1-5 未ログイン・エラー時の画面
  - 内容: 未ログイン時はサンプルを出さず、ログイン案内のみ。エラー時は理由の分かる文言＋再試行
  - 完了条件: ログアウト状態でサンプル予定が一切表示されない
  - 検証方法: 実機スクショ

- [ ] V1-6 仕様 converge と QC 記録
  - 内容: REQUIREMENTS.md に「データ連携」節を正本化（proposal の追記案を承認どおり反映）。docs/qc.md に実機照合の記録（QC-11 として追加を検討）
  - 完了条件: 正本・STATUS・QC が実装の実態と一致している
  - 検証方法: Codex レビューで記録の整合を確認
