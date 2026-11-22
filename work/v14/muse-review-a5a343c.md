# V1-4 レビュー記録（対象: a5a343c、著者 Codex）

## レビューの経緯
- 当初 Claude Code の確認レビューとして開始したが、Claude 週次クォータのフロア（残量20%）を割った状態での投入だったため、ユーザーの指摘で Muse が途中で停止した（2026-11-22 14:27）。
- 停止時点で作業ツリーに未コミットの変更（GoogleEvents.kt の `inFlight -= w.start` の位置移動）が残っていた。Muse が精査した結果、これは修正ではなく negative control の切り戻し忘れと判定した:
  - その変更を再現すると `EventCacheTest.discardedWindowIsImmediatelyEligible` が落ちる（下記 NC-A）。つまり通っているテストと矛盾する変更であり、受理対象の修正ではあり得ない。
  - ファイルのブロブハッシュ（cac4ef1）は Muse が再現したサボタージュと一致した。
  - 変更は破棄し、a5a343c の原状に復元した。以後のレビュー完遂は Muse 自身が行った（差分読み・check.sh 再実行・NC 実施）。
- Claude Code 側の証跡のうち使えるもの: check.sh 成功ログ（check-a5a343c.log）と JUnit 集計（69件・失敗0・省略1）。NC ログ4件は、2件が空（出力未記録）、2件が JAVA_HOME 未設定で未実行だったため、証跡としては採用せず Muse が再実施した。

## Muse の確認内容
1. **キャッシュの正確さ**: EventCache.kt を通読。保存対象は予定（表示日時・照合区間 matchStart/matchEnd・色・所属・eventId）、タブ（id/label/color）、取得済み窓、最終取得日時、version、アカウント名のみ。**トークン・認可情報は含まれない**。書き込みは一時ファイル→rename の置換。読み取りは runCatching で、破損・version 不一致・アカウント不一致は null（空）として扱う。
2. **アカウント分離**: ファイル名 = メールの SHA-256、JSON 内 account の一致を require の二重チェック。MainActivity 側も dataEmail が一致しない間は予定・タブ・日時を描画しない（V1-4a までの既存ガードと整合）。
3. **鮮度表示**: put() の適用成功時に lastFetchedAt を更新し、snapshot 経由で永続化。失敗経路（fail()）では更新されない。「最終取得 M/d HH:mm」に統一。
4. **N1 と既存ガード**: put() の重なり破棄・requestSeq の単調増加・世代ガードは V1-4a の形を維持。破棄時は inFlight から窓を外す（＝ coveredWeeks に入らない）ため、MainActivity の retrySeq 再判定で未取得週が即座に再取得対象になる。テスト `discardedWindowIsImmediatelyEligible` がこの連鎖を固定している。
5. **check.sh**: Muse が原状の a5a343c で再実行し成功（assembleDebug / testDebugUnitTest / lintDebug、SKIP_CHECK 不使用）。単体テスト69件（失敗0・省略1=RealEventsDumpTest）。

## Negative control（Muse 実施、2026-11-22）
- NC-A: put() の `inFlight -= w.start` を破棄判定の後へ移動 → `EventCacheTest.discardedWindowIsImmediatelyEligible` のみ失敗（5件中1件）。復元済み。
- NC-B: 重なり判定を無効化（`it.request > Long.MAX_VALUE`）→ `EventCacheTest.discardedWindowIsImmediatelyEligible` と `GoogleEventsTest.olderNov22FetchCannotUndoNov29SyncDeletionOnDec10`（V1-4a の削除ロールバック回帰）の2件のみ失敗（41件中2件）。復元済み。

## Verdict
**受理可。** 指摘なし。V1-4 の完了条件のうち実機でしか確認できないもの（機内モードでのキャッシュ表示、再起動後の即表示、通常時の最新化、見た目）はユーザーの実機確認に残る。
