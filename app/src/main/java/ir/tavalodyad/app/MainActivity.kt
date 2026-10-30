package ir.tavalodyad.app

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ir.tavalodyad.app.data.AppDatabase
import ir.tavalodyad.app.data.Person
import ir.tavalodyad.app.databinding.ActivityMainBinding
import ir.tavalodyad.app.ui.AddEditPersonDialogFragment
import ir.tavalodyad.app.ui.PersonAdapter
import ir.tavalodyad.app.util.PersianCalendarUtils
import ir.tavalodyad.app.util.sortPeopleByBirthday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val dao by lazy { AppDatabase.getInstance(this).personDao() }
    private lateinit var adapter: PersonAdapter
    private var cachedPeople: List<Person> = emptyList()

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch { writeBackup(uri) }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(ir.tavalodyad.app.R.string.app_name)
        adapter = PersonAdapter(::showEditor, ::confirmDelete)
        binding.peopleList.layoutManager = LinearLayoutManager(this)
        binding.peopleList.adapter = adapter
        binding.addButton.setOnClickListener { showEditor(null) }
        lifecycleScope.launch {
            dao.getAllFlow().collectLatest { people ->
                cachedPeople = people
                render(people)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, 0, "پشتیبان‌گیری")
        menu.add(Menu.NONE, 2, 1, "بازیابی")
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        1 -> { backupLauncher.launch("tavalodyad-backup.json"); true }
        2 -> { restoreLauncher.launch(arrayOf("application/json", "text/*")); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun render(people: List<Person>) {
        binding.emptyState.visibility = if (people.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.peopleList.visibility = if (people.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        val today = PersianCalendarUtils.getTodayJalali()
        val sorted = sortPeopleByBirthday(people, today)
        adapter.submitList(sorted)
        val next = sorted.firstOrNull()
        if (next == null) {
            binding.nearestName.text = "هنوز تولدی ثبت نشده"
            binding.nearestDetails.text = "با دکمهٔ + اولین تولد را اضافه کنید"
        } else {
            binding.nearestName.text = next.person.name
            val countdown = if (next.daysLeft == 0L) "امروز تولدشه!" else "${PersianCalendarUtils.toPersianDigits(next.daysLeft)} روز تا تولد"
            binding.nearestDetails.text = "${PersianCalendarUtils.formatJalali(next.person.jYear, next.person.jMonth, next.person.jDay)}  •  $countdown"
        }
    }

    private fun showEditor(person: Person?) {
        AddEditPersonDialogFragment().configure(person) { edited ->
            lifecycleScope.launch(Dispatchers.IO) { if (edited.id == 0L) dao.insert(edited) else dao.update(edited) }
        }.show(supportFragmentManager, "person-editor")
    }
    private fun confirmDelete(person: Person) {
        AlertDialog.Builder(this).setMessage("«${person.name}» حذف شود؟")
            .setNegativeButton("انصراف", null).setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { dao.delete(person) }
            }.show()
    }

    private suspend fun writeBackup(uri: Uri) {
        try {
            val json = JSONArray()
            cachedPeople.forEach { p -> json.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("jYear", p.jYear); put("jMonth", p.jMonth); put("jDay", p.jDay)
                if (p.note != null) put("note", p.note)
            }) }
            withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json.toString(2)) } ?: error("فایل برای نوشتن باز نشد") }
            toast("پشتیبان با موفقیت ذخیره شد")
        } catch (e: Exception) { toast("ذخیرهٔ پشتیبان ناموفق بود: ${e.localizedMessage}") }
    }

    private fun confirmRestore(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("فایل خوانده نشد")
                val rows = JSONArray(raw)
                val people = buildList {
                    for (i in 0 until rows.length()) {
                        val obj = rows.getJSONObject(i)
                        val year = obj.getInt("jYear"); val month = obj.getInt("jMonth"); val day = obj.getInt("jDay")
                        val currentJalaliYear = PersianCalendarUtils.getTodayJalali().first
                        require(year in (currentJalaliYear - 120)..currentJalaliYear) { "سال تولد نامعتبر در فایل پشتیبان" }
                        require(month in 1..12 && day in 1..PersianCalendarUtils.jalaliMonthLength(year, month)) { "تاریخ نامعتبر در فایل پشتیبان" }
                        add(Person(name = obj.getString("name"), jYear = year, jMonth = month, jDay = day, note = obj.optString("note").takeIf { it.isNotBlank() }))
                    }
                }
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("بازیابی اطلاعات")
                        .setMessage("جایگزینی لیست فعلی یا افزودن به آن؟")
                        .setNegativeButton("انصراف", null)
                        .setNeutralButton("افزودن") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { people.forEach { dao.insert(it) }; toast("بازیابی انجام شد") } }
                        .setPositiveButton("جایگزینی") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { dao.deleteAll(); people.forEach { dao.insert(it) }; toast("لیست جایگزین شد") } }
                        .show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { toast("فایل پشتیبان معتبر نیست: ${e.localizedMessage}") } }
        }
    }
    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
