package com.example.tools

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.database.CreatedToolEntity
import java.io.File
import java.util.UUID

/**
 * Intelligent Tool Generator for Joya Assistant.
 * Builds fully interactive, self-contained HTML5/CSS/JS applications for
 * Calculators, Invoices, Quizzes, EMI Planners, Websites, and Custom Utilities.
 */
object JoyaToolGenerator {

    fun generateTool(prompt: String): CreatedToolEntity {
        val lower = prompt.lowercase().trim()
        val id = UUID.randomUUID().toString()

        return when {
            lower.contains("bill") || lower.contains("बिल") || lower.contains("invoice") || lower.contains("रसीद") -> {
                CreatedToolEntity(
                    id = id,
                    title = "दुकान का डिजिटल बिल (Smart Invoice)",
                    prompt = prompt,
                    category = "व्यापार व बिलिंग",
                    htmlCode = generateInvoiceHtml()
                )
            }
            lower.contains("emi") || lower.contains("ईएमआई") || lower.contains("loan") || lower.contains("लोन") -> {
                CreatedToolEntity(
                    id = id,
                    title = "स्मार्ट EMI व लोन कैलकुलेटर",
                    prompt = prompt,
                    category = "फाइनेंस",
                    htmlCode = generateEmiCalculatorHtml()
                )
            }
            lower.contains("quiz") || lower.contains("क्विज़") || lower.contains("प्रश्न") || lower.contains("test") -> {
                CreatedToolEntity(
                    id = id,
                    title = "इंटरैक्टिव क्विज़ गेम (Quiz Maker)",
                    prompt = prompt,
                    category = "शिक्षा व खेल",
                    htmlCode = generateQuizHtml()
                )
            }
            lower.contains("calculator") || lower.contains("कैलकुलेटर") || lower.contains("hisab") || lower.contains("हिसाब") -> {
                CreatedToolEntity(
                    id = id,
                    title = "JARVIS साइंटिफिक कैलकुलेटर",
                    prompt = prompt,
                    category = "यूटिलिटी",
                    htmlCode = generateCalculatorHtml()
                )
            }
            lower.contains("website") || lower.contains("वेबसाइट") || lower.contains("shop") || lower.contains("portfolio") -> {
                CreatedToolEntity(
                    id = id,
                    title = "आधुनिक व्यावसायिक वेबसाइट",
                    prompt = prompt,
                    category = "वेबसाइट",
                    htmlCode = generateWebsiteHtml(prompt)
                )
            }
            lower.contains("habit") || lower.contains("आदत") || lower.contains("routine") -> {
                CreatedToolEntity(
                    id = id,
                    title = "दैनिक हैबिट ट्रैकर व स्ट्रीक",
                    prompt = prompt,
                    category = "लाइफस्टाइल",
                    htmlCode = generateHabitTrackerHtml()
                )
            }
            else -> {
                CreatedToolEntity(
                    id = id,
                    title = "कस्टम टूल: ${prompt.take(30)}",
                    prompt = prompt,
                    category = "कस्टम",
                    htmlCode = generateGenericCustomToolHtml(prompt)
                )
            }
        }
    }

    fun updateTool(existing: CreatedToolEntity, instruction: String): CreatedToolEntity {
        val lower = instruction.lowercase()
        var updatedHtml = existing.htmlCode

        // Real-time customization based on user feedback
        if (lower.contains("gold") || lower.contains("सुनहरा") || lower.contains("peela")) {
            updatedHtml = updatedHtml.replace("#00e5ff", "#ffd700")
        } else if (lower.contains("green") || lower.contains("हरा")) {
            updatedHtml = updatedHtml.replace("#00e5ff", "#00e676")
        } else if (lower.contains("red") || lower.contains("लाल")) {
            updatedHtml = updatedHtml.replace("#00e5ff", "#ff5252")
        }

        if (lower.contains("gst") || lower.contains("जीएसटी")) {
            updatedHtml = updatedHtml.replace("18%", "28% GST")
        }

        val note = "<!-- Updated with Joya voice: $instruction -->"
        updatedHtml = updatedHtml.replace("</body>", "$note\n</body>")

        return existing.copy(
            htmlCode = updatedHtml,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun pinToolToHomeScreen(context: Context, tool: CreatedToolEntity): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, "होम स्क्रीन शॉर्टकट समर्थित नहीं है", Toast.LENGTH_SHORT).show()
            return false
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("open_tool_id", tool.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "tool_${tool.id}")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setShortLabel(tool.title.take(15))
            .setLongLabel(tool.title)
            .setIntent(launchIntent)
            .build()

        val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, pinShortcutInfo)
        val successCallback = PendingIntent.getBroadcast(
            context,
            0,
            pinnedShortcutCallbackIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val pinned = ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, successCallback.intentSender)
        if (pinned) {
            Toast.makeText(context, "'${tool.title}' का आइकन होम स्क्रीन पर जोड़ दिया गया!", Toast.LENGTH_LONG).show()
        }
        return pinned
    }

    fun shareTool(context: Context, tool: CreatedToolEntity) {
        try {
            val cacheDir = File(context.cacheDir, "shared_tools")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "${tool.title.replace(Regex("[^a-zA-Z0-9]"), "_")}.html")
            file.writeText(tool.htmlCode)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, tool.title)
                putExtra(Intent.EXTRA_TEXT, "Joya Assistant द्वारा तैयार किया गया टूल: ${tool.title}\n\nखोलने के लिए अटैचमेंट ब्राउज़र में खोलें।")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "टूल शेयर करें"))
        } catch (e: Exception) {
            // Fallback plain text share
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, tool.title)
                putExtra(Intent.EXTRA_TEXT, "Joya टूल '${tool.title}':\n\n${tool.prompt}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(shareIntent, "टूल शेयर करें"))
            } catch (ex: Exception) {
                Toast.makeText(context, "शेयर करने में असमर्थ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateInvoiceHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>दुकान का बिल</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #0a0e17; color: #e2e8f0; padding: 16px; }
                .card { background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 18px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                h1 { color: #00e5ff; font-size: 20px; text-align: center; margin-bottom: 12px; }
                .sub { text-align: center; color: #94a3b8; font-size: 12px; margin-bottom: 16px; }
                .input-group { margin-bottom: 12px; }
                label { display: block; font-size: 12px; color: #94a3b8; margin-bottom: 4px; }
                input, select { width: 100%; padding: 10px 12px; background: #1e293b; border: 1px solid #334155; border-radius: 8px; color: #fff; font-size: 14px; outline: none; }
                input:focus { border-color: #00e5ff; }
                table { width: 100%; border-collapse: collapse; margin-top: 14px; font-size: 13px; }
                th { background: #1e293b; color: #00e5ff; padding: 10px; text-align: left; }
                td { padding: 8px 10px; border-bottom: 1px solid #1f2937; }
                .btn { width: 100%; padding: 12px; border: none; border-radius: 10px; font-weight: 600; font-size: 14px; cursor: pointer; margin-top: 12px; }
                .btn-add { background: #00e5ff; color: #000; }
                .btn-print { background: #ffd700; color: #000; }
                .btn-wa { background: #25d366; color: #fff; }
                .del-btn { background: #ef4444; color: #fff; border: none; border-radius: 6px; padding: 4px 8px; cursor: pointer; }
                .total-box { margin-top: 16px; padding: 14px; background: #1e293b; border-radius: 12px; text-align: right; }
                .total-amount { font-size: 22px; color: #ffd700; font-weight: bold; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>⚡ JOYA SMART BILLING</h1>
                <p class="sub">आधुनिक बिलिंग व इनवॉइस जनरेटर</p>
                <div class="input-group">
                    <label>दुकान / विक्रेता का नाम</label>
                    <input type="text" id="shopName" value="मेरी सुपर शॉप" placeholder="दुकान का नाम">
                </div>
                <div class="input-group">
                    <label>ग्राहक का नाम व मोबाइल</label>
                    <input type="text" id="custName" placeholder="जैसे: राहुल शर्मा - 9876543210">
                </div>
                <div style="display:flex; gap: 8px;">
                    <div style="flex:2;">
                        <label>सामान का नाम</label>
                        <input type="text" id="itemName" placeholder="जैसे: दाल 1kg">
                    </div>
                    <div style="flex:1;">
                        <label>मात्रा</label>
                        <input type="number" id="itemQty" value="1" min="1">
                    </div>
                    <div style="flex:1;">
                        <label>दर (₹)</label>
                        <input type="number" id="itemRate" value="100" min="0">
                    </div>
                </div>
                <button class="btn btn-add" onclick="addItem()">+ सामान जोड़ें</button>

                <table>
                    <thead>
                        <tr>
                            <th>सामान</th>
                            <th>मात्रा</th>
                            <th>दर</th>
                            <th>कुल</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody id="itemsList"></tbody>
                </table>

                <div class="total-box">
                    <div>सबटोटल: ₹<span id="subTotal">0</span></div>
                    <div style="color:#94a3b8; font-size:12px;">GST (5%): ₹<span id="gstAmount">0</span></div>
                    <div style="margin-top:6px;">कुल राशि: <span class="total-amount">₹<span id="grandTotal">0</span></span></div>
                </div>

                <button class="btn btn-wa" onclick="sendWhatsApp()">💬 WhatsApp पर बिल भेजें</button>
                <button class="btn btn-print" onclick="window.print()">🖨️ बिल प्रिंट करें / PDF सेव करें</button>
            </div>

            <script>
                let items = [];
                function addItem() {
                    const name = document.getElementById('itemName').value.trim();
                    const qty = parseFloat(document.getElementById('itemQty').value) || 1;
                    const rate = parseFloat(document.getElementById('itemRate').value) || 0;
                    if (!name) return alert('कृपया सामान का नाम लिखें');
                    items.push({ name, qty, rate, total: qty * rate });
                    document.getElementById('itemName').value = '';
                    render();
                }
                function removeItem(index) {
                    items.splice(index, 1);
                    render();
                }
                function render() {
                    const tbody = document.getElementById('itemsList');
                    tbody.innerHTML = '';
                    let sub = 0;
                    items.forEach((it, i) => {
                        sub += it.total;
                        tbody.innerHTML += '<tr><td>' + it.name + '</td><td>' + it.qty + '</td><td>₹' + it.rate + '</td><td>₹' + it.total + '</td><td><button class="del-btn" onclick="removeItem(' + i + ')">×</button></td></tr>';
                    });
                    const gst = Math.round(sub * 0.05);
                    document.getElementById('subTotal').innerText = sub;
                    document.getElementById('gstAmount').innerText = gst;
                    document.getElementById('grandTotal').innerText = sub + gst;
                }
                function sendWhatsApp() {
                    const shop = document.getElementById('shopName').value;
                    const cust = document.getElementById('custName').value;
                    const total = document.getElementById('grandTotal').innerText;
                    let text = '*' + shop + ' - बिल*\nग्राहक: ' + cust + '\n\n';
                    items.forEach(it => { text += '• ' + it.name + ' x ' + it.qty + ' = ₹' + it.total + '\n'; });
                    text += '\n*कुल योग: ₹' + total + '*\nधन्यवाद! फिर पधारें।';
                    window.open('https://api.whatsapp.com/send?text=' + encodeURIComponent(text));
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateEmiCalculatorHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>EMI कैलकुलेटर</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #0a0e17; color: #e2e8f0; padding: 16px; }
                .card { background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 20px; }
                h1 { color: #00e5ff; font-size: 20px; text-align: center; margin-bottom: 16px; }
                .group { margin-bottom: 18px; }
                .label-row { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 6px; }
                input[type=range] { width: 100%; accent-color: #00e5ff; }
                .result-box { background: #1e293b; border-radius: 12px; padding: 18px; text-align: center; margin-top: 20px; border: 1px solid #334155; }
                .emi-val { font-size: 32px; font-weight: bold; color: #ffd700; margin: 8px 0; }
                .detail-row { display: flex; justify-content: space-between; margin-top: 10px; font-size: 13px; color: #94a3b8; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>⚡ JOYA SMART EMI PLANNER</h1>
                <div class="group">
                    <div class="label-row"><span>लोन राशि (₹):</span><b id="pVal">₹5,00,000</b></div>
                    <input type="range" id="pRange" min="10000" max="5000000" step="10000" value="500000" oninput="calc()">
                </div>
                <div class="group">
                    <div class="label-row"><span>ब्याज दर (% प्रति वर्ष):</span><b id="rVal">9.5%</b></div>
                    <input type="range" id="rRange" min="1" max="25" step="0.1" value="9.5" oninput="calc()">
                </div>
                <div class="group">
                    <div class="label-row"><span>अवधि (साल):</span><b id="tVal">5 साल</b></div>
                    <input type="range" id="tRange" min="1" max="30" step="1" value="5" oninput="calc()">
                </div>

                <div class="result-box">
                    <div style="color: #94a3b8; font-size: 13px;">मासिक EMI</div>
                    <div class="emi-val" id="emiVal">₹0</div>
                    <div class="detail-row"><span>मूलधन:</span><b id="pDetail">₹0</b></div>
                    <div class="detail-row"><span>कुल ब्याज:</span><b id="iDetail">₹0</b></div>
                    <div class="detail-row" style="border-top: 1px solid #334155; padding-top: 8px; color: #fff;"><span>कुल भुगतान:</span><b id="totDetail">₹0</b></div>
                </div>
            </div>
            <script>
                function calc() {
                    const p = parseFloat(document.getElementById('pRange').value);
                    const r = parseFloat(document.getElementById('rRange').value) / (12 * 100);
                    const t = parseFloat(document.getElementById('tRange').value) * 12;

                    document.getElementById('pVal').innerText = '₹' + p.toLocaleString('en-IN');
                    document.getElementById('rVal').innerText = document.getElementById('rRange').value + '%';
                    document.getElementById('tVal').innerText = document.getElementById('tRange').value + ' साल';

                    const emi = (p * r * Math.pow(1 + r, t)) / (Math.pow(1 + r, t) - 1);
                    const totalPayment = emi * t;
                    const totalInterest = totalPayment - p;

                    document.getElementById('emiVal').innerText = '₹' + Math.round(emi).toLocaleString('en-IN');
                    document.getElementById('pDetail').innerText = '₹' + p.toLocaleString('en-IN');
                    document.getElementById('iDetail').innerText = '₹' + Math.round(totalInterest).toLocaleString('en-IN');
                    document.getElementById('totDetail').innerText = '₹' + Math.round(totalPayment).toLocaleString('en-IN');
                }
                calc();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateCalculatorHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>JARVIS Calculator</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: monospace; }
                body { background: #050b14; color: #fff; padding: 12px; display: flex; justify-content: center; }
                .calc { width: 100%; max-width: 360px; background: #0b1528; border: 1px solid #1e3a8a; border-radius: 20px; padding: 18px; box-shadow: 0 0 25px rgba(0,229,255,0.2); }
                .display { background: #030712; border: 1px solid #1f2937; border-radius: 12px; padding: 16px; text-align: right; margin-bottom: 16px; }
                .history { font-size: 13px; color: #64748b; height: 18px; overflow: hidden; }
                .current { font-size: 32px; color: #00e5ff; font-weight: bold; overflow-x: auto; }
                .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
                button { padding: 16px; border: none; border-radius: 12px; background: #172554; color: #fff; font-size: 18px; font-weight: bold; cursor: pointer; transition: 0.1s; }
                button:active { background: #1e3a8a; transform: scale(0.96); }
                .btn-op { background: #1e3a8a; color: #38bdf8; }
                .btn-eq { background: #00e5ff; color: #000; grid-column: span 2; }
                .btn-cl { background: #ef4444; color: #fff; }
            </style>
        </head>
        <body>
            <div class="calc">
                <div style="text-align: center; color: #00e5ff; font-size: 13px; margin-bottom: 8px;">JARVIS QUANTUM CALCULATOR</div>
                <div class="display">
                    <div class="history" id="hist"></div>
                    <div class="current" id="disp">0</div>
                </div>
                <div class="grid">
                    <button class="btn-cl" onclick="cl()">C</button>
                    <button class="btn-op" onclick="op('(')">(</button>
                    <button class="btn-op" onclick="op(')')">)</button>
                    <button class="btn-op" onclick="op('/')">÷</button>

                    <button onclick="num('7')">7</button>
                    <button onclick="num('8')">8</button>
                    <button onclick="num('9')">9</button>
                    <button class="btn-op" onclick="op('*')">×</button>

                    <button onclick="num('4')">4</button>
                    <button onclick="num('5')">5</button>
                    <button onclick="num('6')">6</button>
                    <button class="btn-op" onclick="op('-')">−</button>

                    <button onclick="num('1')">1</button>
                    <button onclick="num('2')">2</button>
                    <button onclick="num('3')">3</button>
                    <button class="btn-op" onclick="op('+')">+</button>

                    <button onclick="num('0')">0</button>
                    <button onclick="num('.')">.</button>
                    <button class="btn-eq" onclick="eq()">=</button>
                </div>
            </div>
            <script>
                let expr = '';
                function num(n) { if (expr === '0' && n !== '.') expr = ''; expr += n; upd(); }
                function op(o) { expr += o; upd(); }
                function cl() { expr = ''; upd('0'); document.getElementById('hist').innerText = ''; }
                function eq() {
                    try {
                        document.getElementById('hist').innerText = expr;
                        const res = eval(expr);
                        expr = String(res);
                        upd();
                    } catch(e) { upd('त्रुटि'); }
                }
                function upd(val) { document.getElementById('disp').innerText = val !== undefined ? val : (expr || '0'); }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateQuizHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Joya Quiz</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #0a0e17; color: #fff; padding: 16px; }
                .card { background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 20px; }
                h1 { color: #ffd700; font-size: 20px; text-align: center; margin-bottom: 6px; }
                .score { text-align: center; color: #00e5ff; font-weight: bold; margin-bottom: 16px; font-size: 14px; }
                .q-text { font-size: 16px; line-height: 24px; margin-bottom: 16px; font-weight: 600; }
                .opt { width: 100%; padding: 14px 16px; margin-bottom: 10px; background: #1e293b; border: 1px solid #334155; border-radius: 10px; color: #e2e8f0; font-size: 14px; text-align: left; cursor: pointer; }
                .correct { background: #059669 !important; border-color: #10b981 !important; color: #fff !important; }
                .wrong { background: #dc2626 !important; border-color: #ef4444 !important; color: #fff !important; }
                .btn-next { width: 100%; padding: 12px; background: #00e5ff; color: #000; border: none; border-radius: 10px; font-weight: bold; margin-top: 10px; cursor: pointer; display: none; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>⚡ JOYA SMART QUIZ</h1>
                <div class="score">स्कोर: <span id="score">0</span> / <span id="total">4</span></div>
                <div id="qBox">
                    <div class="q-text" id="qText">सवाल लोड हो रहा है...</div>
                    <div id="opts"></div>
                    <button class="btn-next" id="nextBtn" onclick="nextQuestion()">अगला सवाल ➔</button>
                </div>
            </div>
            <script>
                const questions = [
                    { q: "भारत का पहला कृत्रिम उपग्रह कौन सा था?", opts: ["भास्कर", "आर्यभट्ट", "रोहिणी", "इनसैट"], ans: 1 },
                    { q: "सौरमंडल का सबसे बड़ा ग्रह कौन सा है?", opts: ["मंगल", "बृहस्पति (Jupiter)", "शनि", "शुक्र"], ans: 1 },
                    { q: "कंप्यूटर का मस्तिष्क किसे कहा जाता है?", opts: ["RAM", "हार्ड डिस्क", "CPU", "मॉनिटर"], ans: 2 },
                    { q: "प्रकाश की गति लगभग कितनी होती है?", opts: ["3 लाख किमी/सेकंड", "1 लाख किमी/सेकंड", "50 हजार किमी/सेकंड", "300 मीटर/सेकंड"], ans: 0 }
                ];
                let cur = 0, score = 0;
                function load() {
                    if (cur >= questions.length) {
                        document.getElementById('qBox').innerHTML = '<div style="text-align:center; padding: 20px;"><h2>🎉 क्विज़ समाप्त!</h2><p style="margin-top:10px; font-size:18px; color:#ffd700;">आपका अंतिम स्कोर: ' + score + ' / ' + questions.length + '</p><button class="btn-next" style="display:block; margin-top:20px;" onclick="location.reload()">दोबारा खेलें</button></div>';
                        return;
                    }
                    const item = questions[cur];
                    document.getElementById('qText').innerText = (cur + 1) + ". " + item.q;
                    const optsDiv = document.getElementById('opts');
                    optsDiv.innerHTML = '';
                    item.opts.forEach((op, idx) => {
                        optsDiv.innerHTML += '<button class="opt" onclick="check(' + idx + ')">' + op + '</button>';
                    });
                    document.getElementById('nextBtn').style.display = 'none';
                }
                function check(sel) {
                    const item = questions[cur];
                    const buttons = document.querySelectorAll('.opt');
                    buttons.forEach((b, i) => {
                        b.disabled = true;
                        if (i === item.ans) b.classList.add('correct');
                        else if (i === sel) b.classList.add('wrong');
                    });
                    if (sel === item.ans) { score++; document.getElementById('score').innerText = score; }
                    document.getElementById('nextBtn').style.display = 'block';
                }
                function nextQuestion() { cur++; load(); }
                load();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateWebsiteHtml(topic: String): String {
        val cleanTopic = topic.replace(Regex("(?i)(website|banao|bana|do|वेबसाइट|बनाओ|meri|ki)"), "").trim().ifEmpty { "दुकान व व्यापार" }
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$cleanTopic</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #050b14; color: #fff; line-height: 1.6; }
                header { background: linear-gradient(135deg, #0f172a, #1e1b4b); padding: 30px 20px; text-align: center; border-bottom: 2px solid #00e5ff; }
                h1 { font-size: 26px; color: #ffd700; margin-bottom: 6px; }
                p.lead { color: #94a3b8; font-size: 14px; }
                .container { padding: 20px; max-width: 600px; margin: auto; }
                .hero-badge { display: inline-block; background: #00e5ff; color: #000; font-weight: bold; font-size: 11px; padding: 4px 10px; border-radius: 20px; margin-bottom: 8px; }
                .card { background: #0b1528; border: 1px solid #1e3a8a; border-radius: 14px; padding: 18px; margin-bottom: 16px; }
                h2 { color: #00e5ff; font-size: 18px; margin-bottom: 12px; }
                .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
                .item-card { background: #111e38; padding: 12px; border-radius: 10px; text-align: center; border: 1px solid #1e293b; }
                .btn { display: block; width: 100%; text-align: center; background: #25d366; color: #fff; text-decoration: none; padding: 14px; border-radius: 12px; font-weight: bold; margin-top: 14px; }
                footer { text-align: center; color: #64748b; font-size: 12px; padding: 20px; }
            </style>
        </head>
        <body>
            <header>
                <div class="hero-badge">OFFICIAL STORE</div>
                <h1>$cleanTopic</h1>
                <p class="lead">सर्वश्रेष्ठ गुणवत्ता व तेज़ डिलीवरी का भरोसेमंद केंद्र</p>
            </header>
            <div class="container">
                <div class="card">
                    <h2>⭐ हमारी खासियत</h2>
                    <div class="grid">
                        <div class="item-card">
                            <div style="font-size:24px;">🚚</div>
                            <b>तेज़ डिलीवरी</b>
                            <p style="font-size:11px; color:#94a3b8;">घर बैठे तुरंत ऑर्डर</p>
                        </div>
                        <div class="item-card">
                            <div style="font-size:24px;">💯</div>
                            <b>100% असली</b>
                            <p style="font-size:11px; color:#94a3b8;">उच्च गुणवत्ता गारंटी</p>
                        </div>
                    </div>
                </div>
                <div class="card">
                    <h2>📞 सीधा संपर्क व ऑर्डर</h2>
                    <p style="font-size:13px; color:#cbd5e1;">हमारे पास सभी प्रकार के प्रॉडक्ट्स उपलब्ध हैं। विशेष डिस्काउंट के लिए अभी व्हाट्सएप करें।</p>
                    <a href="https://api.whatsapp.com/send?text=Namaste!%20Mujhe%20order%20karna%20hai" class="btn">💬 WhatsApp पर ऑर्डर करें</a>
                </div>
            </div>
            <footer>Powered by Joya Assistant • Iron Man JARVIS Tech</footer>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateHabitTrackerHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Habit Tracker</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #0a0e17; color: #fff; padding: 16px; }
                .card { background: #111827; border: 1px solid #1f2937; border-radius: 16px; padding: 18px; }
                h1 { color: #00e5ff; font-size: 20px; text-align: center; margin-bottom: 16px; }
                .habit-row { display: flex; align-items: center; justify-content: space-between; background: #1e293b; padding: 12px 14px; border-radius: 10px; margin-bottom: 10px; }
                .check-btn { width: 28px; height: 28px; border-radius: 50%; border: 2px solid #00e5ff; background: transparent; cursor: pointer; }
                .checked { background: #00e5ff !important; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>⚡ DAILY HABIT & STREAK</h1>
                <div class="habit-row">
                    <div><b>💧 3 लीटर पानी पिएं</b><div style="font-size:11px; color:#94a3b8;">स्ट्रीक: 🔥 5 दिन</div></div>
                    <button class="check-btn" onclick="toggle(this)"></button>
                </div>
                <div class="habit-row">
                    <div><b>🏃 30 मिनट कसरत / वॉक</b><div style="font-size:11px; color:#94a3b8;">स्ट्रीक: 🔥 3 दिन</div></div>
                    <button class="check-btn" onclick="toggle(this)"></button>
                </div>
                <div class="habit-row">
                    <div><b>📚 20 मिनट किताब पढ़ना</b><div style="font-size:11px; color:#94a3b8;">स्ट्रीक: 🔥 7 दिन</div></div>
                    <button class="check-btn" onclick="toggle(this)"></button>
                </div>
            </div>
            <script>
                function toggle(btn) { btn.classList.toggle('checked'); }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateGenericCustomToolHtml(prompt: String): String {
        return """
        <!DOCTYPE html>
        <html lang="hi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$prompt</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #050b14; color: #fff; padding: 16px; }
                .card { background: #0b1528; border: 1px solid #1e3a8a; border-radius: 16px; padding: 20px; box-shadow: 0 0 20px rgba(0,229,255,0.15); }
                h1 { color: #00e5ff; font-size: 20px; text-align: center; margin-bottom: 8px; }
                p { color: #94a3b8; font-size: 13px; text-align: center; margin-bottom: 16px; }
                input, textarea { width: 100%; padding: 12px; background: #111e38; border: 1px solid #1e293b; border-radius: 8px; color: #fff; margin-bottom: 12px; outline: none; }
                button { width: 100%; padding: 14px; background: #00e5ff; color: #000; font-weight: bold; border: none; border-radius: 10px; cursor: pointer; }
                .output { margin-top: 16px; padding: 14px; background: #111e38; border-radius: 10px; border-left: 3px solid #ffd700; display: none; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>⚡ JOYA SMART UTILITY</h1>
                <p>$prompt</p>
                <input type="text" id="userInput" placeholder="यहाँ इनपुट दें...">
                <button onclick="process()">कार्य निष्पादित करें</button>
                <div class="output" id="out">
                    <b style="color:#ffd700;">परिणाम:</b>
                    <div id="outText" style="margin-top:6px; font-size:14px;"></div>
                </div>
            </div>
            <script>
                function process() {
                    const val = document.getElementById('userInput').value;
                    if (!val) return alert('कृपया कुछ लिखें');
                    document.getElementById('out').style.display = 'block';
                    document.getElementById('outText').innerText = 'Joya ने प्रोसेस किया: ' + val;
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
