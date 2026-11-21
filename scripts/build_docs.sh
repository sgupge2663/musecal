#!/bin/sh
# ドキュメントサイトの生成（投影）。正本は repo の各 .md で、ここでは staging に集めて mkdocs で描画するだけ
set -e
cd "$(dirname "$0")/.."
rm -rf site_src site
mkdir -p site_src/docs
cp README.md STATUS.md REQUIREMENTS.md PLAN.md TASKS.md AGENTS.md CLAUDE.md site_src/
cp docs/qc.md site_src/docs/
cp -r docs/adr site_src/docs/
cp mkdocs.yml site_src/
MKDOCS_BIN="$(command -v mkdocs || echo toolchain/mkdocs-venv/bin/mkdocs)"
"$MKDOCS_BIN" build -f site_src/mkdocs.yml -d ../site
echo "build_docs.sh: site/ を生成しました"
