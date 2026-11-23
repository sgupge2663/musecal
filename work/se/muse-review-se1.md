# SE-1 レビュー（Muse、2026-11-23）

対象: ブランチ codex/series-edit、コミット 63ada2f（Codex 実装 gpt-6.1-sol / medium。ACP 上限のためコミットと push は Muse が代行し、check.sh を Muse が再実行して確認した）。

## 照合結果
- 適用範囲の選択（この予定のみ / すべての予定、既定はこの予定のみ）が編集画面に追加され、スクリーンショット基準 event_editor_recurring_scope.png を目視で仕様と照合した（適用範囲が最上部に表示され、既定で「この予定のみ」が選択されている）。
- 「すべての予定」では editingEventId が recurringEventId を返し、マスターの GET 結果でフォームと保存対象を置換する。保存は既存の updateEventBody / EventsApi.update（GET＋If-Match PUT）を共用し、recurrence と未編集フィールドの保持が EventEditTest で固定されている。移動はマスターへの events.move で、応答喪失時の扱いは従来の制約どおり。
- マスター取得失敗時は保存・削除が無効化され、何も書き込まない。
- cx の自己レビュー（別セッション）で挙がった P2 2件（範囲切替時のフォーム再初期化＝rememberSaveable のキーに editScope を含める、シリーズ更新・移動後に取得済み全窓を再取得）が修正済みであることを差分で確認した。
- Muse の再実行で scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug）。

## 判定
受理可。指摘なし。実 Google カレンダーへの反映は実機確認（QC-13）として未確認のまま残る。

# SE-2 レビュー追補（Muse、2026-11-23）

対象: コミット 3e00980 と 951f48d（Codex 実装）。

- REQUIREMENTS §8.1 が実装どおりに更新された: 適用範囲の選択と既定、マスター対象の GET＋events.update（recurrence・未編集フィールド保持）、マスターへの events.move、取得失敗時に書き込まないこと、残る制約（RRULE 編集・分割は対象外、例外インスタンスは上書きされない、move 応答喪失は未対応）、シリーズ編集後の全窓再取得、QC-13 への参照。
- docs/qc.md に QC-13 が追加され、エージェント側の証拠と実機確認項目（未確認）が分離されている。
- SE-1 のブランチ CI（run 45178854097）が android-all 取得失敗で赤くなった件は、隠さず QC-13 と tasks に記録されている。このブランチは CI 堅牢化（PR #4）より前の base のためキャッシュ未適用のワークフローで走ったもので、PR 側のラン（main と合流したキャッシュつきワークフロー）で改めて確認する。

判定: SE-2 も受理可。
