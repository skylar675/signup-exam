INSERT IGNORE INTO activity (id, title, status, total_quota, remaining_quota) VALUES
(1001, 'Java 实战分享', 'OPEN', 5, 5),
(1002, '并发验收专用活动', 'OPEN', 10, 10),
(1003, '零名额活动', 'OPEN', 0, 0),
(1004, '已关闭活动', 'CLOSED', 5, 5),
(1005, '请求键冲突验证活动', 'OPEN', 5, 5),
(1006, '同用户不同键并发活动', 'OPEN', 5, 5),
(1007, '同用户相同键并发活动', 'OPEN', 5, 5);
