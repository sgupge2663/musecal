# MuseCal

Business Calendar 2 風の月表示を持つ、個人用の Android カレンダーアプリ。
Google カレンダーと直接同期し、Android 標準のカレンダー同期を経由しない設計にするのが最終目的。

- 仕様: `REQUIREMENTS.md`（BC2 の実スクリーンショットから起こした仕様。原本画像は `reference/`）
- 色の運用ルール: 予定の色は Google 標準 colorId。既定は色なし、フラミンゴ(4)=通常シフト、トマト(11)=イレギュラー・重要 など
- v0 の範囲: 同梱サンプルデータ（`sample_events.json`、2026年12月の実予定）での月表示の見た目検証。通信・認証なし。

## 開発環境（複数環境から触る前提）

正本はこの GitHub リポジトリ。どの環境で作業しても **作業前に pull、終わったら commit + push** を守る。

### VM（メイン開発環境： Muse / Claude Code / Codex）
- 作業コピー: `~/workspace/calendar-app`
- JDK 17 と Android SDK はプロジェクト内 `toolchain/`（Git 管理外）に導入済み。
- ビルド前に `source scripts/env.sh`（Git 管理外。VM のサンドボックス用プロキシ設定を含むため）してから `./gradlew assembleDebug`。

### PC（Windows： Claude Code / Codex）
- このリポジトリを clone して作業する。
- 必要なもの: JDK 17、Android SDK（Android Studio 導入が手軽）。`local.properties` に `sdk.dir` を自分のパスで作る（Git 管理外）。
- ビルド: `gradlew.bat assembleDebug`

### GitHub Actions（予定）
- push するとクラウドで APK をビルドして成果物に置く仕組みを入れる予定（Gradle wrapper のコミット後に追加）。
- ローカルに SDK が無い環境でも、push さえすれば APK が手に入る。

## 役割分担
- Claude Code: 設計・実装（判断と文章が要る部分）
- Codex: 機械的な実装・ビルド修正・レビュー（著者≠検査役）
- Muse（本体）: 要件の取りまとめ、サーバー側データの確認、APK の受け渡し
