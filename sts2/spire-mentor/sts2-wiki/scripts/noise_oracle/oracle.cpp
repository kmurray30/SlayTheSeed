// Oracle harness for `scripts/godot_noise.py`, task 9 of the workbench VFX
// sub-project (see `.superpowers/sdd/2026-08-29-workbench-vfx-creature-events/
// task-9-brief.md`).
//
// There is no Godot binary available to capture reference pixels the way the
// brief originally proposed. This harness is a stronger oracle anyway: it is
// the SAME source Godot 4.5.1 vendors at `thirdparty/noise/FastNoiseLite.h`
// (`FastNoiseLite.h` alongside this file, upstream Auburn/FastNoiseLite
// v1.1.1, MIT licensed, byte-identical to the copy fetched for this task),
// driven with the exact settings `modules/noise/fastnoise_lite.h` (Godot's
// wrapper) uses by default, plus the frequency override
// `vfx_monster_death.tscn`'s two `FastNoiseLite` sub-resources apply:
//
//   NoiseType_OpenSimplex2S (Godot's TYPE_SIMPLEX_SMOOTH), FractalType_FBm,
//   5 octaves, lacunarity 2.0, gain 0.5, seed 0, frequency from argv.
//
// It also reproduces `Noise::_get_image`'s normalization exactly
// (`modules/noise/noise.cpp:81-163`, `p_normalize=true` branch): scan the
// WHOLE image for min/max, then map each sample to `(v - min) / (max - min)`
// — a per-image rescale, not a fixed [-1,1] -> [0,1] map.
//
// Usage:
//   g++ -O2 -std=c++17 -o oracle oracle.cpp
//   ./oracle <width> <height> <frequency> [seed=0]
//
// Prints one line per pixel: "x y raw normalized", where `raw` is
// `GetNoise(x, y)` (bounded roughly [-1, 1]) and `normalized` is the
// pre-quantization `(raw - min) / (max - min)` in [0, 1] — the exact value
// `fast_noise_lite()` in `godot_noise.py` is pinned against. Pixel order is
// row-major (y outer, x inner), matching `Noise::_get_image`'s loop nesting.
//
// Reference invocation used to capture the numbers pinned in
// `test_godot_noise.py`:
//   g++ -O2 -std=c++17 -o /tmp/noise_oracle oracle.cpp
//   /tmp/noise_oracle 8 8 0.0234 0      # test_matches_godots_simplex_smooth_at_known_points
//   /tmp/noise_oracle 64 64 0.0084 0    # test_output_is_normalised_to_unit_range

#include "FastNoiseLite.h"

#include <cstdio>
#include <cstdlib>
#include <limits>
#include <vector>

int main(int argc, char **argv) {
    if (argc < 4) {
        fprintf(stderr, "usage: %s <width> <height> <frequency> [seed=0]\n", argv[0]);
        return 1;
    }
    int width = atoi(argv[1]);
    int height = atoi(argv[2]);
    float frequency = static_cast<float>(atof(argv[3]));
    int seed = argc > 4 ? atoi(argv[4]) : 0;

    FastNoiseLite noise(seed);
    noise.SetSeed(seed);
    noise.SetNoiseType(FastNoiseLite::NoiseType_OpenSimplex2S);
    noise.SetFractalType(FastNoiseLite::FractalType_FBm);
    noise.SetFractalOctaves(5);
    noise.SetFractalLacunarity(2.0f);
    noise.SetFractalGain(0.5f);
    noise.SetFrequency(frequency);

    std::vector<float> values(static_cast<size_t>(width) * height);
    float min_val = std::numeric_limits<float>::max();
    float max_val = -std::numeric_limits<float>::max();

    int idx = 0;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float v = noise.GetNoise(static_cast<float>(x), static_cast<float>(y));
            values[idx] = v;
            if (v > max_val) max_val = v;
            if (v < min_val) min_val = v;
            idx++;
        }
    }

    idx = 0;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float v = values[idx++];
            float norm = (max_val == min_val) ? 0.0f : (v - min_val) / (max_val - min_val);
            printf("%d %d %.9g %.9g\n", x, y, v, norm);
        }
    }
    return 0;
}
