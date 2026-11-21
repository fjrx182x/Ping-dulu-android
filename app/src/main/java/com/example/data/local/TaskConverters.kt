package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.Assignee
import com.example.domain.model.ChecklistItem
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class TaskConverters {

    @TypeConverter
    fun fromAssigneeList(assignees: List<Assignee>?): String {
        if (assignees.isNullOrEmpty()) return "[]"
        return runCatching { assigneeListAdapter.toJson(assignees) }.getOrDefault("[]")
    }

    @TypeConverter
    fun toAssigneeList(json: String?): List<Assignee> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { assigneeListAdapter.fromJson(json).orEmpty() }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromChecklistList(checklist: List<ChecklistItem>?): String {
        if (checklist.isNullOrEmpty()) return "[]"
        return runCatching { checklistListAdapter.toJson(checklist) }.getOrDefault("[]")
    }

    @TypeConverter
    fun toChecklistList(json: String?): List<ChecklistItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { checklistListAdapter.fromJson(json).orEmpty() }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromStringList(items: List<String>?): String {
        if (items.isNullOrEmpty()) return "[]"
        return runCatching { stringListAdapter.toJson(items) }.getOrDefault("[]")
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { stringListAdapter.fromJson(json).orEmpty() }
            .getOrDefault(emptyList())
    }

    companion object {
        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val assigneeListType = Types.newParameterizedType(
            List::class.java,
            Assignee::class.java
        )

        private val checklistListType = Types.newParameterizedType(
            List::class.java,
            ChecklistItem::class.java
        )

        private val stringListType = Types.newParameterizedType(
            List::class.java,
            String::class.java
        )

        private val assigneeListAdapter: JsonAdapter<List<Assignee>> =
            moshi.adapter(assigneeListType)

        private val checklistListAdapter: JsonAdapter<List<ChecklistItem>> =
            moshi.adapter(checklistListType)

        private val stringListAdapter: JsonAdapter<List<String>> =
            moshi.adapter(stringListType)
    }
}
