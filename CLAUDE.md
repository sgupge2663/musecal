@AGENTS.md

Claude Code 固有の補足（数行だけ。原則は AGENTS.md が正）:
- 作業は原則 worktree（.worktrees/claude-<slug>）＋ブランチ claude/<slug> で行い、commit まで。push と main への統合は Muse が行う
- ビルド確認は CI（push 後）か、急ぐ場合のみローカル（source scripts/env.sh のうえ ./gradlew assembleDebug）
- 仕様に無い判断が必要になったら、勝手に決めず報告に「判断してほしいこと」として分けて書く
