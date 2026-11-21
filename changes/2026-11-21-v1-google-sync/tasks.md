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
  - 状態（2026-11-21、Claude Code）: 実装済み（ブランチ `claude/v13-events`、worktree `.worktrees/claude-v13`）。単体テストと check.sh・ブランチ CI まで。Codex レビュー（e369ce3）不可 → 指摘3件＋証拠1件を修正済み、再レビュー待ち。実機照合は Muse が実データ版 APK でまとめて行う（未確認）
    - 取得（`GoogleEvents.kt` の EventsApi）: タブに対応づいた各カレンダーごとに Calendar API v3 `calendars/{id}/events` を REST で直接取得（singleEvents=true、orderBy=startTime、maxResults=2500、pageToken で全ページ、timeMin/timeMax は端末 TZ の 0:00）。トークンは取得のたびに CalendarAuth.authorizeCalendar() で取り直し、保存しない。追加ライブラリなし。calendarId はパス用にエンコード（祝日の `#`・`@`）
    - ウィンドウ（windowToFetch / EventWindowStore）: 取得単位は6週（日曜始まり42日）。月表示のスクロールが止まるたびに（同じ週でも）表示中の週を受け取り、取得済み・取得中の週からはみ出した最初の週から6週を取る（初期表示の 2026年12月は 11/29〜1/9 の1回）。在メモリ保持のみ（永続キャッシュは V1-4）。保存するのは全カレンダー・全ページが揃った完全な応答だけで、新しい窓を保存するときは、以前の窓の予定のうち新しい窓の範囲にかかるものを捨てる（削除・移動された予定を残さない）。全ページ取得で、上限（50ページ）に届いてもトークンが残る・同じトークンが繰り返す場合は失敗扱い
    - 変換（googleEventToCalEvent）: start.date＝終日（end.date は排他的）、start.dateTime＝時間指定（端末 TZ へ変換、0:00 ちょうど終わりは前日まで＝従来のサンプルと同じ規則）。status=cancelled は出さない。summary が無い予定は空文字（装飾しない）。calendarId・calendarName・カレンダー色はタブから付与。colorId はそのまま持たせ、既存の baseColor が標準11色（GoogleEventColors）へ、無い／パレット外はカレンダーの backgroundColor。帯はパレット色そのまま、時間指定の文字色は既存の textShade（T-108）を通る（描画側は変更なし）
    - 差し替え: 本番の表示経路は実データのみ（loadEvents はスクショテストの fixture 用に残し、本番からは呼ばない）。取得前・取得失敗時は空。1カレンダーでも失敗したウィンドウは反映せず、理由をトーストで出す（同じ週のままでも、次にスクロールが止まったときに取り直す）
    - 競合: 予定の取得も AccountRequestGate の札で判定し、加えてアカウント・タブが変わるたびに保持をリセット（世代番号が違う結果は成功・失敗とも捨てる）
    - 検証: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug）。件数は下の「レビュー対応」に記載。GoogleEventsTest（時間指定/終日、UTC の dateTime、日またぎ、colorId の変換・無し・パレット外、textShade への受け渡し、summary 欠落、展開済み繰り返し予定と cancelled、複数カレンダーの calendarId とタブ非表示、パスのエンコード、ウィンドウ判定、保持のリセットと重複除去、全ページ取得とページ上限、重なり窓での削除・移動の反映、失敗後の同一週での再取得）。fixture は実 API と同じ形（`app/src/test/resources/events/`、タイトル・id は架空）。Roborazzi ベースライン3画面は verify で一致
    - 実データでの変換照合（ローカル、2026-11-21）: `hatch_gws_cli calendar events list` で 2026-11-29〜2027-01-10 の3カレンダー分を取得（Git 管理外の work/v13/。取得コマンド・手順・結果は work/v13/README.md）。アプリの変換結果（RealEventsDumpTest の TSV）と、アプリのコードを使わない期待値スクリプト work/v13/make_expected.py の出力（カレンダー・開始日・終了日・終日/時間・開始時刻・終了時刻・タイトル・帯色）を diff し、37件すべて一致（メイン22・AI木曜会10・日本の祝日5、時間指定29・終日8。両ファイル sha256 b16b9127…）。実データには summary 欠落・colorId 1〜11 以外は無かった。描画結果（配置・文字色）は対象外
    - **未確認**: 実機での取得と表示（実 API を端末から呼ぶこと、トークン取得、月表示が Web 版 Google Calendar と一致すること＝完了条件）、スクロールで範囲外へ出たときの取り直しの実際の動き、取得失敗トーストの文言の見え方、端末 TZ が Asia/Tokyo 以外のとき
    - 判断してほしいこと: (1) summary 欠落時は空文字にした（Web 版は「(タイトルなし)」と表示する。実機照合で揃えるなら変換でなく描画側で表示文言を決める） (2) 1カレンダーでも取得に失敗したらそのウィンドウ全体を出さない方式にした（部分表示にするか） (3) 時間指定予定の帯／文字の判定や eventType（workingLocation 等）の扱いは従来の描画規則のまま。実データ12月分は全件 eventType=default だった
    - レビュー対応（2026-11-21、Codex レビュー e369ce3 は「不可」。正本は work/v13/codex-review-e369ce3.md）:
      - 指摘1（中）50ページで打ち切り部分結果を成功扱い → `EventsApi.fetchAllPages` に分け、nextPageToken が無くなるまで取得。上限到達でトークンが残る・同じトークンの繰り返しは IncompleteError（窓は保存されずトースト）。テスト: 全ページ取得、上限到達で失敗・ちょうど上限で成功、同一トークンの繰り返しで失敗
      - 指摘2（中）重なり窓の再取得で削除済み予定が残る → `EventWindowStore.put` で、新しい完全応答の窓の範囲（events.list の timeMin/timeMax と同じ境界）にかかる旧窓の予定を捨てる。テスト: 11/29 窓で取った 12/10 の予定が 11/22 窓の再取得で返らなければ消える（範囲外の 1/5 は残る）、移動した予定が旧日付に残らない
      - 指摘3（中）失敗後に同じ週での再停止で取り直さない → 停止の検出を `ScrollStopDetector`（停止のたびに同じ週でも1回知らせる、スクロール中と単なる再評価では知らせない）に分け、MuseCalRoot は停止ごとに番号（scrollStopSeq）を進めて取得判定の LaunchedEffect を再起動する。テスト: 失敗 → 同じ週で再停止 → 同じ窓が再び取得対象、成功後は再停止しても取らない、検出器の境界
      - 指摘4（証拠）期待値の生成コードが無い → work/v13/make_expected.py と README.md に保存し再実行（上記）。RealEventsDumpTest の出力に終了時刻を追加
      - 修正後の検証: 単体テスト 51件（失敗 0、省略 1＝RealEventsDumpTest は環境変数があるときだけ動く照合用。GoogleEventsTest 23件）、check.sh 成功、Roborazzi 差分なし、ブランチ CI 成功（run 44924796708、951c042）
    - 再レビュー対応（2026-11-21、Codex 修正確認レビュー 951c042 は「不可（コードと記録の範囲）」。正本は work/v13/codex-review-951c042.md。前回指摘1・3・4は解消確認、指摘2は同一 TZ のみ解消で、端末とカレンダーの TZ が違う終日予定の除去漏れ1件が残った。このラウンドはこの1件だけ）:
      - 原因: 旧窓の予定の除去判定を表示日（startDate / endDateInclusive）で行っていたが、events.list は終日予定を**カレンダーのタイムゾーン**で時刻化して timeMin（終了時刻の排他的下限）/ timeMax（開始時刻の排他的上限）と照合する（Google 公式 "Matching all-day events to time-bracketed queries" と events.list リファレンス）。例: 端末 Asia/Tokyo・カレンダー America/Los_Angeles の 2027/1/2 終日は日本時間 1/2 17:00〜1/3 17:00 で 1/3 開始窓の API 範囲に入るが、表示日判定では窓外として残っていた
      - 修正: CalEvent に API 照合用の区間 matchStart / matchEnd（Instant）を表示日とは別に持たせた。時間指定は start/end の時刻、終日は date をカレンダーのタイムゾーンの 0:00 として時刻化（end.date は排他的）。カレンダーのタイムゾーンは events.list 応答の最上位 timeZone（リファレンス上「The time zone of the calendar」）から取り、FIELDS に timeZone を追加。読めないときは端末 TZ。EventWindowStore.put は取得時の端末 TZ を受け取り、その窓の timeMin/timeMax（EventWindow.timeMin/timeMax。取得 URL と同じ計算）に対して「matchEnd > timeMin かつ matchStart < timeMax」の予定だけを除去（API と同じ判定）。照合区間を持たない予定（同梱サンプル）だけ従来の表示日判定。表示日（帯の位置）は変えていない
      - テスト（GoogleEventsTest に5件追加、計28件）: (1) 照合区間がカレンダー TZ で時刻化され表示日はそのまま・timeZone 欠落時は端末 TZ・時間指定は時刻そのもの (2) 端末 Asia/Tokyo×カレンダー America/Los_Angeles で 11/29 窓に保持した 1/2 終日を削除 → 1/3 窓の完全応答で消える（範囲外の 12/20 は残る） (3) 同条件で範囲外（12/24）へ移動 → 旧日付 1/2 に残らず、旧窓の再取得で 12/24 に出る (4) 逆向き（カレンダー Pacific/Kiritimati +14 の 1/3 終日）が 11/22 窓（timeMax 1/3 00:00 JST）の完全応答に無ければ消える (5) 同一 TZ の境界（窓開始ちょうどで終わる終日・時間指定、窓終了ちょうどから始まる終日）は除去しない。既存の削除反映・移動・重複除去のテストは copy() で日付だけ変えていた予定を JSON からの変換で作り直した（照合区間を日付と一致させるため）
      - 陰性対照: 除去判定を一時的に旧来の表示日判定へ戻して GoogleEventsTest を実行 → (2)(3)(4) の3件だけが失敗（28件中3件）。戻した後のソースで check.sh を実施
      - 検証: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug）。単体テスト 56件（失敗 0、省略 1＝RealEventsDumpTest）、Roborazzi ベースライン3画面に差分なし。ブランチ CI は下の追記を参照
      - 未確認: 実 API（カレンダー TZ が端末と異なる実カレンダー）での挙動は未確認（静的な仕様との照合と単体テストのみ）。実機照合はユーザーの最終チェックの範囲

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
