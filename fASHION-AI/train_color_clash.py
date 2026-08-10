import numpy as np
import pickle
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression

print("Loading CLIP to train Color Logic...")
model = SentenceTransformer('clip-ViT-B-32')

# Teach the AI colorway dynamics
color_data = [
    # GOOD COMBOS (1)
    {"top": "black graphic tee", "shoes": "Jordan 4 Retro Black Cat monochrome", "label": 1},
    {"top": "cream white designer tee", "shoes": "Travis Scott AJ1 mocha brown", "label": 1},

    # BAD COMBOS / COLOR CLASHES (0)
    {"top": "bright neon orange designer graphic tee", "shoes": "Jordan 4 Retro Toro Bravo fire red", "label": 0},
    {"top": "pink graphic tee", "shoes": "Travis Scott AJ1 green olive colorway", "label": 0}
]

X, y = [], []
for item in color_data:
    t_v = model.encode(item["top"])
    s_v = model.encode(item["shoes"])
    # Zero pad the missing slots (pants and outerwear) to match 2048 dimensions
    X.append(np.concatenate([t_v, np.zeros(512), s_v, np.zeros(512)]))
    y.append(item["label"])

clf = LogisticRegression()
clf.fit(np.array(X), np.array(y))
with open("color_model.pkl", "wb") as f:
    pickle.dump(clf, f)
print("Successfully generated color_model.pkl! Your server will now use this color logic.")
