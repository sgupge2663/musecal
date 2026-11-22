# V2-2 レビュー（Muse、2026-11-22）

対象: ブランチ codex/v22-create、コミット 14c1cce。実装は Codex（gpt-6.1-sol / medium）。ACP の時間上限でコミット前に終了したため、check.sh の再実行（成功、89 tests・失敗0）とコミット・push は Muse が代行した。レビューは Muse（単純寄りタスクとして実施）。

## 照合結果
- 編集画面のスクリーンショット基準（event_editor_create.png）を Muse が目視確認：キャンセル／予定の作成／保存、タイトル、終日トグル、開始・終了（日付＋時刻）、カレンダー選択、色選択、場所、説明の全項目が仕様どおりに揃っている。
- createEventBody の変換は V1-3 の読み取り変換と対になっている：終日は end.date を翌日（排他）、時間指定は ISO offset dateTime ＋ timeZone、colorId は選択時のみ、location/description を含む。valid() で終了≦開始を弾く。
- 書き込み可能カレンダーは calendarList の accessRole が owner/writer のものだけ（writableCalendars）。祝日などの読み取り専用カレンダーは作成先に出ない。
- 保存成功時は対象窓を syncNow で再取得し、表示・キャッシュを一致させる経路を確認。失敗時は画面を閉じず入力を保持し、日本語の理由を示す（writeFailureMessage は読み取り側の分類・文言を書き込み用に読み替える実装。低優先の観察事項として記録するが、文言の一貫性は保たれており不具合ではない）。
- トークン非保存、新規ライブラリなし。

## 判定
受理可。指摘なし。

## 未確認（実機側）
- 実サーバーへの作成と Web 側との一致は QC-12（V2-5、v2 初回配布時）に含める。
