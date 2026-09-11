package com.aproxyi.internal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.aproxyi.AProxyI
import com.aproxyi.R
import com.aproxyi.core.NetworkRequest
import com.aproxyi.core.RequestStatus
import com.aproxyi.databinding.ActivityRequestDetailBinding

/**
 * Activity showing detailed information about a single network request.
 */
internal class RequestDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRequestDetailBinding
    private var request: NetworkRequest? = null
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    
    private enum class Tab { OVERVIEW, REQUEST, RESPONSE }
    private var currentTab = Tab.OVERVIEW
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: run {
            finish()
            return
        }
        
        request = AProxyI.getRequest(requestId) ?: run {
            Toast.makeText(this, "Request not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar()
        setupTabs()
        displayHeader()
        showTab(Tab.OVERVIEW)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = request?.method ?: "Request"
            subtitle = request?.host
        }
    }
    
    private fun setupTabs() {
        binding.tabOverview.setOnClickListener { showTab(Tab.OVERVIEW) }
        binding.tabRequest.setOnClickListener { showTab(Tab.REQUEST) }
        binding.tabResponse.setOnClickListener { showTab(Tab.RESPONSE) }
    }
    
    private fun showTab(tab: Tab) {
        currentTab = tab
        updateTabSelection()
        
        when (tab) {
            Tab.OVERVIEW -> showOverview()
            Tab.REQUEST -> showRequest()
            Tab.RESPONSE -> showResponse()
        }
    }
    
    private fun updateTabSelection() {
        binding.tabOverview.alpha = if (currentTab == Tab.OVERVIEW) 1.0f else 0.5f
        binding.tabRequest.alpha = if (currentTab == Tab.REQUEST) 1.0f else 0.5f
        binding.tabResponse.alpha = if (currentTab == Tab.RESPONSE) 1.0f else 0.5f
    }
    
    private fun displayHeader() {
        val req = request ?: return
        
        binding.tvMethod.text = req.method
        binding.tvUrl.text = req.url
        binding.tvTime.text = "${req.formattedDate} ${req.formattedStartTime}"
        binding.tvDuration.text = req.formattedDuration
        
        when (req.status) {
            RequestStatus.IN_PROGRESS -> {
                binding.tvStatus.text = "⏳ In Progress"
                binding.tvStatus.setTextColor(Color.parseColor("#FFC107"))
            }
            RequestStatus.SUCCESS -> {
                binding.tvStatus.text = "✓ ${req.responseCode}"
                binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
            RequestStatus.FAILED -> {
                binding.tvStatus.text = "✗ ${req.responseCode ?: "Error"}"
                binding.tvStatus.setTextColor(Color.parseColor("#F44336"))
            }
            RequestStatus.CANCELLED -> {
                binding.tvStatus.text = "⚠️ Cancelled"
                binding.tvStatus.setTextColor(Color.parseColor("#9E9E9E"))
            }
            RequestStatus.TIMED_OUT -> {
                // No completion call arrived: almost always a caller that forgot
                // onRequestSuccess/onRequestFailed, not a network timeout.
                binding.tvStatus.text = "⏱ No completion call"
                binding.tvStatus.setTextColor(Color.parseColor("#FF9800"))
            }
        }
    }
    
    private fun showOverview() {
        val req = request ?: return
        
        val content = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("                  OVERVIEW              ")
            appendLine("═══════════════════════════════════════")
            appendLine()
            
            appendLine("▸ URL")
            appendLine("  ${req.url}")
            appendLine()
            
            appendLine("▸ Method")
            appendLine("  ${req.method}")
            appendLine()
            
            appendLine("▸ Status")
            appendLine("  ${req.status} (${req.responseCode ?: "N/A"})")
            appendLine()
            
            appendLine("▸ Duration")
            appendLine("  ${req.formattedDuration}")
            appendLine()
            
            appendLine("▸ Request Size")
            appendLine("  ${req.requestSize}")
            appendLine()
            
            appendLine("▸ Response Size")
            appendLine("  ${req.responseSize}")
            appendLine()
            
            if (req.tag != null) {
                appendLine("▸ Tag")
                appendLine("  ${req.tag}")
                appendLine()
            }
            
            appendLine("▸ Timestamp")
            appendLine("  ${req.formattedDate} ${req.formattedStartTime}")
            
            if (!req.errorMessage.isNullOrBlank()) {
                appendLine()
                appendLine("═══════════════════════════════════════")
                appendLine("                   ERROR                ")
                appendLine("═══════════════════════════════════════")
                appendLine()
                appendLine(req.errorMessage)
            }
        }
        
        binding.tvContent.text = content
    }
    
    private fun showRequest() {
        val req = request ?: return
        
        val content = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("                  REQUEST               ")
            appendLine("═══════════════════════════════════════")
            appendLine()
            
            appendLine("▸ URL")
            appendLine("  ${req.url}")
            appendLine()
            
            req.params?.takeIf { it.isNotEmpty() }?.let { params ->
                appendLine("▸ Query Parameters")
                params.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
                appendLine()
            }
            
            req.headers?.takeIf { it.isNotEmpty() }?.let { headers ->
                appendLine("▸ Headers")
                headers.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
                appendLine()
            }
            
            req.requestBody?.takeIf { it.isNotBlank() }?.let { body ->
                appendLine("▸ Body")
                appendLine(formatJson(body))
            }
        }
        
        binding.tvContent.text = content
    }
    
    private fun showResponse() {
        val req = request ?: return
        
        val content = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("                 RESPONSE               ")
            appendLine("═══════════════════════════════════════")
            appendLine()
            
            appendLine("▸ Status Code")
            appendLine("  ${req.responseCode ?: "N/A"}")
            appendLine()
            
            appendLine("▸ Duration")
            appendLine("  ${req.formattedDuration}")
            appendLine()
            
            req.responseHeaders?.takeIf { it.isNotEmpty() }?.let { headers ->
                appendLine("▸ Headers")
                headers.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
                appendLine()
            }
            
            if (!req.errorMessage.isNullOrBlank()) {
                appendLine("▸ Error")
                appendLine("  ${req.errorMessage}")
                appendLine()
                
                req.errorStackTrace?.takeIf { it.isNotBlank() }?.let { trace ->
                    appendLine("▸ Stack Trace")
                    appendLine(trace.take(3000))
                    appendLine()
                }
            }
            
            req.responseBody?.takeIf { it.isNotBlank() }?.let { body ->
                appendLine("▸ Body")
                appendLine(formatJson(body))
            }
        }
        
        binding.tvContent.text = content
    }
    
    private fun formatJson(text: String): String {
        return try {
            val jsonElement = JsonParser.parseString(text)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            text
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_request_detail, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_copy -> {
                copyToClipboard()
                true
            }
            R.id.action_share -> {
                shareRequest()
                true
            }
            R.id.action_copy_curl -> {
                copyCurl()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun copyToClipboard() {
        val text = binding.tvContent.text.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Network Request", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareRequest() {
        val req = request ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${req.method} ${req.host}")
            putExtra(Intent.EXTRA_TEXT, binding.tvContent.text.toString())
        }
        startActivity(Intent.createChooser(shareIntent, "Share Request"))
    }
    
    private fun copyCurl() {
        val req = request ?: return
        
        val curl = buildString {
            append("curl -X ${req.method}")
            
            req.headers?.forEach { (key, value) ->
                append(" \\\n  -H '$key: $value'")
            }
            
            if (!req.requestBody.isNullOrBlank()) {
                append(" \\\n  -d '${req.requestBody}'")
            }
            
            append(" \\\n  '${req.url}'")
        }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("cURL", curl))
        Toast.makeText(this, "cURL copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        
        fun newIntent(context: Context, requestId: String): Intent {
            return Intent(context, RequestDetailActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_ID, requestId)
            }
        }
    }
}




