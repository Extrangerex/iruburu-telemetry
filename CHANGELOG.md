# Changelog

## 1.0.3-forge-1.20.1

- Removed position data from periodic `inventory_snapshot` telemetry to avoid unnecessary database growth.
- Kept position data on important player events such as `player_death`, `player_damaged`, `dimension_changed`, `player_connected`, `player_disconnected`, and `advancement_completed`.
