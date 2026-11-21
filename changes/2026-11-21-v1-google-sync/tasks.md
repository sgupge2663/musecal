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
  - 状態（2026-11-21、Claude Code）: 実装済み（ブランチ `claude/v13-events`、worktree `.worktrees/claude-v13`）。単体テストと check.sh まで。実機照合は Muse が実データ版 APK でまとめて行う（未確認）
    - 取得（`GoogleEvents.kt` の EventsApi）: タブに対応づいた各カレンダーごとに Calendar API v3 `calendars/{id}/events` を REST で直接取得（singleEvents=true、orderBy=startTime、maxResults=2500、pageToken で全ページ、timeMin/timeMax は端末 TZ の 0:00）。トークンは取得のたびに CalendarAuth.authorizeCalendar() で取り直し、保存しない。追加ライブラリなし。calendarId はパス用にエンコード（祝日の `#`・`@`）
    - ウィンドウ（windowToFetch / EventWindowStore）: 取得単位は6週（日曜始まり42日）。月表示のスクロールが止まるたびに表示中の週を受け取り、取得済み・取得中の週からはみ出した最初の週から6週を取る（初期表示の 2026年12月は 11/29〜1/9 の1回）。在メモリ保持のみ（永続キャッシュは V1-4）。ウィンドウが重なったときの同じ予定（calendarId＋event id）は後から取ったほうを使う
    - 変換（googleEventToCalEvent）: start.date＝終日（end.date は排他的）、start.dateTime＝時間指定（端末 TZ へ変換、0:00 ちょうど終わりは前日まで＝従来のサンプルと同じ規則）。status=cancelled は出さない。summary が無い予定は空文字（装飾しない）。calendarId・calendarName・カレンダー色はタブから付与。colorId はそのまま持たせ、既存の baseColor が標準11色（GoogleEventColors）へ、無い／パレット外はカレンダーの backgroundColor。帯はパレット色そのまま、時間指定の文字色は既存の textShade（T-108）を通る（描画側は変更なし）
    - 差し替え: 本番の表示経路は実データのみ（loadEvents はスクショテストの fixture 用に残し、本番からは呼ばない）。取得前・取得失敗時は空。1カレンダーでも失敗したウィンドウは反映せず、理由をトーストで出す（次にスクロールが止まったときに取り直す）
    - 競合: 予定の取得も AccountRequestGate の札で判定し、加えてアカウント・タブが変わるたびに保持をリセット（世代番号が違う結果は成功・失敗とも捨てる）
    - 検証: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug。単体テスト 44件: 成功43・省略1〈RealEventsDumpTest は環境変数があるときだけ動く照合用〉）。GoogleEventsTest 16件（時間指定/終日、UTC の dateTime、日またぎ、colorId の変換・無し・パレット外、textShade への受け渡し、summary 欠落、展開済み繰り返し予定と cancelled、複数カレンダーの calendarId とタブ非表示、パスのエンコード、ウィンドウ判定、保持のリセットと重複除去）。fixture は実 API と同じ形（`app/src/test/resources/events/`、タイトル・id は架空）。`verifyRoborazziDebug --rerun` 成功（ベースライン3画面に差分なし）
    - 実データでの変換照合（ローカル、2026-11-21）: `hatch_gws_cli calendar events list` で 2026-11-29〜2027-01-10 の3カレンダー分を取得（Git 管理外の work/v13/）。アプリの変換結果（RealEventsDumpTest の TSV）と、同じ JSON から Python で独立に作った期待値（カレンダー・開始日・終了日・終日/時間・開始時刻・タイトル・帯色）が37件すべて一致（メイン22・AI木曜会10・日本の祝日5、時間指定29・終日8）。実データには summary 欠落・colorId 1〜11 以外は無かった
    - **未確認**: 実機での取得と表示（実 API を端末から呼ぶこと、トークン取得、月表示が Web 版 Google Calendar と一致すること＝完了条件）、スクロールで範囲外へ出たときの取り直しの実際の動き、取得失敗トーストの文言の見え方、端末 TZ が Asia/Tokyo 以外のとき
    - 判断してほしいこと: (1) summary 欠落時は空文字にした（Web 版は「(タイトルなし)」と表示する。実機照合で揃えるなら変換でなく描画側で表示文言を決める） (2) 1カレンダーでも取得に失敗したらそのウィンドウ全体を出さない方式にした（部分表示にするか） (3) 時間指定予定の帯／文字の判定や eventType（workingLocation 等）の扱いは従来の描画規則のまま。実データ12月分は全件 eventType=default だった
  - 状態（2026-11-21、Claude Code）: 実装済み（ブランチ `claude/v13-events`、worktree `.worktrees/claude-v13`）。Codex レビュー待ち、実機照合待ち
    - 取得（`GoogleEvents.kt` の EventsApi）: タブに対応づいた各カレンダーについて Calendar API v3 `calendars/{id}/events` を REST で直接取得（singleEvents=true、orderBy=startTime、maxResults=2500、pageToken で全ページ）。取得のたびに CalendarAuth.authorizeCalendar() でトークンを得て、保存しない。追加ライブラリなし、scope は calendar.readonly のまま
    - ウィンドウ: 取得単位は6週（日曜から42日、timeMin/timeMax は端末タイムゾーンの0時）。スクロールが止まった時点の表示中の週が取得済み・取得中の範囲からはみ出したら、はみ出した最初の週から6週分を取る（`windowToFetch`）。在メモリ保持（`EventWindowStore`。ウィンドウが重なったときは calendarId＋event id で重複を除き、後に取得したほうを残す）。永続キャッシュはしない（V1-4）
    - 変換（`googleEventToCalEvent`）: start.date → 終日（end.date は排他的）、start.dateTime → 時間指定（端末タイムゾーンへ。0:00 ちょうどに終わる予定は前日まで＝既存の扱い）。タイトルは summary をそのまま使い、summary が無ければ空文字（装飾しない）。status=cancelled は出さない。calendarId・カレンダー名・カレンダー色はタブから付ける。colorId はそのまま持たせ、既存の `CalEvent.baseColor` が標準11色（`GoogleEventColors`）へ変換。colorId が無い・パレット外ならカレンダーの backgroundColor。帯はパレット色そのまま、時間指定の文字色は既存の textShade（T-108）の経路のまま
    - 差し替え: サインイン済みの月表示は取得した予定だけを出す（`loadEvents`＝同梱サンプルは本番経路から外し、ScreenshotTest の fixture としてだけ残す）。取得前・取得失敗時は空。失敗はトーストで理由を出す。1つのカレンダーでも失敗したらそのウィンドウは反映しない（一部だけ出して「予定が無い」と見せないため）
    - 競合対策: 予定の取得も V1-2 の AccountRequestGate の札で判定し、さらにアカウント・タブが変わるたびに store の世代を進める。札が古い・世代が違う結果は成功・失敗とも捨てる
    - 検証: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug）。単体テスト 44件（失敗 0、省略 1＝下記の照合用テストは環境変数が無いと省略）。うち GoogleEventsTest 16件: 時間指定/終日の別（UTC 表記の変換、日またぎ、終日の排他的 end）、colorId の有無とパレット外、textShade への受け渡し、summary 欠落、複数カレンダーの calendarId とタブのフィルタ、singleEvents で展開済みの繰り返し（時間指定・終日）と cancelled の除外、pageToken、calendarId のパスエンコード、ウィンドウの判定、store の重複除去と reset。fixture は実 API と同じ形（タイトル・id は架空）。Roborazzi 3画面は verify でベースライン一致（PNG に差分なし）
    - 実データとの変換照合（実機ではなく変換のみ）: 2026-11-21 に `hatch_gws_cli calendar events list` で 2026-11-29〜2027-01-10（12月表示の6週）の3カレンダーを取得し、`RealEventsDumpTest`（MUSECAL_REAL_EVENTS_DIR 指定時のみ動く）でアプリの変換結果を TSV に出力、Python で独立に作った期待値と diff → 37件（メイン 22・AI木曜会 10・日本の祝日 5、時間指定 29・終日 8）すべて一致（カレンダー・開始日・終了日・終日/時間・開始時刻・タイトル・帯色）。実データは個人の予定なのでリポジトリに置かず work/v13/ に置いた
    - **未確認（実機でしか確認できない）**: 完了条件「実機の月表示が Web 版 Google Calendar と一致する」（Muse が実データ版 APK で照合）。実 API への実機からの events.list 呼び出し（トークン・ネットワーク・HTTP エラー時のトースト）。スクロールに応じた取り直しの動作と操作感。アカウント切替時に前アカウントの予定が混ざらないこと（単体テストは札と世代の判定のみ）
    - 判断してほしいこと: (1) summary が無い予定は空文字で出している（Web 版は「(タイトルなし)」と表示する。完了条件の「タイトル一致」をどちらで判定するか）。(2) 1カレンダーでも失敗したらそのウィンドウ全体を出さない方針でよいか。(3) eventType（workingLocation・outOfOffice 等）は絞り込まず API が返すものを出している（12月の実データは全件 default）

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
