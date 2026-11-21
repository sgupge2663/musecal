# 検証環境タスク（接頭辞 EM-）

- [x] EM-1 スクショテスト基盤（Phase 1 / 案D）— 完了（2026-11-21、ブランチ claude/em1-screenshot-tests）
  - 内容: Roborazzi（または同等の JVM スクショテスト）を導入し、月表示（サンプルデータ）・未ログイン案内画面・日ポップアップの3画面を PNG 化するテストを書く。check.sh と CI の両方で走るようにする
  - 完了条件: 変更のたびに3画面のスクショが成果物として残り、Muse が読んで目視確認できる。前回との差分が分かる形（ベースライン管理）になっている
  - 検証方法: わざと色を1つ変えた試験変更で、差分として検出されることを確認し QC に記録
  - 完了記録（2026-11-21）: 3画面のベースラインを `app/src/test/screenshots/` に固定し、運用手順と実証記録は docs/qc.md。当初2画面で先行し、未ログイン案内画面は V1-1（claude/v11-signin、当時は未統合）が main（1614531）に統合された後、本ブランチへ main をマージして3画面目（sign_in_guide.png）を追加し完了とした。あわせて Codex レビュー条件対応として、CI への roborazzi 出力の常時アップロード追加と、verify 強制の対象絞り込みを実施

- [x] EM-2 CI エミュレータジョブ（Phase 2 / 案C）— 完了（2026-11-22、main へ統合済み 8972c8a。Codex 確認レビューで受理可）
  - 内容: GitHub Actions に workflow_dispatch のジョブを追加。Ubuntu ランナー＋KVM でエミュレータを起動し、デバッグ APK をインストール。主要画面の撮影に加え、Compose UI Test 等のシナリオで実際に操作する（タブ切替、日タップ→ポップアップ、月表示のスクロール、未ログイン画面の表示確認）。各ステップのスクショと画面構造ダンプを成果物に上げる
  - 完了条件: 手動実行で操作シナリオまで含めて成功し、PNG と画面構造が成果物から取得できる。サインインは未ログイン状態の確認まで（実アカウントは使わない）
  - 検証方法: 実行ログと成果物を Muse が確認。所要時間も記録する
  - 実装（2026-11-21、ブランチ claude/em2-ci-emulator）: ワークフロー `.github/workflows/emulator.yml`（workflow_dispatch のみ。build.yml は不変）＋本体スクリプト `.github/scripts/em2_scenario.sh`（`scripts/` は .gitignore の許可リスト制のため、既存ファイルを変えずに置ける場所として .github/ 配下にした）＋ instrumented テスト `app/src/androidTest/`。app/build.gradle.kts は末尾に別ブロックで testInstrumentationRunner と androidTestImplementation を追加しただけ（V1-3 との衝突回避）
  - 方式の選択: 「adb 入力＋UI Automator」と「Compose UI Test」の併用。理由: 実アプリは未サインインだと案内画面しか出さず（MuseCalRoot）、実アカウントは使わないので、実アプリを起動して操作できるのは案内画面まで。そこで (1) 実アプリの起動〜未ログイン案内の確認は adb（am start・screencap・uiautomator dump）で入口そのものを確かめ、(2) 月表示の操作（タブ切替・日タップ→ポップアップ・スクロール）は EM-1 と同じ前提（サンプル予定・今日=2026-12-16・既存3タブ）で MuseCalApp を描画し Compose UI Test で実エミュレータ上でタップ・スワイプする。Compose は描画を Canvas で行うため予定の文字は画面構造に出ず、座標指定の adb 入力だけでは「操作が効いたか」を判定しにくい。Compose UI Test ならノード指定の操作・待機と、画面の画素差による判定が確実にできる
  - 証拠: 1ステップごとに PNG（端末のスクリーンショット）・XML（UI Automator dump）・Compose セマンティクスのテキストを成果物 `emulator-evidence-<sha>` に上げる。判定に使った画素差は notes.txt
  - 実行記録（2026-11-21）:
    - workflow_dispatch 実行: run 44923910463（コミット 82aaaa0）成功。run 全体 約4分17秒（2026-11-21T16:19:09Z → 16:23:26Z）、うちジョブ 4分13秒（16:19:12〜16:23:25 UTC）。内訳: APK＋テスト APK のビルド 2分01秒、エミュレータ起動〜シナリオ〜取り出し 2分02秒（timing.txt では 122 秒。シナリオ本体はインストール開始から 36 秒）
    - 端末: Android 14（API 34 google_apis x86_64、pixel_6 プロファイル、1080x2400・420dpi）
    - 結果: 実アプリ起動 PASS（起動後の uiautomator dump に「Google でログイン」）、instrumented `OK (2 tests)`。判定値（notes.txt）: タブ「メイン」非表示で画素差 3.6%、戻すと 0.0%（初期表示と一致）、上下スクロール前後で 23.7%。上スクロール2回で見出しは 2026年12月→2027年9月
    - 成果物 `emulator-evidence-82aaaa0`（34ファイル）: app_launch/（01_launch_signed_out.png・.xml、am_start.txt、resumed_activity.txt、result.txt）、em2/signed_out/（01_sign_in_guide の .png・.xml・_semantics.txt）、em2/month_view/（01_month_initial・02_tab_main_hidden・03_tab_main_shown・04_day_popup_dec16・05_popup_closed・06_scrolled_forward・07_scrolled_back の各 .png・.xml・_semantics.txt、notes.txt）、instrument.txt、logcat.txt、device.txt、timing.txt。PNG は Claude Code が目視確認済み（案内画面、メイン非表示、12/16 ポップアップ「3 予定」、2027年9月へのスクロール）
    - 同じ内容の先行実行: run 44923393577（コミット 665623b、一時 push トリガー起動）成功 4分38秒、instrumented `OK (2 tests)`
    - Muse の確認（2026-11-21）: 成果物 emulator-evidence-82aaaa0（約1.5MB）をダウンロードし、上記の構成（app_launch/・em2/month_view/ 7ステップ・em2/signed_out/・device.txt・instrument.txt・logcat.txt・timing.txt）を確認
    - push 時の Build APK: run 44922971487（221a836）成功 3分49秒。Build APK は androidTest をコンパイルしないため、テストのコンパイルの証拠は emulator ワークフローのビルド工程
  - 手順上の注意（ワークフロー登録）: GitHub は既定ブランチ（main）に無いワークフローをファイル名で workflow_dispatch できない（`gh workflow run emulator.yml` は HTTP 404）。そこで 665623b で一時的に「このブランチへの push」トリガーを足して1回走らせ、ワークフロー ID（426107230）を登録させてから、82aaaa0 で外し（ファイルは 221a836 と同一）、`gh workflow run 426107230 --ref claude/em2-ci-emulator` で起動した。main 統合後は Actions 画面の「Run workflow」またはファイル名で起動できる
  - 未確認: ローカルでの instrumented テストのコンパイル（3回とも、他セッションの Gradle が共有キャッシュを握っていてロック待ちで起動できず。CI の成功で代替）。actionlint によるワークフロー検査（道具の取得が権限で止められたため、YAML の構文解析と GitHub 上での実行成功で代替）。push は SKIP_CHECK=1（理由: 同じ Gradle ロック競合で check.sh が回らないため。gate は CI）。スクロールの滑らかさ（QC-06）はこのジョブの対象外（静止画と画素差のみで、ソフトウェア描画のエミュレータでもあるため触感は実機で判定）
  - レビュー（2026-11-21、Codex、対象 12e4b7b）: 判定は条件付き受理可（記録の正本は work/em2-12e4b7b-review.md）。受理条件 P2: em2_scenario.sh が証拠の取り出し（adb pull / exec-out）に失敗しても exit 0 になり、PNG/XML が0件でも成功扱い（モック adb で再現）。改善推奨 P3: checkout・setup-java・upload-artifact の SHA 固定と permissions: contents: read の明示。P3 の assertion 精度（EmulatorScenarioTest.kt）は今回の対象外
  - 条件への対応（2026-11-22、Claude Code、コミット 682e28e）:
    - em2_scenario.sh: 証拠の取得（起動画面の screencap・adb pull×2、exec-out run-as tar、tar の展開、logcat -d）をすべて検査し、失敗を evidence_errors.txt に記録。終わりに必須ファイル31件（device.txt・instrument.txt・logcat.txt、起動画面の PNG/XML/result.txt、instrumented の8ステップ × PNG・XML・semantics、notes.txt）の存在と非空、PNG はシグネチャ、XML は `<hierarchy` を確かめて evidence_check.txt に出す。全体の終了コードは「起動画面 PASS かつ instrumented PASS かつ 証拠 PASS」。必須一覧は EmulatorScenarioTest.kt の capture() と対応させる（ステップを足したら一覧にも足す）
    - emulator.yml: actions/checkout@11d5960（v4.4.0）・actions/setup-java@cf277c6（v4.9.1）・actions/upload-artifact@ea165f8（v4.6.2）に SHA 固定（いずれも v4 タグが指していたコミット）。`permissions: contents: read` を明示。upload-artifact の if-no-files-found を warn→error（部分的な欠けはスクリプト側の検査で失敗にし、丸ごと空はアップロード側でも失敗にする）
    - 検証（モック adb、VM 上）: `bash -n` OK。adb を模擬するスクリプトを PATH 先頭に置き5通りで実行: 正常→exit 0（31/31）、pull と exec-out を失敗（レビューの再現と同じ）→exit 1（PNG 0・XML 0、ok 4/31）、1ファイル欠落→exit 1（30/31）、exec-out が exit 0 で tar でない出力→exit 1、PNG が壊れている→exit 1。比較として修正前（12e4b7b）のスクリプトは pull/exec-out 失敗で exit 0（PNG 0・XML 0）を再現。再現手順: モック adb の MOCK_MODE を ok / fail_pull_exec / drop_one / garbage_exec / bad_png で切り替え、`PATH=<mock>:$PATH bash .github/scripts/em2_scenario.sh <出力先>` の終了コードと evidence_check.txt を見る（モックは Git 管理外。VM の /tmp/em2-mock/mock_adb.sh）
    - CI: workflow_dispatch run 44942878156（682e28e）成功。ジョブ 4分14秒（19:48:16〜19:52:30 UTC）、エミュレータ〜シナリオ 122 秒。ログに `required: 31 files, ok: 31`・`evidence: PASS`・`OK (2 tests)`。GITHUB_TOKEN の権限表示は Contents: read / Metadata: read のみ（以前の表示にあった Packages: read も無くなり、狭まった）
    - 成果物 `emulator-evidence-682e28e`: 35ファイル（以前の34＋evidence_check.txt）、空ファイル0、PNG 9件はすべて正しいシグネチャ、XML 9件、semantics 8件。判定値（notes.txt）は前回と同じ（3.6% / 0.0% / 23.7%）
    - ローカル check.sh: Gradle のロック競合は解けていたが、testDebugUnitTest の ScreenshotTest 3件が「Robolectric の android-all-instrumented を取得できない」で失敗（VM の中継プロキシ auth_relay_proxy が停止中で 127.0.0.1:18080 に繋がらないため。今回の変更は YAML とシェルだけでアプリのコードに触れていない）。プロキシは今回の作業範囲外なので起動せず、push は SKIP_CHECK=1 とし、CI を gate にした
  - 確認レビュー（2026-11-22、Codex、対象 37ca89c）: 判定は受理可、新規指摘なし（記録の正本は work/em2-37ca89c-review.md）。GITHUB_TOKEN の権限は Contents: read / Metadata: read のみ
  - 統合記録（2026-11-22、Muse）: ブランチ CI 成功（Build APK run 44943340130＝37ca89c、Emulator scenario run 44942878156）を確認してから、main へ merge（8972c8a）→ push → `branch_ops.sh prune --dry-run` → `prune`（ブランチ claude/em2-ci-emulator のローカル＋リモートと worktree を削除）→ `audit` クリーン（exit 0）。統合時の push は一度 pre-push の check.sh で止まった（原因: `generateDebugAndroidTestLintModel` が EM-2 の androidTest 依存（androidx.test:runner 1.6.2 等）を初めて取得する際に PKIX 失敗。ランタイム再起動で egress CA が回転し、toolchain/truststore.jks の hatch-egress-ca（2026-11-21 追加）が旧鍵のままだった。現行 CA（/usr/local/share/ca-certificates/hatch-egress-ca.crt、SHA-256 C5:B1:0D:0B…）を keytool で入れ直して check.sh 成功、push 完了。教訓は ~/AGENTS.md に記録）
  - Firebase Test Lab はやらない（ユーザー決定 2026-11-22）: 仮想デバイスは EM-2 の CI エミュレータと重複し、実機の手触りは測れない。物理デバイスのスモークチェックだけが利点だが、サービスアカウント鍵（新規 Secret）の管理負担と Spark 固定・実行の遅さを考えると効果が薄い。無料枠（仮想10回/日・物理5回/日）は使わない
  - 未確認のまま残るもの: ユーザーの見た目の最終判定（原則5）。実アプリの月表示操作（デバッグ用ダミーサインイン等の仕様追加が必要かは判断待ち）。logcat 公開の件は Codex が秘密値なしを確認済み（未知形式までの完全保証ではない、と留保つき）
  - 判断してほしいこと: (1) 月表示の操作は実アプリ（MainActivity）ではなく MuseCalApp を直接描画した画面で行っている（未サインインでは月表示に入れないため）。実アプリの月表示まで操作したい場合は、デバッグ限定のダミーサインイン入口など仕様の追加が要る。(2) logcat.txt（約2.4MB）も成果物に含めている。公開リポジトリの成果物は誰でも見られるが、中身はエミュレータとサンプル予定のログのみ

- [ ] EM-3 QC への組み込み
  - 内容: docs/qc.md の視覚系ケース（QC-03 レイアウト、QC-06 スクロール等）に、エミュレータ／スクショテストでの一次判定の手順と、実機判定が必要な範囲の切り分けを追記する
  - 完了条件: 次の変更サイクルで、視覚確認が「エージェント一次判定→ユーザーは最終判定のみ」の流れで回る
  - 検証方法: 実際の変更1件で運用し、記録が残ること

- [ ] EM-4 PC エミュレータ対話環境（Phase 3 / 案A の具体化）
  - 内容（本線、2026-11-21 ユーザー提案）: ユーザー PC 上のエミュレータを、PC 側の Claude Code / Codex が mobile-mcp（または phonedriver）で直接操作する。PC 側エージェントは既存の研究分担で常設されているため、この形が一番素直
  - 内容（予備）: VM 側エージェントから直接操作する必要が出た場合のみ、Tailscale 経由の ADB / mobile-mcp HTTP 接続を追加する
  - 完了条件: PC 側エージェントがエミュレータ上の MuseCal を起動→タップ→スクロール→撮影まで対話的に実行し、結果（スクショ＋所見）が VM 側に共有される
  - 検証方法: 往復の記録とスクショ
