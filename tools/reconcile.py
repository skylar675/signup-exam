#!/usr/bin/env python3
"""Read-only reconciliation for activity quota and registration uniqueness.

The live path opens one MySQL session, sets REPEATABLE READ, and reads activity
plus registration inside that transaction. Concurrent commits after the first
read are not mixed into this check. This tool never updates or deletes rows.
"""

import argparse
import json
import os
import subprocess
import sys

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

QUERY = r"""
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT JSON_OBJECT(
  'activities',
  (
    SELECT JSON_ARRAYAGG(JSON_OBJECT(
      'id', id,
      'totalQuota', total_quota,
      'remainingQuota', remaining_quota,
      'successCount', success_count
    ))
    FROM (
      SELECT a.id, a.total_quota, a.remaining_quota,
             (SELECT COUNT(*) FROM registration r
              WHERE r.activity_id = a.id AND r.status = 'REGISTERED') AS success_count
      FROM activity a
      ORDER BY a.id
    ) ordered_activity
  ),
  'duplicateActivities',
  (
    SELECT JSON_ARRAYAGG(JSON_OBJECT('userId', user_id, 'activityId', activity_id, 'count', cnt))
    FROM (
      SELECT user_id, activity_id, COUNT(*) AS cnt
      FROM registration
      GROUP BY user_id, activity_id
      HAVING COUNT(*) > 1
    ) dup_activity
  ),
  'duplicateRequests',
  (
    SELECT JSON_ARRAYAGG(JSON_OBJECT('userId', user_id, 'requestId', request_id, 'count', cnt))
    FROM (
      SELECT user_id, request_id, COUNT(*) AS cnt
      FROM registration
      GROUP BY user_id, request_id
      HAVING COUNT(*) > 1
    ) dup_request
  )
) AS report;
COMMIT;
"""


def issues_for(activity, duplicate_activities):
    found = []
    total = activity["totalQuota"]
    remaining = activity["remainingQuota"]
    success = activity["successCount"]
    if remaining < 0:
        found.append("remainingQuota < 0")
    if remaining > total:
        found.append("remainingQuota > totalQuota")
    if total != remaining + success:
        found.append("totalQuota != remainingQuota + successCount")
    for dup in duplicate_activities:
        if dup["activityId"] == activity["id"]:
            found.append("duplicate user+activity")
    return found


def build_report(payload, source):
    activities = payload.get("activities") or []
    duplicate_activities = payload.get("duplicateActivities") or []
    duplicate_requests = payload.get("duplicateRequests") or []
    rows = []
    for activity in activities:
        row_issues = issues_for(activity, duplicate_activities)
        rows.append({
            "id": activity["id"],
            "totalQuota": activity["totalQuota"],
            "remainingQuota": activity["remainingQuota"],
            "successCount": activity["successCount"],
            "consistent": not row_issues,
            "issues": row_issues,
        })
    global_issues = []
    if duplicate_activities:
        global_issues.append("duplicate user+activity rows")
    if duplicate_requests:
        global_issues.append("duplicate user+requestId rows")
    consistent = not global_issues and all(row["consistent"] for row in rows)
    return {
        "source": source,
        "consistent": consistent,
        "activities": rows,
        "duplicateActivities": duplicate_activities,
        "duplicateRequests": duplicate_requests,
        "issues": global_issues,
    }


def load_mysql():
    env = os.environ.copy()
    password = env.get("MYSQL_PASSWORD", "")
    if not password:
        raise RuntimeError("MYSQL_PASSWORD is empty")
    env["MYSQL_PWD"] = password
    command = [
        MYSQL,
        "--protocol=TCP",
        "-h", env.get("MYSQL_HOST", "127.0.0.1"),
        "-P", env.get("MYSQL_PORT", "3306"),
        "-u", env.get("MYSQL_USER", "signup_dev"),
        "--database", env.get("MYSQL_DATABASE", "signup_exam"),
        "--batch",
        "--raw",
        "--default-character-set=utf8mb4",
        "-e",
        QUERY,
    ]
    completed = subprocess.run(command, env=env, capture_output=True, text=True)
    if completed.returncode != 0:
        message = (completed.stderr or completed.stdout or "mysql failed").strip()
        raise RuntimeError(message)
    lines = [line for line in completed.stdout.splitlines() if line and line != "report"]
    if not lines:
        raise RuntimeError("mysql returned no report")
    return json.loads(lines[-1])


def load_fixture(path):
    with open(path, "r", encoding="utf-8") as handle:
        raw = json.load(handle)
    activities = []
    for activity in raw.get("activities", []):
        success = 0
        for registration in raw.get("registrations", []):
            if registration["activityId"] == activity["id"] and registration.get("status", "REGISTERED") == "REGISTERED":
                success += 1
        activities.append({
            "id": activity["id"],
            "totalQuota": activity["totalQuota"],
            "remainingQuota": activity["remainingQuota"],
            "successCount": success,
        })
    dup_activity = {}
    dup_request = {}
    for registration in raw.get("registrations", []):
        activity_key = (registration["userId"], registration["activityId"])
        request_key = (registration["userId"], registration["requestId"])
        dup_activity[activity_key] = dup_activity.get(activity_key, 0) + 1
        dup_request[request_key] = dup_request.get(request_key, 0) + 1
    return {
        "activities": activities,
        "duplicateActivities": [
            {"userId": key[0], "activityId": key[1], "count": count}
            for key, count in dup_activity.items() if count > 1
        ],
        "duplicateRequests": [
            {"userId": key[0], "requestId": key[1], "count": count}
            for key, count in dup_request.items() if count > 1
        ],
    }


def main():
    parser = argparse.ArgumentParser(description="Read-only signup quota reconciliation")
    parser.add_argument("--fixture", help="Check a JSON sample instead of MySQL")
    args = parser.parse_args()
    try:
        if args.fixture:
            payload = load_fixture(args.fixture)
            source = "fixture:" + args.fixture
        else:
            payload = load_mysql()
            source = "mysql"
        report = build_report(payload, source)
    except Exception as exc:
        print(json.dumps({"consistent": False, "error": str(exc)}, ensure_ascii=False))
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["consistent"] else 1


if __name__ == "__main__":
    sys.exit(main())
