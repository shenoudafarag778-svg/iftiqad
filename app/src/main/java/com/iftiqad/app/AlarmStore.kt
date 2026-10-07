package com.iftiqad.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object AlarmStore {

    private fun prefs(c: Context) =
        c.getSharedPreferences("iftiqad_alarms", Context.MODE_PRIVATE)

    private fun load(c: Context): JSONArray = try {
        JSONArray(prefs(c).getString("list", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun save(c: Context, a: JSONArray) {
        prefs(c).edit().putString("list", a.toString()).apply()
    }

    private fun pending(
        c: Context, id: Int, name: String, address: String, phone: String
    ): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = "com.iftiqad.app.ALARM"
            putExtra("id", id)
            putExtra("name", name)
            putExtra("address", address)
            putExtra("phone", phone)
        }
        return PendingIntent.getBroadcast(
            c, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun removeFromList(c: Context, id: Int) {
        val old = load(c)
        val nw = JSONArray()
        for (k in 0 until old.length()) {
            val o = old.getJSONObject(k)
            if (o.getInt("id") != id) nw.put(o)
        }
        save(c, nw)
    }

    fun schedule(
        c: Context, id: Int, name: String, address: String, phone: String,
        time: Long, persist: Boolean = true
    ) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(time, show),
            pending(c, id, name, address, phone)
        )
        if (persist) {
            removeFromList(c, id)
            val list = load(c)
            list.put(JSONObject().apply {
                put("id", id)
                put("name", name)
                put("address", address)
                put("phone", phone)
                put("time", time)
            })
            save(c, list)
        }
    }

    fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, id, "", "", ""))
        removeFromList(c, id)
    }

    fun rescheduleAll(c: Context) {
        val list = load(c)
        val keep = JSONArray()
        val now = System.currentTimeMillis()
        for (k in 0 until list.length()) {
            val o = list.getJSONObject(k)
            if (o.getLong("time") > now) {
                schedule(
                    c, o.getInt("id"), o.getString("name"), o.getString("address"),
                    o.getString("phone"), o.getLong("time"), false
                )
                keep.put(o)
            }
        }
        save(c, keep)
    }
}
