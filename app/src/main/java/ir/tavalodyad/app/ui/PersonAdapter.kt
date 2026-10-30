package ir.tavalodyad.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ir.tavalodyad.app.R
import ir.tavalodyad.app.data.Person
import ir.tavalodyad.app.databinding.ItemPersonBinding
import ir.tavalodyad.app.util.BirthdayInfo
import ir.tavalodyad.app.util.PersianCalendarUtils

class PersonAdapter(
    private val onEdit: (Person) -> Unit,
    private val onDelete: (Person) -> Unit
) : ListAdapter<BirthdayInfo, PersonAdapter.PersonHolder>(Diff) {
    object Diff : DiffUtil.ItemCallback<BirthdayInfo>() {
        override fun areItemsTheSame(oldItem: BirthdayInfo, newItem: BirthdayInfo) = oldItem.person.id == newItem.person.id
        override fun areContentsTheSame(oldItem: BirthdayInfo, newItem: BirthdayInfo) = oldItem == newItem
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonHolder = PersonHolder(
        ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: PersonHolder, position: Int) = holder.bind(getItem(position))
    inner class PersonHolder(private val binding: ItemPersonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(info: BirthdayInfo) {
            val p = info.person
            binding.personName.text = p.name
            binding.personDate.text = PersianCalendarUtils.formatJalali(p.jYear, p.jMonth, p.jDay)
            binding.personDays.text = if (info.daysLeft == 0L) "امروز تولدشه!" else "${PersianCalendarUtils.toPersianDigits(info.daysLeft)} روز تا تولد"
            binding.personAge.text = "سن جدید: ${PersianCalendarUtils.toPersianDigits(info.turningAge)} سالگی"
            binding.personNote.text = p.note.orEmpty()
            binding.personNote.visibility = if (p.note.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.personMore.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    menu.add(0, 1, 0, "ویرایش")
                    menu.add(0, 2, 1, "حذف")
                    setOnMenuItemClickListener { item -> when (item.itemId) {
                        1 -> { onEdit(p); true }
                        2 -> { onDelete(p); true }
                        else -> false
                    } }
                }.show()
            }
        }
    }
}
