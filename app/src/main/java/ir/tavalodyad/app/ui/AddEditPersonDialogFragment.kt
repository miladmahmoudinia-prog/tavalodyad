package ir.tavalodyad.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import ir.tavalodyad.app.data.Person
import ir.tavalodyad.app.databinding.DialogDatePickerBinding
import ir.tavalodyad.app.databinding.DialogPersonBinding
import ir.tavalodyad.app.util.PersianCalendarUtils

class AddEditPersonDialogFragment : DialogFragment() {
    private var initial: Person? = null
    private var onSave: ((Person) -> Unit)? = null
    fun configure(person: Person?, callback: (Person) -> Unit): AddEditPersonDialogFragment { initial = person; onSave = callback; return this }

    private fun transparent(dialog: AlertDialog) {
        val m = (20 * resources.displayMetrics.density).toInt()
        dialog.window?.setBackgroundDrawable(InsetDrawable(ColorDrawable(Color.TRANSPARENT), m))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPersonBinding.inflate(LayoutInflater.from(requireContext()))
        val existing = initial
        val today = PersianCalendarUtils.getTodayJalali()
        var y = existing?.jYear ?: (today.first - 20)
        var m = existing?.jMonth ?: 1
        var d = existing?.jDay ?: 1
        fun faDate() = PersianCalendarUtils.formatJalali(y, m, d)
        binding.dialogTitle.text = if (existing == null) "افزودن تولد" else "ویرایش مشخصات"
        binding.inputName.setText(existing?.name.orEmpty())
        binding.inputNote.setText(existing?.note.orEmpty())
        binding.dateText.text = faDate()

        binding.dateBox.setOnClickListener {
            val pb = DialogDatePickerBinding.inflate(LayoutInflater.from(requireContext()))
            val years = (today.first downTo today.first - 120).toList()
            pb.pickYear.apply {
                minValue = 0; maxValue = years.size - 1
                displayedValues = years.map { PersianCalendarUtils.toPersianDigits(it) }.toTypedArray()
                value = years.indexOf(y).coerceAtLeast(0); wrapSelectorWheel = false
            }
            pb.pickMonth.apply {
                minValue = 0; maxValue = 11
                displayedValues = PersianCalendarUtils.monthNames
                value = m - 1; wrapSelectorWheel = true
            }
            fun setDays(keep: Int) {
                val len = PersianCalendarUtils.jalaliMonthLength(years[pb.pickYear.value], pb.pickMonth.value + 1)
                pb.pickDay.displayedValues = null
                pb.pickDay.minValue = 1; pb.pickDay.maxValue = len
                pb.pickDay.displayedValues = (1..len).map { PersianCalendarUtils.toPersianDigits(it) }.toTypedArray()
                pb.pickDay.value = keep.coerceIn(1, len)
                pb.pickDay.wrapSelectorWheel = true
            }
            fun preview() {
                pb.pickPreview.text = PersianCalendarUtils.formatJalali(years[pb.pickYear.value], pb.pickMonth.value + 1, pb.pickDay.value)
            }
            setDays(d); preview()
            val change = NumberPicker.OnValueChangeListener { _, _, _ -> setDays(pb.pickDay.value); preview() }
            pb.pickYear.setOnValueChangedListener(change)
            pb.pickMonth.setOnValueChangedListener(change)
            pb.pickDay.setOnValueChangedListener { _, _, _ -> preview() }
            val dlg = AlertDialog.Builder(requireContext()).setView(pb.root).create()
            pb.pickOk.setOnClickListener {
                y = years[pb.pickYear.value]; m = pb.pickMonth.value + 1; d = pb.pickDay.value
                binding.dateText.text = faDate()
                dlg.dismiss()
            }
            transparent(dlg)
            dlg.show()
        }

        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnSave.setOnClickListener {
            val name = binding.inputName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.formError.text = "نام را وارد کنید"
                binding.formError.visibility = android.view.View.VISIBLE
            } else {
                onSave?.invoke(Person(existing?.id ?: 0L, name, y, m, d, binding.inputNote.text?.toString()?.trim()?.ifEmpty { null }))
                dialog.dismiss()
            }
        }
        transparent(dialog)
        return dialog
    }
}
