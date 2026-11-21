# 検証環境タスク（接頭辞 EM-）

- [x] EM-1 スクショテスト基盤（Phase 1 / 案D）— 完了（2026-11-21、ブランチ claude/em1-screenshot-tests）
  - 内容: Roborazzi（または同等の JVM スクショテスト）を導入し、月表示（サンプルデータ）・未ログイン案内画面・日ポップアップの3画面を PNG 化するテストを書く。check.sh と CI の両方で走るようにする
  - 完了条件: 変更のたびに3画面のスクショが成果物として残り、Muse が読んで目視確認できる。前回との差分が分かる形（ベースライン管理）になっている
  - 検証方法: わざと色を1つ変えた試験変更で、差分として検出されることを確認し QC に記録
  - 完了記録（2026-11-21）: 3画面のベースラインを `app/src/test/screenshots/` に固定し、運用手順と実証記録は docs/qc.md。当初2画面で先行し、未ログイン案内画面は V1-1（claude/v11-signin、当時は未統合）が main（1614531）に統合された後、本ブランチへ main をマージして3画面目（sign_in_guide.png）を追加し完了とした。あわせて Codex レビュー条件対応として、CI への roborazzi 出力の常時アップロード追加と、verify 強制の対象絞り込みを実施

- [ ] EM-2 CI エミュレータジョブ（Phase 2 / 案C）
  - 内容: GitHub Actions に workflow_dispatch のジョブを追加。Ubuntu ランナー＋KVM でエミュレータを起動し、デバッグ APK をインストール。主要画面の撮影に加え、Compose UI Test 等のシナリオで実際に操作する（タブ切替、日タップ→ポップアップ、月表示のスクロール、未ログイン画面の表示確認）。各ステップのスクショと画面構造ダンプを成果物に上げる
  - 完了条件: 手動実行で操作シナリオまで含めて成功し、PNG と画面構造が成果物から取得できる。サインインは未ログイン状態の確認まで（実アカウントは使わない）
  - 検証方法: 実行ログと成果物を Muse が確認。所要時間も記録する
  - 実装（2026-11-21、ブランチ claude/em2-ci-emulator）: ワークフロー `.github/workflows/emulator.yml`（workflow_dispatch のみ。build.yml は不変）＋本体スクリプト `.github/scripts/em2_scenario.sh`（`scripts/` は .gitignore の許可リスト制のため、既存ファイルを変えずに置ける場所として .github/ 配下にした）＋ instrumented テスト `app/src/androidTest/`。app/build.gradle.kts は末尾に別ブロックで testInstrumentationRunner と androidTestImplementation を追加しただけ（V1-3 との衝突回避）
  - 方式の選択: 「adb 入力＋UI Automator」と「Compose UI Test」の併用。理由: 実アプリは未サインインだと案内画面しか出さず（MuseCalRoot）、実アカウントは使わないので、実アプリを起動して操作できるのは案内画面まで。そこで (1) 実アプリの起動〜未ログイン案内の確認は adb（am start・screencap・uiautomator dump）で入口そのものを確かめ、(2) 月表示の操作（タブ切替・日タップ→ポップアップ・スクロール）は EM-1 と同じ前提（サンプル予定・今日=2026-12-16・既存3タブ）で MuseCalApp を描画し Compose UI Test で実エミュレータ上でタップ・スワイプする。Compose は描画を Canvas で行うため予定の文字は画面構造に出ず、座標指定の adb 入力だけでは「操作が効いたか」を判定しにくい。Compose UI Test ならノード指定の操作・待機と、画面の画素差による判定が確実にできる
  - 証拠: 1ステップごとに PNG（端末のスクリーンショット）・XML（UI Automator dump）・Compose セマンティクスのテキストを成果物 `emulator-evidence-<sha>` に上げる。判定に使った画素差は notes.txt
  - 実行記録: （CI 実行後に記入）

- [ ] EM-3 QC への組み込み
  - 内容: docs/qc.md の視覚系ケース（QC-03 レイアウト、QC-06 スクロール等）に、エミュレータ／スクショテストでの一次判定の手順と、実機判定が必要な範囲の切り分けを追記する
  - 完了条件: 次の変更サイクルで、視覚確認が「エージェント一次判定→ユーザーは最終判定のみ」の流れで回る
  - 検証方法: 実際の変更1件で運用し、記録が残ること

- [ ] EM-4 PC エミュレータ対話環境（Phase 3 / 案A の具体化）
  - 内容（本線、2026-11-21 ユーザー提案）: ユーザー PC 上のエミュレータを、PC 側の Claude Code / Codex が mobile-mcp（または phonedriver）で直接操作する。PC 側エージェントは既存の研究分担で常設されているため、この形が一番素直
  - 内容（予備）: VM 側エージェントから直接操作する必要が出た場合のみ、Tailscale 経由の ADB / mobile-mcp HTTP 接続を追加する
  - 完了条件: PC 側エージェントがエミュレータ上の MuseCal を起動→タップ→スクロール→撮影まで対話的に実行し、結果（スクショ＋所見）が VM 側に共有される
  - 検証方法: 往復の記録とスクショ
