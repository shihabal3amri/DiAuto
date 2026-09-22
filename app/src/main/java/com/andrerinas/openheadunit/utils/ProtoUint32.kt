package com.andrerinas.openheadunit.utils

/**
 * Protobuf `uint32` fields are represented as signed [Int] in generated Java code.
 * Values above 2³¹−1 appear negative; use this before arithmetic or comparisons with duration/position.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Int.protoUint32ToLong(): Long = toLong() and 0xFFFFFFFFL
