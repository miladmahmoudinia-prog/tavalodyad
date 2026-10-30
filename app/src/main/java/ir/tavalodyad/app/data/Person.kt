package ir.tavalodyad.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jYear: Int,
    val jMonth: Int,
    val jDay: Int,
    val note: String? = null
)
