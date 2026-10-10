package com.kode.app.kode_app.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kode.app.kode_app.databinding.ItemEventBinding
import com.kode.app.kode_app.model.Event

class EventAdapter(
    private val events: List<Event>,
    private val currentUserId: Long?,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]


        holder.binding.tvCategoryBadge.text = "• ${event.category}"
        holder.binding.tvEventTitle.text = event.title
        holder.binding.tvEventDescription.text = event.description
        holder.binding.tvEventDate.text = " ${event.date}"
        holder.binding.tvEventLocation.text = "${event.location}"


        val remainingSeats = (event.capacity - event.registeredCount).coerceAtLeast(0)
        if (remainingSeats > 0) {
            holder.binding.tvCapacityBadge.text = "Quedan $remainingSeats cupos"
        } else {
            holder.binding.tvCapacityBadge.text = "Agotado"
        }


        if (event.isPrivate) {
            holder.binding.tvAccessType.text = "Privado (Con clave)"
        } else {
            holder.binding.tvAccessType.text = "Pase Libre"
        }


        val isCreator = currentUserId != null && event.creatorId == currentUserId
        if (isCreator) {
            holder.binding.btnVerEvento.text = "Gestionar"
        } else {
            holder.binding.btnVerEvento.text = "Ver evento ➔"
        }

        holder.binding.btnVerEvento.setOnClickListener { onEventClick(event) }
        holder.itemView.setOnClickListener { onEventClick(event) }
    }

    override fun getItemCount(): Int = events.size
}