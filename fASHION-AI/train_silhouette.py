import json
import numpy as np
import pickle
from datetime import datetime
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression

print("Loading CLIP to train Silhouette Logic...")
model = SentenceTransformer('clip-ViT-B-32')

# Teach the AI proportions
silhouette_data = [
    # GOOD SILHOUETTES (1)
    {"top": "oversized boxy heavy vintage streetwear tee", "bottom": "stacked black skinny jeans", "shoes": "Jordan 4 Retro bulky chunky silhouette", "label": 1},
    {"top": "slim fit luxury designer tee shirt", "bottom": "distressed super skinny jeans", "shoes": "AJ1 low top slim profile sneaker", "label": 1},

    # BAD SILHOUETTES / PROPORTION CLASHES (0)
    {"top": "oversized massive boxy punk hoodie", "bottom": "baggy loose track pants", "shoes": "slim low top canvas deck shoes", "label": 0},
    {"top": "tight compression athletic gym shirt", "bottom": "tight skinny jeans", "shoes": "massive oversized triple s chunky dad shoes", "label": 0}
]

X, y = [], []
for item in silhouette_data:
    t_v = model.encode(item["top"])
    p_v = model.encode(item["bottom"])
    s_v = model.encode(item["shoes"])
    X.append(np.concatenate([t_v, p_v, s_v, np.zeros(512)]))
    y.append(item["label"])

clf = LogisticRegression()
clf.fit(np.array(X), np.array(y))
with open("silhouette_model.pkl", "wb") as f:
    pickle.dump(clf, f)

with open("silhouette_model_meta.json", "w") as f:
    json.dump({
        "total_examples": len(y),
        "good": sum(y),
        "bad": len(y) - sum(y),
        "trained_at": datetime.now().isoformat(),
    }, f)

print(f"Successfully generated silhouette_model.pkl from {len(y)} examples! Your server will now use this silhouette proportion logic.")
