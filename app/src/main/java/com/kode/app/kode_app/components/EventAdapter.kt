package com.kode.app.kode_app.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kode.app.kode_app.R
import com.kode.app.kode_app.model.Event

class EventAdapter(

    private val events: List<Event>,

    private val currentUserId: Long?,

    private val onEventClick: (Event) -> Unit

) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val category: TextView =
            view.findViewById(
                R.id.tvCategory
            )

        val title: TextView =
            view.findViewById(
                R.id.tvTitle
            )

        val description: TextView =
            view.findViewById(
                R.id.tvDescription
            )

        val date: TextView =
            view.findViewById(
                R.id.tvDate
            )

        val location: TextView =
            view.findViewById(
                R.id.tvLocation
            )

        val button: Button =
            view.findViewById(
                R.id.btnViewEvent
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): EventViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_event,
                    parent,
                    false
                )

        return EventViewHolder(
            view
        )
    }

    override fun onBindViewHolder(
        holder: EventViewHolder,
        position: Int
    ) {

        val event =
            events[position]

        holder.category.text =
            event.category

        holder.title.text =
            event.title

        holder.description.text =
            event.description

        holder.date.text =
            event.date

        holder.location.text =
            event.location

        val isCreator =
            currentUserId != null &&
                    event.creatorId == currentUserId

        if (isCreator) {

            holder.button.text =
                "Gestionar"

        } else {

            holder.button.text =
                "Ver evento"
        }

        holder.button.setOnClickListener {

            onEventClick(
                event
            )
        }
    }

    override fun getItemCount(): Int {

        return events.size
    }
}