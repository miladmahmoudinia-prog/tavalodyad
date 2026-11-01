package ir.tavalodyad.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE ASC") fun getAllFlow(): Flow<List<Person>>
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE ASC") suspend fun getAll(): List<Person>
    @Insert suspend fun insert(person: Person): Long
    @Update suspend fun update(person: Person)
    @Delete suspend fun delete(person: Person)
    @Query("DELETE FROM people") suspend fun deleteAll()
    @Transaction suspend fun replaceAll(people: List<Person>) {
        deleteAll()
        people.forEach { insert(it) }
    }
}
