"""生成本地开发账号 SQL（固定口令哈希不进入仓库）。

用法（在仓库根目录执行）：
    pip install bcrypt
    python tools/gen_dev_users.py
    mysql -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 zhixue < zhixue-server/sql/seed-dev-users.local.sql

默认生成 16 位随机口令并在终端打印一次；也可用 --password 指定（不推荐在共享机器使用）。
输出文件 zhixue-server/sql/seed-dev-users.local.sql 已加入 .gitignore，禁止提交。
"""

from __future__ import annotations

import argparse
import secrets
import string
from pathlib import Path

import bcrypt

OUT = Path(__file__).resolve().parent.parent / "zhixue-server" / "sql" / "seed-dev-users.local.sql"
PASSWORD_CHARS = string.ascii_letters + string.digits + "!@#%^*-_"

SQL_TEMPLATE = """-- 本地开发账号（由 tools/gen_dev_users.py 生成；禁止提交）
USE zhixue;
SET NAMES utf8mb4;

INSERT INTO sys_user (user_id, username, password, real_name, role, class_name)
VALUES (1, 'teacher01', '{password_hash}', '教师A', 'TEACHER', NULL)
ON DUPLICATE KEY UPDATE password = VALUES(password), real_name = VALUES(real_name),
                        role = VALUES(role), class_name = VALUES(class_name), is_active = 1;

INSERT INTO sys_user (user_id, username, password, real_name, role, class_name)
VALUES (2, 'student01', '{password_hash}', '学生A', 'STUDENT', '23软件工程1班')
ON DUPLICATE KEY UPDATE password = VALUES(password), real_name = VALUES(real_name),
                        role = VALUES(role), class_name = VALUES(class_name), is_active = 1;
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="生成本地开发账号 SQL")
    parser.add_argument("--password", default=None, help="指定开发口令；缺省随机生成 16 位")
    args = parser.parse_args()

    password = args.password or "".join(secrets.choice(PASSWORD_CHARS) for _ in range(16))
    hashed = bcrypt.hashpw(
        password.encode("utf-8"), bcrypt.gensalt(rounds=10, prefix=b"2a")
    ).decode("ascii")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(SQL_TEMPLATE.format(password_hash=hashed), encoding="utf-8")

    print("已生成 " + str(OUT))
    print("开发账号口令（仅本次显示，请妥善保存）: " + password)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
