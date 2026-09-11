package com.aproxyi.internal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aproxyi.AProxyI
import com.aproxyi.R
import com.aproxyi.core.NetworkRequest
import com.aproxyi.core.RequestStats
import com.aproxyi.core.RequestStatus
import com.aproxyi.databinding.ActivityRequestListBinding

/**
 * Activity showing a list of all tracked network requests.
 */
internal class RequestListActivity : AppCompatActivity(), AProxyI.RequestListener {
    
    private lateinit var binding: ActivityRequestListBinding
    private lateinit var adapter: RequestAdapter
    
    private var currentFilter: RequestStatus? = null
    private var searchQuery: String = ""

    // Registered unconditionally: ActivityResultLauncher must be created before
    // the activity reaches STARTED.
    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupSearch()
        setupFilterChips()
        setupRecyclerView()
        
        AProxyI.addListener(this)
        updateData()

        requestNotificationPermissionIfNeeded()
    }

    /**
     * Ask for POST_NOTIFICATIONS here rather than making every integrating app
     * write it.
     *
     * The library declares the permission, but on API 33+ declaring is not
     * granting: without the runtime grant, posting the ongoing notification
     * throws SecurityException, which is caught and swallowed, leaving no
     * notification and no diagnostic. Asking once the developer has opened the
     * inspector is the natural moment, and it is also the only moment we are
     * guaranteed to have an Activity.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        // Not using shouldShowRequestPermissionRationale: this is a debug-only
        // tool shown to the developer who just opened it, so an extra rationale
        // dialog would be noise. A denial is silently accepted -- the list
        // screen works regardless; only the notification shortcut is lost.
        notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AProxyI.removeListener(this)
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
            AProxyI.searchRequests(searchQuery)
        } else {
            AProxyI.getRequests()
        }
        
        if (currentFilter != null) {
            requests = requests.filter { it.status == currentFilter }
        }
        
        adapter.submitList(requests)
        
        val stats = AProxyI.getStats()
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
                AProxyI.clearAll()
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
                RequestStatus.TIMED_OUT -> {
                    tvStatus.text = "⏱"
                    tvMethod.setTextColor(Color.parseColor("#FF9800"))
                    tvStatus.setTextColor(Color.parseColor("#FF9800"))
                }
            }
            
            itemView.setOnClickListener { onClick(request) }
        }
    }
}




