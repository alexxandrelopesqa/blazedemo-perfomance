#!/usr/bin/env python3
import csv
import json
import os
import sys
import uuid
from collections import Counter, defaultdict
from pathlib import Path

TARGET_URL = os.getenv("TARGET_URL", "https://www.blazedemo.com")
ACCEPTANCE_RPS = float(os.getenv("ACCEPTANCE_RPS", "250"))
ACCEPTANCE_P90_MS = int(float(os.getenv("ACCEPTANCE_P90_MS", "2000")))
TOP_FAILED_SAMPLES = int(float(os.getenv("ALLURE_FAILED_SAMPLES", "5")))


def safe_int(value: str, default: int = 0) -> int:
    try:
        return int(float(value))
    except Exception:
        return default


def percentile(values, pct):
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = max(0, min(int(len(ordered) * pct) - 1, len(ordered) - 1))
    return float(ordered[idx])


def compute_metrics(rows):
    if not rows:
        return {
            "total": 0,
            "failed": 0,
            "error_pct": 0.0,
            "avg_ms": 0.0,
            "avg_latency_ms": 0.0,
            "p90_ms": 0.0,
            "rps": 0.0,
            "min_ts": 0,
            "max_ts": 0,
        }

    elapsed = [safe_int(r.get("elapsed", "0")) for r in rows]
    latency = [safe_int(r.get("Latency", "0")) for r in rows]
    timestamps = [safe_int(r.get("timeStamp", "0")) for r in rows]
    failures = [r for r in rows if str(r.get("success", "")).lower() != "true"]

    min_ts = min(timestamps)
    max_ts = max(timestamps)
    total_seconds = max((max_ts - min_ts) / 1000.0, 1.0)
    total = len(rows)
    failed = len(failures)

    return {
        "total": total,
        "failed": failed,
        "error_pct": (failed / total) * 100 if total else 0.0,
        "avg_ms": sum(elapsed) / total if total else 0.0,
        "avg_latency_ms": sum(latency) / total if total else 0.0,
        "p90_ms": percentile(elapsed, 0.9),
        "rps": total / total_seconds if total_seconds else 0.0,
        "min_ts": min_ts,
        "max_ts": max_ts,
    }


def group_rows_by_label(rows):
    grouped = defaultdict(list)
    for row in rows:
        grouped[row.get("label", "unknown")] += [row]
    return grouped


def build_status(metrics):
    status = "passed"
    status_details = {"message": "Execucao concluida dentro do SLO esperado."}

    if metrics["failed"] > 0:
        status = "failed"
        status_details = {"message": f"Foram detectadas falhas funcionais: {metrics['failed']} amostras com success=false."}
    elif metrics["p90_ms"] >= ACCEPTANCE_P90_MS or metrics["rps"] < ACCEPTANCE_RPS:
        status = "failed"
        status_details = {
            "message": (
                f"SLO nao atendido. rps={metrics['rps']:.2f} (meta >= {ACCEPTANCE_RPS:.0f}), "
                f"p90={metrics['p90_ms']:.2f}ms (meta < {ACCEPTANCE_P90_MS}ms)."
            )
        }

    return status, status_details


def write_attachment(output_dir: Path, name: str, content: str, mime_type: str):
    suffix = ".txt"
    if mime_type == "application/json":
        suffix = ".json"
    source = f"{uuid.uuid4()}-attachment{suffix}"
    (output_dir / source).write_text(content, encoding="utf-8")
    return {"name": name, "source": source, "type": mime_type}


def write_allure_result(output_dir: Path, result: dict):
    result_file = output_dir / f"{uuid.uuid4()}-result.json"
    result_file.write_text(json.dumps(result, ensure_ascii=True, indent=2), encoding="utf-8")


def build_common_labels(test_name):
    return [
        {"name": "framework", "value": "jmeter"},
        {"name": "language", "value": "groovy"},
        {"name": "suite", "value": "BlazeDemo"},
        {"name": "parentSuite", "value": "Performance"},
        {"name": "epic", "value": "BlazeDemo"},
        {"name": "feature", "value": "Checkout"},
        {"name": "story", "value": test_name},
    ]


def write_test_summary_case(output_dir: Path, test_name: str, source_file: str, metrics: dict, rows):
    status, status_details = build_status(metrics)
    labels = build_common_labels(test_name)
    labels += [{"name": "severity", "value": "critical" if status == "failed" else "normal"}]

    failed_rows = [r for r in rows if str(r.get("success", "")).lower() != "true"][:TOP_FAILED_SAMPLES]
    sample_errors = [
        {
            "label": r.get("label", "unknown"),
            "responseCode": r.get("responseCode", ""),
            "responseMessage": r.get("responseMessage", ""),
            "elapsed": safe_int(r.get("elapsed", "0")),
            "url": r.get("URL", ""),
        }
        for r in failed_rows
    ]

    attachments = [
        write_attachment(
            output_dir,
            "metrics-summary",
            json.dumps(
                {
                    "test_name": test_name,
                    "source_file": source_file,
                    "target_url": TARGET_URL,
                    "total_samples": metrics["total"],
                    "failed_samples": metrics["failed"],
                    "error_pct": round(metrics["error_pct"], 4),
                    "avg_ms": round(metrics["avg_ms"], 2),
                    "avg_latency_ms": round(metrics["avg_latency_ms"], 2),
                    "p90_ms": round(metrics["p90_ms"], 2),
                    "rps": round(metrics["rps"], 2),
                    "acceptance_rps": ACCEPTANCE_RPS,
                    "acceptance_p90_ms": ACCEPTANCE_P90_MS,
                },
                ensure_ascii=True,
                indent=2,
            ),
            "application/json",
        ),
        write_attachment(
            output_dir,
            "failed-sample-extract",
            json.dumps(sample_errors, ensure_ascii=True, indent=2),
            "application/json",
        ),
    ]

    result = {
        "uuid": str(uuid.uuid4()),
        "name": test_name,
        "fullName": f"BlazeDemo::{test_name}",
        "historyId": test_name.replace(" ", "_").lower(),
        "status": status,
        "statusDetails": status_details,
        "stage": "finished",
        "start": metrics["min_ts"],
        "stop": metrics["max_ts"],
        "labels": labels,
        "parameters": [
            {"name": "target_url", "value": TARGET_URL},
            {"name": "source_file", "value": source_file},
            {"name": "total_samples", "value": str(metrics["total"])},
            {"name": "failed_samples", "value": str(metrics["failed"])},
            {"name": "error_pct", "value": f"{metrics['error_pct']:.4f}"},
            {"name": "avg_ms", "value": f"{metrics['avg_ms']:.2f}"},
            {"name": "avg_latency_ms", "value": f"{metrics['avg_latency_ms']:.2f}"},
            {"name": "p90_ms", "value": f"{metrics['p90_ms']:.2f}"},
            {"name": "rps", "value": f"{metrics['rps']:.2f}"},
            {"name": "acceptance_rps", "value": f"{ACCEPTANCE_RPS:.0f}"},
            {"name": "acceptance_p90_ms", "value": str(ACCEPTANCE_P90_MS)},
        ],
        "attachments": attachments,
    }
    write_allure_result(output_dir, result)


def write_label_cases(output_dir: Path, test_name: str, grouped_rows):
    for label, rows in grouped_rows.items():
        metrics = compute_metrics(rows)
        status, status_details = build_status(metrics)
        response_codes = Counter([r.get("responseCode", "unknown") for r in rows])
        errors_by_message = Counter(
            [r.get("responseMessage", "unknown") for r in rows if str(r.get("success", "")).lower() != "true"]
        )

        labels = build_common_labels(test_name)
        labels += [
            {"name": "subSuite", "value": "Samplers"},
            {"name": "story", "value": label},
            {"name": "severity", "value": "normal"},
        ]

        detail_payload = {
            "test_name": test_name,
            "sampler_label": label,
            "samples": metrics["total"],
            "failed": metrics["failed"],
            "error_pct": round(metrics["error_pct"], 4),
            "rps": round(metrics["rps"], 2),
            "avg_ms": round(metrics["avg_ms"], 2),
            "avg_latency_ms": round(metrics["avg_latency_ms"], 2),
            "p90_ms": round(metrics["p90_ms"], 2),
            "response_codes": response_codes,
            "top_error_messages": errors_by_message.most_common(5),
        }

        attachments = [
            write_attachment(output_dir, "sampler-metrics", json.dumps(detail_payload, ensure_ascii=True, indent=2), "application/json")
        ]

        result = {
            "uuid": str(uuid.uuid4()),
            "name": f"{test_name} :: {label}",
            "fullName": f"BlazeDemo::{test_name}::{label}",
            "historyId": f"{test_name}-{label}".replace(" ", "_").lower(),
            "status": status,
            "statusDetails": status_details,
            "stage": "finished",
            "start": metrics["min_ts"],
            "stop": metrics["max_ts"],
            "labels": labels,
            "parameters": [
                {"name": "sampler", "value": label},
                {"name": "samples", "value": str(metrics["total"])},
                {"name": "failed", "value": str(metrics["failed"])},
                {"name": "error_pct", "value": f"{metrics['error_pct']:.4f}"},
                {"name": "p90_ms", "value": f"{metrics['p90_ms']:.2f}"},
                {"name": "rps", "value": f"{metrics['rps']:.2f}"},
            ],
            "attachments": attachments,
        }
        write_allure_result(output_dir, result)


def write_environment_properties(output_dir: Path):
    env_file = output_dir / "environment.properties"
    existing = {}

    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                existing[key.strip()] = value.strip()

    existing.update(
        {
            "target.url": TARGET_URL,
            "acceptance.rps": f"{ACCEPTANCE_RPS:.0f}",
            "acceptance.p90.ms": str(ACCEPTANCE_P90_MS),
            "jmeter.version": os.getenv("JMETER_VERSION", "5.6.3"),
            "python.version": sys.version.split()[0],
            "runner.os": os.getenv("RUNNER_OS", os.name),
            "runner.arch": os.getenv("RUNNER_ARCH", os.getenv("PROCESSOR_ARCHITECTURE", "unknown")),
            "github.repository": os.getenv("GITHUB_REPOSITORY", "local"),
            "github.ref": os.getenv("GITHUB_REF_NAME", "local"),
            "github.sha": os.getenv("GITHUB_SHA", "local"),
        }
    )

    lines = [f"{k}={v}" for k, v in sorted(existing.items())]
    env_file.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_executor_json(output_dir: Path):
    executor = {
        "name": "GitHub Actions" if os.getenv("GITHUB_ACTIONS") == "true" else "Local Execution",
        "type": "github",
        "url": os.getenv("GITHUB_SERVER_URL", ""),
        "buildOrder": safe_int(os.getenv("GITHUB_RUN_NUMBER", "0")),
        "buildName": os.getenv("GITHUB_WORKFLOW", "local-run"),
        "buildUrl": (
            f"{os.getenv('GITHUB_SERVER_URL', '')}/{os.getenv('GITHUB_REPOSITORY', '')}/actions/runs/"
            f"{os.getenv('GITHUB_RUN_ID', '')}"
            if os.getenv("GITHUB_ACTIONS") == "true"
            else ""
        ),
        "reportUrl": "",
        "reportName": "BlazeDemo performance",
    }
    (output_dir / "executor.json").write_text(json.dumps(executor, ensure_ascii=True, indent=2), encoding="utf-8")


def write_categories_json(output_dir: Path):
    categories = [
        {
            "name": "SLO breach (latency/throughput)",
            "matchedStatuses": ["failed"],
            "messageRegex": "SLO nao atendido.*",
        },
        {
            "name": "Functional failure",
            "matchedStatuses": ["failed"],
            "messageRegex": "Foram detectadas falhas funcionais.*",
        },
        {
            "name": "Infrastructure timeout/network",
            "matchedStatuses": ["broken", "failed"],
            "traceRegex": ".*(SocketTimeoutException|Read timed out|ConnectException).*",
        },
    ]
    (output_dir / "categories.json").write_text(json.dumps(categories, ensure_ascii=True, indent=2), encoding="utf-8")


def write_summary_file(output_dir: Path, test_name: str, metrics: dict):
    summary = {
        "test_name": test_name,
        "samples": metrics["total"],
        "failed": metrics["failed"],
        "error_pct": round(metrics["error_pct"], 4),
        "avg_ms": round(metrics["avg_ms"], 2),
        "avg_latency_ms": round(metrics["avg_latency_ms"], 2),
        "p90_ms": round(metrics["p90_ms"], 2),
        "rps": round(metrics["rps"], 2),
        "acceptance_rps": ACCEPTANCE_RPS,
        "acceptance_p90_ms": ACCEPTANCE_P90_MS,
    }
    (output_dir / f"{test_name.replace(' ', '_').lower()}-summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=True))


def main():
    if len(sys.argv) != 4:
        print("Uso: python scripts/jtl_to_allure.py <jtl_input> <allure_results_dir> <test_name>")
        sys.exit(1)

    jtl_input = Path(sys.argv[1])
    allure_results = Path(sys.argv[2])
    test_name = sys.argv[3]

    if not jtl_input.exists():
        print(f"Nao foi possivel encontrar o JTL: {jtl_input}")
        sys.exit(2)

    allure_results.mkdir(parents=True, exist_ok=True)

    with jtl_input.open("r", encoding="utf-8", newline="") as fp:
        reader = csv.DictReader(fp)
        rows = list(reader)

    metrics = compute_metrics(rows)
    grouped = group_rows_by_label(rows)

    write_test_summary_case(allure_results, test_name, str(jtl_input), metrics, rows)
    write_label_cases(allure_results, test_name, grouped)
    write_environment_properties(allure_results)
    write_executor_json(allure_results)
    write_categories_json(allure_results)
    write_summary_file(allure_results, test_name, metrics)


if __name__ == "__main__":
    main()
