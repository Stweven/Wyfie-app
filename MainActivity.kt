
package com.example.billionairecoach

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.pow

const val DEFAULT_CURRENCY = "KES"
const val DEFAULT_MONTHLY_SALARY = 30000.0
const val DEFAULT_STRATEGIES_URL = "https://raw.githubusercontent.com/public-templates/blank/main/strategies.json"

val Context.dataStore by preferencesDataStore(name = "billionaire_prefs")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("weekly_reminder", "Weekly Reminder", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        setContent { BillionaireCoachScreen() }
    }
}

@Composable
fun BillionaireCoachScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val salaryKey = stringPreferencesKey("salary")
    val netKey = stringPreferencesKey("net")
    val savePctKey = stringPreferencesKey("save_pct")
    val returnKey = stringPreferencesKey("return")
    val yearsKey = intPreferencesKey("plan_years")
    val targetKey = stringPreferencesKey("target")
    val currencyKey = stringPreferencesKey("currency")
    val strategiesUrlKey = stringPreferencesKey("strategies_url")
    val strategiesJsonKey = stringPreferencesKey("strategies_json")
    val strategiesUpdatedAtKey = stringPreferencesKey("strategies_updated_at")

    val checklistKeys = (1..10).map { booleanPreferencesKey("check_$it") }

    var salaryText by remember { mutableStateOf(String.format("%.0f", DEFAULT_MONTHLY_SALARY)) }
    var currency by remember { mutableStateOf(DEFAULT_CURRENCY) }
    var monthlySavePercentText by remember { mutableStateOf("50") }
    var netWorthText by remember { mutableStateOf("0") }
    var targetText by remember { mutableStateOf("1000000000") }
    var expectedReturnText by remember { mutableStateOf("0.12") }
    var planYearsText by remember { mutableStateOf("30") }
    var strategiesUrl by remember { mutableStateOf(DEFAULT_STRATEGIES_URL) }
    var strategiesJson by remember { mutableStateOf("[]") }
    var strategiesUpdatedAt by remember { mutableStateOf("Never") }

    val checks = remember { mutableStateListOf<Boolean>().apply { repeat(10) { add(false) } } }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        salaryText = prefs[salaryKey] ?: String.format("%.0f", DEFAULT_MONTHLY_SALARY)
        netWorthText = prefs[netKey] ?: "0"
        monthlySavePercentText = prefs[savePctKey] ?: "50"
        expectedReturnText = prefs[returnKey] ?: "0.12"
        planYearsText = prefs[yearsKey]?.toString() ?: "30"
        targetText = prefs[targetKey] ?: "1000000000"
        currency = prefs[currencyKey] ?: DEFAULT_CURRENCY
        strategiesUrl = prefs[strategiesUrlKey] ?: DEFAULT_STRATEGIES_URL
        strategiesJson = prefs[strategiesJsonKey] ?: "[]"
        strategiesUpdatedAt = prefs[strategiesUpdatedAtKey] ?: "Never"
        checklistKeys.forEachIndexed { idx, key -> checks[idx] = prefs[key] ?: false }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Billionaire Coach") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Welcome to your Billionaire Journey", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))

            Text("Inputs", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = salaryText, onValueChange = { salaryText = it }, label = { Text("Monthly take-home salary ($currency)") })
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = monthlySavePercentText, onValueChange = { monthlySavePercentText = it }, label = { Text("% to save") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = expectedReturnText, onValueChange = { expectedReturnText = it }, label = { Text("Expected annual return (e.g., 0.12)") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = planYearsText, onValueChange = { planYearsText = it }, label = { Text("Years (int)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = targetText, onValueChange = { targetText = it }, label = { Text("Target net worth") }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    context.dataStore.edit { prefs ->
                        prefs[salaryKey] = salaryText
                        prefs[netKey] = netWorthText
                        prefs[savePctKey] = monthlySavePercentText
                        prefs[returnKey] = expectedReturnText
                        prefs[yearsKey] = planYearsText.toIntOrNull() ?: 30
                        prefs[targetKey] = targetText
                        prefs[currencyKey] = currency
                        prefs[strategiesUrlKey] = strategiesUrl
                        prefs[strategiesJsonKey] = strategiesJson
                        prefs[strategiesUpdatedAtKey] = strategiesUpdatedAt
                        checklistKeys.forEachIndexed { idx, key -> prefs[key] = checks[idx] }
                    }
                }
            }) { Text("Save inputs & checklist") }

            Spacer(Modifier.height(12.dp))
            val salary = salaryText.toDoubleOrNull() ?: DEFAULT_MONTHLY_SALARY
            val net = netWorthText.toDoubleOrNull() ?: 0.0
            val savePct = (monthlySavePercentText.toDoubleOrNull() ?: 0.0) / 100.0
            val expectedReturn = expectedReturnText.toDoubleOrNull() ?: 0.12
            val planYears = planYearsText.toIntOrNull() ?: 30
            val monthlySave = salary * savePct
            val target = targetText.toDoubleOrNull() ?: 1_000_000_000.0

            Text("Using: ${formatMoney(salary,currency)}, saving=${formatMoney(monthlySave,currency)} per month, expected return=${expectedReturn*100}%")
            Spacer(Modifier.height(6.dp))
            val yearsAtCurrent = remember(salaryText, netWorthText, monthlySavePercentText, expectedReturnText) { yearsToTarget(net, target, monthlySave, expectedReturn) }
            Text("Estimated years to reach target at current saving rate: $yearsAtCurrent years")
            Spacer(Modifier.height(6.dp))
            val requiredMonthly = remember(netWorthText, targetText, expectedReturnText, planYearsText) { requiredMonthlyToHitInYears(net, target, expectedReturn, planYears) }
            Text("Required monthly investment to hit ${formatMoney(target,currency)} in $planYears years: ${formatMoney(requiredMonthly,currency)}")

            Spacer(Modifier.height(12.dp))
            Text("Checklist (tap to mark complete)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            val checklistItems = listOf(
                "Open investment account and enable automated monthly deposit",
                "Set automatic transfer of savings on payday",
                "Create outline for a 4-week paid course",
                "Validate an MVP with 10 paying customers in 90 days",
                "Build tutoring/product funnel (website/contact form)",
                "Set emergency fund = 6 months essentials",
                "Create accounting folder and consult an accountant",
                "Setup tracking sheet for net worth & cash flow",
                "Plan a quarterly review & rebalance",
                "Export progress to CSV and backup"
            )

            LazyColumn(modifier = Modifier.height(240.dp)) {
                itemsIndexed(checklistItems) { idx, item ->
                    Row(modifier = Modifier.fillParentMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item, modifier = Modifier.weight(1f))
                        Checkbox(checked = checks[idx], onCheckedChange = { checked ->
                            checks[idx] = checked
                            scope.launch(Dispatchers.IO) {
                                context.dataStore.edit { prefs -> prefs[checklistKeys[idx]] = checks[idx] }
                            }
                        })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scheduleWeeklyReminder(context) }) { Text("Enable weekly reminders") }
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val csv = buildCsvReport(salary, monthlySave, net, target, expectedReturn, checks, checklistItems)
                        val file = saveCsvToFile(context, csv)
                        if (file != null) shareFile(context, file)
                    }
                }) { Text("Export progress to CSV") }
            }

            Spacer(Modifier.height(16.dp))
            Text("Live Strategies (auto-updating)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = strategiesUrl, onValueChange = { strategiesUrl = it }, label = { Text("Strategy source (editable URL)") })
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val (json, updatedAt) = fetchStrategiesOnce(strategiesUrl)
                        if (json != null) {
                            strategiesJson = json
                            strategiesUpdatedAt = updatedAt
                            context.dataStore.edit { prefs ->
                                prefs[strategiesJsonKey] = strategiesJson
                                prefs[strategiesUpdatedAtKey] = strategiesUpdatedAt
                                prefs[strategiesUrlKey] = strategiesUrl
                            }
                        }
                    }
                }) { Text("Update strategies now") }
                Text("Last updated: $strategiesUpdatedAt")
            }

            Spacer(Modifier.height(8.dp))
            val parsed = remember(strategiesJson) { parseStrategies(strategiesJson) }
            LazyColumn(modifier = Modifier.height(200.dp)) {
                itemsIndexed(parsed) { idx, item ->
                    Column(modifier = Modifier.fillParentMaxWidth().padding(6.dp)) {
                        Text("${idx+1}. ${item.title}", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(item.summary)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Defaults localized to KES 30,000. Change constants at top of file to adjust.")
        }
    }
}

data class Strategy(val title: String, val summary: String)

fun parseStrategies(json: String): List<Strategy> {
    return try {
        val arr = org.json.JSONArray(json)
        val out = mutableListOf<Strategy>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Strategy(o.optString("title", "Untitled"), o.optString("summary", "")))
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}

fun fetchStrategiesOnce(urlStr: String): Pair<String?, String> {
    return try {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.doInput = true
        val code = conn.responseCode
        if (code == 200) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                sb.append(line).append('\n')
                line = reader.readLine()
            }
            reader.close()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val now = sdf.format(Date())
            Pair(sb.toString(), now)
        } else Pair(null, "Failed: $code")
    } catch (e: Exception) {
        Pair(null, "Error: ${e.message}")
    }
}

class StrategiesWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        val url = prefs[stringPreferencesKey("strategies_url")] ?: DEFAULT_STRATEGIES_URL
        val (json, updatedAt) = fetchStrategiesOnce(url)
        if (json != null) {
            applicationContext.dataStore.edit { p ->
                p[stringPreferencesKey("strategies_json")] = json
                p[stringPreferencesKey("strategies_updated_at")] = updatedAt
            }
        }
        return Result.success()
    }
}

fun scheduleWeeklyReminder(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("weekly_reminder", ExistingPeriodicWorkPolicy.REPLACE, request)

    val fetchReq = PeriodicWorkRequestBuilder<StrategiesWorker>(1, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("strategies_fetch", ExistingPeriodicWorkPolicy.KEEP, fetchReq)
}

class ReminderWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(applicationContext, "weekly_reminder")
        } else {
            android.app.Notification.Builder(applicationContext)
        }
        builder.setContentTitle("Billionaire Coach — Weekly Review")
            .setContentText("Time to run your weekly tasks: automate savings, work on your product, and review expenses.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)

        nm.notify(1001, builder.build())
        return Result.success()
    }
}

fun yearsToTarget(start: Double, target: Double, monthlyContribution: Double, annualReturn: Double, maxYears: Int = 200): Int {
    var years = 0
    var balance = start
    val monthlyReturn = annualReturn / 12.0
    while (balance < target && years < maxYears) {
        for (m in 1..12) {
            balance *= (1 + monthlyReturn)
            balance += monthlyContribution
        }
        years += 1
    }
    return years
}

fun requiredMonthlyToHitInYears(start: Double, target: Double, annualReturn: Double, years: Int): Double {
    val months = years * 12
    val monthlyReturn = annualReturn / 12.0
    val fvStart = start * (1 + monthlyReturn).pow(months.toDouble())
    val factor = if (monthlyReturn == 0.0) months.toDouble() else ((1 + monthlyReturn).pow(months.toDouble()) - 1) / monthlyReturn
    val required = (target - fvStart) / factor
    return if (required.isFinite() && required > 0) required else 0.0
}

fun formatMoney(value: Double, currency: String): String {
    val rounded = String.format("%,.0f", value)
    return "$currency $rounded"
}

fun buildCsvReport(salary: Double, monthlySave: Double, net: Double, target: Double, expectedReturn: Double, checks: List<Boolean>, checklistItems: List<String>): String {
    val sb = StringBuilder()
    sb.append("Field,Value
")
    sb.append("Monthly Salary,${'$'}salary
")
    sb.append("Monthly Save,${'$'}monthlySave
")
    sb.append("Current Net,${'$'}net
")
    sb.append("Target,${'$'}target
")
    sb.append("ExpectedReturn,${'$'}expectedReturn
")
    sb.append("
Checklist Item,Completed
")
    for (i in checklistItems.indices) {
        sb.append(""${'$'}{checklistItems[i]}",${'$'}{checks[i]}
")
    }
    return sb.toString()
}

fun saveCsvToFile(context: Context, csv: String): File? {
    return try {
        val folder = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(folder, "billionaire_progress.csv")
        FileWriter(file).use { it.write(csv) }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, "com.example.billionairecoach.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
