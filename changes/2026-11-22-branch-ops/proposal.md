# 変更提案： ブランチ運用を文章でなく仕組みで担保する

## なぜ
2026-11-21 の説明画像に対する Codex 監査で「統合後ブランチ削除などの運用が規約文書のどこにも定めがない」と指摘された。実際、命名と統合担当は AGENTS.md 原則8 にあったが、統合後の片づけ（ブランチ・worktree の削除）と、それを確かめる手段は無かった。放置すると統合済みブランチと worktree が溜まり、どれが作業中かを人が台帳で追うことになる。

## 設計方針（ユーザー承認済み）
- 正本は1か所： AGENTS.md に「ブランチ運用」節を新設する。原則8 と README はそこを参照するだけにする
- 担保は仕組み： `scripts/branch_ops.sh`（audit / prune）と、統合手順への組み込み（統合 → prune → audit でクリーン確認）
- 手作業の台帳に頼らない： 状態は毎回 git から求める

## 何を変えるか（tasks.md が正）
1. AGENTS.md に「ブランチ運用」節（命名・統合・統合後・節目はタグ・統合手順・prune が触らないもの）。原則8 は参照に縮め、原則9 に pre-push の削除 push 省略を追記
2. `scripts/branch_ops.sh`（新規、bash）
   - `audit [--no-fetch]`： ローカル＋リモートの全ブランチの統合状態・最終コミット日・対応 worktree・判定（main / 作業中 / 未着手 / 要掃除 / 要掃除(保留)）を表示。要掃除・保留が 0 件ならクリーン（終了コード 0）、あれば 2
   - `prune [--dry-run] [--no-fetch]`： origin/main（無ければ main）に完全統合済みのブランチだけを、ローカル（`git branch -d`）＋リモート（統合済みを再確認し、値が変わっていないときだけ削除）で消す。対応 worktree は `git worktree remove`（force なし）で外す
3. `.githooks/pre-push`： ブランチ削除だけの push では check.sh（数分の Gradle ビルド）を省略する。prune のリモート削除のたびにビルドが走るのを避けるため。コードを送る push の扱いは不変
4. `.gitignore`： `scripts/*` が無視対象のため、`!scripts/branch_ops.sh` を追加
5. README の参照先を「ブランチ運用」節へ

## 設計上の判断（理由つき）
- 「統合済み」= ブランチの先端が基準（origin/main）の祖先。`git branch -d` と同じ「マージ済み」判定を、実行場所の HEAD ではなく公開済みの main に対して行う。Muse の統合方式（fast-forward / merge）はこれで判定できる。squash / rebase 統合は判定できないため、規約側で fast-forward か merge に限定した（判定できない場合は「未統合」＝消さない側に倒れる）
- 作りたて保護： 作ったばかりで自分のコミットが無いブランチは、先端が main と同じなので「統合済み」に見える。ローカルの reflog が作成の1件だけのものは「未着手」として残す。同名のローカルブランチが守られている場合、そのリモートブランチも守る（実演で発見： 当初はリモート側が消える対象になっていた）
- worktree だけ消えてブランチが残る半端を防ぐため、`git branch -d` が拒否する条件（upstream か HEAD に未マージ）を worktree を外す前に確かめる
- リモート削除は `git push --force-with-lease=<ref>:<確認した値> origin :<ref>`。名前に force を含むが、強制上書きではなく「リモートの値が確認した値のままのときだけ削除する」条件付き削除で、素の削除 push より安全側。ローカルの `git branch -D`・`git worktree remove --force` は使わない
- 監査の基準を origin/main にするため、audit / prune は既定で `git fetch --prune origin` を行う（手元の追跡参照の更新だけで、リモートは変えない）

## 変えないもの
- アプリのコード、CI のワークフロー、Secrets、権限、外部サービス（原則10）
- 統合と main の push は Muse のみ、という担当

## 正本への反映（archive 前の記録）
- REQUIREMENTS.md（アプリ仕様）への変更なし。本変更は開発方式の変更で、反映先は AGENTS.md（「ブランチ運用」節の新設、原則8・9）、README、`scripts/branch_ops.sh`、`.githooks/pre-push`、`.gitignore`、docs/qc.md の実施記録
- archive は、別エージェント（Codex）のレビューと、Muse が実際の統合で手順（統合 → prune → audit）を1回通したことを確認してから行う
