# MuseCal 開発ルール（仕様駆動開発 × エージェント開発）

このリポジトリの開発は「仕様駆動開発（ドキュメント駆動）× エージェント開発」で進める。
参加者： Muse（調整・検品・統合）、Claude Code（設計・実装）、Codex（機械的実装・レビュー）、PC側の Claude Code / Codex。

## 原則
1. **仕様が正本**： `REQUIREMENTS.md` が仕様の正本。事実は正本1か所にだけ書き、他からは参照する（重複記載はドリフトの原因）。実装と仕様が食い違ったら、まず仕様を直すか、実装を仕様に合わせるかを決める。仕様に無いものを勝手に作らない。
2. **変更の順序**： 仕様（REQUIREMENTS.md）→ 計画（PLAN.md）→ タスク（完了条件つき）→ 実装 → 検証 → 仕様へ反映（converge）。中規模以上の変更は `changes/<slug>/` に proposal と tasks を切ってから着手する（計画とタスクは changes/ に集約し、PLAN.md・TASKS.md へ重複転記しない）。archive の条件は、正本（REQUIREMENTS.md）への反映内容を proposal 末尾に記録し、レビューと証拠が揃っていること。完了したら `changes/archive/` へ移す。小さい変更は TASKS.md のタスクで足りる。
3. **タスクは小さく**： 1タスク = 1セッションで終わる大きさ。各タスクには「完了条件」と「検証方法」を書く。
4. **著者 ≠ 検査役**： 実装したエージェントとは別のエージェントがレビューする。レビューは仕様との照合で行い、指摘には根拠（ファイル・箇所）と重大度を付ける。
5. **完了は証拠つきでのみ受理**： 「できました」ではなく、ビルド結果・実行出力・スクリーンショット等の証拠で確認する。確認できない項目は「未確認」と明記する。繰り返し確認する項目は `docs/qc.md` の固定ケース＋実施記録に残す。UI 変更の受理には変更後のスクリーンショット（可能なら変更前との比較）とユーザーの判定が要る。未取得なら「受理待ち」とする。
6. **判断は人間に残す**： 仕様の追加・変更、優先順位、最終的な見た目の判定はユーザー（林田）が決める。エージェントは勝手にタスクを増やさない。
7. **UI だけでなく UX**： 見た目の再現だけでなく、操作感（スクロールの滑らかさ、タップ応答、遷移の自然さ）も仕様とタスクの対象にする。「動く」ではなく「気持ちよく動く」を完了条件に含める。
8. **作業分離**： 1タスク = 1 worktree = 1ブランチ。他人のツリーで直接作業しない。命名・統合・統合後の片づけは下の「ブランチ運用」が正本。
9. **強制は文章でなく仕組みで**： `.githooks/pre-commit` が秘密情報を止め、`pre-push` が `scripts/check.sh`（assembleDebug）を回す（ブランチ削除だけの push は省略）。clone したら `git config core.hooksPath .githooks` を設定する。チェックを飛ばす場合は SKIP_CHECK=1 と明示し、理由を報告に残す。SDK 等の環境が無いマシンでは無理に通さず、CI を gate の正とする（その旨を報告に残す）。
10. **公開リポジトリ前提のセキュリティ**（2026-11-21 ユーザー指示）： 本リポジトリは public。公開済みの予定サンプルデータはユーザーが許容したが、それ以外の秘密情報・鍵・個人情報は commit しない。防御は二重にする： ローカルの pre-commit に加え、GitHub 側の secret scanning と push protection を有効に保つ。Secrets の追加、CI の権限拡大、新しい外部サービスの接続は、Muse が内容を確認し、ユーザーへ報告してから行う。

## ブランチ運用（正本。担保は `scripts/branch_ops.sh`）
- **命名**： 作業ブランチは `claude/<slug>` / `codex/<slug>`。worktree は `.worktrees/<agent>-<slug>` に作り、同じ `<slug>` でブランチと1対1に対応づける（例： `claude/branch-ops` ↔ `.worktrees/claude-branch-ops`）。
- **統合**： main への統合と main の push は Muse だけが行う。統合の前に、そのブランチの最終コミットで CI（Build APK）が成功していることを確かめる。統合は fast-forward か merge で行う（squash・rebase で統合すると、ブランチの先端が main に含まれず `prune` が統合済みと判定できない）。
- **統合後**： 統合したブランチは、ローカル・リモートとも削除し、対応する worktree も外す。残してよいのは未統合の作業ブランチだけ。手作業の台帳は作らない（状態は `scripts/branch_ops.sh audit` が git から毎回求める）。
- **節目の記録**： 版や節目を残したいときはブランチでなくタグで残す。経緯の正本は git 履歴と `changes/` の収束記録で、ブランチは記録に使わない。
- **統合手順**（Muse。main を checkout した主作業ツリーで行う）：
  1. ブランチの最終コミットで CI（Build APK）が成功していることを確認する
  2. main へ統合（fast-forward か merge）し、main を push する
  3. `scripts/branch_ops.sh prune --dry-run` で削除対象を確かめ、`scripts/branch_ops.sh prune` で統合済みのブランチ（ローカル＋リモート）と worktree を片づける
  4. `scripts/branch_ops.sh audit` が「クリーン」（終了コード 0）になることを確かめる。「保留」が出たら、その worktree の変更・使用状況を確かめてから片づける
- **prune が触らないもの**： main、未統合のブランチ、自分のコミットがまだ無いブランチ（作りたてや、main へ fast-forward しただけのものを含む）、使用中（実行中・主作業ツリー・lock 中）や未コミット変更ありの worktree、`git branch -d` が拒否する状態（upstream が古い等）のブランチ、それらと同名のリモートブランチ、同名ローカルの削除が実行中に失敗したリモートブランチ。`audit` の「prune で消せる」と `prune` が消すものは同じ判定から出る。
- **削除の方法**： ローカルは `git branch -d` と `git worktree remove`（どちらも force なし。`-D`・`--force` は使わない）。リモートは、統合済みを確かめた SHA に限定した `git push --force-with-lease=refs/heads/<名前>:<SHA> origin :refs/heads/<名前>` で消す（リモートがその SHA から動いていたら拒否されて残る条件付き削除。無条件の force push はしない）。

## ファイルの役割
- `REQUIREMENTS.md` … 仕様の正本（What / Why）。実測値もここに記録する
- `STATUS.md` … 現在地と Must/Should/Could（短く保つ。履歴は書かない）
- `PLAN.md` … 実装計画（How）
- `TASKS.md` … 小タスクの一覧と状態、完了条件
- `changes/<slug>/` … 中規模以上の変更の proposal と tasks（完了後は archive/）。タスク番号は変更ごとの接頭辞＋連番（例： dev-system-v2 は DS-1〜）で振り、TASKS.md の番号と重ねない
- `docs/adr/` … 決定記録（1決定1ファイル。覆すときは supersede と明記して新規に書く）
- `docs/qc.md` … 固定ケース集と QC 実施記録（証拠ログ）
- `CLAUDE.md` … Claude Code 向けの薄い入口（@AGENTS.md＋固有の補足のみ）
- `reference/` … 仕様の根拠（BC の実スクリーンショット）
- `work/` … 作業メモ・レビュー結果（Git 管理外）
