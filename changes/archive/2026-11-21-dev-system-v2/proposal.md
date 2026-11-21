# 変更提案： 開発方式を「.md駆動＋再配分」形へ移行する

## なぜ
2026-11-21 の調査（okita-noon/ms-agent-hackathon の開発システムの深掘り＋「これより良いやり方」の外部調査、報告： ~/workspace/research_notes/beyond-md-driven-agent-dev-20261121-0911/report.md）の結論は「.md駆動は捨てるものではなく、内部の再配分」だった。ユーザーの指示により、MuseCal の cc/cx 開発方式をその推奨形に合わせる。

## 現状との差分（この repo に当てはめたもの）
- 憲法（AGENTS.md）は既に薄い（21行）が、作業は単一ツリー・main 直行で、エージェント同士の衝突が起きうる（実際に同日、Muse と cc が同じツリーで作業した）
- 強制は文章のみ。秘密情報ブロックや push 前チェックの仕組みが無い
- 検証は都度のレビュー頼みで、固定ケース＋証拠ログが無い
- 状態（STATUS）と決定の記録（ADR）が分かれていない。決定の理由がコミットメッセージに埋もれる

## 何を変えるか（tasks.md が正）
1. changes/ 運用： 中規模以上の変更は changes/<slug>/ に proposal＋tasks を切ってから着手し、完了後 archive する（本変更が第1号）
2. worktree＋prefix ブランチ： 1タスク=1 worktree=1ブランチ（claude/…、codex/…）。統合は Muse が main へ
3. gate の機械化: .githooks/pre-commit（秘密情報ブロック）、pre-push（scripts/check.sh = assembleDebug）。CI はブランチ push でも APK を作る
4. QC 証拠ログ: docs/qc.md に固定ケース集と実施記録（入力／経路／状態／結果／AI判定／人判定／理想／対策）
5. STATUS.md（短い現状＋Must/Should/Could）と docs/adr/（1決定1ファイル、supersede 方式）を分離
6. CLAUDE.md を薄い入口にする（@AGENTS.md＋Claude 固有の数行）。Codex は AGENTS.md を直接読む

## 変えないもの
- 仕様の正本は REQUIREMENTS.md のまま。TASKS.md は小タスクの台帳として残す
- 著者≠検査役、完了は証拠つき、最終判断はユーザー、の原則は不変

## 正本への反映（archive 前の記録）
- REQUIREMENTS.md（アプリ仕様）への変更なし。本変更は開発方式の変更であり、反映先は AGENTS.md（原則2・5・8・9、ファイルの役割）、README、STATUS.md、docs/qc.md、docs/adr/0001〜0004、hooks と CI
- 仕様側で古さが見つかった点（REQUIREMENTS §7 の角丸の記述、§1 の行数と省略の文言）は、仕様変更がユーザー判断のため本変更では触らず、別途提示する
