# ADR-0002: APK ビルドは GitHub Actions を正とする
- Status: accepted (2026-11-21, ユーザーが workflow スコープ付与を決定)
- Context: VM ローカルにも toolchain はあるが、環境依存（プロキシ、SDK）が大きく、PC 側には SDK が無い
- Decision: push のたびに Actions が assembleDebug して APK を成果物に置く。ローカルビルドは開発中の高速確認用に残す
- Consequences: どの環境から push しても同じ手順で APK が得られる。ビルド証拠が run として残る
