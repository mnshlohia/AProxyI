package com.aproxyi.internal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.aproxyi.AnalyticsInspector
import com.aproxyi.R
import com.aproxyi.core.AnalyticsEvent
import com.aproxyi.core.AnalyticsSource

/**
 * Activity to display analytics event details - matches AProxyI styling
 */
internal class AnalyticsDetailActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvSource: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvEventName: TextView
    private lateinit var tvParamsCount: TextView
    private lateinit var tvParams: TextView
    private lateinit var btnCopy: MaterialButton
    
    private var event: AnalyticsEvent? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics_detail)
        
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        event = eventId?.let { AnalyticsInspector.getEvent(it) }
        
        if (event == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupViews()
        setupToolbar()
        loadEvent()
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        tvSource = findViewById(R.id.tvSource)
        tvTimestamp = findViewById(R.id.tvTimestamp)
        tvEventName = findViewById(R.id.tvEventName)
        tvParamsCount = findViewById(R.id.tvParamsCount)
        tvParams = findViewById(R.id.tvParams)
        btnCopy = findViewById(R.id.btnCopy)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Event Details"
        }
        toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun loadEvent() {
        val evt = event ?: return
        
        tvSource.text = evt.source.name
        tvSource.setBackgroundColor(getSourceColor(evt.source))
        tvTimestamp.text = evt.formattedTime
        tvEventName.text = evt.eventName
        tvParamsCount.text = "${evt.paramsCount} parameters"
        
        // Format params nicely
        val paramsText = if (evt.params.isEmpty()) {
            "(No parameters)"
        } else {
            evt.params.entries
                .sortedBy { it.key }
                .joinToString("\n") { "${it.key} = ${it.value}" }
        }
        tvParams.text = paramsText
        
        btnCopy.setOnClickListener { copyToClipboard() }
    }
    
    private fun getSourceColor(source: AnalyticsSource): Int {
        val colorRes = when (source) {
            AnalyticsSource.FIREBASE -> R.color.aproxyi_warning
            AnalyticsSource.CLEVERTAP -> R.color.aproxyi_info
            AnalyticsSource.APPSFLYER -> R.color.aproxyi_success
            AnalyticsSource.FACEBOOK -> R.color.aproxyi_accent
        }
        return ContextCompat.getColor(this, colorRes)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy -> {
                copyToClipboard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun copyToClipboard() {
        val evt = event ?: return
        
        val text = buildString {
            appendLine("Event: ${evt.eventName}")
            appendLine("Source: ${evt.source.name}")
            appendLine("Time: ${evt.formattedTime}")
            appendLine()
            appendLine("Parameters:")
            evt.params.entries.sortedBy { it.key }.forEach {
                appendLine("  ${it.key} = ${it.value}")
            }
        }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Analytics Event", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        
        fun intent(context: Context, eventId: String): Intent {
            return Intent(context, AnalyticsDetailActivity::class.java).apply {
                putExtra(EXTRA_EVENT_ID, eventId)
            }
        }
    }
}
