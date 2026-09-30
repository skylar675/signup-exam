DELETE FROM registration;

UPDATE activity
SET title = CASE id
        WHEN 1001 THEN 'Java 实战分享'
        WHEN 1002 THEN '并发验收专用活动'
        WHEN 1003 THEN '零名额活动'
        WHEN 1004 THEN '已关闭活动'
        WHEN 1005 THEN '请求键冲突验证活动'
        WHEN 1006 THEN '同用户不同键并发活动'
        WHEN 1007 THEN '同用户相同键并发活动'
    END,
    total_quota = CASE id
        WHEN 1002 THEN 10
        WHEN 1003 THEN 0
        ELSE 5
    END,
    remaining_quota = CASE id
        WHEN 1002 THEN 10
        WHEN 1003 THEN 0
        ELSE 5
    END,
    status = CASE id
        WHEN 1004 THEN 'CLOSED'
        ELSE 'OPEN'
    END
WHERE id BETWEEN 1001 AND 1007;
