package com.mosscurve.rowing

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_mode.*
import kotlinx.android.synthetic.main.inflate_record.view.*
import org.json.JSONObject


const val PLAYER = "player"
const val DISTANCE = "distance"
const val BABEL = "babel"
const val COMPETITORS = "competitors"

const val RECORD_ID = "record_id"

var checked_record_list = mutableListOf<String>()
lateinit var records_array: MutableList<JSONObject>

class ModeActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    var player = "이지훈"
    var distance = 500
    var babel = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode)

        checked_record_list = mutableListOf()

        buildSpinner()
        buildRecyclerView()
        setClickListener()
    }


    private fun buildSpinner() {
        spinner_player.onItemSelectedListener = this
        spinner_player.tag = PLAYER
        spinner_distance.onItemSelectedListener = this
        spinner_distance.tag = DISTANCE
        spinner_babel.onItemSelectedListener = this
        spinner_babel.tag = BABEL

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            this,
            R.array.player_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner_player.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.distance_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner_distance.adapter = adapter
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.babel_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner_babel.adapter = adapter
        }
    }


    private fun buildRecyclerView() {
        // Creates a vertical Layout Manager
        recycler_record.layoutManager = LinearLayoutManager(this)
        // You can use GridLayoutManager if you want multiple columns. Enter the number of columns as a parameter.
//        recycler_record.layoutManager = GridLayoutManager(this, 2)

        records()

        // Access the RecyclerView Adapter and load the data into it
        recycler_record.adapter = RecordAdapter(records_array)
    }


    private fun records() {
        val all_map = getSharedPreferences(SP_RECORD, Context.MODE_PRIVATE).all
        records_array = mutableListOf()
        for ((key, value) in all_map) {
            val json_object = JSONObject(value as String)
            json_object.put(RECORD_ID, key)
            records_array = records_array.plus(json_object).toMutableList() //주의! array에 다시 할당해주어야 한다.
        }
    }


    private fun setClickListener() {
        btn_start.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra(PLAYER, player)
            intent.putExtra(DISTANCE, distance)
            intent.putExtra(BABEL, babel)
            intent.putExtra(COMPETITORS, checked_record_list.toTypedArray())

            startActivity(intent)
        }
    }


    override fun onNothingSelected(parent: AdapterView<*>?) {
        Log.d(TAG, "onNothingSelected")
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val text = parent!!.getItemAtPosition(position).toString()

        when (parent.tag) {
            PLAYER -> player = text
            DISTANCE -> distance = text.toInt()
            BABEL -> babel = text.toInt()
        }
    }


    override fun onResume() {
        super.onResume()
        records()
        (recycler_record.adapter as RecordAdapter).updateData(records_array)
        recycler_record.adapter?.notifyDataSetChanged()
    }
}




class RecordAdapter(items_: MutableList<JSONObject>): RecyclerView.Adapter<MyViewHolder>() {
    lateinit var context: Context
    private lateinit var items: MutableList<JSONObject>

    init {
        updateData(items_)
    }

    fun updateData(data: MutableList<JSONObject>) {
        data.reverse()
        items = data
    }

    override fun getItemCount(): Int {
        return items.size
    }

    // Inflates the item views
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        context = parent.context
        return MyViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.inflate_record, parent, false))
    }

    // Binds each item to a view
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.tv_record_player_name.text = items[position].getString(PLAYER)
        holder.tv_record_babel.text = items[position].getString(BABEL)
        holder.tv_record_total_distance.text = items[position].getString(DISTANCE)
        holder.tv_record_time.text = items[position].getString(TIME)
        holder.tv_record_avg_velocity.text = items[position].getString(AVG_VELOCITY)
        holder.tv_record_max_velocity.text = items[position].getString(MAX_VELOCITY)
        holder.tv_record_date.text = items[position].getString(DATE)

        setClickListener(holder, items[position].getString(RECORD_ID), position, context, this)
    }


    private fun setClickListener(holder: MyViewHolder, record_id: String, position: Int, context: Context, recordAdapter: RecordAdapter) {
        holder.cb_record.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!checked_record_list.contains(record_id)) {
                    checked_record_list = checked_record_list.plus(record_id).toMutableList()
                }
            }
            else checked_record_list.remove(record_id)
        }

        holder.img_record_delete.setOnClickListener {
            showConfirmDeleteDialog(record_id, position, context, recordAdapter)
        }
    }


    private fun showConfirmDeleteDialog(record_id: String, position: Int, context: Context, recordAdapter: RecordAdapter) {
        val builder = AlertDialog.Builder(context)
        with (builder) {
            setTitle("Are you sure???????????")
            setPositiveButton("HELL YES") { dialog, which ->
                val sp = context.getSharedPreferences(SP_RECORD, Context.MODE_PRIVATE)
                sp.edit().remove(record_id).apply()

                records_array.removeAt(position)
                updateData(records_array)
                recordAdapter.notifyItemRemoved(position)

                checked_record_list.remove(record_id)
            }
            show()
        }
    }
}


class MyViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    // Holds the View that will add each data to
    val tv_record_player_name = view.tv_record_player_name
    val tv_record_babel = view.tv_record_babel
    val tv_record_total_distance = view.tv_record_total_distance
    val tv_record_time = view.tv_record_time
    val tv_record_avg_velocity = view.tv_record_avg_velocity
    val tv_record_max_velocity = view.tv_record_max_velocity
    val tv_record_date = view.tv_record_date

    val cb_record = view.cb_record
    val img_record_delete = view.img_record_delete
}
