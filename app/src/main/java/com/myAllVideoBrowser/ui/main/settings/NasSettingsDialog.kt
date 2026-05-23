package com.myAllVideoBrowser.ui.main.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.myAllVideoBrowser.R
import com.myAllVideoBrowser.data.nas.NasConfig
import com.myAllVideoBrowser.data.nas.NasDestinationType

object NasSettingsDialog {

    fun show(
        context: Context,
        initial: NasConfig,
        onTest: (NasConfig, (Result<Unit>) -> Unit) -> Unit,
        onSave: (NasConfig) -> Unit,
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_nas_settings, null, false)

        val radioLocal = view.findViewById<RadioButton>(R.id.radio_nas_local)
        val radioWebDav = view.findViewById<RadioButton>(R.id.radio_nas_webdav)
        val radioSmb = view.findViewById<RadioButton>(R.id.radio_nas_smb)
        val section = view.findViewById<View>(R.id.section_remote_inputs)
        val inputHost = view.findViewById<TextInputEditText>(R.id.input_host)
        val inputPort = view.findViewById<TextInputEditText>(R.id.input_port)
        val inputShareLayout = view.findViewById<TextInputLayout>(R.id.input_share_layout)
        val inputShare = view.findViewById<TextInputEditText>(R.id.input_share)
        val inputPath = view.findViewById<TextInputEditText>(R.id.input_path)
        val inputUser = view.findViewById<TextInputEditText>(R.id.input_user)
        val inputPassword = view.findViewById<TextInputEditText>(R.id.input_password)
        val switchTls = view.findViewById<MaterialSwitch>(R.id.switch_tls)
        val switchDeleteLocal = view.findViewById<MaterialSwitch>(R.id.switch_delete_local)
        val btnTest = view.findViewById<MaterialButton>(R.id.btn_test_connection)
        val tvResult = view.findViewById<TextView>(R.id.tv_test_result)

        when (initial.type) {
            NasDestinationType.LOCAL -> radioLocal.isChecked = true
            NasDestinationType.WEBDAV -> radioWebDav.isChecked = true
            NasDestinationType.SMB -> radioSmb.isChecked = true
        }
        inputHost.setText(initial.host)
        if (initial.port > 0) inputPort.setText(initial.port.toString())
        inputShare.setText(initial.shareName)
        inputPath.setText(initial.remotePath)
        inputUser.setText(initial.username)
        inputPassword.setText(initial.password)
        switchTls.isChecked = initial.useTls
        switchDeleteLocal.isChecked = initial.deleteLocalAfterUpload

        fun currentType(): NasDestinationType = when {
            radioWebDav.isChecked -> NasDestinationType.WEBDAV
            radioSmb.isChecked -> NasDestinationType.SMB
            else -> NasDestinationType.LOCAL
        }

        fun applyTypeUi() {
            val type = currentType()
            section.visibility = if (type == NasDestinationType.LOCAL) View.GONE else View.VISIBLE
            inputShareLayout.visibility =
                if (type == NasDestinationType.SMB) View.VISIBLE else View.GONE
            switchTls.visibility =
                if (type == NasDestinationType.WEBDAV) View.VISIBLE else View.GONE
            tvResult.visibility = View.GONE
        }
        applyTypeUi()
        radioLocal.setOnCheckedChangeListener { _, _ -> applyTypeUi() }
        radioWebDav.setOnCheckedChangeListener { _, _ -> applyTypeUi() }
        radioSmb.setOnCheckedChangeListener { _, _ -> applyTypeUi() }

        fun collect(): NasConfig {
            return NasConfig(
                type = currentType(),
                host = inputHost.text?.toString()?.trim().orEmpty(),
                port = inputPort.text?.toString()?.toIntOrNull() ?: 0,
                shareName = inputShare.text?.toString()?.trim().orEmpty(),
                remotePath = inputPath.text?.toString()?.trim().orEmpty(),
                username = inputUser.text?.toString().orEmpty(),
                password = inputPassword.text?.toString().orEmpty(),
                useTls = switchTls.isChecked,
                deleteLocalAfterUpload = switchDeleteLocal.isChecked,
            )
        }

        btnTest.setOnClickListener {
            val cfg = collect()
            if (cfg.type == NasDestinationType.LOCAL) {
                tvResult.visibility = View.VISIBLE
                tvResult.text = context.getString(R.string.nas_test_local)
                return@setOnClickListener
            }
            if (!cfg.isComplete()) {
                tvResult.visibility = View.VISIBLE
                tvResult.text = context.getString(R.string.nas_test_incomplete)
                return@setOnClickListener
            }
            btnTest.isEnabled = false
            tvResult.visibility = View.VISIBLE
            tvResult.text = context.getString(R.string.nas_test_running)
            onTest(cfg) { result ->
                btnTest.isEnabled = true
                tvResult.visibility = View.VISIBLE
                tvResult.text = if (result.isSuccess) {
                    context.getString(R.string.nas_test_success)
                } else {
                    context.getString(
                        R.string.nas_test_failure,
                        result.exceptionOrNull()?.message ?: "Unknown"
                    )
                }
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.nas_settings_entry)
            .setView(view)
            .setPositiveButton(R.string.save_proxy) { d, _ ->
                onSave(collect())
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }
}
