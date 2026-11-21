#!/usr/bin/env bash
# ブランチ運用の点検と掃除（AGENTS.md「ブランチ運用」が正本。ここは仕組み側）
#
# 使い方:
#   scripts/branch_ops.sh audit [--no-fetch]            全ブランチの統合状態・最終コミット日・worktree を一覧
#   scripts/branch_ops.sh prune [--dry-run] [--no-fetch] main に完全統合済みのブランチだけを削除
#
# 判定の基準（BASE）は origin/main（無ければ main）。「統合済み」= ブランチの先端が BASE の祖先
# （git branch -d と同じ「マージ済み」判定を、HEAD ではなく BASE に対して行う）。
# squash / rebase で統合したブランチは先端が祖先にならないため「未統合」扱いになり、消さない（安全側）。
#
# prune が絶対に触らないもの（対象外として報告する）:
#   - main 自身（ローカル・リモートとも）
#   - BASE に統合されていないブランチ
#   - 未着手のローカルブランチ（reflog に自分のコミットの記録が無い。作りたての作業ブランチを守る。判定は is_untouched）
#   - worktree が「使用中」（このスクリプトを実行している worktree、主作業ツリー、lock 中、ディレクトリ欠落）
#     または「変更あり」（未コミット・未追跡ファイルあり）のブランチ
#   - git branch -d が拒否する状態のローカルブランチ（upstream が古い等。消すと半端になるため保留）
#   - 同名のローカルブランチが上記で守られているリモートブランチ。prune の実行中に同名ローカルの削除が
#     失敗した（worktree を外せなかった等）リモートブランチも消さない
# 判定は classify_local / classify_remote の1か所で行い、audit の表示と prune の実行は同じ判定結果（ROWS）を使う。
# ローカルの削除は git branch -d（安全削除）と git worktree remove（force なし）だけで行う。-D / --force は使わない。
# リモートの削除は、判定時に確かめた SHA に限定した git push --force-with-lease=<ref>:<SHA> で行う
# （その間にリモートが更新されていたら push が拒否され、消さない）。無条件の force push はしない。
#
# 終了コード: audit は 要掃除 0 件なら 0、要掃除ありなら 2。prune は対象外があっても 0、削除の失敗があれば 1。

set -u
set -o pipefail

REMOTE=origin
MAIN=main

die() { echo "branch_ops: $*" >&2; exit 1; }

usage() {
  sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

git rev-parse --git-dir >/dev/null 2>&1 || die "git リポジトリの中で実行してください"

CMD="${1:-}"; [ $# -gt 0 ] && shift
DRY_RUN=0; FETCH=1
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=1 ;;
    --no-fetch) FETCH=0 ;;
    -h|--help) usage 0 ;;
    *) echo "branch_ops: 不明な引数: $a" >&2; usage 1 ;;
  esac
done
case "$CMD" in audit|prune) ;; -h|--help|"") usage 0 ;; *) echo "branch_ops: 不明なコマンド: $CMD" >&2; usage 1 ;; esac
if [ "$CMD" = audit ] && [ "$DRY_RUN" = 1 ]; then die "--dry-run は prune 用です"; fi

HAS_REMOTE=0
git remote get-url "$REMOTE" >/dev/null 2>&1 && HAS_REMOTE=1

if [ "$FETCH" = 1 ] && [ "$HAS_REMOTE" = 1 ]; then
  # 消えたリモートブランチを追跡参照から外すため --prune を付ける（追跡参照の更新のみ。リモートは変えない）
  git fetch --prune --quiet "$REMOTE" || die "git fetch に失敗しました（--no-fetch で手元の情報だけで実行できます）"
fi

if [ "$HAS_REMOTE" = 1 ] && git rev-parse -q --verify "refs/remotes/$REMOTE/$MAIN" >/dev/null; then
  BASE="$REMOTE/$MAIN"
elif git rev-parse -q --verify "refs/heads/$MAIN" >/dev/null; then
  BASE="$MAIN"
else
  die "基準の $MAIN が見つかりません"
fi

TOP=$(git rev-parse --show-toplevel)
MAIN_WT=$(git worktree list --porcelain | sed -n '1s/^worktree //p')   # 主作業ツリー（最初の1件）

# --- worktree の一覧: branch -> path / 状態 ---
declare -A WT_PATH WT_LOCKED WT_PRUNABLE
DETACHED_WT=()
cur_path=""; cur_branch=""; cur_locked=0; cur_prunable=0; cur_detached=0
flush_wt() {
  if [ -n "$cur_path" ]; then
    if [ -n "$cur_branch" ]; then
      WT_PATH["$cur_branch"]="$cur_path"; WT_LOCKED["$cur_branch"]=$cur_locked; WT_PRUNABLE["$cur_branch"]=$cur_prunable
    elif [ "$cur_detached" = 1 ]; then
      DETACHED_WT+=("$cur_path")
    fi
  fi
  cur_path=""; cur_branch=""; cur_locked=0; cur_prunable=0; cur_detached=0
}
while IFS= read -r line; do
  case "$line" in
    "worktree "*) flush_wt; cur_path="${line#worktree }" ;;
    "branch refs/heads/"*) cur_branch="${line#branch refs/heads/}" ;;
    detached) cur_detached=1 ;;
    locked|"locked "*) cur_locked=1 ;;
    prunable|"prunable "*) cur_prunable=1 ;;
  esac
done < <(git worktree list --porcelain; echo)
flush_wt

is_integrated() { git merge-base --is-ancestor "$1" "$BASE" 2>/dev/null; }
last_date() { git log -1 --format=%cd --date=format-local:'%Y-%m-%d %H:%M %z' "$1" 2>/dev/null; }

# 未着手: ローカルブランチの reflog に「このブランチ上で新しいコミットを作った」記録が1件も無い。
# 件数では判定しない（古い main から作って main へ fast-forward しただけのブランチは reflog が2件以上になるが、
# 自分のコミットは無い）。reflog の各記録の件名（%gs）を見て、次のものだけを「自分のコミット」と数える:
#   commit: / commit (amend|merge|initial): / cherry-pick: / revert: / am: / 〜: Merge made by …（merge コミット）
# 作成（branch: Created from）・fast-forward（merge X: Fast-forward、pull: Fast-forward）・reset・rebase・rename などは
# 新しいコミットを作らない（rebase は既存コミットの付け替えで、元のコミットは commit: として残っている）ので数えない。
# 記録の種類が分からないときは「自分のコミットが無い」側（＝未着手として保護）に倒れる。
# reflog が無い・期限切れで消えた場合も未着手扱いになり、消さない（安全側）。
# reflog はパイプで grep -q に流さず、いったん変数に最後まで読み込んでから調べる。grep -q は一致した時点で
# 入力を閉じるため、長い reflog では git reflog が SIGPIPE（141）で終わり、pipefail によりパイプ全体が失敗して
# 判定が反転する（自分のコミットが多い統合済みブランチが「未着手」になる）。この形ならその混入が起きない。
REFLOG_COMMIT_RE='^(commit|commit \((amend|merge|initial)\)|cherry-pick|revert|am): |: Merge made by '
is_untouched() {
  local log
  log=$(git reflog show --format=%gs "refs/heads/$1" -- 2>/dev/null) || return 0   # 読めない＝未着手（安全側）
  ! grep -Eq "$REFLOG_COMMIT_RE" <<<"$log"
}

# git branch -d が受け入れるか（upstream があれば upstream、無ければ HEAD にマージ済みか）。
# 拒否される状態なら worktree も外さない（worktree だけ消えてブランチが残る半端を防ぐ）
branch_d_ok() {
  local up
  up=$(git rev-parse -q --verify "$1@{upstream}" 2>/dev/null || true)
  git merge-base --is-ancestor "$1" "${up:-HEAD}" 2>/dev/null
}

# worktree の状態: 空=worktree なし / busy:<理由> / dirty / clean
wt_state() {
  local b="$1" p="${WT_PATH[$1]:-}"
  [ -z "$p" ] && { echo ""; return; }
  if [ "${WT_PRUNABLE[$b]:-0}" = 1 ] || [ ! -d "$p" ]; then echo "busy:ディレクトリ欠落（git worktree prune で整理）"; return; fi
  if [ "$p" = "$MAIN_WT" ]; then echo "busy:主作業ツリー"; return; fi
  if [ "$p" = "$TOP" ]; then echo "busy:実行中の worktree"; return; fi
  if [ "${WT_LOCKED[$b]:-0}" = 1 ]; then echo "busy:lock 中"; return; fi
  if [ -n "$(git -C "$p" status --porcelain --untracked-files=normal 2>/dev/null)" ]; then echo "dirty"; return; fi
  echo "clean"
}

rel() { case "$1" in "$MAIN_WT"/*) echo "${1#"$MAIN_WT"/}" ;; "$MAIN_WT") echo "(主作業ツリー)" ;; *) echo "$1" ;; esac; }

# --- 対象ブランチの収集 ---
mapfile -t LOCALS < <(git for-each-ref --format='%(refname:short)' refs/heads/ | sort)
REMOTES=()
if [ "$HAS_REMOTE" = 1 ]; then
  mapfile -t REMOTES < <(git for-each-ref --format='%(refname:short)' "refs/remotes/$REMOTE/" | grep -v "^$REMOTE/HEAD$" | grep -v "^$REMOTE$" | sort)
fi

# 判定: 結果を  kind|name|integrated|date|worktree|verdict|reason  で返す
classify_local() {
  local b="$1" integ="未統合" st verdict reason="" wt
  st=$(wt_state "$b"); wt=$([ -n "${WT_PATH[$b]:-}" ] && rel "${WT_PATH[$b]}" || echo "-")
  [ -n "$st" ] && [ "$st" != clean ] && wt="$wt [${st#busy:}]"
  if [ "$b" = "$MAIN" ]; then echo "local|$b|基準|$(last_date "$b")|$wt|main|"; return; fi
  if is_integrated "$b"; then integ="統合済み"; fi
  if [ "$integ" = 未統合 ]; then verdict="作業中"; reason="未統合"
  elif is_untouched "$b"; then verdict="未着手"; reason="自分のコミットが無い（作成・fast-forward のみ）"
  elif [ "$st" = dirty ]; then verdict="要掃除(保留)"; reason="worktree に未コミットの変更あり"
  elif [ "${st%%:*}" = busy ]; then verdict="要掃除(保留)"; reason="worktree 使用中: ${st#busy:}"
  elif ! branch_d_ok "$b"; then
    verdict="要掃除(保留)"
    if git rev-parse -q --verify "$b@{upstream}" >/dev/null 2>&1; then reason="git branch -d が拒否する状態（upstream $(git rev-parse --abbrev-ref "$b@{upstream}") に未反映のコミットあり）— push するか upstream を確かめる"
    else reason="git branch -d が拒否する状態（この作業ツリーの HEAD に未マージ）— main を checkout した主作業ツリーで実行する"; fi
  else verdict="要掃除"; fi
  echo "local|$b|$integ|$(last_date "$b")|$wt|$verdict|$reason"
}
classify_remote() {
  local rb="$1" short="${1#"$REMOTE"/}" integ="未統合" verdict reason="" lv
  if [ "$short" = "$MAIN" ]; then echo "remote|$rb|基準|$(last_date "$rb")|-|main|"; return; fi
  if is_integrated "$rb"; then integ="統合済み"; verdict="要掃除"; else verdict="作業中"; reason="未統合"; fi
  # 同名のローカルブランチが消せない状態（作業中・未着手・保留）なら、リモートも守る
  if [ "$verdict" = 要掃除 ] && [ -n "${LOCAL_VERDICT[$short]:-}" ] && [ "${LOCAL_VERDICT[$short]}" != 要掃除 ]; then
    lv="${LOCAL_VERDICT[$short]}"; verdict="要掃除(保留)"; reason="同名のローカルブランチが ${lv} のため"
  fi
  echo "remote|$rb|$integ|$(last_date "$rb")|-|$verdict|$reason"
}

ROWS=()
declare -A LOCAL_VERDICT
for b in "${LOCALS[@]}"; do
  row=$(classify_local "$b"); ROWS+=("$row")
  IFS='|' read -r _k _n _i _d _w v _r <<<"$row"; LOCAL_VERDICT["$b"]="$v"
done
for rb in "${REMOTES[@]}"; do ROWS+=("$(classify_remote "$rb")"); done

# 表示幅（全角=2）。UTF-8 の多バイト文字は概ね3バイトで全角なので、文字数＋(バイト数−文字数)/2 で近似する
dispw() { local s="$1" c b; c=$(printf "%s" "$s" | LC_ALL=C.UTF-8 wc -m); b=$(LC_ALL=C; printf '%s' "$s" | wc -c); echo $(( c + (b - c) / 2 )); }

print_table() {
  local r kind name integ date wt verdict reason i w line cell
  local -a lines=() widths=(0 0 0 0 0)
  lines+=("種別|ブランチ|統合|最終コミット|worktree|判定")
  for r in "${ROWS[@]}"; do
    IFS='|' read -r kind name integ date wt verdict reason <<<"$r"
    lines+=("$kind|$name|$integ|$date|$wt|$verdict${reason:+ — $reason}")
  done
  for line in "${lines[@]}"; do
    IFS='|' read -r -a cell <<<"$line"
    for i in 0 1 2 3 4; do w=$(dispw "${cell[$i]}"); [ "$w" -gt "${widths[$i]}" ] && widths[$i]=$w; done
  done
  for line in "${lines[@]}"; do
    IFS='|' read -r -a cell <<<"$line"
    for i in 0 1 2 3 4; do w=$(dispw "${cell[$i]}"); printf '%s%*s  ' "${cell[$i]}" $(( widths[i] - w )) ""; done
    printf '%s\n' "${cell[5]}"
  done
  local d; for d in "${DETACHED_WT[@]}"; do echo "注意: ブランチの無い（detached）worktree: $(rel "$d")"; done
}

if [ "$CMD" = audit ]; then
  echo "基準: $BASE ($(git rev-parse --short "$BASE"))"
  print_table
  n_prune=0; n_hold=0
  for r in "${ROWS[@]}"; do
    case "$r" in *"|要掃除(保留)|"*) n_hold=$((n_hold+1)) ;; *"|要掃除|"*) n_prune=$((n_prune+1)) ;; esac
  done
  if [ "$n_prune" = 0 ] && [ "$n_hold" = 0 ]; then
    echo "結果: クリーン（統合済みなのに残っているブランチは 0 件）"; exit 0
  fi
  echo "結果: 要掃除 $((n_prune+n_hold)) 件（prune で消せる: $n_prune 件 / 保留: $n_hold 件）"
  [ "$n_prune" -gt 0 ] && echo "  → scripts/branch_ops.sh prune --dry-run で削除対象を確認してから prune"
  [ "$n_hold" -gt 0 ] && echo "  → 保留は worktree の変更・使用状況を人が確かめてから片づける（prune は触らない）"
  exit 2
fi

# --- prune ---
echo "基準: $BASE ($(git rev-parse --short "$BASE"))$([ "$DRY_RUN" = 1 ] && echo '  ※ --dry-run: 何も削除しません')"
DEL_LOCAL=(); DEL_REMOTE=(); SKIPS=()
for r in "${ROWS[@]}"; do
  IFS='|' read -r kind name integ date wt verdict reason <<<"$r"
  case "$verdict" in
    要掃除) if [ "$kind" = local ]; then DEL_LOCAL+=("$name"); else DEL_REMOTE+=("${name#"$REMOTE"/}"); fi ;;
    main) SKIPS+=("$kind $name: main（触らない）") ;;
    *) SKIPS+=("$kind $name: $verdict — $reason") ;;
  esac
done

FAIL=0
# 削除可否は上の判定（ROWS）で確定済み。ここでは判定し直さず、実行時の失敗だけを扱う
for b in "${DEL_LOCAL[@]}"; do
  p="${WT_PATH[$b]:-}"
  if [ "$DRY_RUN" = 1 ]; then
    echo "削除予定: local $b${p:+ ＋ worktree $(rel "$p")}"; continue
  fi
  if [ -n "$p" ]; then
    if git worktree remove "$p"; then echo "削除: worktree $(rel "$p")"
    else echo "失敗: worktree $(rel "$p") を外せませんでした（ブランチ $b も残します）" >&2; FAIL=1; continue; fi
  fi
  # 統合済みは確認済み。git branch -d は HEAD/upstream 基準でも再判定し、拒否されたら残す
  if git branch -d "$b" >/dev/null 2>&1; then echo "削除: local $b"
  else echo "失敗: local $b は git branch -d に拒否されました（main を checkout した作業ツリーで再実行してください）" >&2; FAIL=1; fi
done
for b in "${DEL_REMOTE[@]}"; do
  if [ "$DRY_RUN" = 1 ]; then echo "削除予定: remote $REMOTE/$b"; continue; fi
  # 同名ローカルの削除が失敗して残っていたら、リモートも残す（判定時は 要掃除 でも、実行結果で守る側に倒す）
  if git rev-parse -q --verify "refs/heads/$b" >/dev/null; then
    echo "失敗: remote $REMOTE/$b は同名のローカルブランチが削除できず残っているため残します" >&2; FAIL=1; continue
  fi
  # 直前に統合済みを再確認してから、リモートの値がその時点のままのときだけ消す（--force-with-lease 相当の保護）
  sha=$(git rev-parse "refs/remotes/$REMOTE/$b")
  if ! is_integrated "$sha"; then echo "失敗: remote $b が統合済みでなくなりました（残します）" >&2; FAIL=1; continue; fi
  if git push --quiet --force-with-lease="refs/heads/$b:$sha" "$REMOTE" ":refs/heads/$b"; then echo "削除: remote $REMOTE/$b"
  else echo "失敗: remote $REMOTE/$b を削除できませんでした" >&2; FAIL=1; fi
done
[ ${#DEL_LOCAL[@]} -eq 0 ] && [ ${#DEL_REMOTE[@]} -eq 0 ] && echo "削除対象: なし"
for s in "${SKIPS[@]}"; do echo "対象外: $s"; done
[ "$DRY_RUN" = 1 ] || echo "次: scripts/branch_ops.sh audit でクリーンを確認"
exit "$FAIL"
