package com.mosscurve.rowing

import android.util.Log


object Calculation {

    //a, b: 꺼짐, A, B: 켜짐
//forward: ab -> Ab -> AB -> aB -> ab
//backward: ab -> aB -> AB -> Ab -> ab
    const val A = "a"
    const val B = "b"
    const val FORWARD = "forward"
    var sensorMap = mutableMapOf(A to "a", B to "b", FORWARD to false)

    // 적당한 값을 찾아가야 하는 계수
    const val CONVERT_K = 20
    const val BREAK_K = 20
    const val DECREASE_K = 0.005f
    var current_time = 0
    var current_velocity = 0f
    var current_distance = 0f

    var max_velocity = 0f

    lateinit var interval_timer: IntervalTimer
//lateinit var decrease_boat_speed: DecreaseBoatSpeed
//lateinit var current_timer: CurrentTimer


    var can_start_current_timer = false

    fun calculation(which_sensor: String) {
        when (which_sensor) {
            "a", "A" -> {
                // aB -> AB
                if (sensorMap[A] == "a" && sensorMap[B] == "B") {
                    if (which_sensor == "A") {
                        // 방향 판단
                        sensorMap[FORWARD] = false
                        // 타이머 정지
                        if (::interval_timer.isInitialized) interval_timer.interrupt()
                    }
                }

                sensorMap[A] = which_sensor
            }
            "b", "B" -> {
                // Ab -> AB
                if (sensorMap[A] == "A" && sensorMap[B] == "b") {
                    if (which_sensor == "B") {
                        // 방향 판단
                        sensorMap[FORWARD] = true

                        // 타이머 정지
                        if (::interval_timer.isInitialized) interval_timer.interrupt()

                        // 계산들..
                        if (sawtooth_interval_millis != 0) {
                            // 톱니 한 구간의 속력 계산 (한 구간 거리 24mm)
                            val v_sawtooth = 24f / sawtooth_interval_millis // mm/millis
//                        Log.d(TAG, "v_sawtooth: $v_sawtooth")

                            // 배의 속력으로 변환
                            val temp_velocity = v_sawtooth * CONVERT_K //-> km/h

                            // 갑자기 센서값이 튀는 경우 보정
                            if (temp_velocity > current_velocity * 1.3) {
                                if (current_velocity == 0f) {
                                    current_velocity = temp_velocity
                                } else {
                                    current_velocity *= 1.3f
                                }

                            } else if (temp_velocity > current_velocity) {
                                current_velocity = temp_velocity

                            } else {
                                //노를 현재 속도보다 느리게 저었다면 약간의 브레이크 역할을 한다.
                                current_velocity -= (current_velocity - temp_velocity) / BREAK_K
                                Log.d(TAG, "break occur: -" + (current_velocity - temp_velocity) / BREAK_K)
                            }
                            Log.d(TAG, "current_velocity: $current_velocity")

                            if (max_velocity < current_velocity) max_velocity = current_velocity
                        }

                        // 타이머 시작
                        interval_timer = IntervalTimer()
                        interval_timer.start()

                        if (can_start_current_timer) {
                            current_timer = CurrentTimer()
                            current_timer.start()
                            decrease_boat_speed = DecreaseBoatSpeed()
                            decrease_boat_speed.start()

                            can_start_current_timer = false
                        }
                    }
                }

                sensorMap[B] = which_sensor
            }
        }

    }


    var sawtooth_interval_millis = 0

    class IntervalTimer : Thread() {
        override fun run() {
            super.run()
            sawtooth_interval_millis = 0

            while (true) {
                try {
                    sleep(10)
                    sawtooth_interval_millis += 10

                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }


    class DecreaseBoatSpeed : Thread() {
        override fun run() {
            super.run()
            while (true) {
                try {
                    sleep(10)
                    if (current_velocity > 0) {
                        current_velocity -= DECREASE_K
                    } else {
                        current_velocity = 0f
                    }

                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }


    class CurrentTimer : Thread() {
        override fun run() {
            super.run()
            while (true) {
                try {
                    sleep(1000)
                    current_time += 1000
                    timeText()

                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        private fun timeText() {
            val totalSecond = current_time / 1000
            val totalMinute = totalSecond / 60
            val h = totalMinute / 60
            val m = totalMinute % 60
            val s = totalSecond % 60

            val stringH = if (h < 10) "0$h" else h.toString()
            val stringM = if (m < 10) "0$m" else m.toString()
            val stringS = if (s < 10) "0$s" else s.toString()

            val readMsg = handler_for_GameActivity.obtainMessage(WHAT_TIME, "$stringH:$stringM:$stringS")
            readMsg.sendToTarget()
        }

    }

}