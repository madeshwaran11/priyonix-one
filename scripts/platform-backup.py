#!/usr/bin/env python3
"""BSON snapshot backup and verified restore into a new, differently named database.

Requires pymongo. Connection strings are read from environment variables and never printed.
Stop application writers during a backup/restore drill so collection/index definitions stay stable.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
from datetime import datetime, timezone

from bson import BSON, decode_file_iter, json_util
from pymongo import MongoClient, IndexModel


def database_name(value):
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,63}", value) or value in {"admin", "local", "config"}:
        raise ValueError("Choose a non-system database name with letters, numbers, underscores or hyphens")
    return value


def client_from_env(variable):
    uri = os.environ.get(variable)
    if not uri:
        raise ValueError(f"Set the {variable} environment variable to a MongoDB connection string")
    client = MongoClient(uri, serverSelectionTimeoutMS=10000, appname="priyonix-backup")
    client.admin.command("ping")
    return client


def digest_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def backup(args):
    if not args.writers_stopped:
        raise ValueError("Stop application writers and pass --writers-stopped for a consistent recovery drill")
    target = Path(args.output).resolve()
    if target.exists():
        raise ValueError("Backup output must be a new directory; existing files are never overwritten")
    with client_from_env(args.uri_env) as client:
        db = client[database_name(args.database)]
        definitions = sorted((d for d in db.list_collections() if not d["name"].startswith("system.")), key=lambda d: d["name"])
        if any(d.get("type") != "collection" for d in definitions):
            raise ValueError("This application backup format supports collections, not views")
        target.mkdir(parents=True)
        manifest = {"format": 1, "sourceDatabase": args.database,
                    "createdAt": datetime.now(timezone.utc).isoformat(), "collections": []}
        # MongoDB snapshot sessions use one cluster timestamp across all collection reads.
        with client.start_session(snapshot=True) as session:
            for definition in definitions:
                name = definition["name"]
                filename = hashlib.sha256(name.encode()).hexdigest() + ".bson"
                path = target / filename
                count = 0
                with path.open("xb") as output:
                    for doc in db[name].find({}, session=session).sort("_id", 1):
                        output.write(BSON.encode(doc))
                        count += 1
                manifest["collections"].append({"name": name, "file": filename,
                    "count": count, "sha256": digest_file(path), "options": definition.get("options", {}),
                    "indexes": list(db[name].list_indexes())})
        (target / "manifest.json").write_text(json_util.dumps(manifest, indent=2), encoding="utf-8")
        print(json.dumps({"status": "BACKUP_COMPLETE", "database": args.database,
                          "collections": len(manifest["collections"]),
                          "documents": sum(c["count"] for c in manifest["collections"]), "output": str(target)}))


def load_manifest(folder):
    root = Path(folder).resolve()
    manifest = json_util.loads((root / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != 1:
        raise ValueError("Unsupported backup format")
    names = set()
    for entry in manifest["collections"]:
        name = entry["name"]
        if name in names or name.startswith("system.") or "$" in name or "\0" in name:
            raise ValueError("Backup contains an invalid or duplicate collection")
        names.add(name)
        filename = entry["file"]
        if not re.fullmatch(r"[a-f0-9]{64}\.bson", filename):
            raise ValueError("Backup contains an unsafe file path")
        path = (root / filename).resolve()
        if path.parent != root or path.is_symlink() or digest_file(path) != entry["sha256"]:
            raise ValueError("Backup file checksum failed; nothing has been restored")
        with path.open("rb") as stream:
            if sum(1 for _ in decode_file_iter(stream)) != entry["count"]:
                raise ValueError("Backup document count failed; nothing has been restored")
    return root, manifest


def normalized_index(index):
    # Compound index key order is meaningful; server metadata and the obsolete background
    # build flag are not. Compare uniqueness, TTL, sparse/partial filters and collation too.
    return {"key": list(index["key"].items()),
            **{k: v for k, v in index.items() if k not in {"key", "v", "ns", "background"}}}


def verify_database(db, manifest):
    checks = []
    for entry in manifest["collections"]:
        digest, count = hashlib.sha256(), 0
        for doc in db[entry["name"]].find({}).sort("_id", 1):
            digest.update(BSON.encode(doc))
            count += 1
        if count != entry["count"] or digest.hexdigest() != entry["sha256"]:
            raise ValueError("Restored document verification failed; retain the destination for investigation")
        expected_indexes = {i["name"]: normalized_index(i) for i in entry["indexes"]}
        actual_indexes = {i["name"]: normalized_index(i) for i in db[entry["name"]].list_indexes()}
        if expected_indexes != actual_indexes:
            raise ValueError("Restored index verification failed")
        if db[entry["name"]].options() != entry.get("options", {}):
            raise ValueError("Restored collection options verification failed")
        checks.append({"collection": entry["name"], "count": count, "sha256Verified": True,
                       "indexesVerified": True, "collectionOptionsVerified": True})
    return checks


def restore(args):
    root, manifest = load_manifest(args.input)
    destination = database_name(args.database)
    if destination == manifest["sourceDatabase"]:
        raise ValueError("Restore destination must have a different name from the source database")
    report = Path(args.report).resolve() if args.report else None
    if report is not None:
        if report.exists():
            raise ValueError("Verification report already exists; choose a new report filename")
        report.parent.mkdir(parents=True, exist_ok=True)
    with client_from_env(args.uri_env) as client:
        db = client[destination]
        if db.list_collection_names():
            raise ValueError("Restore destination is not empty; use a new database name")
        for entry in manifest["collections"]:
            collection = db.create_collection(entry["name"], **entry.get("options", {}))
            batch = []
            with (root / entry["file"]).open("rb") as stream:
                for doc in decode_file_iter(stream):
                    batch.append(doc)
                    if len(batch) >= 100:
                        collection.insert_many(batch, ordered=True)
                        batch = []
            if batch:
                collection.insert_many(batch, ordered=True)
            indexes = []
            for index in entry["indexes"]:
                if index["name"] == "_id_":
                    continue
                options = {k: v for k, v in index.items() if k not in {"key", "v", "ns"}}
                indexes.append(IndexModel(list(index["key"].items()), **options))
            if indexes:
                collection.create_indexes(indexes)
        checks = verify_database(db, manifest)
        result = {"status": "RESTORE_VERIFIED", "sourceDatabase": manifest["sourceDatabase"],
                  "destinationDatabase": destination, "collections": checks,
                  "documents": sum(c["count"] for c in checks)}
        if report is not None:
            with report.open("x", encoding="utf-8") as output:
                json.dump(result, output, indent=2)
        print(json.dumps(result))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    save = commands.add_parser("backup")
    save.add_argument("--uri-env", default="MONGODB_URI")
    save.add_argument("--database", required=True)
    save.add_argument("--output", required=True)
    save.add_argument("--writers-stopped", action="store_true")
    recover = commands.add_parser("restore")
    recover.add_argument("--uri-env", default="MONGODB_RESTORE_URI")
    recover.add_argument("--database", required=True)
    recover.add_argument("--input", required=True)
    recover.add_argument("--report")
    args = parser.parse_args()
    try:
        (backup if args.command == "backup" else restore)(args)
    except ValueError as error:
        parser.exit(2, f"Validation failed: {error}\n")
    except Exception as error:
        # Driver exceptions can embed connection/document details. Never echo them to logs.
        parser.exit(1, f"Operation failed ({type(error).__name__}); no connection or document details were logged.\n")


if __name__ == "__main__":
    main()
