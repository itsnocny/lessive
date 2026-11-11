package dev.nocny.lessive.data

import androidx.annotation.StringRes
import dev.nocny.lessive.R

/** A code shown on the machine's display, with what to check before calling for repair. */
class Fault(val code: String, @param:StringRes val title: Int, val tips: List<Int>)

private val restartWash = listOf(R.string.tip_restart_wash)

val Faults = listOf(
    Fault("E01", R.string.fault_door_lock, listOf(R.string.tip_door_closed, R.string.tip_door_light)),
    Fault(
        "E02", R.string.fault_water_inlet,
        listOf(R.string.tip_tap_open, R.string.tip_inlet_hose, R.string.tip_inlet_filter, R.string.tip_drain_height),
    ),
    Fault(
        "E03", R.string.fault_drain,
        listOf(R.string.tip_drain_filter, R.string.tip_drain_hose, R.string.tip_drain_height, R.string.tip_wall_drain),
    ),
    Fault("E04", R.string.fault_water_level, listOf(R.string.tip_foam, R.string.tip_keeps_filling)),
    Fault("E05", R.string.fault_electronics, listOf(R.string.tip_hot_wash)),
    Fault("E06", R.string.fault_unbalanced, listOf(R.string.tip_rebalance)),
    Fault("E07", R.string.fault_door_release, listOf(R.string.tip_wait_unlock)),
    Fault("E08", R.string.fault_drum, listOf(R.string.tip_stuck_item, R.string.tip_spread_restart)),
    Fault("E09", R.string.fault_electronics, restartWash),
    Fault("E11", R.string.fault_electronics, listOf(R.string.tip_restart_dry)),
    Fault("E12", R.string.fault_drying, listOf(R.string.tip_restart_dry)),
    Fault("E13", R.string.fault_electronics, restartWash),
    Fault("E15", R.string.fault_electronics, restartWash),
    Fault("E16", R.string.fault_heating, restartWash),
    Fault("E17", R.string.fault_electronics, restartWash),
)
