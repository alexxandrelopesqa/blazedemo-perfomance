#!/usr/bin/env python3
import csv
import json
import sys
import uuid
from pathlib import Path


def safe_int(value: str, default: int = 0) -> int:
    try:
        return int(float(value))
    except Exception:
        return default


def compute_metrics(rows):
    if not rows:
        return {
            "total": 0,
            "failed": 0,
            "avg_ms": 0.0,
            "p90_ms": 0.0,
            "rps": 0.0,
            "min_ts": 0,
            "max_ts": 0,
        }

    elapsed = [safe_int(r.get("elapsed", "0")) for r in rows]
    timestamps = [safe_int(r.get("timeStamp", "0")) for r in rows]
    failures = [r for r in rows if str(r.get("success", "")).lower() != "true"]
    sorted_elapsed = sorted(elapsed)

    p90_index = int(len(sorted_elapsed) * 0.9) - 1
    p90_index = max(0, min(p90_index, len(sorted_elapsed) - 1))
    p90 = sorted_elapsed[p90_index]

    min_ts = min(timestamps)
    max_ts = max(timestamps)
    total_seconds = max((max_ts - min_ts) / 1000.0, 1.0)
    rps = len(rows) / total_seconds

    return {
        "total": len(rows),
        "failed": len(failures),
        "avg_ms": sum(elapsed) / len(elapsed),
        "p90_ms": p90,
        "rps": rps,
        "min_ts": min_ts,
        "max_ts": max_ts,
    }


def write_allure_result(output_dir: Path, test_name: str, metrics: dict, source_file: str):
    status = "passed"
    status_details = {"message": "Execucao concluida dentro do SLO esperado."}

    if metrics["failed"] > 0:
        status = "failed"
        status_details = {"message": f"Foram detectadas falhas funcionais: {metrics['failed']} amostras com success=false."}
    elif metrics["p90_ms"] >= 2000 or metrics["rps"] < 250:
        status = "failed"
        status_details = {
            "message": (
                f"SLO nao atendido. rps={metrics['rps']:.2f} (meta >= 250), "
                f"p90={metrics['p90_ms']:.2f}ms (meta < 2000ms)."
            )
        }

    result = {
        "uuid": str(uuid.uuid4()),
        "name": test_name,
        "fullName": test_name,
        "historyId": test_name.replace(" ", "_").lower(),
        "status": status,
        "statusDetails": status_details,
        "stage": "finished",
        "start": metrics["min_ts"],
        "stop": metrics["max_ts"],
        "labels": [
            {"name": "framework", "value": "jmeter"},
            {"name": "language", "value": "groovy"},
            {"name": "suite", "value": "BlazeDemo Performance"},
        ],
        "parameters": [
            {"name": "source_file", "value": source_file},
            {"name": "total_samples", "value": str(metrics["total"])},
            {"name": "failed_samples", "value": str(metrics["failed"])},
            {"name": "avg_ms", "value": f"{metrics['avg_ms']:.2f}"},
            {"name": "p90_ms", "value": f"{metrics['p90_ms']:.2f}"},
            {"name": "rps", "value": f"{metrics['rps']:.2f}"},
        ],
    }

    output_file = output_dir / f"{uuid.uuid4()}-result.json"
    output_file.write_text(json.dumps(result, ensure_ascii=True, indent=2), encoding="utf-8")


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
    write_allure_result(allure_results, test_name, metrics, str(jtl_input))

    summary = {
        "test_name": test_name,
        "samples": metrics["total"],
        "failed": metrics["failed"],
        "avg_ms": round(metrics["avg_ms"], 2),
        "p90_ms": round(metrics["p90_ms"], 2),
        "rps": round(metrics["rps"], 2),
    }
    (allure_results / f"{test_name.replace(' ', '_').lower()}-summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=True))


if __name__ == "__main__":
    main()
