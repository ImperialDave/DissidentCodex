#!/usr/bin/env python3
"""Push codex-web source files to GitHub using batch JSON payloads for MCP push_files."""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "codex-web")
REMOTE_PATHS = None  # set after fetching tree


def local_files():
    paths = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in {"node_modules", ".next"}]
        for name in sorted(filenames):
            if name == ".env.local":
                continue
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, ROOT).replace("\\", "/")
            paths.append(rel)
    return sorted(paths)


def load_file(rel: str) -> dict:
    full = os.path.join(ROOT, rel)
    if rel.endswith(".ico"):
        import base64
        with open(full, "rb") as f:
            return {"path": rel, "content": base64.b64encode(f.read()).decode("ascii"), "encoding": "base64"}
    with open(full, "r", encoding="utf-8", errors="replace") as f:
        return {"path": rel, "content": f.read()}


def batches(paths, max_bytes=85000):
    items = [load_file(p) for p in paths]
    out, cur, size = [], [], 0
    for item in items:
        b = len(item["content"].encode("utf-8"))
        if cur and size + b > max_bytes:
            out.append(cur)
            cur, size = [], 0
        cur.append({"path": item["path"], "content": item["content"]})
        size += b
    if cur:
        out.append(cur)
    return out


def main():
    if len(sys.argv) < 2:
        print("usage: push-to-github.py <batch-index|all|list-missing>")
        sys.exit(1)

    cmd = sys.argv[1]
    all_paths = local_files()

    if cmd == "list-missing":
        remote_file = sys.argv[2] if len(sys.argv) > 2 else "/tmp/github-remote-paths.json"
        if os.path.exists(remote_file):
            remote = set(json.load(open(remote_file)))
        else:
            remote = set()
        missing = [p for p in all_paths if p not in remote]
        print(json.dumps(missing, indent=2))
        return

    if cmd == "all":
        paths = all_paths
    elif cmd == "missing":
        remote_file = sys.argv[2] if len(sys.argv) > 2 else "/tmp/github-remote-paths.json"
        remote = set(json.load(open(remote_file))) if os.path.exists(remote_file) else set()
        paths = [p for p in all_paths if p not in remote]
    else:
        remote_file = sys.argv[2] if len(sys.argv) > 2 else "/tmp/github-remote-paths.json"
        remote = set(json.load(open(remote_file))) if os.path.exists(remote_file) else set()
        paths = [p for p in all_paths if p not in remote]
        b = batches(paths)
        idx = int(cmd)
        json.dump(b[idx], open(f"/tmp/github-push-batch-{idx}.json", "w"), ensure_ascii=False)
        print(f"/tmp/github-push-batch-{idx}.json ({len(b[idx])} files)")
        return

    b = batches(paths)
    for i, batch in enumerate(b):
        out = f"/tmp/github-push-batch-{i}.json"
        json.dump(batch, open(out, "w"), ensure_ascii=False)
        print(f"{out}: {len(batch)} files")


if __name__ == "__main__":
    main()