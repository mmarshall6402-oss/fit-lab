"""
Bulk-train fit_model.pkl from many full-outfit photos at once, instead of
rating them one at a time through /add-fit in Swagger.

Usage:
    python train_fit_bulk.py path/to/dataset
    python train_fit_bulk.py path/to/dataset.zip

The folder (or the folder the zip extracts to) must contain two
subfolders of full-outfit photos - pictures of someone wearing a
complete fit, e.g. saved from Pinterest or your own camera roll:

    dataset/
        good/
            fit1.jpg
            fit2.jpg
        bad/
            fit3.jpg
            fit4.jpg

This ADDS to whatever's already in fit_training_data.pkl (including
anything rated live via /add-fit) rather than replacing it, then
retrains fit_model.pkl on the combined set - same held-out accuracy
check and backup-before-overwrite behavior as the live endpoint.

Only run this while the server (uvicorn) is stopped, or restart the
server afterward - it caches nothing in memory between requests, but a
request that's already in flight when this script finishes won't see
the update until the next one.
"""
import os
import io
import sys
import json
import pickle
import shutil
import tempfile
import zipfile
import numpy as np
from pathlib import Path
from datetime import datetime
from PIL import Image
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score

FIT_MODEL_FILE = "fit_model.pkl"
FIT_DATA_FILE = "fit_training_data.pkl"
FIT_BACKUP_DIR = "fit_model_backups"
FIT_HISTORY_FILE = "fit_training_history.jsonl"
MAX_BACKUPS = 5
MIN_SAMPLES_FOR_HOLDOUT = 6
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def resolve_dataset_root(path_arg):
    """Returns (folder_to_read_from, temp_dir_to_clean_up_or_None)."""
    p = Path(path_arg)
    if p.is_dir():
        return p, None
    if p.suffix.lower() == ".zip":
        tmp_dir = tempfile.mkdtemp(prefix="fit_bulk_")
        with zipfile.ZipFile(p) as zf:
            zf.extractall(tmp_dir)
        root = Path(tmp_dir)
        # If the zip contains one wrapper folder (e.g. Finder/Windows zip
        # of a folder), step into it so good/bad are found directly.
        entries = [e for e in root.iterdir() if not e.name.startswith("__MACOSX")]
        if len(entries) == 1 and entries[0].is_dir():
            root = entries[0]
        return root, tmp_dir
    raise SystemExit(f"'{path_arg}' is not a folder or a .zip file")


def encode_folder(folder: Path, model):
    embeddings = []
    for f in sorted(folder.iterdir()):
        if f.suffix.lower() not in IMAGE_EXTS:
            continue
        try:
            img = Image.open(f).convert("RGB")
            embeddings.append(model.encode(img))
        except Exception as e:
            print(f"  skipping {f.name}: {e}")
    return embeddings


def main():
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python train_fit_bulk.py <folder-or-zip-with-good/-and-bad/-subfolders>")

    dataset_root, cleanup_dir = resolve_dataset_root(sys.argv[1])
    good_dir = dataset_root / "good"
    bad_dir = dataset_root / "bad"
    if not good_dir.is_dir() or not bad_dir.is_dir():
        raise SystemExit(f"Expected '{dataset_root}' to contain 'good/' and 'bad/' subfolders of full-outfit photos.")

    print("Loading CLIP...")
    model = SentenceTransformer('clip-ViT-B-32')

    print("Encoding good/ ...")
    good_embs = encode_folder(good_dir, model)
    print(f"  {len(good_embs)} good outfits encoded")
    print("Encoding bad/ ...")
    bad_embs = encode_folder(bad_dir, model)
    print(f"  {len(bad_embs)} bad outfits encoded")

    if not good_embs or not bad_embs:
        raise SystemExit(f"Need at least 1 good and 1 bad image - found {len(good_embs)} good, {len(bad_embs)} bad.")

    # Load whatever's already been rated (live via /add-fit, or a previous
    # bulk run) so this ADDS to it instead of clobbering it.
    X_memory, y_memory = [], []
    if os.path.exists(FIT_DATA_FILE):
        with open(FIT_DATA_FILE, "rb") as f:
            X_memory, y_memory = pickle.load(f)

    for emb in good_embs:
        X_memory.append(np.concatenate([emb, emb, emb, emb]))
        y_memory.append(1)
    for emb in bad_embs:
        X_memory.append(np.concatenate([emb, emb, emb, emb]))
        y_memory.append(0)

    n_good = sum(y_memory)
    n_bad = len(y_memory) - n_good
    print(f"Total dataset after this import: {len(y_memory)} outfits ({n_good} good / {n_bad} bad)")

    X = np.array(X_memory)
    y = np.array(y_memory)

    holdout_note = ""
    if len(y) >= MIN_SAMPLES_FOR_HOLDOUT:
        try:
            X_train, X_test, y_train, y_test = train_test_split(
                X, y, test_size=0.2, stratify=y, random_state=42
            )
            probe = LogisticRegression()
            probe.fit(X_train, y_train)
            acc = accuracy_score(y_test, probe.predict(X_test))
            with open(FIT_HISTORY_FILE, "a") as f:
                f.write(json.dumps({
                    "timestamp": datetime.now().isoformat(),
                    "n_samples": len(y),
                    "holdout_accuracy": acc,
                    "source": "bulk_import",
                }) + "\n")
            holdout_note = f" Held-out accuracy: {acc:.0%} on {len(y_test)} samples."
        except ValueError:
            holdout_note = " (couldn't compute held-out accuracy - need more examples of both classes)"

    if os.path.exists(FIT_MODEL_FILE):
        os.makedirs(FIT_BACKUP_DIR, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
        shutil.copy2(FIT_MODEL_FILE, os.path.join(FIT_BACKUP_DIR, f"fit_model_{stamp}.pkl"))
        backups = sorted(os.listdir(FIT_BACKUP_DIR))
        for old in backups[:-MAX_BACKUPS]:
            os.remove(os.path.join(FIT_BACKUP_DIR, old))

    clf = LogisticRegression()
    clf.fit(X, y)
    with open(FIT_MODEL_FILE, "wb") as f:
        pickle.dump(clf, f)
    with open(FIT_DATA_FILE, "wb") as f:
        pickle.dump((X_memory, y_memory), f)

    print(f"Done! fit_model.pkl retrained on {len(y_memory)} total outfits.{holdout_note}")

    if cleanup_dir:
        shutil.rmtree(cleanup_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
