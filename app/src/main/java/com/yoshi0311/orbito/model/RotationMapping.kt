package com.yoshi0311.orbito.model

// src → dst : 각 셀의 공이 어디로 이동하는지 (반시계 1칸)
val ROTATION_MAPPING: Map<Pair<Int, Int>, Pair<Int, Int>> = mapOf(
    // 바깥 궤도 (12칸)
    Pair(0,0) to Pair(1,0), Pair(0,1) to Pair(0,0), Pair(0,2) to Pair(0,1),
    Pair(0,3) to Pair(0,2), Pair(1,3) to Pair(0,3), Pair(2,3) to Pair(1,3),
    Pair(3,3) to Pair(2,3), Pair(3,2) to Pair(3,3), Pair(3,1) to Pair(3,2),
    Pair(3,0) to Pair(3,1), Pair(2,0) to Pair(3,0), Pair(1,0) to Pair(2,0),
    // 안쪽 궤도 (4칸)
    Pair(1,1) to Pair(2,1), Pair(1,2) to Pair(1,1),
    Pair(2,2) to Pair(1,2), Pair(2,1) to Pair(2,2)
)
