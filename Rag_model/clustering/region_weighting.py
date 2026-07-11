"""Coordinate-based regional weighting (per spec): map each region to lat/lon, scale to 0-1,
then multiply by REGION_WEIGHT_MULTIPLIER before concatenating onto the message embedding. This
artificially stretches inter-region distance so DenStream naturally separates e.g. a
Mumbai-electricity-scam micro-cluster from a Delhi-electricity-scam one, instead of merging them
just because the message text is near-identical.

Sampling weight is deliberately skewed toward Mumbai/Delhi (per spec: "give more presence to
region... like mumbai, delhi") since these are the highest fraud-report-volume metros; other
regions still get realistic representation.
"""

from config.settings import settings

# (lat, lon, sampling_weight) - weight is relative probability mass for synthetic/seed assignment,
# NOT used at inference time (real messages carry their own region from user_map / app install location).
REGIONS: dict[str, tuple[float, float, float]] = {
    "Mumbai":      (19.0760, 72.8777, 0.18),
    "Delhi":       (28.7041, 77.1025, 0.16),
    "Bengaluru":   (12.9716, 77.5946, 0.09),
    "Hyderabad":   (17.3850, 78.4867, 0.08),
    "Chennai":     (13.0827, 80.2707, 0.07),
    "Kolkata":     (22.5726, 88.3639, 0.06),
    "Pune":        (18.5204, 73.8567, 0.06),
    "Ahmedabad":   (23.0225, 72.5714, 0.05),
    "Jaipur":      (26.9124, 75.7873, 0.04),
    "Lucknow":     (26.8467, 80.9462, 0.04),
    "Chandigarh":  (30.7333, 76.7794, 0.03),
    "Bhopal":      (23.2599, 77.4126, 0.03),
    "Patna":       (25.5941, 85.1376, 0.03),
    "Kochi":       (9.9312, 76.2673, 0.03),
    "Guwahati":    (26.1445, 91.7362, 0.02),
    "Bhubaneswar": (20.2961, 85.8245, 0.02),
    "Unknown":     (22.3511, 78.6677, 0.01),  # geographic center of India, low-weight fallback
}

_LAT_MIN, _LAT_MAX = 6.5, 35.5   # India's approximate lat bounding box
_LON_MIN, _LON_MAX = 68.0, 97.5  # India's approximate lon bounding box


def region_names_and_weights() -> tuple[list[str], list[float]]:
    names = list(REGIONS.keys())
    weights = [REGIONS[n][2] for n in names]
    return names, weights


def region_to_scaled_vector(region: str) -> list[float]:
    """Returns the [lat_scaled, lon_scaled] pair for a region, each in [0, 1], pre-weight-multiply."""
    lat, lon, _ = REGIONS.get(region, REGIONS["Unknown"])
    lat_scaled = (lat - _LAT_MIN) / (_LAT_MAX - _LAT_MIN)
    lon_scaled = (lon - _LON_MIN) / (_LON_MAX - _LON_MIN)
    return [lat_scaled, lon_scaled]


def weighted_region_features(region: str) -> list[float]:
    """The two extra dimensions appended to a message's PCA-reduced embedding before DenStream."""
    lat_scaled, lon_scaled = region_to_scaled_vector(region)
    w = settings.region_weight_multiplier
    return [lat_scaled * w, lon_scaled * w]
