# V1-5 レビュー記録（対象: 0ff094d、著者 Codex、レビュアー Muse）

## 経緯
- Codex の実装は2回の ACP ランで下書き→記録まで進んだが、両回とも時間切れでコミットに至らなかった。Muse が check.sh を回して合格を確認し、コミット（0ff094d）と push、ブランチ CI 成功（run 45003520081）までを代行した。レビューも Muse が行った（2026-11-22 の役割整理: 簡単寄りのレビューは Muse が担う）。

## 確認内容
1. **未ログイン**: 本番ルートは account == null で SignInScreen のみ。サンプル（loadEvents）は本番ルートから呼ばれないことを差分と回帰テストで確認。
2. **エラー表示**: 失敗は画面内状態（fetchErrors、窓キー/access キー）で保持し、トースト依存を解消。文言は fetchFailureMessage が例外種別ごとに固定文へ写像し、API の生レスポンスやアカウント情報を出さない（401/403・SecurityException=許可、IncompleteError=未完了、HttpError=取得失敗、IOException=通信）。
3. **キャッシュ有無の分岐**: fetchedAt == null ならエラー帯のみで月グリッドを出さない。有れば予定＋「最終取得 M/d HH:mm の予定を表示しています。最新ではない可能性があります。」＋再試行。Roborazzi 基準画像2枚（fetch_error_empty / fetch_error_cached）を Muse が目視し、仕様どおりであることを確認した。
4. **再試行の競合防止**: 再試行は access 系なら requestCalendarAccess、窓系なら syncNow を再利用。syncing/accessing の共有ガードで今すぐ同期との二重実行を防ぎ、成功した窓のエラーだけを消す（別窓の成功で失敗表示を消さない）。
5. **check.sh / CI**: Muse のローカル実行で成功。単体テストは JUnit 集計で **76件・失敗0・エラー0・省略1**（RealEventsDumpTest。V1-4 の69件から +7 = ScreenshotTest 4件・FetchFailureTest 3件）。ブランチ CI 成功。スコープは calendar.readonly のみ、トークン保存なし、新規ライブラリ・権限なし。

## Verdict
**受理可、指摘なし。** 実機確認（通信を切った状態でのエラー帯・再試行の実操作、見た目）はユーザー側に残る。
