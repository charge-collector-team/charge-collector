package kr.chargebadge.core

const val MATCH_RADIUS_M = 50.0
const val MIN_SOC_GAIN = 3
const val GOLD_CHANCE = 0.01
const val SYNTH_COST = 10
const val TRADE_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000
const val TREE_SLOTS = 6

// 실제 충전은 1%p씩 오른다. 한 번에 이보다 크게 뛰면 값이 다시 설정된 것(에뮬레이터 재설정 등)으로 본다
const val MAX_SOC_STEP = 3
const val BOOT_JUDGE_WINDOW_MS = 10L * 60 * 1000

fun isSocJump(prev: Int, soc: Int): Boolean = soc - prev > MAX_SOC_STEP

fun bootWindowOpen(serviceStartMs: Long, nowMs: Long): Boolean = nowMs - serviceStartMs <= BOOT_JUDGE_WINDOW_MS
