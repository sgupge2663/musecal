# QC 証拠ログ

固定ケース集に対して実施記録を追記していく。判定は deterministic な確認（ビルド、コード照合、ピクセル実測）を先に、AI の印象判定は補助、人（ユーザー）の判定は別欄に残す。

## 固定ケース
- QC-01 月表示： 2026年12月（37件）が欠けなく描画される
- QC-02 複数日帯： 「帰省」が 12/30→1/2 に連続した帯で表示される
- QC-03 終日と時間指定の区別： 終日は帯。時間指定は単日なら時刻つきテキスト、複数日にまたがる場合は帯で表示される
- QC-04 日ポップアップ： 日付タップでその日の予定一覧と見出しアイコン3つが出る
- QC-05 下部タブ： カレンダー切替ができ、選択中タブにカレンダー色の線が出る。非表示にしたカレンダーは灰色になる
- QC-06 スクロール： 月表示の縦スクロールが引っかからず滑らか（人判定）
- QC-07 文字の収まり： 予定名が文字の途中で切れない（人判定＋スクショ）
- QC-08 色の質感： 帯と文字の見た目が BC と同等に感じる（人判定。色値の根拠は REQUIREMENTS.md §7 の実測）

- QC-09 「今日」の更新： 日付をまたいで画面に戻ったとき、今日の強調と今日ボタンの移動先が当日になる（T-102 の回帰確認）
- QC-10 フォント倍率： 端末の文字サイズを最大にしても、予定テキストが重ならずセル内に収まる（T-103 の回帰確認）

## 実施記録の様式

新しい記録はこの型で書く：
- 日付 / 対象（commit・CI run への参照）
- 入力・経路（どの操作・どの画面か）
- 状態（データ・端末・文字倍率など前提）
- 結果（実測値や出力。証拠ファイルのパス）
- AI判定 / 人判定（別々に） / 理想 / 対策

## スクショテスト（EM-1）の運用手順

JVM スクショテスト（Roborazzi + Robolectric、エミュレータなし）で主要画面の描画を固定する。Phase 1（案D）の仕組みであり、操作のテストではない（操作は Phase 2 の EM-2）。

- 実装: `app/src/test/java/jp/hayashida/musecal/ScreenshotTest.kt`
  - `month_view.png`: 月表示（サンプルデータ、2026年12月、「今日」は 2026-12-16 に固定。V1-1 統合後はアカウント帯つき。テスト用メールアドレスは固定ダミー）
  - `day_popup_dec16.png`: 12月16日のセルを実際にタップして開いた日ポップアップ
  - `sign_in_guide.png`: 未ログイン案内画面（V1-1 の SignInScreen を未サインイン状態で描画）
  - 前提は固定する： 画面 w360dp-h800dp-xxhdpi（1080x2400px）、タイムゾーン Asia/Tokyo、SDK 35
- ベースラインの置き場: `app/src/test/screenshots/`（git 管理する。ここが正本）
- 検証（通常運用）: `scripts/check.sh` と CI の `testDebugUnitTest` が verify モードで比較し、差分があればテスト失敗になる。失敗時は `app/build/outputs/roborazzi/` に `<名前>_actual.png`（今回の描画）と `<名前>_compare.png`（Reference/Diff/New の比較画像）が出るので、まずそれを見て「意図した変更か、崩れか」を判定する。CI では同ディレクトリが成果物 `roborazzi-outputs-<sha>` として常時（失敗時も）アップロードされる
- ベースラインの更新（意図した見た目の変更のときだけ）:
  1. `./gradlew recordRoborazziDebug` を実行する
  2. `app/src/test/screenshots/` の差分を目視確認してから、コード変更と一緒に commit する
  3. 意図しない差分でベースラインを更新して誤魔化さない。崩れは実装側を直す
- 注意: Roborazzi はプロパティ未指定だとキャプチャ自体が無効になるため、`app/build.gradle.kts` で通常実行時は `roborazzi.test.verify=true` を既定にしている。この設定を消すと検証が静かに無効化されるので消さないこと。対象は Roborazzi が実行される単体テストタスク（test&lt;Variant&gt;UnitTest）だけに限定し、verify 強制を外すのは「そのタスクと同じ variant の record / compare Roborazzi タスク（または variant なしの集約タスク）が要求されたとき」だけ（Codex レビューの低指摘への対応。以前は要求タスク名に record/compare を含むと全 Test タスクの verify が外れていた）

## 実施記録

### 2026-11-21 v0（commit 0cd52b5 以前〜v0 配布時点）
- 経路: Codex レビュー（work/review_v0.md）＋ユーザーの実機確認
- QC-01〜03: AI判定=概ね適合（構造は仕様どおり）。人判定=未実施（個別には未確認）
- QC-04: AI判定=不適合（見出しアイコン3つが未実装）→ T-101 で対応
- QC-06: 人判定=不適合（「少しだけカクカク」）→ T-105 で対応
- QC-07: 人判定=不適合（文字が途中で切れる）→ T-106 で対応
- QC-08: 人判定=不適合（「色の質感がチープ」）→ T-107 で対応

### 2026-11-21 v0.1（commit 3834e8e）
- 経路: Claude Code 実装、Muse がコードと CI ビルドで検品。実機判定はユーザー待ち
- QC-04: AI判定=適合（コードにアイコン実装を確認）
- QC-05: AI判定=適合見込み（タブ等幅化と色線の修正をコードで確認）
- QC-06/07/08: 人判定待ち

### 2026-11-21 v0.2 / T-107（commit e76e9be）
- 経路: Claude Code が BC と MuseCal のスクショをピクセル実測して実装。色値（Events.kt）は無変更
- 実測で判明: BC の帯は角丸なし、帯文字は標準の太さ。帯の高さ約34px に合わせ帯文字を 11sp に変更（副作用: 1セルに入る行数が増える）
- QC-08: 人判定=要改善（2026-11-21 ユーザー実機判定「色もうちょい鮮やかというか、濃ゆくて、しっかりしててもいいかも」）→ T-108 で色を鮮やか・濃くする方向へ仕様 §6 を更新して対応
- QC-05: 実機スクショでタブの色線が表示されていることを Muse が目視確認（2026-11-21）
- QC-06/07: 人判定は未受領（継続）

### 2026-11-21 v0.3 / T-108（ブランチ claude/t108-vivid-color）
- 経路: Claude Code 実装。仕様 §6 にユーザーの方向（BC 実測一致より鮮やかさ・濃さ優先）を追記してから着手
- 入力・経路: 月表示の単日・時間指定予定の文字色（Events.kt の色ユーティリティ → MainActivity の drawWeek）
- 状態: 帯はパレット原色のまま無変更。文字色の経路のみ変更
- 結果: 旧 readableOn（コントラスト比 3.2 に届くまで黒/白へ lerp し、色相が動き淡くなりがち）を廃し、textShade に置換。定義は HSL で色相・彩度を保持し、明るい背景では L' = L × 0.72、暗い背景（選択セルの黒）では対称に L' = 1 − (1 − L) × 0.72。代表色の変換は単体テスト TextShadeTest（6件）で固定し全通過： #DC2127→#9E181C、#FBD75B→#F0BC06、#FFB878→#FF810F、#5484ED→#1651D1（いずれも白背景）、#FBD75B→#FCE289（黒背景）。`assembleDebug testDebugUnitTest` 成功（lintDebug は VM の中継 TLS 問題のためローカル未実施、CI が gate）
- AI判定 / 人判定: AI判定=実装済み（証拠つき） / 人判定=適合（2026-11-21 ユーザー実機判定「いいねみやすい」。月表示と日ポップアップの実機スクショが証拠。係数 0.72 のままで確定）
- 理想 / 対策: 実機で「鮮やか・濃い」と感じられること。人判定で不足なら係数 0.72 を調整する → 適合のため調整なし

### 2026-11-21 EM-1 スクショテスト基盤（ブランチ claude/em1-screenshot-tests）
- 経路: Claude Code 実装（changes/2026-11-21-emulator-verification の EM-1）。Roborazzi 1.30.1 + Robolectric 4.14.1 を選定（理由: tasks.md の指定が Roborazzi であり、Robolectric のネイティブ描画なら Canvas/drawBehind の実描画がそのまま PNG になり、日セルへの実タップでポップアップも開けるため）
- 入力・経路: 月表示（2026年12月・サンプル37件・「今日」= 2026-12-16 固定）と、12月16日セルをタップして開く日ポップアップを JVM 上で描画
- 状態: w360dp-h800dp-xxhdpi（1080x2400px）、Asia/Tokyo、SDK 35、フォント倍率 1.0
- 結果: ベースライン2枚を `app/src/test/screenshots/` に生成し目視確認した（月表示は QC-01/02/03 の構造どおり、日ポップアップは「3 予定」＋見出しアイコン3つ＋予定3件）。record を2回実行しても同一ハッシュで決定的。`assembleDebug testDebugUnitTest lintDebug` 全通過（ScreenshotTest 2件＋TextShadeTest 6件）
- 差分検出の実証: 試験変更として MainActivity の TodayBg を #FFF4C2 → #00FF00 に変えて通常の `testDebugUnitTest` を実行 → monthView_dec2026 のみ FAILED（AssertionError: Roborazzi … is changed、差分率 1.61%、比較画像 `month_view_compare.png` で 16 日セルだけが差分として示された）。試験変更は戻し済みで、戻した後は全通過
- AI判定 / 人判定: AI判定=適合（描画の固定と差分検出を証拠つきで確認） / 人判定=未実施（ベースラインの見た目自体は v0.3 で人判定済みの画面の固定であり、新規の見た目判定は不要と Muse が判断したらその旨を残す）
- 理想 / 対策: 変更のたびに after スクショが自動で残り、崩れが gate で止まること。未ログイン案内画面は V1-1 統合後に追加する（EM-1 の残件）
### 2026-11-21 v1 V1-1 ログイン基盤（ブランチ claude/v11-signin）
- 経路: Claude Code 実装（worktree .worktrees/claude-v11）。レビューは別エージェント（Codex）待ち
- 内容: Credential Manager（GetGoogleIdOption）でサインイン状態を確立し、Calendar 認可は AuthorizationClient（scope は calendar.readonly のみ要求）に分離（CalendarAuth.kt が認証の窓口）。未サインイン時はサンプル予定を出さず案内画面のみ（V1-5 の未ログイン画面を兼ねる）。サインイン済みはヘッダー下の細い帯にメールアドレスとサインアウトを表示。月表示のデータはまだサンプル（実データ接続は V1-3）
- 証拠: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug すべて OK）。単体テスト 7 件全通過（TextShadeTest 6 件＋CalendarAuthTest 1 件: scope 定数の文字列が読み取り専用であることを検証 ※実際の要求一覧そのものの検証は下記のレビュー条件対応で強化）
- 環境メモ: ローカル lint が落ちていた既知問題は、toolchain/truststore.jks に中継プロキシの CA（Hatch Sandbox Egress CA）が含まれていなかったことが原因と判明し、CA を追加して解消した（リポジトリ外の環境修正）。あわせて応答しなくなっていた auth_relay_proxy を再起動した
- AI判定 / 人判定: AI判定=実装済み（ビルド証拠つき） / 実機検証=**未実施**。Google Cloud 側の OAuth クライアント作成が未完了で、Credential Manager 用の Web クライアント ID（R.string.google_web_client_id）もプレースホルダのため、実機サインインはまだ成功しない。クライアント ID 設定後に「実機でログインできる・再起動後も保持される・サインアウトで案内画面に戻る」を確認する。固定ケース化は V1-6 で行う

### 2026-11-21 v1 V1-1 レビュー条件対応（ブランチ claude/v11-signin）
- 経路: Codex レビュー（条件付き受理可）の指摘1（中）への対応を Claude Code が実施
- 内容: (1) CalendarAuthTest を強化。従来は scope 定数（CALENDAR_READONLY_SCOPE）の文字列しか検証しておらず、認可要求のリストに別 scope が混入しても通過する状態だった。要求 scope の集合を `CalendarAuth.REQUESTED_SCOPE_URIS` に集約し（認可要求は必ずこの一覧から組み立てる）、テストがその一覧そのものについて「calendar.readonly 1 件のみ」であることを検証するようにした。なお Scope オブジェクトの生成は GMS の Preconditions 経由で android.text.TextUtils を呼ぶため JVM 単体テストでは実行できず、一覧から Scope への変換（1 対 1 のラップ）はテスト対象外。(2) Google Cloud プロジェクト「musecal」の Web クライアント ID を `R.string.google_web_client_id` に実値差し替え（クライアント ID は公開情報。シークレットは不使用・未記録）
- 証拠: `scripts/check.sh` 成功（assembleDebug / testDebugUnitTest / lintDebug すべて OK）。単体テスト 8 件全通過（TextShadeTest 6 件＋CalendarAuthTest 2 件）
- AI判定 / 人判定: AI判定=レビュー条件対応済み / 実機検証=**未実施**（ユーザーの実機でログイン試行待ち）。V1-1 の完了条件（実機でログインできる・再起動後も保持される）は未確認のため、V1-1 は未完了のまま

### 2026-11-21 EM-1 条件対応・3画面目追加（ブランチ claude/em1-screenshot-tests）
- 経路: Codex レビュー（条件付き受理可）への対応を Claude Code が実施。現行 main（1614531、V1-1 統合済み）を本ブランチへマージ（マージコミット 72e5ec1）。競合は本ファイルの実施記録セクションのみで、両側の記録（EM-1 基盤と V1-1 の2件）を意味を保って併記して解消。changes/2026-11-21-emulator-verification/ はブランチ側の追跡版を正本とした
- 3画面目（未ログイン案内画面）: ScreenshotTest に `signInGuide_signedOut` を追加し、V1-1 の SignInScreen を未サインイン状態で描画。ベースライン `sign_in_guide.png` を新規固定し目視確認した（黒ヘッダー＋アプリ名＋説明3行＋「Google でログイン」ボタン。サンプル予定は出ていない）。あわせて月表示は V1-1 のアカウント帯（テスト用固定ダミーのメールアドレス＋サインアウト）を反映して `month_view.png` を再記録し目視確認。日ポップアップのベースラインは不変。SignInScreen は本テストから参照するため private → internal に変更（MainActivity.kt）
- CI 成果物: `.github/workflows/build.yml` に `app/build/outputs/roborazzi/` を `if: always()` でアップロードする step を追加（成果物名 `roborazzi-outputs-<sha>`、permissions の変更なし。EM-2 に先立つ小追加として Muse の指示・承認済み Phase 2 の範囲内）
- verify 絞り込み（Codex 低指摘）: `app/build.gradle.kts` の verify 強制を、Roborazzi が実行される単体テストタスク（test&lt;Variant&gt;UnitTest）に限定し、強制を外す条件を「同じ variant の record / compare Roborazzi タスク（または集約タスク）が要求されたとき」だけに変更。実証: 絞り込み後も通常の `testDebugUnitTest` で試験変更（TodayBg #FFF4C2 → #00FF00）を与えると monthView_dec2026 が FAILED になり、`month_view_actual.png` / `month_view_compare.png` が出力されることを確認（試験変更は戻し済み）。recordRoborazziDebug も正常に記録できることを確認済み
- 証拠: `recordRoborazziDebug` 成功（ベースライン3枚が決定）。`scripts/check.sh` とブランチ CI の結果は本記録の追記後に確定し、コミットメッセージと Muse への報告で参照する
- AI判定 / 人判定: AI判定=EM-1 完了条件（3画面）を満たす / 人判定=対象外（新規の見た目変更ではなく、既存画面の固定追加のため）

### 2026-11-21 v1 V1-1 実機検証（ユーザーの実機）
- 経路: ユーザーが v0.4（main 1614531 のビルド）を実機にインストールし、スクショ5枚で報告
- 結果: アカウント選択画面に本人のアカウントが表示され、kou1995yurin530@gmail.com を選択 → 「Google で確認されていません」警告（同意画面が Testing 状態のため想定どおり）を続行 → サインイン済みの月表示（メール帯＋サインアウトボタン）を確認。完了条件「実機で本人のアカウントでログインできる」を満たす
- 未確認: 再起動後のログイン保持、サインアウトで案内画面に戻ること（明示確認なし。次回起動時に自然に確認される見込み）
- AI判定 / 人判定: 人判定=ログイン成功（ユーザーの実機スクショが証拠）

### 2026-11-21 v1 V1-2 カレンダー一覧とタブ対応（ブランチ claude/v12-calendar-list）
- 対象: ブランチ `claude/v12-calendar-list`（main d0c81b7 から分岐）。CI run はコミットメッセージと Muse への報告で参照
- 入力・経路: 単体テスト（CalendarsTest）で calendarList → タブの対応づけを検証。スクショテスト（ScreenshotTest）は月表示のタブを calendarList 実物（2026-11-21 取得の3件）のフィクスチャから `mapCalendarTabs` で作って描画
- 状態: JVM（Robolectric）、w360dp-h800dp-xxhdpi、Asia/Tokyo。予定はサンプルのまま（V1-3 で差し替え）
- 結果: `scripts/check.sh` 成功（単体テスト 21件全通過、うち CalendarsTest 10件）。Roborazzi 3画面は verify で一致し、`recordRoborazziDebug` で再記録しても PNG の差分なし（month_view.png sha1 a85504c…）。月表示のタブ帯を切り出して目視し、メイン=#9fe1e7・AI木曜会=#fad165・日本の祝日=#9a9cff の色線を確認
- QC-05: AI判定=適合見込み（タブの ON/OFF を calendarId で判定、色線は calendarList の色） / 人判定=未確認（実機確認は V1-3 後に実データ版でまとめて行う）
- 未確認: 実機での calendarList 取得（実 API 呼び出し）と、各タブにそのカレンダーの予定だけが出ること（V1-2 完了条件。V1-3 後）
- レビュー条件対応（2026-11-21、Codex 条件1）: 取得要求をアカウントに紐づけ、今のアカウントと一致しない結果を捨てる `AccountRequestGate` を追加。固定ケース「取得中にサインアウト→別アカウントへサインイン」を `AccountRequestGateTest` で再現（遅れて返った前アカウントの結果は、新アカウントの結果の前後どちらで返っても反映されない）。結果: `scripts/check.sh` 成功（単体テスト 27件全通過、うち AccountRequestGateTest 6件）、`verifyRoborazziDebug` 一致（ベースライン変更なし）。UI 結合（実機でのサインアウト→別アカウント）は未確認
- 条件2（実機証拠とユーザー判定）: 未確認のまま据え置き（V1-3 後の実データ版でまとめて行う）
