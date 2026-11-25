"""Run the CampusHub load scenarios against a new loopback-only MySQL instance."""

import argparse
import base64
import concurrent.futures
import csv
import hashlib
import http.client
import json
import math
import os
import platform
import secrets
import shutil
import socket
import subprocess
import threading
import time
from collections import Counter
from pathlib import Path
from uuid import uuid4


SERVER = Path(__file__).resolve().parents[1]
HOST = "127.0.0.1"
DB = "campushub_test"
USER_START = 100_001
ARTICLE_START = 1_000_001
THINK_SECONDS = 0.2
HIDDEN = getattr(subprocess, "CREATE_NO_WINDOW", 0)
PHASES = [
    ("normal-ramp", 30, 60, True), ("normal", 30, 600, False),
    ("stress-10", 10, 120, False), ("stress-30", 30, 120, False),
    ("stress-50", 50, 120, False), ("stress-100", 100, 120, False),
    ("after-stress", 10, 120, False), ("stability", 10, 1800, False),
]


def local_port():
    with socket.socket() as listener:
        listener.bind((HOST, 0))
        return listener.getsockname()[1]


def wait_ready(process, check, seconds, label):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"{label} stopped before readiness")
        try:
            if check():
                return
        except (OSError, subprocess.CalledProcessError):
            pass
        time.sleep(0.5)
    raise RuntimeError(f"{label} readiness timed out")


class LocalDatabase:
    def __init__(self, directory):
        self.directory = directory
        self.data = directory / "mysql-data"
        self.port = local_port()
        self.password = secrets.token_hex(24)
        mysql = shutil.which("mysql.exe")
        if mysql is None:
            raise RuntimeError("MySQL 8 executables must be on PATH")
        self.bin = Path(mysql).parent
        self.base = [str(self.bin / "mysqld.exe"), "--no-defaults",
                     f"--basedir={self.bin.parent}", f"--datadir={self.data}",
                     f"--log-error={directory / 'mysql.log'}"]
        self.process = None
        self.owns_connection = False

    def start(self):
        subprocess.run(self.base + ["--initialize-insecure"], check=True, creationflags=HIDDEN)
        self.process = subprocess.Popen(self.base + [f"--port={self.port}", f"--bind-address={HOST}",
                                                    "--mysqlx=OFF", "--skip-log-bin"], creationflags=HIDDEN)
        wait_ready(self.process, lambda: Path(self.root_sql("SELECT @@datadir")).resolve() == self.data,
                   40, "isolated MySQL")
        self.owns_connection = True
        self.root_sql(
            f"CREATE DATABASE {DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
            f"CREATE USER 'campushub_load'@'{HOST}' IDENTIFIED BY '{self.password}';"
            f"GRANT ALL PRIVILEGES ON {DB}.* TO 'campushub_load'@'{HOST}';"
        )

    def root_sql(self, sql, database=None):
        args = [str(self.bin / "mysql.exe"), "--no-defaults", "--protocol=TCP", f"--host={HOST}",
                f"--port={self.port}", "--user=root", "--default-character-set=utf8mb4", "--batch",
                "--skip-column-names"]
        if database:
            args.append(database)
        environment = os.environ.copy()
        environment.pop("MYSQL_PWD", None)
        return subprocess.run(args, input=sql, encoding="utf-8", capture_output=True, check=True,
                              env=environment, timeout=35, creationflags=HIDDEN).stdout.strip()

    def close(self):
        if self.process is None or self.process.poll() is not None:
            return
        if self.owns_connection:
            environment = os.environ.copy()
            environment.pop("MYSQL_PWD", None)
            try:
                subprocess.run([str(self.bin / "mysqladmin.exe"), "--no-defaults", "--protocol=TCP",
                                f"--host={HOST}", f"--port={self.port}", "--user=root", "shutdown"],
                               env=environment, timeout=15, capture_output=True, creationflags=HIDDEN)
            except subprocess.TimeoutExpired:
                pass
        stop(self.process)


def stop(process):
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()


def insert(db, header, rows):
    for start in range(0, len(rows), 250):
        db.root_sql(header + ",".join(rows[start:start + 250]) + ";", DB)


def seed(db):
    token_list = [secrets.token_urlsafe(32) for _ in range(200)]
    users = range(USER_START, USER_START + len(token_list))
    insert(db, "INSERT INTO platform_user(id,email,password_hash,created_at,password_changed_at) VALUES ",
           [f"({user},'load{user}@stu.hzcu.edu.cn','load-fixture',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))" for user in users])
    insert(db, "INSERT INTO campus_certification(user_id,verified_at) VALUES ",
           [f"({user},UTC_TIMESTAMP(6))" for user in users])
    insert(db, "INSERT INTO auth_session(user_id,token_digest,created_at,expires_at) VALUES ",
           [f"({user},'{hashlib.sha256(token.encode()).hexdigest()}',UTC_TIMESTAMP(6),"
            f"DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY))"
            for user, token in zip(users,token_list)])
    existing = int(db.root_sql("SELECT COUNT(*) FROM article", DB))
    articles = range(ARTICLE_START, ARTICLE_START + 2000 - existing)
    insert(db, "INSERT INTO article(id,state,owner_user_id,created_at,updated_at,count_origin) VALUES ",
           [f"({article},'DRAFT',{users[index % len(users)]},NOW(6),NOW(6),'NONE')"
            for index, article in enumerate(articles)])
    body = "Synthetic CampusHub load-test content. " * 24
    insert(db, "INSERT INTO article_version(id,article_id,version_number,title,summary,body,experience_stage,"
           "source_type,source_note,published_at,created_at,updated_at) VALUES ",
           [f"({article},{article},1,'Load article {article}','Synthetic example','{body}','Load','DEMO','Load fixture',NOW(6),NOW(6),NOW(6))"
            for article in articles])
    db.root_sql(f"UPDATE article SET state='PUBLISHED',public_version_id=id,first_published_at=NOW(6) "
                f"WHERE id BETWEEN {articles.start} AND {articles.stop - 1};", DB)
    insert(db, "INSERT INTO article_draft(article_id,body) VALUES ",
           [f"({article},'Initial synthetic draft')" for article in articles[:len(users)]])
    insert(db, "INSERT INTO article_version_category(version_id,category_id) VALUES ",
           [f"({article},1)" for article in articles])
    existing_comments = int(db.root_sql("SELECT COUNT(*) FROM discussion_comment", DB))
    comments = range(2_000_001, 2_000_001 + 5000 - existing_comments)
    insert(db, "INSERT INTO discussion_comment(id,article_id,user_id,body,created_at) VALUES ",
           [f"({comment},{articles[index % len(articles)]},{users[index % len(users)]},'Synthetic comment',NOW(6))"
            for index, comment in enumerate(comments)])
    return token_list, articles


class Client:
    def __init__(self, port, index, tokens, articles):
        self.connection = http.client.HTTPConnection(HOST, port, timeout=8)
        self.index = index
        self.token = tokens[index]
        self.articles = articles
        self.revision = None
        self.reward = {"requestId": str(uuid4())}

    def request(self, method, path, payload=None):
        headers = {"Authorization": f"Bearer {self.token}"}
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload).encode()
        started = time.time_ns()
        timer = time.monotonic()
        try:
            self.connection.request(method, "/api/v1" + path, body, headers)
            response = self.connection.getresponse()
            raw = response.read()
            data = json.loads(raw)
            return started, (time.monotonic()-timer)*1000, response.status, data
        except (OSError, ValueError, http.client.HTTPException):
            self.connection.close()
            return started, (time.monotonic()-timer)*1000, 0, {}

    def work(self, step):
        article = self.articles[(self.index * 17 + step) % len(self.articles)]
        kind = step % 10
        if kind < 3:
            return "read-list", self.request("GET", "/articles?size=20")
        if kind < 6:
            return "read-detail", self.request("GET", f"/articles/{article}")
        if kind == 6:
            return "read-comments", self.request("GET", f"/articles/{article}/comments")
        if kind == 7:
            return "write-comment", self.request("POST", f"/articles/{article}/comments",
                                                 {"requestId": str(uuid4()), "body": "Synthetic comment",
                                                  "imageIds": [], "replyToCommentId": None})
        if kind == 8:
            path = f"/me/articles/{self.articles[self.index]}/draft"
            if self.revision is None:
                fetched = self.request("GET", path)
                if fetched[2] == 200:
                    self.revision = fetched[3]["data"]["revision"]
                return "read-draft", fetched
            payload = {"expectedRevision": self.revision, "title": "Synthetic draft", "summary": "",
                       "body": f"## Load edit {step}\n\nSynthetic markdown.", "jobIds": [1], "majorIds": [],
                       "companyIds": [], "topicIds": [], "experienceStage": "Load", "sourceType": "DEMO",
                       "sourceNote": "Load fixture", "sourceLinks": []}
            saved = self.request("PUT", path, payload)
            self.revision = saved[3]["data"]["revision"] if saved[2] == 200 else None
            return "write-draft", saved
        return "write-reward", self.request("POST", "/me/rewards/login", self.reward)


def percentile(values):
    return round(sorted(values)[math.ceil(len(values) * 0.95)-1], 2) if values else None


def phase_load(label, concurrency, duration, ramp, clients, writer):
    deadline = time.monotonic() + duration
    elapsed_begin = time.monotonic()
    totals = Counter()
    errors = Counter()
    times = {"read": [], "write": []}
    operation_times = {}
    write_lock = threading.Lock()

    def worker(client):
        if ramp:
            time.sleep(duration * client.index / concurrency)
        step = client.index
        while time.monotonic() < deadline:
            operation, (start, elapsed, status, data) = client.work(step)
            passed = 200 <= status < 300 and data.get("code") == "OK"
            group = operation.split("-", 1)[0]
            response_code = data.get("code", "NETWORK_OR_INVALID_RESPONSE")
            with write_lock:
                totals[operation] += 1
                if passed:
                    times[group].append(elapsed)
                    operation_times.setdefault(operation, []).append(elapsed)
                else:
                    errors[f"{operation}:{status}:{response_code}"] += 1
                writer.writerow((start//1_000_000, round(elapsed), operation, status, response_code,
                                 f"client-{client.index+1}", str(passed).lower(), label))
            step += 1
            time.sleep(THINK_SECONDS)

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(pool.map(worker, clients[:concurrency]))
    requests = sum(totals.values())
    return {"stage": label, "concurrency": concurrency, "plannedSeconds": duration,
            "durationSeconds": round(time.monotonic() - elapsed_begin, 2), "requests": requests,
            "readP95Ms": percentile(times["read"]), "writeP95Ms": percentile(times["write"]),
            "errorRate": round(sum(errors.values())/max(1, requests), 6),
            "requestsPerSecond": round(requests/max(1, time.monotonic() - elapsed_begin), 2),
            "operations": dict(totals), "operationP95Ms": {name: percentile(values)
                    for name, values in operation_times.items()}, "errors": dict(errors)}


def counters(db):
    values = db.root_sql("""SELECT
        (SELECT COUNT(*) FROM platform_user), (SELECT COUNT(*) FROM article),
        (SELECT COUNT(*) FROM discussion_comment), (SELECT COUNT(*) FROM coin_event),
        (SELECT COUNT(*) FROM coin_wallet w LEFT JOIN
          (SELECT user_id,SUM(delta) delta,COUNT(*) events FROM coin_event GROUP BY user_id) e
          ON e.user_id=w.user_id WHERE w.balance<>COALESCE(e.delta,0) OR w.revision<>COALESCE(e.events,0))
          + (SELECT COUNT(DISTINCT e.user_id) FROM coin_event e LEFT JOIN coin_wallet w ON w.user_id=e.user_id
            WHERE w.user_id IS NULL),
        (SELECT COUNT(*) FROM article a LEFT JOIN article_version v ON a.public_version_id=v.id
          WHERE a.state='PUBLISHED' AND (v.id IS NULL OR v.article_id<>a.id));
        """, DB)
    return dict(zip(("users","articles","comments","coinEvents","walletMismatches","brokenPublicVersions"),
                    map(int, values.split("\t"))))


def sample_resources(processes, stop_sampling, destination):
    ps = shutil.which("pwsh")
    if ps is None:
        return
    with destination.open("w",encoding="utf-8") as report:
        while not stop_sampling.is_set():
            ids = ",".join(str(process.pid) for process in processes)
            command = f"Get-Process -Id {ids} | Select-Object Id,CPU,WorkingSet64 | ConvertTo-Json -Compress"
            result = subprocess.run([ps,"-NoProfile","-Command",command], capture_output=True, text=True,
                                    timeout=10, creationflags=HIDDEN)
            if result.returncode == 0:
                report.write(json.dumps({"epoch": time.time(), "processes": json.loads(result.stdout)})+"\n")
                report.flush()
            stop_sampling.wait(10)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--smoke", action="store_true", help="Run short preparation checks only")
    args = parser.parse_args()
    jar = SERVER / "target/campushub-server-0.1.0.jar"
    destination = SERVER / "target" / ("backend-load-" + uuid4().hex)
    destination.mkdir()
    print(f"Evidence directory {destination}", flush=True)
    db = LocalDatabase(destination)
    business = sampler = None
    stop_sampling = threading.Event()
    clients = []
    try:
        db.start()
        port = local_port()
        java_overrides = {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}
        environment = {key: value for key,value in os.environ.items()
                       if not key.startswith(("CAMPUSHUB_", "SPRING_")) and key not in java_overrides}
        environment.update(CAMPUSHUB_DB_HOST=HOST, CAMPUSHUB_DB_PORT=str(db.port), CAMPUSHUB_DB_NAME=DB,
                           CAMPUSHUB_DB_USERNAME="campushub_load", CAMPUSHUB_DB_PASSWORD=db.password,
                           CAMPUSHUB_AUTH_PEPPER=base64.b64encode(secrets.token_bytes(48)).decode(), CAMPUSHUB_MAIL_HOST=HOST,
                           CAMPUSHUB_MAIL_PASSWORD="load-fixture-only", CAMPUSHUB_PORT=str(port),
                           CAMPUSHUB_RUNTIME_ENV="test", CAMPUSHUB_RUN_MODE="release",
                           CAMPUSHUB_LOG_ROOT=str(destination/"logs"), CAMPUSHUB_MEDIA_ROOT=str(destination/"media"))
        with (destination/"business-console.log").open("wb") as console:
            business = subprocess.Popen(["java","-Xmx512m","-jar",str(jar)], cwd=destination,
                                        env=environment,stdout=console,stderr=subprocess.STDOUT,creationflags=HIDDEN)

        def healthy():
            connection = http.client.HTTPConnection(HOST, port, timeout=2)
            try:
                connection.request("GET","/actuator/health")
                response = connection.getresponse()
                envelope = json.loads(response.read())
                return response.status == 200 and envelope.get("data", {}).get("status") == "UP"
            finally:
                connection.close()

        wait_ready(business, healthy, 60, "business application")
        tokens, articles = seed(db)
        before = counters(db)
        if (before["users"], before["articles"], before["comments"]) != (200,2000,5000):
            raise RuntimeError("Prepared dataset does not match the SRS sample sizes")
        with jar.open("rb") as release:
            jar_sha256 = hashlib.file_digest(release,"sha256").hexdigest()
        report = {"mode": "smoke" if args.smoke else "srs", "dataset": before, "thinkSeconds": THINK_SECONDS,
                  "jarSha256": jar_sha256,
                  "environment": {"os": platform.platform(), "logicalCpu": os.cpu_count(), "jvmHeapMaximumMiB": 512,
                                  "mysqlVersion": db.root_sql("SELECT VERSION()")},
                  "applicationPid": business.pid, "mysqlPid": db.process.pid, "stages": []}
        clients = [Client(port, index, tokens, articles) for index in range(100)]
        sampler = threading.Thread(target=sample_resources,
                                   args=([business,db.process],stop_sampling,destination/"resources.jsonl"),daemon=True)
        sampler.start()
        with (destination/"requests.jtl").open("w",encoding="utf-8",newline="") as samples:
            writer = csv.writer(samples)
            writer.writerow(("timeStamp","elapsed","label","responseCode","responseMessage","threadName","success","stage"))
            for label, concurrency, duration, ramp in PHASES:
                print(f"Starting {label} ({concurrency} users)", flush=True)
                stage = phase_load(label,concurrency,3 if args.smoke else duration,ramp,clients,writer)
                samples.flush()
                report["stages"].append(stage)
                (destination/"summary.json").write_text(json.dumps(report,indent=2),"utf-8")
                print(json.dumps(stage),flush=True)
        report["after"] = counters(db)
        (destination/"summary.json").write_text(json.dumps(report,indent=2),"utf-8")
    finally:
        for client in clients:
            client.connection.close()
        stop_sampling.set()
        if sampler is not None:
            sampler.join(timeout=12)
        stop(business)
        db.close()


if __name__ == "__main__":
    main()
