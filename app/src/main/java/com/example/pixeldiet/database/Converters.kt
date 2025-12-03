package com.example.pixeldiet.database

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    // --- Map<String, Int> <-> String (JSON) 변환 (DailyUsage용) ---
    @TypeConverter
    fun fromStringToMap(value: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        if (value.isEmpty()) return map

        try {
            val jsonObject = JSONObject(value)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.getInt(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    @TypeConverter
    fun fromMapToString(map: Map<String, Int>): String {
        val jsonObject = JSONObject()
        map.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    // --- List<String> <-> String (JSON) 변환 (TrackingHistory용) ---
    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        val list = mutableListOf<String>()
        if (value.isEmpty()) return list

        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @TypeConverter
    fun fromListToString(list: List<String>): String {
        val jsonArray = JSONArray()
        list.forEach {
            jsonArray.put(it)
        }
        return jsonArray.toString()
    }
}