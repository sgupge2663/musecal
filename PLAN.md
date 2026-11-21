# MuseCal v0 実装計画（短縮版。仕様本体は work/v0_prompt.txt）

- 問題: Business Calendar 2 風の月表示が読みやすく作れるかを、同梱サンプルデータで見た目検証する。
- 成功基準: `./gradlew assembleDebug` が通り、12月の実データが月グリッドに仕様どおり（バー連結・時刻行・+n・週番号・前後月グレー）出る。
- スコープ: 月表示1画面＋日タップのボトムシート。通信・認証・書き込み・ウィジェットは対象外。
- 制約: JDK/SDK はプロジェクト内 toolchain/。メモリ少 → Gradle -Xmx1536m・daemon無効・Kotlin in-process。
  サンドボックスのプロキシ対策: scripts/auth_relay_proxy.py（認証付き中継）＋ toolchain/truststore.jks（中継CA）＋ IPv4 固定。`source scripts/env.sh` で設定。
- 順序: ツールチェーン → Gradle 骨組み → データ層(org.json) → 週ごとのレイアウト計算（バーのレーン割当・可視行・+n）→ Compose 描画 → ボトムシート → ビルド。
- 判断: 週数は 5〜6 のうち必要な数（セルを高くするため）。ISO週番号は行の月曜で計算。時刻はOSのタイムゾーンで表示。
