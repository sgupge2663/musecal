# v1 タスク（接頭辞 V1-）

- [ ] V1-1 ログイン基盤
  - 内容: Google Sign-In の導線（未ログイン時の案内画面＋ログインボタン）、ログイン状態の保持と表示、ログアウト
  - 完了条件: 実機で本人のアカウントでログインでき、再起動後もログイン状態が保たれる。権限は calendar.readonly のみ要求する
  - 検証方法: 実機スクショ＋ユーザーの確認。要求 scope はコードと同意画面で照合
  - 状態: 実装済み（ブランチ `claude/v11-signin`、worktree `.worktrees/claude-v11`）。方式は Credential Manager（GetGoogleIdOption）＋ AuthorizationClient。Codex レビューは条件付き受理可 → 条件対応済み: (1) テスト強化 — 要求 scope の集合を `CalendarAuth.REQUESTED_SCOPE_URIS` に集約し（認可要求は必ずこの一覧から組み立てる）、CalendarAuthTest がその一覧そのもの（AuthorizationClient へ渡す scope の集合。Scope オブジェクトへの変換は1対1のラップのみ）が calendar.readonly 1件のみであることを検証する（従来は scope 定数の文字列のみの検証だった）。(2) Web クライアント ID 実値差し替え済み — Google Cloud プロジェクト「musecal」の Web クライアント ID を `R.string.google_web_client_id` に設定（Android クライアントも作成済み）。`scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug、単体テスト 8件全通過）。**実機検証は未実施**（ユーザーの実機でログイン試行待ち）。完了条件の「実機でログインできる」「再起動後の保持」は実機で確認する

- [ ] V1-2 カレンダー一覧とタブ対応
  - 内容: calendarList 取得。既存タブ（メイン / AI木曜会 / 日本の祝日）を実カレンダーに対応づけ、タブの色線はカレンダー色を使う
  - 完了条件: 実機で3カレンダーが正しく切り替わり、各タブにそのカレンダーの予定だけが出る
  - 検証方法: 実機スクショと Google Calendar（Web）との目視照合

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
