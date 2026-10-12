-- Fictional teaching examples. Counts are demo constants, NOT real interactions or student experiences.
INSERT INTO category (id,kind,name,display_order) VALUES
(1,'JOB','Java后端开发（演示）',1),(2,'JOB','前端开发（演示）',2),
(11,'MAJOR','计算机科学与技术',1),(12,'MAJOR','软件工程',2),
(21,'COMPANY','示例科技A（虚构）',1),(22,'COMPANY','示例科技B（虚构）',2),
(31,'TOPIC','准备路线',1),(32,'TOPIC','投递与面试',2),(33,'TOPIC','待遇与避坑',3);

INSERT INTO article (id,state,created_at,effective_coins,effective_likes) VALUES
(1001,'DRAFT','2026-10-01 00:00:00',20,30),
(1002,'DRAFT','2026-10-01 00:00:00',20,30),
(1003,'DRAFT','2026-10-01 00:00:00',20,25),
(1004,'DRAFT','2026-10-01 00:00:00',5,80),
(1005,'DRAFT','2026-10-01 00:00:00',0,0),
(1006,'DRAFT','2026-10-01 00:00:00',20,30),
(2001,'DRAFT','2026-10-01 00:00:00',999,999),
(2002,'AUTHOR_OFFLINE','2026-10-01 00:00:00',999,999),
(2003,'ADMIN_HIDDEN','2026-10-01 00:00:00',999,999),
(2004,'DELETED','2026-10-01 00:00:00',999,999);

INSERT INTO article_version (id,article_id,version_number,title,summary,body,experience_stage,source_type,source_note,published_at,created_at) VALUES
(11001,1001,1,'【演示】Java后端实习准备路线','演示从Java基础到项目表达的准备路线。','# 演示文章\n\n本文是虚构教学示例，不是已核验的本校亲历。\n\n## 准备路线\n1. 理解Java对象与集合。\n2. 熟悉SQL与接口。\n3. 练习清楚描述项目。\n\n[Java资料](https://dev.java/learn/)\n\n正文可包含作者自行公开的文字，测试示例：自愿联系文字。','大三实习准备','DEMO','由项目演示脚本生成；获币和点赞为演示常量。','2026-10-08 02:00:00','2026-10-01 00:00:00'),
(11002,1002,1,'【演示】Java面试项目表达','演示如何解释接口、数据库和项目取舍。','# 演示项目表达\n\n这是演示内容。说明问题、做法和验证结果。','大三实习','DEMO','虚构教学内容；不代表真实公司面试。','2026-10-08 02:00:00','2026-10-01 00:00:00'),
(11003,1003,1,'【演示】Java投递复盘','演示记录投递阶段、面试问题和下一步。','# 演示投递复盘\n\n记录准备与复盘，不编造录用结果。','大四求职','DEMO','虚构教学内容。','2026-10-09 02:00:00','2026-10-01 00:00:00'),
(11004,1004,1,'【演示】前端作品集准备','演示界面作品、交互说明及面试表达。','# 演示前端作品集\n\n说明作品与职责。','大三实习准备','DEMO','虚构教学内容。','2026-10-10 02:00:00','2026-10-01 00:00:00'),
(11005,1005,1,'【演示】实习待遇与避坑','演示查清职责、工作时间和实习条件。','# 演示待遇与避坑\n\n本文仅是教学示例。','大四实习','DEMO','虚构教学内容。','2026-10-11 02:00:00','2026-10-01 00:00:00'),
(11006,1006,1,'【演示】100%准备与下划线_记录','演示SQL特殊字符：100%、下划线_、感叹号!。','# 特殊字符演示\n\n字符仅作为正文文字。','大三实习准备','DEMO','虚构教学内容。','2026-10-07 02:00:00','2026-10-01 00:00:00'),
(11901,1001,2,'未公开修订标题','未公开修订摘要','PRIVATE_DRAFT_BODY','大三实习准备','DEMO','未发布的草稿版本。',NULL,'2026-10-12 00:00:00'),
(12001,2001,1,'秘密草稿','PRIVATE_SUMMARY','PRIVATE_DRAFT_BODY','演示','DEMO','私有草稿。',NULL,'2026-10-01 00:00:00'),
(12002,2002,1,'作者下架演示','PRIVATE_SUMMARY','PRIVATE_OFFLINE_BODY','演示','DEMO','下架示例。','2026-10-01 00:00:00','2026-10-01 00:00:00'),
(12003,2003,1,'管理员隐藏演示','PRIVATE_SUMMARY','PRIVATE_HIDDEN_BODY','演示','DEMO','隐藏示例。','2026-10-01 00:00:00','2026-10-01 00:00:00'),
(12004,2004,1,'删除演示','PRIVATE_SUMMARY','PRIVATE_DELETED_BODY','演示','DEMO','删除示例。','2026-10-01 00:00:00','2026-10-01 00:00:00');

UPDATE article SET public_version_id=11001,state='PUBLISHED' WHERE id=1001;
UPDATE article SET public_version_id=11002,state='PUBLISHED' WHERE id=1002;
UPDATE article SET public_version_id=11003,state='PUBLISHED' WHERE id=1003;
UPDATE article SET public_version_id=11004,state='PUBLISHED' WHERE id=1004;
UPDATE article SET public_version_id=11005,state='PUBLISHED' WHERE id=1005;
UPDATE article SET public_version_id=11006,state='PUBLISHED' WHERE id=1006;
UPDATE article SET public_version_id=12002 WHERE id=2002;
UPDATE article SET public_version_id=12003 WHERE id=2003;
UPDATE article SET public_version_id=12004 WHERE id=2004;

INSERT INTO article_version_category (version_id,category_id) VALUES
(11001,1),(11001,11),(11001,21),(11001,31),
(11002,1),(11002,11),(11002,21),(11002,32),
(11003,1),(11003,12),(11003,22),(11003,32),
(11004,2),(11004,12),(11004,22),(11004,31),
(11005,1),(11005,11),(11005,33),
(11006,1),(11006,11),(11006,21),(11006,31),
(11901,2),(11901,12),(11901,22),(11901,33),
(12001,1),(12001,11),(12002,1),(12002,11),(12003,1),(12003,11),(12004,1),(12004,11);

INSERT INTO article_source_link (id,version_id,label,url) VALUES
(1,11001,'Java官方学习资料（外部来源）','https://dev.java/learn/');
