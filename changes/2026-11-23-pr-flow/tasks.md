# タスク: PR 検問型への移行

正本は proposal.md。検証方法は EM-3 のフロー（エージェント一次判定→ユーザーは最終判定のみ）。

- [ ] PRF-1 GitHub 設定とルールセット（Muse が実施）
  - 内容: リポジトリ設定で「merge commit のみ許可」「マージ後にヘッドブランチを自動削除」を有効化する。main のルールセットを作成する: PR 経由必須、必須ステータスチェック = Build APK の build ジョブ、force push 禁止、バイパスアクターなし、up-to-date 要求なし。有効化は PRF-2/PRF-3 のブランチが push され、レビューと記録相乗りまで終わった直後に行う
  - 完了条件: ルールセットが active で、設定内容が proposal の決定と一致する
  - 検証方法: gh api で設定を読み戻して照合する

- [ ] PRF-2 AGENTS.md ブランチ運用の改訂（Codex 実装、Muse レビュー）
  - 内容: 統合手順を PR 前提に書き換える（ブランチ push → PR 作成 → CI → レビュア役が PR にレビュー投稿＋work/ 記録をブランチへコミット → gh pr merge --merge → ローカル側の prune / audit）。記録相乗りルール（決定2）と、Approve が使えないことの明記（決定4）を含む。従来の「Muse がローカルで merge する」記述を置き換える
  - 完了条件: 改訂後の手順だけで統合が再現でき、現行手順との矛盾が残っていない
  - 検証方法: レビューで proposal の決定事項と突き合わせる
  - 実装記録（2026-11-23、Codex）: AGENTS.md「ブランチ運用」を PR の merge commit 前提に改訂。直接 push 禁止・バイパスなし、PR コメント＋work/ 記録、記録相乗り、最終コミットの CI 確認、GitHub 自動削除とローカル prune / audit を明記。STATUS.md は変更していない。proposal との自己照合済み。Muse によるレビュー・受理は未実施。

- [ ] PRF-3 branch_ops.sh の追随（Codex 実装、Muse レビュー）
  - 内容: prune が「リモートブランチが既に GitHub に削除済み」を正常として扱えるようにする。audit の判定（統合済みなのに残っているブランチの検出）は変えない。ローカルブランチと worktree の片づけは従来どおり
  - 完了条件: リモート削除済み・ローカル残存の状態で prune が成功し、audit がクリーンになることが再現できる
  - 検証方法: 実証（PRF-4）の prune で確認する
  - 実装記録（2026-11-23、Codex）: リモート削除 push の失敗時だけ git ls-remote --exit-code --heads で再照会し、終了コード 2（参照なし）の場合を正常扱いにした。通信・認証エラーと参照残存は失敗を維持。audit の判定、SHA 固定 lease、ローカル削除処理は変更していない。
  - 検証記録: bash -n と git diff --check が成功。一時リポジトリ6ケース（自動削除後 fetch あり／追跡参照残存、通常削除、リモート SHA 更新、通信失敗、upstream による branch -d 拒否）がすべて成功。削除成功3ケースで worktree・ローカルブランチの除去と audit のクリーンを確認。SHA 更新時はリモートを保護し、branch -d 拒否時はローカル・リモート・worktree を保持。本番での実証・Muse レビューは PRF-4 側で実施待ち。
  - 共通検証: 主作業ツリーの scripts/env.sh を source し、./scripts/check.sh（assembleDebug / testDebugUnitTest / lintDebug）が成功。初回は既存の中継プロキシ未起動で依存取得に失敗したが、既存 scripts/auth_relay_proxy.py を起動して再実行し解消。共有 Gradle キャッシュへの書き込み権限を付けて実行し、チェックの省略はしていない。次のアクションは Muse のレビューと PRF-1／PRF-4。

- [ ] PRF-4 実証（Muse が実施）
  - 内容: PRF-2/PRF-3 のブランチを、ルールセット有効化後に PR 化し、新フローで統合する。あわせて直接 push が拒否されることを負の対照（小さなコミットの push 試行→拒否→取り消し）で確認する
  - 完了条件: PR 経由で main に統合され、直接 push の拒否が記録され、prune / audit がクリーン
  - 検証方法: PR の URL と CI・拒否の出力を記録する
