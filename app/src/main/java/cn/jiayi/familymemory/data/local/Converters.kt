package cn.jiayi.familymemory.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun stringsToJson(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun jsonToStrings(value: String): List<String> =
        runCatching { gson.fromJson<List<String>>(value, object : TypeToken<List<String>>() {}.type) }.getOrDefault(emptyList())
}
