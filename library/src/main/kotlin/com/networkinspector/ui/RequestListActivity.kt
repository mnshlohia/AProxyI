package com.networkinspector.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.networkinspector.NetworkInspector
import com.networkinspector.R
import com.networkinspector.core.NetworkRequest
import com.networkinspector.core.RequestStats
import com.networkinspector.core.RequestStatus
import com.networkinspector.databinding.ActivityRequestListBinding

/**
 * Activity showing a list of all tracked network requests.
 */
class RequestListActivity : AppCompatActivity(), NetworkInspector.RequestListener {
    
    private lateinit var binding: ActivityRequestListBinding
    private lateinit var adapter: RequestAdapter
    
    private var currentFilter: RequestStatus? = null
    private var searchQuery: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupSearch()
        setupFilterChips()
        setupRecyclerView()
        
        NetworkInspector.addListener(this)
        updateData()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        NetworkInspector.removeListener(this)
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val eventsButton = findViewById<TextView>(R.id.events)
        
        eventsButton.setOnClickListener {
            startActivity(AnalyticsListActivity.intent(this))
        }
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Network"
        }
    }
    
    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString() ?: ""
            updateData()
        }
    }
    
    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { 
            currentFilter = null
            updateChipSelection()
            updateData()
        }
        binding.chipSuccess.setOnClickListener { 
            currentFilter = RequestStatus.SUCCESS
            updateChipSelection()
            updateData()
        }
        binding.chipFailed.setOnClickListener { 
            currentFilter = RequestStatus.FAILED
            updateChipSelection()
            updateData()
        }
        binding.chipInProgress.setOnClickListener { 
            currentFilter = RequestStatus.IN_PROGRESS
            updateChipSelection()
            updateData()
        }
    }
    
    private fun updateChipSelection() {
        binding.chipAll.isChecked = currentFilter == null
        binding.chipSuccess.isChecked = currentFilter == RequestStatus.SUCCESS
        binding.chipFailed.isChecked = currentFilter == RequestStatus.FAILED
        binding.chipInProgress.isChecked = currentFilter == RequestStatus.IN_PROGRESS
    }
    
    private fun setupRecyclerView() {
        adapter = RequestAdapter { request ->
            startActivity(RequestDetailActivity.newIntent(this, request.id))
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RequestListActivity)
            adapter = this@RequestListActivity.adapter
            addItemDecoration(
                DividerItemDecoration(this@RequestListActivity, DividerItemDecoration.VERTICAL)
            )
        }
    }
    
    private fun updateData() {
        var requests = if (searchQuery.isNotBlank()) {
            NetworkInspector.searchRequests(searchQuery)
        } else {
            NetworkInspector.getRequests()
        }
        
        if (currentFilter != null) {
            requests = requests.filter { it.status == currentFilter }
        }
        
        adapter.submitList(requests)
        
        val stats = NetworkInspector.getStats()
        updateStatsBar(stats)
        
        binding.emptyView.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun updateStatsBar(stats: RequestStats) {
        binding.tvStats.text = buildString {
            append("${stats.total} total")
            append(" • ${stats.active} active")
            append(" • ${stats.successful} success")
            if (stats.failed > 0) append(" • ${stats.failed} failed")
        }
    }
    
    override fun onRequestsUpdated(requests: List<NetworkRequest>, stats: RequestStats) {
        runOnUiThread { updateData() }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_request_list, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_clear -> {
                showClearConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Requests")
            .setMessage("Are you sure you want to clear all recorded requests?")
            .setPositiveButton("Clear") { _, _ ->
                NetworkInspector.clearAll()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

/**
 * RecyclerView adapter for network requests
 */
private class RequestAdapter(
    private val onClick: (NetworkRequest) -> Unit
) : RecyclerView.Adapter<RequestAdapter.ViewHolder>() {
    
    private var requests: List<NetworkRequest> = emptyList()
    
    fun submitList(list: List<NetworkRequest>) {
        requests = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_request, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(requests[position])
    }
    
    override fun getItemCount(): Int = requests.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMethod: TextView = itemView.findViewById(R.id.tvMethod)
        private val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        
        fun bind(request: NetworkRequest) {
            tvMethod.text = request.method
            tvUrl.text = request.path.take(60)
            tvTime.text = request.formattedStartTime
            tvDuration.text = request.formattedDuration
            tvSize.text = request.responseSize
            
            when (request.status) {
                RequestStatus.IN_PROGRESS -> {
                    tvStatus.text = "⏳"
                    tvMethod.setTextColor(Color.parseColor("#FFC107"))
                    tvStatus.setTextColor(Color.parseColor("#FFC107"))
                }
                RequestStatus.SUCCESS -> {
                    tvStatus.text = "${request.responseCode}"
                    tvMethod.setTextColor(Color.parseColor("#4CAF50"))
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                RequestStatus.FAILED -> {
                    tvStatus.text = "${request.responseCode ?: "ERR"}"
                    tvMethod.setTextColor(Color.parseColor("#F44336"))
                    tvStatus.setTextColor(Color.parseColor("#F44336"))
                }
                RequestStatus.CANCELLED -> {
                    tvStatus.text = "⚠️"
                    tvMethod.setTextColor(Color.parseColor("#9E9E9E"))
                    tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
                }
            }
            
            itemView.setOnClickListener { onClick(request) }
        }
    }
}




