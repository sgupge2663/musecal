# タスク: シリーズ編集

正本は proposal.md。検証方法は EM-3 のフロー。

- [ ] SE-1 シリーズ編集の実装（Codex 実装、Muse レビュー）
  - 内容: 編集画面に適用範囲の選択（この予定のみ / すべての予定）を追加する。「すべての予定」ではマスター予定（recurringEventId）を取得してフィールドを表示し、保存は events.update（GET で現行値と ETag を取り、未編集フィールドと recurrence を保持）、移動は events.move でマスターを対象にする。対象 ID の決定と更新ボディの構築は単体編集と共通の仕組みを使い、単体テストで固定する。範囲切替のスクリーンショット基準を ScreenshotTest に追加する
  - 完了条件: 単体テストで「すべての予定の保存先がマスター ID になること」「recurrence が保持されること」「この予定のみの従来動作が変わらないこと」が固定され、全テストと lint が成功する。スクリーンショット基準を Muse が目視で仕様と照合する
  - 検証方法: scripts/check.sh とブランチ CI、スクリーンショット基準

  - 実装記録（2026-11-23、Codex）:
    - 編集画面に範囲選択を追加。既定はインスタンス。全体を選ぶと recurringEventId の GET 結果でフォームと保存対象を置換し、マスター取得失敗時は保存・削除を無効化する。
    - 保存は既存 updateEventBody / EventsApi.update（GET＋If-Match PUT）を共用。マスターは既存 move に渡し、移動後の更新失敗は移動先 ID で再試行する。シリーズ更新・移動後は取得済み全窓も再取得する。
    - 単体のカレンダー移動制限と読み取り専用制御を維持。削除の範囲は編集範囲から独立して従来どおり選択する。
    - EventEditTest に保存対象 ID、マスター日時、recurrence・未編集フィールド・ETag の保持を追加。ScreenshotTest に範囲選択の基準と等値 draft の範囲切替時の初期化検証を追加。
    - 独立レビュアの P2 指摘（等値 draft の初期化、別窓キャッシュの再取得）を修正。Muse の最終レビュー・画面判定と実 Google Calendar に対する反映は未確認、受理待ち。
    - 引き継ぎ: 次は Muse が差分と event_editor_recurring_scope.png を照合し、ブランチ CI を確認。その後 SE-2 で仕様・QC に収束する。STATUS.md / REQUIREMENTS.md / docs/qc.md は変更していない。

- [ ] SE-2 仕様への収束（Codex 実装、Muse レビュー）
  - 内容: REQUIREMENTS §8.1 のシリーズ編集の制約記述を実装済みに更新し（例外インスタンスを上書きしない点と RRULE 編集・分割が対象外である点は制約として残す）、docs/qc.md に QC-13 としてエージェント側の証拠と実機確認項目（未確認）を追加する
  - 完了条件: 仕様と実装の食い違いが残っていない
  - 検証方法: レビューで実装と突き合わせる


  - 実装記録（2026-11-23、Codex）:
    - REQUIREMENTS.md §8.1 を proposal と SE-1 `63ada2f` に収束。範囲選択・既定値、マスター取得／GET＋If-Match update／move、取得失敗時の書き込み禁止、全取得済み窓の再取得を反映。RRULE・分割・例外・move 応答喪失の制約を明記。
    - docs/qc.md に QC-13 を追加。単体テスト・スクリーンショット基準・ローカル検証・ブランチ CI と、未確認の実機項目を分離。QC-12 の旧仕様への言及は QC-13 参照へ更新。全体判定は実機確認待ち。
    - 検証: 本体 scripts/env.sh を source し ./scripts/check.sh 成功（assembleDebug / testDebugUnitTest / lintDebug OK、/tmp/se2-check.log）。共有 Gradle キャッシュへの書き込みを権限拡張して実行。中継プロキシの再起動は不要だった。EventEditTest 6件・ScreenshotTest 14件はいずれも失敗0。git diff --check 成功。
    - CI: SE-1 の Build APK run 45178854097 は記録時点で実行中、成功未確認。SE-2 最終コミットの CI は push 後に確認する（参照は QC-13）。
    - 独立レビュー: 別 Codex エージェントが proposal・実装・文書差分を照合、修正必須の指摘なし。Muse の最終レビュー・画像照合、ユーザーの画面判定と実機反映確認は未確認のため、チェック欄は未完了のまま。
    - 引き継ぎ: 次は Muse が最終コミットの CI と仕様収束差分・基準画像を確認し、ユーザーが QC-13 の実機項目を確認する。変更ファイルは REQUIREMENTS.md / docs/qc.md / 本 tasks.md のみ。STATUS.md は変更していない。成果物は各ファイルの差分と QC-13。
