# タスク: 小物（TextShadeTest 境界）

正本は proposal.md。

- [x] SF-1 TextShadeTest に境界ケースを追加する（Codex 実装、Muse レビュー）
  - 内容: 黒（L=0）・白（L=1）・無彩色（S=0 のグレー、明背景と暗背景）・中間グレーの暗背景のケースを、定義（L' = L × 0.72 / L' = 1 − (1 − L) × 0.72）から計算した期待値つきで追加する。実装（Events.kt の textShade）は変更しない。現行実装が定義と食い違うケースがあれば、直さずに差異を実装記録へ書く
  - 完了条件: 追加テストを含む全テストが成功し、境界の期待値の出どころ（定義からの計算）がテスト内に読み取れる
  - 検証方法: scripts/check.sh とブランチ CI
  - 実装記録（2026-11-23、Codex）:
    - TextShadeTest.kt に黒・白・中間グレー（#808080）それぞれの白背景／黒背景、計6ケースを追加。無彩色（S=0）と中間グレーの暗背景を含み、定義からの明度変換と RGB の丸め計算をコメントに記載した。
    - 追加6ケースはすべて定義どおりで、現行実装との差異なし。Events.kt と STATUS.md は変更していない。
    - 本体の scripts/env.sh を source して ./scripts/check.sh を実行し、assembleDebug / testDebugUnitTest / lintDebug が成功（終了コード0）。TextShadeTest は既存6件＋追加6件の計12件成功。
    - 初回は env.sh が指定する 127.0.0.1:18080 のプロキシが未起動で、Robolectric の依存取得に失敗。既存 scripts/auth_relay_proxy.py を起動して再実行し解消した。検証には共有 Gradle キャッシュの書き込み権限を使用。チェックの省略なし。
    - 証拠: app/build/test-results/testDebugUnitTest/TEST-jp.hayashida.musecal.TextShadeTest.xml、app/build/reports/lint-results-debug.html（ローカル生成物）。
    - 次のアクション: ブランチ CI の結果確認と Muse によるレビュー。チェック欄はレビュー待ちのため未チェックのまま。
