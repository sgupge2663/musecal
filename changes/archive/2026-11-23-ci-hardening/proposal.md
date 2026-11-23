# 提案: CI の堅牢化（2026-11-23、ユーザー「全部進める」）

## 背景
2026-11-22 に main の Build APK が2回赤くなった。原因はいずれもコードではなく、ランナー側の取得失敗: (1) Roborazzi Gradle プラグインの解決失敗、(2) Robolectric が実行時に取得する android-all の Maven 取得失敗（AssertionError at MavenArtifactFetcher）。PR 検問型へ移行した今、必須チェックの取得失敗は統合全体を止めるため、取得済み依存をキャッシュして再取得を減らす。また既知の小欠陥として、テスト失敗時に vars ステップ（短縮 SHA）が走らず、スクリーンショット成果物の名前が roborazzi-outputs- に壊れる件を直す。

## 決定事項
1. build.yml に Gradle の依存キャッシュを入れる（gradle/actions/setup-gradle を使い、Gradle ユーザーホームのキャッシュとラッパーを保存・復元する）。
2. Robolectric の実行時取得物（~/.m2 配下）も actions/cache でキャッシュする。キーは Gradle 関連ファイルのハッシュを含める。
3. vars ステップに if: always() を付け、ビルド失敗時でも成果物名が正しく付くようにする。
4. 対象は build.yml のみ（emulator.yml は手動実行のみのため対象外）。必須チェックの内容（assembleDebug / testDebugUnitTest / lintDebug）は変えない。新しい外部サービス接続はしない（GitHub 公式・Gradle 公式アクションのみ）。
