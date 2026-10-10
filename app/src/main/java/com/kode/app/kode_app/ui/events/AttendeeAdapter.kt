package com.kode.app.kode_app.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.kode.app.kode_app.R
import com.kode.app.kode_app.model.AttendeeItem

class AttendeeAdapter(
    private var attendees: List<AttendeeItem>,
    private val onStatusChanged: (AttendeeItem, String) -> Unit
) : RecyclerView.Adapter<AttendeeAdapter.AttendeeViewHolder>() {

    class AttendeeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatarInitials: TextView = itemView.findViewById(R.id.tvAvatarInitials)
        val tvAttendeeName: TextView = itemView.findViewById(R.id.tvAttendeeName)
        val tvAttendeeDetails: TextView = itemView.findViewById(R.id.tvAttendeeDetails)
        val tvTicketCode: TextView = itemView.findViewById(R.id.tvTicketCode)
        val toggleAttendanceStatus: MaterialButtonToggleGroup = itemView.findViewById(R.id.toggleAttendanceStatus)
        val btnStatusPending: MaterialButton = itemView.findViewById(R.id.btnStatusPending)
        val btnStatusAttended: MaterialButton = itemView.findViewById(R.id.btnStatusAttended)
        val btnStatusAbsent: MaterialButton = itemView.findViewById(R.id.btnStatusAbsent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendeeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendee_card, parent, false)
        return AttendeeViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendeeViewHolder, position: Int) {
        val item = attendees[position]

        val initials = item.user.name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("")
            .uppercase()

        holder.tvAvatarInitials.text = if (initials.isNotEmpty()) initials else "U"
        holder.tvAttendeeName.text = item.user.name.ifEmpty { "Sin Nombre" }
        holder.tvAttendeeDetails.text = "${item.user.age} años · ${item.user.gender}"
        holder.tvTicketCode.text = item.ticketCode

        holder.toggleAttendanceStatus.clearChecked()

        when (item.status) {
            "ATTENDED", "ASISTIO_REAL" -> holder.toggleAttendanceStatus.check(R.id.btnStatusAttended)
            "ABSENT" -> holder.toggleAttendanceStatus.check(R.id.btnStatusAbsent)
            else -> holder.toggleAttendanceStatus.check(R.id.btnStatusPending)
        }

        holder.btnStatusPending.setOnClickListener {
            if (item.status != "PENDING") {
                item.status = "PENDING"
                onStatusChanged(item, "PENDING")
            }
        }

        holder.btnStatusAttended.setOnClickListener {
            if (item.status != "ATTENDED") {
                item.status = "ATTENDED"
                onStatusChanged(item, "ATTENDED")
            }
        }

        holder.btnStatusAbsent.setOnClickListener {
            if (item.status != "ABSENT") {
                item.status = "ABSENT"
                onStatusChanged(item, "ABSENT")
            }
        }
    }

    override fun getItemCount(): Int = attendees.size

    fun updateData(newAttendees: List<AttendeeItem>) {
        this.attendees = newAttendees
        notifyDataSetChanged()
    }
}