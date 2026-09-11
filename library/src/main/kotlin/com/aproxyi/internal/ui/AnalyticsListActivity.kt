package com.aproxyi.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.aproxyi.AnalyticsInspector
import com.aproxyi.R
import com.aproxyi.core.AnalyticsEvent
import com.aproxyi.core.AnalyticsSource

/**
 * Activity to display analytics events - matches Network Inspector styling
 */
internal class AnalyticsListActivity : AppCompatActivity(), AnalyticsInspector.EventListener {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var etSearch: EditText
    private lateinit var chipAll: Chip
    private lateinit var chipFirebase: Chip
    private lateinit var chipClevertap: Chip
    private lateinit var chipAppsflyer: Chip
    private lateinit var chipFacebook: Chip
    private lateinit var tvEventCount: TextView
    private lateinit var tvClear: TextView
    private lateinit var rvEvents: RecyclerView
    private lateinit var tvEmpty: TextView
    
    private val adapter = AnalyticsAdapter { event ->
        startActivity(AnalyticsDetailActivity.intent(this, event.id))
    }
    
    private var currentFilter: AnalyticsSource? = null
    private var searchQuery: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics_list)
        
        setupViews()
        setupToolbar()
        setupSearch()
        setupFilterChips()
        setupRecyclerView()
        setupClearButton()
        
        AnalyticsInspector.addListener(this)
        updateData()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AnalyticsInspector.removeListener(this)
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        etSearch = findViewById(R.id.etSearch)
        chipAll = findViewById(R.id.chipAll)
        chipFirebase = findViewById(R.id.chipFirebase)
        chipClevertap = findViewById(R.id.chipClevertap)
        chipAppsflyer = findViewById(R.id.chipAppsflyer)
        chipFacebook = findViewById(R.id.chipFacebook)
        tvEventCount = findViewById(R.id.tvEventCount)
        tvClear = findViewById(R.id.tvClear)
        rvEvents = findViewById(R.id.rvEvents)
        tvEmpty = findViewById(R.id.tvEmpty)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Analytics Events"
        }
        toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun setupSearch() {
        etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString() ?: ""
            updateData()
        }
    }
    
    private fun setupFilterChips() {
        chipAll.setOnClickListener {
            currentFilter = null
            updateChipSelection()
            updateData()
        }
        chipFirebase.setOnClickListener {
            currentFilter = AnalyticsSource.FIREBASE
            updateChipSelection()
            updateData()
        }
        chipClevertap.setOnClickListener {
            currentFilter = AnalyticsSource.CLEVERTAP
            updateChipSelection()
            updateData()
        }
        chipAppsflyer.setOnClickListener {
            currentFilter = AnalyticsSource.APPSFLYER
            updateChipSelection()
            updateData()
        }
        chipFacebook.setOnClickListener {
            currentFilter = AnalyticsSource.FACEBOOK
            updateChipSelection()
            updateData()
        }
    }
    
    private fun updateChipSelection() {
        chipAll.isChecked = currentFilter == null
        chipFirebase.isChecked = currentFilter == AnalyticsSource.FIREBASE
        chipClevertap.isChecked = currentFilter == AnalyticsSource.CLEVERTAP
        chipAppsflyer.isChecked = currentFilter == AnalyticsSource.APPSFLYER
        chipFacebook.isChecked = currentFilter == AnalyticsSource.FACEBOOK
    }
    
    private fun setupRecyclerView() {
        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = adapter
        rvEvents.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
    }
    
    private fun setupClearButton() {
        tvClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear All Events")
                .setMessage("Are you sure you want to clear all analytics events?")
                .setPositiveButton("Clear") { _, _ ->
                    AnalyticsInspector.clearAll()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
    
    private fun updateData() {
        // Get events based on search
        var events = if (searchQuery.isNotBlank()) {
            AnalyticsInspector.searchEvents(searchQuery)
        } else {
            AnalyticsInspector.getEvents()
        }
        
        // Apply source filter
        if (currentFilter != null) {
            events = events.filter { it.source == currentFilter }
        }
        
        adapter.submitList(events)
        
        // Update stats
        val totalCount = AnalyticsInspector.getEventCount()
        tvEventCount.text = buildString {
            append("${events.size}")
            if (currentFilter != null || searchQuery.isNotBlank()) {
                append(" of $totalCount")
            }
            append(" events")
        }
        
        // Show/hide empty state
        tvEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        rvEvents.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }
    
    override fun onEventsUpdated(events: List<AnalyticsEvent>) {
        runOnUiThread { updateData() }
    }
    
    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AnalyticsListActivity::class.java)
        }
    }
}

/**
 * Adapter for analytics events
 */
internal class AnalyticsAdapter(
    private val onClick: (AnalyticsEvent) -> Unit
) : RecyclerView.Adapter<AnalyticsAdapter.EventViewHolder>() {
    
    private var events: List<AnalyticsEvent> = emptyList()
    
    fun submitList(newEvents: List<AnalyticsEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analytics_event, parent, false)
        return EventViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }
    
    override fun getItemCount(): Int = events.size
    
    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvEventName: TextView = itemView.findViewById(R.id.tvEventName)
        private val tvParamsCount: TextView = itemView.findViewById(R.id.tvParamsCount)
        private val tvParamsPreview: TextView = itemView.findViewById(R.id.tvParamsPreview)
        
        fun bind(event: AnalyticsEvent) {
            tvSource.text = event.source.name
            tvSource.setBackgroundColor(getSourceColor(event.source))
            tvTime.text = event.formattedTime
            tvEventName.text = event.eventName
            tvParamsCount.text = "${event.paramsCount} params"
            
            // Show first few params as preview
            val preview = event.params.entries.take(3)
                .joinToString(", ") { "${it.key}=${it.value}" }
            tvParamsPreview.text = preview
            tvParamsPreview.visibility = if (preview.isNotEmpty()) View.VISIBLE else View.GONE
            
            itemView.setOnClickListener { onClick(event) }
        }
        
        private fun getSourceColor(source: AnalyticsSource): Int {
            val colorRes = when (source) {
                AnalyticsSource.FIREBASE -> R.color.aproxyi_warning
                AnalyticsSource.CLEVERTAP -> R.color.aproxyi_info
                AnalyticsSource.APPSFLYER -> R.color.aproxyi_success
                AnalyticsSource.FACEBOOK -> R.color.aproxyi_accent
            }
            return itemView.context.getColor(colorRes)
        }
    }
}
