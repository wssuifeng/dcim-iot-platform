-- ============================================================
-- V12: 模拟运行模块整体退役（simulation_* 四张表 DROP）
-- 对应计划: docs/阶段二与阶段三实施计划-数据接收门与机房仿真服务.md 第二步 S1
--
-- 仿真职责迁移至独立仓库 dcim-simulator（设备模块 + 网关模块，
-- 经第一步的 ingest 契约上报，平台零特判）；历史 simulation_* 表随
-- 平台内仿真引擎一并退役，V8 建表与 V10 改列保留原文（checksum 不可改）。
-- ============================================================

DROP TABLE IF EXISTS simulation_generated_data_ref;
DROP TABLE IF EXISTS simulation_run_record;
DROP TABLE IF EXISTS simulation_scene_step;
DROP TABLE IF EXISTS simulation_scene;
