package com.mosscurve.rowing

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_game.*
import kotlinx.android.synthetic.main.inflate_player.*
import kotlinx.android.synthetic.main.inflate_player.view.*
import kotlinx.android.synthetic.main.inflate_result.view.*
import org.json.JSONObject
import java.util.*
import android.widget.LinearLayout
import com.mosscurve.rowing.Calculation.A
import com.mosscurve.rowing.Calculation.B
import com.mosscurve.rowing.Calculation.FORWARD
import com.mosscurve.rowing.Calculation.can_start_current_timer
import com.mosscurve.rowing.Calculation.current_distance
import com.mosscurve.rowing.Calculation.current_time
import com.mosscurve.rowing.Calculation.current_velocity
import com.mosscurve.rowing.Calculation.max_velocity
import com.mosscurve.rowing.Calculation.sensorMap


lateinit var decrease_boat_speed: Calculation.DecreaseBoatSpeed
lateinit var current_timer: Calculation.CurrentTimer


lateinit var handler_for_GameActivity: Handler
const val WHAT_TIME = 0
const val WHAT_FREFRESH_ME = 1
const val WHAT_FREFRESH_COMPETITORS = 2

const val TIME = "time"
const val AVG_VELOCITY = "avg_velocity"
const val MAX_VELOCITY = "max_velocity"
const val DATE = "date"
const val VELOCITY_ARRAY = "velocity_array"
const val DISTANCE_ARRAY = "distance_array"
const val REFRESH_TIME = "refresh_time" //혹시 몰라 저장

const val SP_RECORD = "sp_record"

class GameActivity : AppCompatActivity() {

    lateinit var data_refresh_rate: DataRefreshRate
    val DATA_REFRESH_MILLIS = 100L

    var player = "이지훈"
    var total_distance = 500
    var babel = 0
    lateinit var competitors: Array<String>

    var race_map = mutableMapOf<String, Any>()
    var velocity_ary = arrayOf<Float>()
    var distance_ary = arrayOf<Float>()

    var converted_list_map = mutableMapOf<String, Map<String, List<String>>>() //MutableMap<String, MutableMap<String, List<String>>>
    val CONVERTED_VELOCITY = "velocity_array"
    val CONVERTED_DISTANCE = "distance_array"
    var converted_index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        //초기화
        sensorMap = mutableMapOf(A to "a", B to "b", FORWARD to false)
        current_velocity = 0f
        current_distance = 0f
        current_time = 0
        max_velocity = 0f

        player = intent.getStringExtra(PLAYER)
        total_distance = intent.getIntExtra(DISTANCE, 500)
        babel = intent.getIntExtra(BABEL, 0)
        competitors = intent.getStringArrayExtra(COMPETITORS)
        addCompetitorsView()

        tv_total_distance.text = "$total_distance m"
        tv_player_name.text = player

        data_refresh_rate = DataRefreshRate()
        data_refresh_rate.start()
        defineHandler()

        can_start_current_timer = true
    }



    private fun addCompetitorsView() {
        for (record_id in competitors) {
            val json_string = getSharedPreferences(SP_RECORD, Context.MODE_PRIVATE).getString(record_id, "")
            val json_object = JSONObject(json_string)

            val view = layoutInflater.inflate(R.layout.inflate_player, null)

            //xml의 레이아웃 속성들은 그대로 가져와 지질 않노. 여기서 해줘야 한다. 왜 그래야만 하지-
            val param = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.0f
            )
            view.layoutParams = param

            view.tag = record_id
            view.img_boat.setImageResource(R.drawable.ic_rowing_competitor)
            view.tv_player_name.text = json_object.getString(PLAYER)
//            view.tv_current_velocity.text = "0.0 km/h"
//            view.tv_current_distance.text = "0.0 m"

            layout_player_container.addView(view)

            convertString2List(record_id, json_object.getString(VELOCITY_ARRAY), json_object.getString(DISTANCE_ARRAY))
        }
    }


    private fun convertString2List(record_id: String, velocity_array: String, distance_array: String) {
        var v = velocity_array.drop(1)
        v = v.dropLast(1)
        val v_list = v.split(",")

        var d = distance_array.drop(1)
        d = d.dropLast(1)
        val d_list = d.split(",")

        converted_list_map[record_id] = mapOf(CONVERTED_VELOCITY to v_list, CONVERTED_DISTANCE to d_list)
    }



    private fun defineHandler() {
        handler_for_GameActivity = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                when (msg?.what) {
                    WHAT_TIME -> tv_current_time.text = msg.obj.toString()

                    WHAT_FREFRESH_ME -> {
                        val current_velocity_round = (Math.round(current_velocity * 10) / 10.0).toFloat()
                        tv_current_velocity.text = "$current_velocity_round km/h"
                        calculateDistance(current_velocity_round)
                    }

                    WHAT_FREFRESH_COMPETITORS -> {
                        for (record_id in competitors) {
                            val velocity_list = converted_list_map[record_id]?.get(CONVERTED_VELOCITY)
                            val distance_list = converted_list_map[record_id]?.get(CONVERTED_DISTANCE)
                            val view = layout_player_container.findViewWithTag<View>(record_id)

                            if (converted_index < velocity_list!!.size) {
                                view.tv_current_velocity.text = "${velocity_list[converted_index]} km/h"
                            }
                            if (converted_index < distance_list!!.size) {
                                view.tv_current_distance.text = "${distance_list[converted_index]} m"
                            }
                        }

                        converted_index++
                    }
                }
            }
        }
    }


    private fun calculateDistance(current_velocity_round:Float) {
        if (current_distance >= total_distance) {
            tv_current_distance.text = "$total_distance m"
            trackRecord(current_velocity_round, total_distance.toFloat())

            interruptThread()
            showResultDialog()

        } else {
            current_distance += current_velocity / 3600 * DATA_REFRESH_MILLIS
            val current_distance_round = (Math.round(current_distance * 10) / 10.0).toFloat()
            tv_current_distance.text = "$current_distance_round m"

            trackRecord(current_velocity_round, current_distance_round)
        }
    }


    private fun trackRecord(current_velocity_round: Float, current_distance_round: Float) {
        velocity_ary = velocity_ary.plus(current_velocity_round) //다시 할당해줘야 한다!
        distance_ary = distance_ary.plus(current_distance_round)
    }



    private fun showResultDialog() {
        val customView = LayoutInflater.from(applicationContext).inflate(R.layout.inflate_result, null)
        val dialog = AlertDialog.Builder(this).create()
        with (dialog) {
            setView(customView)
            show()
        }

        val avg_velocity =  total_distance / current_time.toFloat() * 3600 //-> km/h
        val avg_velocity_round = (Math.round(avg_velocity * 10.0) / 10.0).toFloat()
        val max_velocity_round = (Math.round(max_velocity * 10.0) / 10.0).toFloat()

        with (customView) {
            tv_result_player_name.text = player
            tv_result_babel.text = "$babel kg"
            tv_result_total_distance.text = "$total_distance m"
            tv_result_time.text = tv_current_time.text
            tv_result_avg_velocity.text = "$avg_velocity_round km/h"
            tv_result_max_velocity.text = "$max_velocity_round km/h"
            tv_result_date.text = Calendar.getInstance().time.toString()

            btn_OK.setOnClickListener {
                dialog.dismiss()
                finish()
            }
        }

        saveRaceData(customView)
    }


    private fun saveRaceData(customView: View) {
        race_map[PLAYER] = player
        race_map[BABEL] = babel
        race_map[DISTANCE] = total_distance
        race_map[TIME] = customView.tv_result_time.text
        race_map[AVG_VELOCITY] = customView.tv_result_avg_velocity.text
        race_map[MAX_VELOCITY] = customView.tv_result_max_velocity.text
        race_map[DATE] = customView.tv_result_date.text
        race_map[VELOCITY_ARRAY] = velocity_ary
        race_map[DISTANCE_ARRAY] = distance_ary
        race_map[REFRESH_TIME] = DATA_REFRESH_MILLIS

        val json_string = Gson().toJson(race_map)
        val sp = getSharedPreferences(SP_RECORD, Context.MODE_PRIVATE)
        sp.edit().putString(System.currentTimeMillis().toString(), json_string).apply()
    }


    inner class DataRefreshRate : Thread() {
        override fun run() {
            super.run()
            while (true) {
                try {
                    sleep(DATA_REFRESH_MILLIS)
                    val readMsg = handler_for_GameActivity.obtainMessage(WHAT_FREFRESH_ME)
                    readMsg.sendToTarget()
                    val readMsg2 = handler_for_GameActivity.obtainMessage(WHAT_FREFRESH_COMPETITORS)
                    readMsg2.sendToTarget()

                } catch (e: InterruptedException) {
                    Log.d(TAG, "IntervalTimer has been interrupted.")
                    break
                }
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        interruptThread()
    }


    private fun interruptThread() {
        can_start_current_timer = false
        if (::data_refresh_rate.isInitialized) data_refresh_rate.interrupt()
//        if (::interval_timer.isInitialized) interval_timer.interrupt()
        if (::current_timer.isInitialized) current_timer.interrupt()
        if (::decrease_boat_speed.isInitialized) decrease_boat_speed.interrupt()
    }


}
