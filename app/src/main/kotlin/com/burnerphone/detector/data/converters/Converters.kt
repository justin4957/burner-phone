package com.burnerphone.detector.data.converters

import androidx.room.TypeConverter
import com.burnerphone.detector.data.models.LocationPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun fromLocationPointList(value: String): List<LocationPoint> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun toLocationPointList(list: List<LocationPoint>): String {
        return json.encodeToString(list)
    }
}
