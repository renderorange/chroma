package config

import (
	"os"
	"path/filepath"

	"github.com/BurntSushi/toml"
)

type Config struct {
	CC    map[string]int `toml:"cc"`
	Notes map[string]int `toml:"notes"`
}

func DefaultConfig() Config {
	return Config{
		CC: map[string]int{
			"gain":               1,
			"input_freeze_len":   2,
			"filter_amount":      3,
			"filter_cutoff":      4,
			"filter_resonance":   5,
			"granular_density":   6,
			"granular_size":      7,
			"granular_mix":       8,
			"reverb_delay_blend": 9,
			"decay_time":         10,
			"dry_wet":            11,
		},
		Notes: map[string]int{
			"input_freeze":    60,
			"granular_freeze": 62,
			"mode_mirror":     64,
			"mode_complement": 65,
			"mode_transform":  67,
		},
	}
}

func Load() Config {
	cfg := DefaultConfig()

	configDir, err := os.UserConfigDir()
	if err != nil {
		return cfg
	}

	configPath := filepath.Join(configDir, "chroma", "midi.toml")
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		return cfg
	}

	if _, err := toml.DecodeFile(configPath, &cfg); err != nil {
		return DefaultConfig()
	}

	return cfg
}
