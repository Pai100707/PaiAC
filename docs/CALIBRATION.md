# Calibrating crystal / anchor / totem thresholds

1. Keep `enforcement-mode: DETECTION_ONLY` (default).
2. Crystal: set `crystal.macro.baseline-collection: true` in `checks/crystal.yml`, `/paiac reload`. While on, nothing is flagged; every `sample-size` cycles a row goes to table `baselines` (`metric`=profile, `v1`=mean cycle ms, `v2`=stddev ms, `v3`=cycles/s, `ping`).
3. Look at your best legitimate players: pick `competitive_ht1` or edit `custom` so limits sit clearly above them, then turn baseline-collection off.
4. Run with the default dry-run punishments for a few days and read `/paiac vl <player>`, alerts and table `punishments` (`dry`=1 rows).
5. Only then consider `ENFORCE`, starting with ALERT/CANCEL_ACTION/SETBACK; keep `auto-ban-enabled: false` until you trust the numbers.

Timing notes: events are processed on the tick loop (50 ms resolution); lag (TPS < 18, ping jitter > 80 ms) makes the timing checks skip instead of guessing.
