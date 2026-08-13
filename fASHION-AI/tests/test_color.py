"""
Tests for the color-matching signal added to replace color_model.pkl
(see main.py's palette_similarity docstring for why the classifier was
dropped) - a single average color collapses "mostly black with a green
accent" into a blended color no real item resembles, so these lock in
that the palette approach actually preserves distinct colors and scores
them sensibly.
"""
import numpy as np
import pytest
from PIL import Image

import main


def test_rgb_similarity_identical_colors_is_one():
    assert main._rgb_similarity([10, 20, 30], [10, 20, 30]) == pytest.approx(1.0)


def test_rgb_similarity_black_vs_white_is_low_but_not_the_floor():
    # In CIE LAB, black-vs-white is delta-E 100 - clearly dissimilar, but
    # not the most extreme distance the space can produce (fully-saturated
    # opposite hues go well beyond that), so this shouldn't clamp to 0.
    sim = main._rgb_similarity([0, 0, 0], [255, 255, 255])
    assert 0.0 < sim < 0.5


def test_rgb_similarity_extreme_hue_opposites_clamp_to_zero():
    # Green vs. blue sits at delta-E ~259, well past LAB_DISTANCE_CEILING -
    # similarity must clamp at 0, not go negative.
    sim = main._rgb_similarity([0, 255, 0], [0, 0, 255])
    assert sim == 0.0


def test_rgb_similarity_is_symmetric():
    a, b = [10, 200, 50], [220, 20, 90]
    assert main._rgb_similarity(a, b) == pytest.approx(main._rgb_similarity(b, a))


def test_color_palette_finds_two_distinct_colors_with_correct_weights():
    # 75% black, 25% green, laid out as clean blocks so k-means has no
    # ambiguity about where the cluster boundary is.
    arr = np.zeros((100, 100, 3), dtype=np.uint8)
    arr[:, :] = [20, 20, 20]
    arr[75:, :] = [30, 140, 40]
    img = Image.fromarray(arr)

    palette = main.color_palette(img, k=3)
    assert len(palette) == 2  # only 2 distinct colors present, even though k=3 was allowed

    weights = sorted((c["weight"] for c in palette), reverse=True)
    assert weights[0] == pytest.approx(0.75, abs=0.01)
    assert weights[1] == pytest.approx(0.25, abs=0.01)


def test_color_palette_excludes_white_background_fill():
    # crop_to_garment fills non-garment pixels with exact pure white -
    # the palette should reflect only the real (red) garment pixels, not
    # get diluted by the white fill surrounding them.
    arr = np.full((100, 100, 3), 255, dtype=np.uint8)
    arr[25:75, 25:75] = [200, 30, 30]
    img = Image.fromarray(arr)

    palette = main.color_palette(img, k=2)
    assert len(palette) == 1
    assert palette[0]["color"] == pytest.approx([200, 30, 30], abs=2)


def test_palette_similarity_rewards_shared_accent_color():
    # A mostly-black top with a green accent should score noticeably
    # closer to a green shoe than a single average-color comparison would
    # (a flat average of 84% black + 16% green is a dark olive blend far
    # from either real color) - this is the actual bug being fixed.
    top_palette = [
        {"color": [20, 20, 20], "weight": 0.84},
        {"color": [30, 140, 40], "weight": 0.16},
    ]
    black_shoe = [{"color": [15, 15, 15], "weight": 1.0}]
    green_shoe = [{"color": [30, 140, 40], "weight": 1.0}]

    sim_black = main.palette_similarity(top_palette, black_shoe)
    sim_green = main.palette_similarity(top_palette, green_shoe)

    # Black still wins (it IS the majority color) but green must be
    # competitive, not crushed the way a flat-average comparison would
    # crush it (a naive RGB average vs the same two shoes scores ~0.94 vs
    # ~0.77 - a much wider gap than palette matching should produce).
    assert sim_black > sim_green
    assert sim_green > 0.7


def test_palette_similarity_is_symmetric():
    a = [{"color": [10, 200, 50], "weight": 0.6}, {"color": [90, 10, 10], "weight": 0.4}]
    b = [{"color": [220, 20, 90], "weight": 1.0}]
    assert main.palette_similarity(a, b) == pytest.approx(main.palette_similarity(b, a))


def test_color_palette_handles_fully_white_garment():
    # A genuinely white garment (not background fill) should still
    # produce a usable palette instead of an empty one.
    img = Image.new("RGB", (50, 50), (255, 255, 255))
    palette = main.color_palette(img)
    assert len(palette) >= 1
    assert palette[0]["weight"] == pytest.approx(1.0)
