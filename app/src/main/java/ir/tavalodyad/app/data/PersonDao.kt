package ir.tavalodyad.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE ASC") fun getAllFlow(): Flow<List<Person>>
    @Insert suspend fun insert(person: Person): Long
    @Update suspend fun update(person: Person)
    @Delete suspend fun delete(person: Person)
    @Query("DELETE FROM people") suspend fun deleteAll()
}
