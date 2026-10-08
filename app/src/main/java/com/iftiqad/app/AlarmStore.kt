package com.iftiqad.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object AlarmStore {

    const val DAILY_RC = 2000000000
    const val TEST_RC = 1999999990
    const val BD_RC = 2000000010

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
        c: Context, rc: Int, kind: String, name: String, address: String,
        phone: String, lead: Int, vt: Long
    ): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = "com.iftiqad.app.ALARM"
            putExtra("rc", rc)
            putExtra("kind", kind)
            putExtra("name", name)
            putExtra("address", address)
            putExtra("phone", phone)
            putExtra("lead", lead)
            putExtra("vt", vt)
        }
        return PendingIntent.getBroadcast(
            c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setExact(c: Context, time: Long, pi: PendingIntent) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    private fun setAlarm(c: Context, kind: String, time: Long, pi: PendingIntent) {
        if (kind == "daily") {
            setExact(c, time, pi)
            return
        }
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            val show = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi)
        } catch (e: Exception) {
            setExact(c, time, pi)
        }
    }

    private fun removeRc(c: Context, rc: Int) {
        val old = load(c)
        val nw = JSONArray()
        for (k in 0 until old.length()) {
            val o = old.getJSONObject(k)
            if (o.getInt("rc") != rc) nw.put(o)
        }
        save(c, nw)
    }

    private fun addEntry(
        c: Context, rc: Int, kind: String, name: String, address: String,
        phone: String, time: Long, lead: Int, vt: Long, persist: Boolean
    ) {
        setAlarm(c, kind, time, pending(c, rc, kind, name, address, phone, lead, vt))
        if (persist) {
            removeRc(c, rc)
            val list = load(c)
            list.put(JSONObject().apply {
                put("rc", rc)
                put("kind", kind)
                put("name", name)
                put("address", address)
                put("phone", phone)
                put("time", time)
                put("lead", lead)
                put("vt", vt)
            })
            save(c, list)
        }
    }

    fun scheduleVisit(
        c: Context, id: Int, time: Long, name: String, address: String,
        phone: String, lead: Int
    ) {
        cancelVisit(c, id)
        val now = System.currentTimeMillis()
        if (time > now) {
            addEntry(c, id * 10, "visit", name, address, phone, time, lead, time, true)
        }
        if (lead > 0 && time - lead * 60000L > now) {
            addEntry(c, id * 10 + 1, "pre", name, address, phone, time - lead * 60000L, lead, time, true)
        }
    }

    fun setBirthdays(c: Context, json: String, on: Boolean, pre: Boolean, hour: Int, minute: Int) {
        prefs(c).edit()
            .putString("bdays", json)
            .putBoolean("bd_on", on)
            .putBoolean("bd_pre", pre)
            .putInt("bd_h", hour)
            .putInt("bd_m", minute)
            .apply()
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, BD_RC, "bday", "", "", "", 0, 0))
        if (on) scheduleNextBd(c)
    }

    fun scheduleNextBd(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("bd_on", false)) return
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, p.getInt("bd_h", 9))
        cal.set(Calendar.MINUTE, p.getInt("bd_m", 0))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        setExact(c, cal.timeInMillis, pending(c, BD_RC, "bday", "", "", "", 0, 0))
    }

    fun birthdaysOn(c: Context, cal: Calendar): List<String> {
        val arr = try {
            JSONArray(prefs(c).getString("bdays", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        val out = ArrayList<String>()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val od = o.getInt("d")
            val om = o.getInt("m")
            val hit = (od == d && om == m) || (od == 29 && om == 2 && !leap && d == 28 && m == 2)
            if (hit) out.add(o.getString("n"))
        }
        return out
    }

    fun scheduleTest(c: Context) {
        val t = System.currentTimeMillis() + 10000L
        addEntry(c, TEST_RC, "visit", "اختبار التنبيه", "—", "—", t, 0, t, false)
    }

    fun cancelVisit(c: Context, id: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, id * 10, "visit", "", "", "", 0, 0))
        am.cancel(pending(c, id * 10 + 1, "pre", "", "", "", 0, 0))
        removeRc(c, id * 10)
        removeRc(c, id * 10 + 1)
    }

    fun cancelAll(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val list = load(c)
        for (k in 0 until list.length()) {
            val o = list.getJSONObject(k)
            am.cancel(pending(c, o.getInt("rc"), o.getString("kind"), "", "", "", 0, 0))
        }
        save(c, JSONArray())
    }

    fun rescheduleAll(c: Context) {
        val list = load(c)
        val keep = JSONArray()
        val now = System.currentTimeMillis()
        for (k in 0 until list.length()) {
            val o = list.getJSONObject(k)
            if (o.getLong("time") > now) {
                addEntry(
                    c, o.getInt("rc"), o.getString("kind"), o.getString("name"),
                    o.getString("address"), o.getString("phone"), o.getLong("time"),
                    o.optInt("lead", 0), o.optLong("vt", 0), false
                )
                keep.put(o)
            }
        }
        save(c, keep)
        scheduleNextDaily(c)
        scheduleNextBd(c)
    }

    fun setDaily(c: Context, on: Boolean, hour: Int, minute: Int) {
        prefs(c).edit()
            .putBoolean("daily_on", on)
            .putInt("daily_h", hour)
            .putInt("daily_m", minute)
            .apply()
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, DAILY_RC, "daily", "", "", "", 0, 0))
        if (on) scheduleNextDaily(c)
    }

    fun scheduleNextDaily(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("daily_on", false)) return
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, p.getInt("daily_h", 8))
        cal.set(Calendar.MINUTE, p.getInt("daily_m", 0))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        setExact(c, cal.timeInMillis, pending(c, DAILY_RC, "daily", "", "", "", 0, 0))
    }

    fun todayVisits(c: Context): List<Pair<String, Long>> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 24L * 60 * 60 * 1000
        val out = ArrayList<Pair<String, Long>>()
        val list = load(c)
        for (k in 0 until list.length()) {
            val o = list.getJSONObject(k)
            if (o.getString("kind") == "visit") {
                val t = o.getLong("time")
                if (t in start until end) out.add(Pair(o.getString("name"), t))
            }
        }
        out.sortBy { it.second }
        return out
    }
}
