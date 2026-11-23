# タスク: CI の堅牢化

正本は proposal.md。

- [x] CH-1 build.yml の堅牢化（Codex 実装、Muse レビュー）
  - 内容: (a) gradle/actions/setup-gradle を追加し Gradle 依存キャッシュを有効にする。(b) actions/cache で ~/.m2（Robolectric の android-all 等の実行時取得物）をキャッシュする。キーは gradle 関連ファイルのハッシュを含める。(c) vars ステップ（短縮 SHA）に if: always() を付け、失敗時も roborazzi-outputs-<短縮SHA> と正しく命名されるようにする。ジョブ名 build と実行コマンドは変えない
  - 完了条件: ブランチの CI が成功し、キャッシュの保存・復元がログで確認できる（初回は保存、再実行では復元）。成果物名が従来どおり付く
  - 検証方法: ブランチ CI のログと成果物（この変更自体の PR が検証を兼ねる）

  - 実装記録（2026-11-23 / Codex）:
    - `setup-gradle@v6` を追加。公式の現行タグを既存のタグ指定に合わせて採用し、`cache-provider: basic` で GitHub Actions のキャッシュを利用する。`cache-read-only: false` により作業ブランチでも Gradle ユーザーホーム（依存・wrapper 配布物）を保存できるようにした。
    - `actions/cache@v4` で `~/.m2` を保存・復元。OS と `**/build.gradle.kts`、`**/settings.gradle.kts`、`gradle/wrapper/gradle-wrapper.properties`、`**/libs.versions.toml` のハッシュをキーに含め、同じ OS の接頭辞でフォールバックする。
    - `vars` に `if: always()` を追加。ジョブ `build`、全 run コマンド、必須チェック、emulator.yml は変更なし。
    - ローカル検証: Python 3 / PyYAML 6.0.1 で YAML を読み込み成功。HEAD との比較でジョブ名と全 run コマンドの一致、vars の条件を確認。`git diff --check` 成功。
    - キャッシュの保存・復元および成果物名はローカルでは未確認。PR のブランチ CI で初回保存と再実行時の復元、成果物名を確認する。CH-1 のチェック欄はレビュー・CI 検証まで `[ ]` のまま。
    - 引き継ぎ: 変更ファイルは `.github/workflows/build.yml` と本変更の proposal/tasks。次は Muse が仕様照合レビューと PR CI のログ・成果物を確認する。
    - 参照: https://github.com/gradle/actions/blob/main/docs/setup-gradle.md （キャッシュ対象、basic provider、既定の main のみ書き込みの説明）。
    - `source /home/hatch/workspace/calendar-app/scripts/env.sh` 後の `sh scripts/check.sh` は終了コード 0、`assembleDebug / testDebugUnitTest / lintDebug OK`。共有 Gradle キャッシュの書き込みに sandbox 外実行を使用。停止していた `scripts/auth_relay_proxy.py` を起動後に検証完了。これは pre-push 用のアプリ検証であり、ワークフローの実行検証ではない。
    - 追補（2026-11-23、PR #4 のマージ試行で発覚）: ワークフローが push と pull_request の両方で発火するため、PR のブランチでは同名チェック build が2つ立ち、片方（push 側）の失敗が必須チェックを汚染してマージがブロックされた。対策として push トリガーを main のみに限定する（ブランチのチェックは PR 側で走る。新統合手順とも一致）。この1コミットは診断が一意的で小さいため Muse が実施し、PR で明示する。
