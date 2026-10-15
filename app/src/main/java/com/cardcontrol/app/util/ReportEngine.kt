package com.cardcontrol.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.cardcontrol.app.data.local.HolderEntity
import com.cardcontrol.app.data.local.InstallmentEntity
import com.cardcontrol.app.security.SecuritySanitizer
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object ReportEngine {
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    fun formatCurrency(value: Double): String = currencyFormatter.format(value)

    fun buildWhatsAppSummaryText(
        holder: HolderEntity,
        month: Int,
        year: Int,
        installments: List<InstallmentEntity>,
        paidInMonth: Double,
        globalCommitted: Double
    ): String {
        val totalMonth = installments.sumOf { it.amount }
        val netDue = (totalMonth - paidInMonth).coerceAtLeast(0.0)
        val availableSubLimit = holder.subLimit - globalCommitted

        return buildString {
            appendLine("*[CardControl] Fatura %02d/%d*".format(month, year))
            appendLine("Titular/Dependente: *${SecuritySanitizer.sanitizeHeaderField(holder.name)}*")
            appendLine("--------------------------------")
            if (installments.isEmpty()) {
                appendLine("Nenhum lancamento neste periodo.")
            } else {
                installments.forEach { item ->
                    val parcelTag = if (item.totalInstallments > 1) " (${item.installmentNumber}/${item.totalInstallments})" else ""
                    appendLine("- ${item.merchantSnapshot}$parcelTag: *${formatCurrency(item.amount)}*")
                }
            }
            appendLine("--------------------------------")
            appendLine("Total do Mes: *${formatCurrency(totalMonth)}*")
            appendLine("Pago no Mes: *${formatCurrency(paidInMonth)}*")
            appendLine("*Saldo a Pagar: ${formatCurrency(netDue)}*")
            if (holder.subLimit > 0) {
                appendLine("Sublimite Disponivel: *${formatCurrency(availableSubLimit)}*")
                if (availableSubLimit < 0) appendLine("[!] *ATENCAO: Sublimite excedido!*")
            }
        }
    }

    fun generatePdfReport(
        context: Context,
        holder: HolderEntity,
        month: Int,
        year: Int,
        installments: List<InstallmentEntity>,
        paidInMonth: Double,
        globalCommitted: Double
    ): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { color = Color.rgb(20, 20, 20); textSize = 18f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.rgb(50, 50, 50); textSize = 12f }
        val boldPaint = Paint().apply { color = Color.rgb(0, 0, 0); textSize = 13f; isFakeBoldText = true }

        var y = 50f
        canvas.drawText("RELATORIO DE FATURA - %02d/%d".format(month, year), 40f, y, titlePaint)
        y += 28f
        canvas.drawText("Responsavel: ${SecuritySanitizer.sanitizeHeaderField(holder.name)}", 40f, y, boldPaint)
        y += 20f

        val totalMonth = installments.sumOf { it.amount }
        val netDue = (totalMonth - paidInMonth).coerceAtLeast(0.0)
        val availableSubLimit = holder.subLimit - globalCommitted

        canvas.drawText("Total: ${formatCurrency(totalMonth)} | Pago: ${formatCurrency(paidInMonth)} | A Pagar: ${formatCurrency(netDue)}", 40f, y, bodyPaint)
        y += 18f
        if (holder.subLimit > 0) {
            canvas.drawText("Sublimite: ${formatCurrency(holder.subLimit)} | Disponivel: ${formatCurrency(availableSubLimit)}", 40f, y, bodyPaint)
            y += 24f
        }

        canvas.drawLine(40f, y, 555f, y, bodyPaint)
        y += 22f
        canvas.drawText("LANCAMENTOS DO PERIODO:", 40f, y, boldPaint)
        y += 22f

        installments.take(28).forEach { item ->
            val parcelInfo = if (item.totalInstallments > 1) " [${item.installmentNumber}/${item.totalInstallments}]" else " [A vista]"
            canvas.drawText("• ${SecuritySanitizer.sanitizeHeaderField(item.merchantSnapshot, 40)}$parcelInfo", 45f, y, bodyPaint)
            canvas.drawText(formatCurrency(item.amount), 460f, y, boldPaint)
            y += 18f
        }

        pdf.finishPage(page)

        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeHolderSegment = SecuritySanitizer.sanitizeFileNameSegment(holder.name)
        val file = File(dir, "Fatura_${safeHolderSegment}_%02d_%d.pdf".format(month, year))

        check(file.canonicalPath.startsWith(dir.canonicalPath)) { "Caminho de arquivo invalido" }

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun sharePdfAndTextViaWhatsApp(
        context: Context,
        holder: HolderEntity,
        month: Int,
        year: Int,
        installments: List<InstallmentEntity>,
        paidInMonth: Double,
        globalCommitted: Double
    ) {
        val pdfFile = generatePdfReport(context, holder, month, year, installments, paidInMonth, globalCommitted)
        val summaryText = buildWhatsAppSummaryText(holder, month, year, installments, paidInMonth, globalCommitted)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val cleanPhone = holder.whatsapp.filter { it.isDigit() }
            if (cleanPhone.isNotBlank()) {
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
            }
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Enviar Fatura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Compartilhar Fatura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun sendInstantPurchaseEmail(
        senderEmail: String,
        appPassword: String,
        recipientEmail: String,
        holderName: String,
        cardName: String,
        merchant: String,
        amount: Double,
        installments: Int,
        remainingSubLimit: Double
    ) {
        try {
            val safeMerchant = SecuritySanitizer.sanitizeHeaderField(merchant, 50)
            val safeHolder = SecuritySanitizer.sanitizeHeaderField(holderName, 50)
            val safeCard = SecuritySanitizer.sanitizeHeaderField(cardName, 50)

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(senderEmail, appPassword)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail, "CardControl Automatico"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, true))
                subject = "[CardControl] Nova Compra: $safeMerchant"
                val statusAlert = if (remainingSubLimit < 0) "\n[!] ATENCAO: Seu sublimite foi ultrapassado!" else ""
                setText(
                    """
                    Ola, $safeHolder!
                    
                    Compra registrada no cartao $safeCard:
                    • Estabelecimento: $safeMerchant
                    • Valor Total: ${formatCurrency(amount)}
                    • Parcelas: ${installments}x
                    • Limite Restante: ${formatCurrency(remainingSubLimit)}
                    $statusAlert
                    """.trimIndent(),
                    "UTF-8"
                )
            }
            Transport.send(message)
        } catch (_: Exception) {
        }
    }
}
