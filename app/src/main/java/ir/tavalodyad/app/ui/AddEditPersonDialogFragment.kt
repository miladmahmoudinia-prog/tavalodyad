package ir.tavalodyad.app.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import ir.tavalodyad.app.data.Person
import ir.tavalodyad.app.databinding.DialogPersonBinding
import ir.tavalodyad.app.util.PersianCalendarUtils

class AddEditPersonDialogFragment : DialogFragment() {
    private var initial: Person? = null
    private var onSave: ((Person) -> Unit)? = null
    fun configure(person: Person?, callback: (Person) -> Unit): AddEditPersonDialogFragment { initial = person; onSave = callback; return this }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPersonBinding.inflate(LayoutInflater.from(requireContext()))
        val existing = initial
        val today = PersianCalendarUtils.getTodayJalali()
        binding.inputName.setText(existing?.name.orEmpty())
        val years = ((today.first) downTo (today.first - 120)).toList()
        val ctx = requireContext()
        fun sp(items: List<String>) = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, items)
        binding.spinYear.adapter = sp(years.map { PersianCalendarUtils.toPersianDigits(it) })
        binding.spinMonth.adapter = sp(PersianCalendarUtils.monthNames.toList())
        fun fillDays(keep: Int) {
            val y = years[binding.spinYear.selectedItemPosition]
            val len = PersianCalendarUtils.jalaliMonthLength(y, binding.spinMonth.selectedItemPosition + 1)
            binding.spinDay.adapter = sp((1..len).map { PersianCalendarUtils.toPersianDigits(it) })
            binding.spinDay.setSelection((keep.coerceIn(1, len)) - 1)
        }
        binding.spinYear.setSelection(years.indexOf(existing?.jYear ?: (today.first - 20)).coerceAtLeast(0))
        binding.spinMonth.setSelection((existing?.jMonth ?: 1) - 1)
        fillDays(existing?.jDay ?: 1)
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                fillDays(binding.spinDay.selectedItemPosition + 1)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        binding.spinYear.onItemSelectedListener = listener
        binding.spinMonth.onItemSelectedListener = listener
        binding.inputNote.setText(existing?.note.orEmpty())
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) "افزودن تولد" else "ویرایش مشخصات")
            .setView(binding.root)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val name = binding.inputName.text?.toString()?.trim().orEmpty()
                    require(name.isNotEmpty()) { "نام را وارد کنید" }
                    val y = years[binding.spinYear.selectedItemPosition]
                    val m = binding.spinMonth.selectedItemPosition + 1
                    val d = binding.spinDay.selectedItemPosition + 1
                    require(d in 1..PersianCalendarUtils.jalaliMonthLength(y, m)) { "تاریخ شمسی معتبر نیست" }
                    onSave?.invoke(Person(existing?.id ?: 0L, name, y, m, d, binding.inputNote.text?.toString()?.trim()?.ifEmpty { null }))
                    dialog.dismiss()
                } catch (e: Exception) {
                    binding.formError.text = e.message ?: "اطلاعات واردشده معتبر نیست"
                    binding.formError.visibility = android.view.View.VISIBLE
                }
            }
        }
        return dialog
    }
}
