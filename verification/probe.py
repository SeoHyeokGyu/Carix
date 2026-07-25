"""원천 API 호출 → 원문 저장 + 필드 경로 목록 출력. 일회성 측정 도구."""
import argparse
import json
import os
import pathlib
import sys
import urllib.parse
import urllib.request

OUT_DIR = pathlib.Path(__file__).parent / "out"


def load_config(source: str) -> dict:
    config_path = pathlib.Path(__file__).parent / "config.json"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    if source not in config:
        sys.exit(f"config.json에 원천 없음: {source}")
    return config[source]


def fetch(base_url: str, params: dict) -> dict:
    url = f"{base_url}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = resp.read().decode("utf-8")
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return {"_raw_text": body[:5000]}


def field_inventory(node, prefix="", out=None) -> dict:
    if out is None:
        out = {}
    if isinstance(node, dict):
        for key, value in node.items():
            path = f"{prefix}.{key}" if prefix else key
            out.setdefault(path, type(value).__name__)
            field_inventory(value, path, out)
    elif isinstance(node, list) and node:
        field_inventory(node[0], f"{prefix}[]", out)
    return out


def next_index(source: str) -> int:
    return len(list(OUT_DIR.glob(f"{source}_raw_*.json"))) + 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("--params", default="{}", help="쿼리 파라미터 JSON")
    parser.add_argument("--key-param", default=None,
                        help="API 키를 넣을 쿼리 파라미터 이름")
    args = parser.parse_args()

    conf = load_config(args.source)
    params = json.loads(args.params)
    if args.key_param:
        key = os.environ.get(conf["key_env"])
        if not key:
            sys.exit(f"환경변수 없음: {conf['key_env']}")
        params[args.key_param] = key

    OUT_DIR.mkdir(exist_ok=True)
    data = fetch(conf["base_url"], params)

    idx = next_index(args.source)
    raw_path = OUT_DIR / f"{args.source}_raw_{idx}.json"
    raw_path.write_text(json.dumps(data, ensure_ascii=False, indent=2),
                        encoding="utf-8")

    fields = field_inventory(data)
    fields_path = OUT_DIR / f"{args.source}_fields.json"
    fields_path.write_text(json.dumps(fields, ensure_ascii=False, indent=2),
                           encoding="utf-8")

    print(f"raw    -> {raw_path}")
    print(f"fields -> {fields_path}")
    for path, type_name in sorted(fields.items()):
        print(f"  {path}: {type_name}")


if __name__ == "__main__":
    main()
