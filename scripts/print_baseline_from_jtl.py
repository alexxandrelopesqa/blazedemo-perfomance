#!/usr/bin/env python3
"""
Agrega métricas dos JTL com a mesma lógica que jtl_to_allure.py (p90 sobre elapsed).
Uso (na raiz do repo): python scripts/print_baseline_from_jtl.py
"""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

_REPO = Path(__file__).resolve().parents[1]
if str(_REPO / "scripts") not in sys.path:
    sys.path.insert(0, str(_REPO / "scripts"))

import jtl_to_allure as jta  # noqa: E402


def load_rows(jtl_path: Path) -> list:
    with jtl_path.open("r", encoding="utf-8", newline="") as fp:
        return list(csv.DictReader(fp))


def main() -> None:
    load_path = _REPO / "results" / "load" / "load.jtl"
    peak_path = _REPO / "results" / "peak" / "peak.jtl"
    json_out = "--json" in sys.argv

    out: list[dict] = []
    for label, path in (("Load 250 RPS", load_path), ("Peak 350 RPS", peak_path)):
        if not path.exists():
            print(f"Ficheiro em falta: {path}", file=sys.stderr)
            sys.exit(1)
        rows = load_rows(path)
        m = jta.compute_metrics(rows)
        out.append(
            {
                "scenario": label,
                "jtl": path.relative_to(_REPO).as_posix(),
                "samples": m["total"],
                "failed": m["failed"],
                "error_pct": round(m["error_pct"], 4),
                "avg_ms": round(m["avg_ms"], 2),
                "avg_latency_ms": round(m["avg_latency_ms"], 2),
                "p90_ms": round(m["p90_ms"], 2),
                "rps": round(m["rps"], 2),
            }
        )

    if json_out:
        print(json.dumps(out, indent=2, ensure_ascii=True))
        return

    print("Métricas (mesma base que jtl_to_allure.py / elapsed p90):\n")
    for row in out:
        print(f"**{row['scenario']}** (`{row['jtl']}`)")
        print(
            f"- throughput: **{row['rps']} RPS**, p90: **{row['p90_ms']} ms**, "
            f"falhas: **{row['failed']} / {row['samples']}**, "
            f"latência média (elapsed): **{row['avg_ms']} ms**"
        )
        print()


if __name__ == "__main__":
    main()
