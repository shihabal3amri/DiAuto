package com.andrerinas.openheadunit.main.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.openheadunit.R

data class AutoConnectMethod(
    val id: String,
    val nameResId: Int,
    val descriptionResId: Int,
    var isEnabled: Boolean
)

class AutoConnectAdapter(
    private val items: MutableList<AutoConnectMethod>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<AutoConnectAdapter.ViewHolder>() {

    var itemTouchHelper: ItemTouchHelper? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val priorityNumber: TextView = view.findViewById(R.id.priority_number)
        val methodName: TextView = view.findViewById(R.id.method_name)
        val methodDescription: TextView = view.findViewById(R.id.method_description)
        val btnMoveUp: ImageButton = view.findViewById(R.id.btn_move_up)
        val btnMoveDown: ImageButton = view.findViewById(R.id.btn_move_down)
        val methodToggle: Switch = view.findViewById(R.id.method_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_auto_connect, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.priorityNumber.text = "#${position + 1}"
        holder.methodName.setText(item.nameResId)
        holder.methodDescription.setText(item.descriptionResId)

        // Arrow visibility
        holder.btnMoveUp.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.btnMoveDown.visibility = if (position == items.size - 1) View.INVISIBLE else View.VISIBLE

        // Toggle
        holder.methodToggle.setOnCheckedChangeListener(null)
        holder.methodToggle.isChecked = item.isEnabled
        holder.methodToggle.setOnCheckedChangeListener { _, isChecked ->
            item.isEnabled = isChecked
            onChanged()
        }

        // Arrow click listeners
        holder.btnMoveUp.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos > 0) {
                swapItems(pos, pos - 1)
            }
        }

        holder.btnMoveDown.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < items.size - 1) {
                swapItems(pos, pos + 1)
            }
        }

        // Drag handle touch listener
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(holder)
            }
            false
        }

        // Background based on position
        val bgRes = when {
            items.size == 1 -> R.drawable.bg_setting_single
            position == 0 -> R.drawable.bg_setting_top
            position == items.size - 1 -> R.drawable.bg_setting_bottom
            else -> R.drawable.bg_setting_middle
        }
        holder.itemView.setBackgroundResource(bgRes)
    }

    override fun getItemCount() = items.size

    fun swapItems(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        // Rebind both items to update priority numbers and arrow visibility
        notifyItemChanged(from)
        notifyItemChanged(to)
        onChanged()
    }

    fun getOrderedIds(): List<String> = items.map { it.id }

    fun getEnabledStates(): Map<String, Boolean> = items.associate { it.id to it.isEnabled }
}

class AutoConnectTouchCallback(
    private val adapter: AutoConnectAdapter
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        adapter.swapItems(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe
    }

    override fun isLongPressDragEnabled() = false
}
