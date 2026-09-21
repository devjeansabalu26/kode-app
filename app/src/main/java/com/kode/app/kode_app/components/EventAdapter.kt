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
    class EventViewHolder(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.binding.tvCategory.text = event.category
        holder.binding.tvTitle.text = event.title
        holder.binding.tvDescription.text = event.description
        holder.binding.tvDate.text = event.date
        holder.binding.tvLocation.text = event.location
        val isCreator = currentUserId != null && event.creatorId == currentUserId
        if (isCreator) {
            holder.binding.btnViewEvent.text = "Gestionar"
        } else {
            holder.binding.btnViewEvent.text = "Ver evento"
        }
        holder.binding.btnViewEvent.setOnClickListener {
            onEventClick(event)
        }
    }

    override fun getItemCount(): Int {
        return events.size
    }
}