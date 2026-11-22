# V1-5b レビュー記録（対象: 29c7c78、著者 Codex、レビュアー Muse）

## 原因（実装側の特定を Muse がコードで裏取り）
- `INITIAL_MONTH: YearMonth = YearMonth.of(2026, 12)` という固定値が初期表示月を決めていた。12月 fixture で開発していた時期の名残で、本番の today（LocalDate.now()）は初期位置に一切使われていなかった。だから実機では常に 2026 年 12 月が開いた。

## 確認内容
1. 修正は `remember { YearMonth.from(todayState.value) }` で初回だけ当月を初期位置にする形。復帰・today 更新で表示位置を奪わない（回帰テストで「today を 2027-01-01 に更新しても 12 月表示を維持」を固定）。
2. スクショテストの前提日（today=2026-12-16 注入）は分離が保たれ、Roborazzi 基準画像は無変更で比較成功。INITIAL_MONTH の残存参照なし（grep 0件）。
3. check.sh 成功、単体テスト 77件・失敗0・省略1（ブランチ CI run も成功）。スコープ追加なし。

## Verdict
**受理可、指摘なし。** 実機での最終確認（v0.9 で当月に開くこと）はユーザー側に残る。
