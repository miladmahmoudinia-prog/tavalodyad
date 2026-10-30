package ir.tavalodyad.app.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
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
        binding.inputYear.setText((existing?.jYear ?: today.first).toString())
        binding.inputMonth.setText((existing?.jMonth ?: today.second).toString())
        binding.inputDay.setText((existing?.jDay ?: today.third).toString())
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
                    val y = binding.inputYear.readInt("سال")
                    val m = binding.inputMonth.readInt("ماه")
                    val d = binding.inputDay.readInt("روز")
                    require(y in (today.first - 120)..today.first) { "سال تولد باید بین ۱۲۰ سال گذشته تا سال جاری باشد" }
                    require(m in 1..12 && d in 1..PersianCalendarUtils.jalaliMonthLength(y, m)) { "تاریخ شمسی معتبر نیست" }
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
    private fun TextInputEditText.readInt(label: String): Int = text?.toString()?.trim()?.toIntOrNull() ?: error("$label را درست وارد کنید")
}
