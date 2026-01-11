package com.punishman.legendmaster

import java.io.Serializable

data class ControlRow(
 //   var orderNum: Int,
    var cpNum: Int = 31,
    var icons: IntArray = IntArray(6) { 0 }
) : Serializable