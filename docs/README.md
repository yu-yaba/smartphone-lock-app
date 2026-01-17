# ドキュメント目次

最終確認日: 2026-01-17
更新理由: 2026/01/17 再起動シナリオ拡張（no_lock / lock_end_before / lock_end_after / MY_PACKAGE_REPLACED / lock_60m短縮）と TestControlReceiver/ログ判定改善、FGS 起動例外対策を反映。

最初に読む
- /README.md（プロダクト概要）
- /docs/roadmap/implementation_plan.md（ロードマップ・進捗・リスク）
- /docs/agents/AGENTS.md（AIエージェント入口）

ディレクトリ
- /docs/concepts/ : コンセプト・プロダクトの位置づけ
- /docs/roadmap/ : 実装計画・フェーズ進捗
- /docs/specs/ : 機能・システム仕様
- /docs/research/ : 調査・検証計画・分析メモ
- /docs/agents/ : AI向けのガイドと要約
- /docs/policies/ : ポリシー・コンプライアンス
- /docs/store/ : ストア用素材・文言
- /docs/iac/ : IaC / Terraform 関連

主要ドキュメント
- /docs/concepts/concept.md
- /docs/specs/emergency_unlock_spec.md
- /docs/specs/android_design_best_practices.md
- /docs/specs/notification_permission_recovery_spec.md
- /docs/research/focusguard_analysis.md
- /docs/research/overlay_reliability_investigation_plan.md
- /docs/research/overlay_reboot_recovery_report_2026-01-16.md
- /docs/store/play_release_settings.md

運用ルール
- README と implementation_plan の日付・ステータスは揃える。
- 変更が入ったドキュメントには更新理由を1行で残す。
