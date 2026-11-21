# ADR-0004: ドキュメントサイトは MkDocs で生成する（Pages 公開は保留）
- Status: accepted (2026-11-21, ユーザー決定)
- Context: 調査報告では MkDocs 等の公開基盤は個人規模では過剰（P2）とされたが、ユーザーは Okita の構成（repo が正本、サイトは自動生成の投影）に倣ってサイト化を決定した。ただし repo は PRIVATE で、現プランでは GitHub Pages が使えない（有効化 API が 422）。repo には実予定データ（sample_events.json）と REQUIREMENTS にカレンダー名等が含まれるため、public 化はユーザーの明示確認なしに行わない
- Decision: mkdocs.yml と scripts/build_docs.sh（staging 方式）でサイトを生成できる状態にし、docs workflow は当面 workflow_dispatch のみで動かす（赤い run を出さない）。Pages の公開方法（public 化／有料プラン／公開しない）はユーザーが別途決める。サイトに載せるのは README・STATUS・REQUIREMENTS・PLAN・TASKS・QC・ADR・開発ルール（AGENTS.md / CLAUDE.md）。changes/ は作業中の一時文書なので載せない
- Consequences: サイトは常に repo の .md から生成される投影であり、編集先は repo のみ。正本の所在は変わらない
