-- 07_skill_dictionary_additions.sql
-- 점핏 기술 스택 중 사전에 없어 버려지던 값을 표준 스킬·별칭으로 더한다.
-- 이름으로 찾아 없을 때만 넣어 여러 번 실행해도 같다(운영 DB 와 신규 환경 공용).

-- 1. 새 표준 스킬
INSERT INTO core.skill (id, standard_name, category, is_custom, created_by)
SELECT gen_random_uuid(), v.name, v.category, false, NULL
FROM (VALUES
    ('Machine Learning', 'AI_ML'),
    ('Matplotlib', 'AI_ML'),
    ('DVC', 'AI_ML'),
    ('Jackson', 'BACKEND'),
    ('Socket.IO', 'BACKEND'),
    ('RxJS', 'FRONTEND'),
    ('Jotai', 'FRONTEND'),
    ('Vuex', 'FRONTEND'),
    ('RxSwift', 'MOBILE'),
    ('Koin', 'MOBILE'),
    ('Robolectric', 'MOBILE'),
    ('Capacitor', 'MOBILE'),
    ('NativeScript', 'MOBILE'),
    ('Firebase Crashlytics', 'MOBILE'),
    ('Datadog', 'DEVOPS'),
    ('Loki', 'DEVOPS'),
    ('Vault', 'DEVOPS'),
    ('Zookeeper', 'DEVOPS'),
    ('TeamCity', 'DEVOPS'),
    ('MinIO', 'DEVOPS'),
    ('k6', 'DEVOPS'),
    ('OpenVPN', 'DEVOPS'),
    ('libvirt', 'DEVOPS'),
    ('PowerDNS', 'DEVOPS'),
    ('Confluent', 'DEVOPS'),
    ('Visual Studio', 'TOOLS'),
    ('Perforce', 'TOOLS'),
    ('SourceTree', 'TOOLS'),
    ('pytest', 'TOOLS'),
    ('YAML', 'TOOLS'),
    ('FigJam', 'TOOLS'),
    ('Linear', 'TOOLS')
) AS v(name, category)
WHERE NOT EXISTS (SELECT 1 FROM core.skill s WHERE lower(s.standard_name) = lower(v.name));

-- 2. 표준 스킬 별칭 (표준 스킬이 있을 때만)
INSERT INTO core.skill_alias (id, skill_id, alias_name)
SELECT gen_random_uuid(), s.id, v.alias
FROM (VALUES
    ('AWS', 'amazon ec2'),
    ('AWS', 'amazon rds'),
    ('AWS', 'amazon eks'),
    ('AWS', 'amazon sqs'),
    ('AWS', 'amazon quicksight'),
    ('AWS', 'amazon cloudfront'),
    ('AWS', 'amazon kinesis'),
    ('AWS', 'amazon sagemaker'),
    ('R', 'r language'),
    ('Network', 'ethernet'),
    ('Network', 'l2'),
    ('Network', 'l3'),
    ('Apache Spark', 'pyspark'),
    ('Retrofit2', 'retrofit'),
    ('SSH', 'openssh'),
    ('Machine Learning', 'machinelearning'),
    ('Firebase Crashlytics', 'crashlytics')
) AS v(skill_name, alias)
JOIN core.skill s ON s.standard_name = v.skill_name AND s.is_custom = false
WHERE NOT EXISTS (SELECT 1 FROM core.skill_alias a WHERE lower(a.alias_name) = v.alias);
