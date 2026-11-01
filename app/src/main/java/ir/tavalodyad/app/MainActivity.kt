package ir.tavalodyad.app

import android.net.Uri
import androidx.activity.OnBackPressedCallback
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
import ir.tavalodyad.app.data.Categories
import androidx.core.widget.doAfterTextChanged
import ir.tavalodyad.app.databinding.ActivityMainBinding
import ir.tavalodyad.app.ui.AddEditPersonDialogFragment
import ir.tavalodyad.app.ui.PersonAdapter
import ir.tavalodyad.app.util.GreetingMessages
import ir.tavalodyad.app.util.PersianCalendarUtils
import ir.tavalodyad.app.util.sortPeopleByBirthday
import ir.tavalodyad.app.reminders.BirthdayReminderWorker
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
    private var lastBackPressTime = 0L

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) BirthdayReminderWorker.checkNow(this)
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch { writeBackup(uri) }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BirthdayReminderWorker.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            BirthdayReminderWorker.checkNow(this)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(ir.tavalodyad.app.R.string.app_name)
        adapter = PersonAdapter(::showEditor, ::confirmDelete)
        binding.peopleList.layoutManager = LinearLayoutManager(this)
        binding.peopleList.adapter = adapter
        binding.addButton.setOnClickListener { showEditor(null) }
        binding.filterCategory.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(Categories.ALL) + Categories.values)
        binding.searchPeople.doAfterTextChanged { renderList() }
        binding.filterCategory.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = renderList()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        val t = PersianCalendarUtils.getTodayJalali()
        val dow = arrayOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")[java.time.LocalDate.now().dayOfWeek.value - 1]
        supportActionBar?.subtitle = "امروز: $dow ${PersianCalendarUtils.formatJalali(t.first, t.second, t.third)}"
        setupGreeting()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@MainActivity, "برای خروج، یک بار دیگر دکمه بازگشت را بزنید", Toast.LENGTH_SHORT).show()
                }
            }
        })
        lifecycleScope.launch {
            dao.getAllFlow().collectLatest { people ->
                cachedPeople = people
                render(people)
                BirthdayReminderWorker.checkNow(this@MainActivity)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, 0, "پشتیبان در حافظه یا فایل")
        menu.add(Menu.NONE, 3, 1, "پشتیبان در Google Drive")
        menu.add(Menu.NONE, 2, 2, "بازیابی پشتیبان")
        menu.add(Menu.NONE, 4, 3, "بازیابی از Google Drive")
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        1 -> { backupLauncher.launch("tavalodyad-backup.json"); true }
        3 -> {
            AlertDialog.Builder(this)
                .setTitle("پشتیبان در Google Drive")
                .setMessage("در پنجرهٔ بعد ممکن است ابتدا فایل‌های گوشی نمایش داده شوند. روی منوی سه‌خط بالای پنجره بزنید، Google Drive و حساب موردنظر را انتخاب کنید و سپس فایل را ذخیره کنید. اگر Drive نمایش داده نمی‌شود، برنامهٔ Google Drive را نصب کنید و وارد حساب شوید. پس از ذخیره، همگام‌سازی فایل را در Drive بررسی کنید.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتخاب محل ذخیره") { _, _ -> backupLauncher.launch("tavalodyad-backup.json") }
                .show()
            true
        }
        2 -> { restoreLauncher.launch(arrayOf("application/json", "text/*")); true }
        4 -> {
            AlertDialog.Builder(this)
                .setTitle("بازیابی از Google Drive")
                .setMessage("در پنجرهٔ بعد ممکن است ابتدا فایل‌های گوشی نمایش داده شوند. روی منوی سه‌خط بالای پنجره بزنید، Google Drive و حساب موردنظر را انتخاب کنید و فایل پشتیبان JSON را باز کنید. اگر Drive نمایش داده نمی‌شود، برنامهٔ Google Drive را نصب کنید و وارد حساب شوید. پس از انتخاب فایل، می‌توانید اطلاعات را اضافه یا جایگزین کنید.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتخاب فایل پشتیبان") { _, _ -> restoreLauncher.launch(arrayOf("application/json", "text/*")) }
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupGreeting() {
        binding.bottomNav.inflateMenu(R.menu.bottom_nav)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val greet = item.itemId == R.id.nav_greet
            binding.greetPage.visibility = if (greet) android.view.View.VISIBLE else android.view.View.GONE
            binding.birthdaysPage.visibility = if (greet) android.view.View.GONE else android.view.View.VISIBLE
            binding.addButton.visibility = if (greet) android.view.View.GONE else android.view.View.VISIBLE
            true
        }
        binding.greetTemplate.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            GreetingMessages.templates.indices.map { "پیام ${PersianCalendarUtils.toPersianDigits(it + 1)}" })
        val refresh = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = updatePreview()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        binding.greetPerson.onItemSelectedListener = refresh
        binding.greetTemplate.onItemSelectedListener = refresh
        binding.btnCopy.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("greeting", currentMessage()))
            toast("پیام کپی شد")
        }
        binding.btnWhatsapp.setOnClickListener { sendTo("com.whatsapp") }
        binding.btnTelegram.setOnClickListener { sendTo("org.telegram.messenger") }
    }
    private var lastKey = ""
    private fun currentMessage(): String = binding.greetPreview.text?.toString().orEmpty()
    private fun updatePreview() {
        if (cachedPeople.isEmpty()) { lastKey = ""; binding.greetPreview.setText("ابتدا یک تولد اضافه کنید."); return }
        val p = cachedPeople.getOrNull(binding.greetPerson.selectedItemPosition) ?: return
        val t = binding.greetTemplate.selectedItemPosition.coerceAtLeast(0)
        val key = "${p.id}|${p.name}|$t"
        if (key == lastKey) return // keep user's edits
        lastKey = key
        binding.greetPreview.setText(GreetingMessages.build(t, p.name))
    }
    private fun sendTo(pkg: String) {
        if (cachedPeople.isEmpty()) { toast("ابتدا یک تولد اضافه کنید"); return }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, currentMessage())
        }
        try { startActivity(android.content.Intent(intent).setPackage(pkg)) }
        catch (e: Exception) { startActivity(android.content.Intent.createChooser(intent, "ارسال تبریک")) }
    }

    private fun render(people: List<Person>) {
        val today = PersianCalendarUtils.getTodayJalali()
        val sorted = sortPeopleByBirthday(people, today)
        val sel = binding.greetPerson.selectedItemPosition
        val names = sorted.map { it.person.name }
        // greeting list follows the same order as cachedPeople, so keep it in sync
        cachedPeople = sorted.map { it.person }
        renderList()
        binding.greetPerson.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        if (sel in names.indices) binding.greetPerson.setSelection(sel)
        updatePreview()
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

    private fun renderList() {
        val query = binding.searchPeople.text?.toString()?.trim().orEmpty().replace('ي', 'ی').replace('ك', 'ک')
        val category = binding.filterCategory.selectedItem as? String ?: Categories.ALL
        val filtered = cachedPeople.filter { person ->
            person.name.replace('ي', 'ی').replace('ك', 'ک').contains(query, ignoreCase = true) && (category == Categories.ALL || person.category == category)
        }
        binding.emptyState.visibility = if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.peopleList.visibility = if (filtered.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        binding.emptyState.text = if (cachedPeople.isEmpty()) "هنوز کسی را اضافه نکرده‌اید.\nبرای ثبت اولین تولد، دکمهٔ + را بزنید." else "موردی پیدا نشد"
        adapter.submitList(sortPeopleByBirthday(filtered, PersianCalendarUtils.getTodayJalali()))
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
            val people = withContext(Dispatchers.IO) { dao.getAll() }
            people.forEach { p -> json.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("jYear", p.jYear); put("jMonth", p.jMonth); put("jDay", p.jDay)
                put("category", p.category)
                if (p.note != null) put("note", p.note)
            }) }
            withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json.toString(2)) } ?: error("فایل برای نوشتن باز نشد") }
            toast("فایل پشتیبان به محل انتخاب‌شده تحویل داده شد")
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
                        val category = obj.optString("category", "سایر").takeIf { it in Categories.values } ?: "سایر"
                        add(Person(name = obj.getString("name"), jYear = year, jMonth = month, jDay = day, note = obj.optString("note").takeIf { it.isNotBlank() }, category = category))
                    }
                }
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("بازیابی اطلاعات")
                        .setMessage("جایگزینی لیست فعلی یا افزودن به آن؟")
                        .setNegativeButton("انصراف", null)
                        .setNeutralButton("افزودن") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { people.forEach { dao.insert(it) }; toast("بازیابی انجام شد") } }
                        .setPositiveButton("جایگزینی") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { dao.replaceAll(people); toast("لیست جایگزین شد") } }
                        .show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { toast("فایل پشتیبان معتبر نیست: ${e.localizedMessage}") } }
        }
    }
    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
