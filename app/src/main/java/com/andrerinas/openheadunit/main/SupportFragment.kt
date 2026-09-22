package com.andrerinas.openheadunit.main

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.QrCodeGenerator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dedicated support screen: a clickable link to the project's GitHub issues, a QR code of that
 * same URL (so a head unit without internet can be scanned from a phone), a short how-to, and a
 * note that opening an issue needs a GitHub account.
 */
class SupportFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        val url = getString(R.string.github_issues_url)

        // Clickable link that opens the system browser if the head unit has one.
        val link = view.findViewById<TextView>(R.id.support_link)
        link.text = fromHtml("<a href=\"$url\">${getString(R.string.support_open_issues)}</a>")
        link.movementMethod = LinkMovementMethod.getInstance()

        view.findViewById<MaterialButton>(R.id.support_rules_button).setOnClickListener {
            showRulesDialog()
        }

        // QR of the same URL. Generated offline, so it works without internet on the head unit.
        val qr = view.findViewById<ImageView>(R.id.support_qr)
        val bitmap = QrCodeGenerator.generateQrCode(url, 500)
        if (bitmap != null) {
            qr.setImageBitmap(bitmap)
        } else {
            // Generation failed: hide the QR block so we do not show an empty white box.
            view.findViewById<View>(R.id.support_qr_container).visibility = View.GONE
            view.findViewById<View>(R.id.support_qr_caption).visibility = View.GONE
        }
    }

    /** Modal with the rules for opening a good, actionable issue, split into required vs optional. */
    private fun showRulesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reporting_rules, null)
        dialogView.findViewById<TextView>(R.id.rules_intro).text = getString(R.string.support_rules_intro)
        dialogView.findViewById<TextView>(R.id.rules_required_header).text =
            getString(R.string.support_rules_section_required)
        dialogView.findViewById<TextView>(R.id.rules_recommend_header).text =
            getString(R.string.support_rules_section_recommend)

        // Required: English (title shown red + bold on purpose) and no duplicates.
        val required = listOf(
            Triple(R.string.support_rules_english_title, R.string.support_rules_english_desc, true),
            Triple(R.string.support_rules_search_title, R.string.support_rules_search_desc, false)
        )
        // Recommendations: optional, but they speed up a fix.
        val recommend = listOf(
            R.string.support_rules_device_title to R.string.support_rules_device_desc,
            R.string.support_rules_connection_title to R.string.support_rules_connection_desc,
            R.string.support_rules_appversion_title to R.string.support_rules_appversion_desc,
            R.string.support_rules_logs_title to R.string.support_rules_logs_desc,
            R.string.support_rules_phone_title to R.string.support_rules_phone_desc,
            R.string.support_rules_describe_title to R.string.support_rules_describe_desc,
            R.string.support_rules_oneissue_title to R.string.support_rules_oneissue_desc
        )

        // <font color> and <b> are legacy HTML tags that Html.fromHtml renders on every API level.
        dialogView.findViewById<TextView>(R.id.rules_required).text = fromHtml(
            required.joinToString("<br><br>") { (title, desc, red) ->
                val t = getString(title)
                val titleHtml = if (red) "<b><font color=\"#E53935\">$t</font></b>" else "<b>$t</b>"
                "$titleHtml<br>${getString(desc)}"
            }
        )
        dialogView.findViewById<TextView>(R.id.rules_recommend).text = fromHtml(
            recommend.joinToString("<br><br>") { (title, desc) ->
                "<b>${getString(title)}</b><br>${getString(desc)}"
            }
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.support_rules_button)
            .setView(dialogView)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun fromHtml(html: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }
}
