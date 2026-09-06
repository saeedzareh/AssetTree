package com.example.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.TreeEngine
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.utils.NumberFormatUtils

enum class WizardStep(val titleFa: String) {
    INPUT("ورود اطلاعات"),
    NEEDS_REVIEW("بررسی ناهنجاری‌ها"),
    DUPLICATES("مدیریت تکراری‌ها"),
    NEW_SYMBOLS("نمادهای جدید"),
    ABSENT_STOCKS("سهام ناموجود"),
    FINAL_CONFIRM("تأیید نهایی")
}

@Composable
fun ExcelImportDialog(
    currentStoredNodes: List<StoredNodeEntity>,
    calculatedTree: CalculatedNode,
    symbolBook: List<SymbolEntryEntity>,
    settings: DisplaySettings,
    onDismiss: () -> Unit,
    onApplyImport: (plan: ImportPlan, skipAllDuplicates: Boolean, confirmDeleteAbsent: Boolean) -> Unit
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    var currentStep by remember { mutableStateOf(WizardStep.INPUT) }
    var pastedText by remember { mutableStateOf("") }
    var importPlan by remember { mutableStateOf<ImportPlan?>(null) }
    var errorMsg by remember { mutableStateOf("") }

    var skipAllDuplicates by remember { mutableStateOf(false) }
    var confirmDeleteAbsent by remember { mutableStateOf(false) }

    // وظیفه ۴: تکست باکس فقط دارایی‌های با ارزش بیشتر از با پیش‌فرض صفر و فرمت ۳ رقم ۳ رقم
    var minThresholdRaw by remember { mutableStateOf("0") }
    var groupSmallAssets by remember { mutableStateOf(true) }
    var onlyTradeableAssets by remember { mutableStateOf(true) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    // Column Mapping States
    var parsedHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var colMapSymbol by remember { mutableIntStateOf(-1) }
    var colMapCompany by remember { mutableIntStateOf(-1) }
    var colMapQty by remember { mutableIntStateOf(-1) }
    var colMapRialVal by remember { mutableIntStateOf(-1) }
    var colMapAssetType by remember { mutableIntStateOf(-1) }
    var colMapStatus by remember { mutableIntStateOf(-1) }

    LaunchedEffect(pastedText) {
        if (pastedText.isNotBlank()) {
            val lines = pastedText.trim().lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val firstLine = lines[0]
                val delimiter = when {
                    firstLine.contains('	') -> '	'
                    firstLine.contains(';') -> ';'
                    firstLine.contains(',') -> ','
                    else -> ' '
                }
                val headers = firstLine.split(delimiter).map { it.trim() }
                parsedHeaders = headers

                // Default columns based on user request (0-indexed)
                // "نماد"[ستون۳]=2، "نام شرکت"[ستون۴]=3، "تعدادسهم"[ستون۵]=4، "ارزش ریالی"[ستون۷]=6، "نوع دارایی"[ستون۹]=8، "وضعیت"[ستون۱۱]=10
                colMapSymbol = if (headers.size > 2) 2 else -1
                colMapCompany = if (headers.size > 3) 3 else -1
                colMapQty = if (headers.size > 4) 4 else -1
                colMapRialVal = if (headers.size > 6) 6 else -1
                colMapAssetType = if (headers.size > 8) 8 else -1
                colMapStatus = if (headers.size > 10) 10 else -1

                // Smart auto-detect to override default if we find matching headers
                headers.forEachIndexed { idx, h ->
                    val cleanH = h.replace(" ", "")
                    if (cleanH.contains("نماد")) colMapSymbol = idx
                    if (cleanH.contains("شرکت") || cleanH.contains("نام")) colMapCompany = idx
                    if (cleanH.contains("تعداد") || cleanH.contains("حجم")) colMapQty = idx
                    if (cleanH.contains("ارزش") || cleanH.contains("مبلغ") || cleanH.contains("خالص")) colMapRialVal = idx
                    if (cleanH.contains("نوع")) colMapAssetType = idx
                    if (cleanH.contains("وضعیت") || (cleanH.contains("قابل") && cleanH.contains("تعداد"))) colMapStatus = idx
                }
            }
        } else {
            parsedHeaders = emptyList()
        }
    }

    // File Picker for Excel / CSV / TXT in storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor != null && nameIndex != null && nameIndex >= 0 && cursor.moveToFirst()) {
                    selectedFileName = cursor.getString(nameIndex)
                    cursor.close()
                } else {
                    selectedFileName = uri.lastPathSegment ?: "فایل انتخاب شده"
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotBlank()) {
                    pastedText = content
                    errorMsg = ""
                } else {
                    errorMsg = "فایل انتخاب شده خالی است یا فرمت آن خوانده نشد."
                }
            } catch (e: Exception) {
                errorMsg = "خطا در خواندن فایل: ${e.localizedMessage}"
            }
        }
    }

    fun parseThresholdDouble(): Double {
        val clean = minThresholdRaw.replace(",", "").replace("،", "").replace(" ", "").trim()
        val num = clean.toDoubleOrNull() ?: 0.0
        // If app currency is TOMAN, convert threshold to RIAL for the engine if needed
        return if (settings.currencyUnit == CurrencyUnit.TOMAN) num * 10.0 else num
    }

    fun formatWithCommas(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""
        val num = digitsOnly.toLongOrNull() ?: return raw
        val javaFormatted = String.format(java.util.Locale.US, "%,d", num)
        return if (settings.usePersianDigits) {
            NumberFormatUtils.toPersianDigits(javaFormatted)
        } else {
            javaFormatted
        }
    }

    fun processRawRows(rows: List<RawBourseRow>) {
        if (rows.isEmpty()) {
            errorMsg = "هیچ ردیف معتبری یافت نشد یا تمام ردیف‌ها با فیلترها نادیده گرفته شدند."
            return
        }
        val plan = TreeEngine.buildImportPlan(rows, currentStoredNodes, calculatedTree, symbolBook)
        importPlan = plan
        currentStep = if (plan.needsReviewRows.isNotEmpty()) {
            WizardStep.NEEDS_REVIEW
        } else if (plan.duplicateRows.isNotEmpty()) {
            WizardStep.DUPLICATES
        } else if (plan.newSymbolsRows.isNotEmpty()) {
            WizardStep.NEW_SYMBOLS
        } else {
            WizardStep.FINAL_CONFIRM
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 750.dp)
                .fillMaxHeight(0.88f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Wizard Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.gainContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = colors.gain
                            )
                        }
                        Column {
                            Text(
                                text = "دستیار هوشمند ورود سبد سهام بورسی (اکسل / متنی)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "مرحله: ${currentStep.titleFa}",
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = colors.textSecondary)
                    }
                }

                // Step Progress Pills
                if (importPlan != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.background)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            WizardStep.NEEDS_REVIEW to "ناهنجاری (${importPlan!!.needsReviewRows.size})",
                            WizardStep.DUPLICATES to "تکراری (${importPlan!!.duplicateRows.size})",
                            WizardStep.NEW_SYMBOLS to "جدید (${importPlan!!.newSymbolsRows.size})",
                            WizardStep.ABSENT_STOCKS to "ناموجود (${importPlan!!.absentTreeNodes.size})",
                            WizardStep.FINAL_CONFIRM to "تأیید نهایی"
                        ).forEach { (step, label) ->
                            val isCurrent = currentStep == step
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrent) colors.primary else colors.surfaceVariant,
                                modifier = Modifier.clickable { currentStep = step }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) Color.White else colors.textPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.border)

                // Wizard Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(14.dp)
                ) {
                    if (errorMsg.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.lossContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = errorMsg,
                                color = colors.loss,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    when (currentStep) {
                        WizardStep.INPUT -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Storage File Picker & Drag-and-Drop Zone (وظیفه ۷)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceVariant.copy(alpha = 0.6f),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, colors.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            filePickerLauncher.launch("*/*")
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Text(
                                            text = if (selectedFileName != null) "فایل انتخاب شده: $selectedFileName" else "جستجوی فایل در حافظه ذخیره‌سازی و دراگ & دراپ فایل",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = if (selectedFileName != null) colors.primary else colors.textPrimary
                                        )

                                        Text(
                                            text = "پشتیبانی از فایل‌های اکسل، CSV، متن و خروجی کارگزاری‌های بورس",
                                            fontSize = 10.5.sp,
                                            color = colors.textSecondary
                                        )

                                        Button(
                                            onClick = { filePickerLauncher.launch("*/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("انتخاب فایل از گوشی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // 2. Sample Data Button
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = colors.primaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                                            Column {
                                                Text(
                                                    text = "آزمایش سریع با سبد نمونه بورس",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "نمادهای فولاد، فملی، شپنا، صندوق طلا و...",
                                                    fontSize = 9.5.sp,
                                                    color = colors.textSecondary
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                processRawRows(TreeEngine.getSampleBourseRows())
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("بارگذاری نمونه", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = "یا الصاق مستقیم متن جدول (اکسل / کارگزاری):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )

                                OutlinedTextField(
                                    value = pastedText,
                                    onValueChange = { pastedText = it },
                                    placeholder = {
                                        Text(
                                            text = "جدول کپی شده از اکسل کارگزاری را اینجا الصاق کنید:\nنماد\tتعداد\tارزش ریالی\tنوع دارایی\nفولاد\t40000\t248000000\tقابل معامله\nفملی\t25000\t195000000\tقابل معامله",
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            color = colors.textSecondary
                                        )
                                    },
                                    textStyle = LocalTextStyle.current.copy(color = colors.inputText),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.inputText,
                                        unfocusedTextColor = colors.inputText,
                                        focusedContainerColor = colors.inputBackground,
                                        unfocusedContainerColor = colors.inputBackground,
                                        focusedBorderColor = colors.primary,
                                        unfocusedBorderColor = colors.border,
                                        cursorColor = colors.inputText
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // وظیفه ۷: فیلتر فقط دریافت دارایی‌های قابل معامله
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "فقط دریافت دارایی‌های «قابل معامله»",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "ستون «نوع دارایی» در فایل اکسل باید «قابل معامله» باشد (حذف خودکار نمادهای متوقف یا غیرقابل معامله)",
                                                fontSize = 9.5.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                        Switch(
                                            checked = onlyTradeableAssets,
                                            onCheckedChange = { onlyTradeableAssets = it }
                                        )
                                    }
                                }

                                // وظیفه ۴ و وظیفه جدید: کادر دارایی‌های خرد و رادیو باتن‌ها
                                Surface(
                                    color = colors.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "دارایی های با ارزش کمتر از:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        OutlinedTextField(
                                            value = formatWithCommas(minThresholdRaw),
                                            onValueChange = { input ->
                                                val digits = input.filter { it.isDigit() }
                                                minThresholdRaw = digits.ifEmpty { "0" }
                                            },
                                            placeholder = {
                                                Text(
                                                    text = "۰ ${settings.currencyUnit.labelFa} (پیش‌فرض ۰)",
                                                    fontSize = 11.sp,
                                                    color = colors.textSecondary
                                                )
                                            },
                                            trailingIcon = {
                                                Text(
                                                    text = settings.currencyUnit.labelFa,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textSecondary,
                                                    modifier = Modifier.padding(end = 12.dp)
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                color = colors.inputText,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = colors.inputText,
                                                unfocusedTextColor = colors.inputText,
                                                focusedContainerColor = colors.inputBackground,
                                                unfocusedContainerColor = colors.inputBackground,
                                                focusedBorderColor = colors.primary,
                                                unfocusedBorderColor = colors.border,
                                                cursorColor = colors.inputText
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { groupSmallAssets = true }
                                            ) {
                                                RadioButton(
                                                    selected = groupSmallAssets,
                                                    onClick = { groupSmallAssets = true },
                                                    colors = RadioButtonDefaults.colors(selectedColor = colors.primary)
                                                )
                                                Text(
                                                    text = "در دارایی «سایر سهام» تشکیل شود.",
                                                    fontSize = 11.5.sp,
                                                    color = colors.textPrimary
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { groupSmallAssets = false }
                                            ) {
                                                RadioButton(
                                                    selected = !groupSmallAssets,
                                                    onClick = { groupSmallAssets = false },
                                                    colors = RadioButtonDefaults.colors(selectedColor = colors.primary)
                                                )
                                                Text(
                                                    text = "در نظر گرفته نشوند.",
                                                    fontSize = 11.5.sp,
                                                    color = colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // دکمه پردازش و تحلیل اطلاعات (وظیفه ۷)
                                Button(
                                    onClick = {
                                        val minThreshold = parseThresholdDouble()
                                        val columnMapping = TreeEngine.ColumnMapping(
                                            symbolCol = colMapSymbol,
                                            companyCol = colMapCompany,
                                            qtyCol = colMapQty,
                                            rialValCol = colMapRialVal,
                                            assetTypeCol = colMapAssetType,
                                            statusCol = colMapStatus
                                        )
                                        val rows = TreeEngine.parsePastedTextToRows(
                                            text = pastedText,
                                            minRialThreshold = minThreshold,
                                            onlyTradeable = onlyTradeableAssets,
                                            groupSmallAssets = groupSmallAssets,
                                            columnMapping = columnMapping
                                        )
                                        processRawRows(rows)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.gain, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("پردازش و تحلیل اطلاعات", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        WizardStep.NEEDS_REVIEW -> {
                            val rows = importPlan?.needsReviewRows ?: emptyList()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "سطرهای دارای ناهنجاری و نیازمند بررسی:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "مواردی مانند ارزش اسمی ۱ ریالی، نمادهای متوقف یا سهام غیرقابل معامله شناسایی شده‌اند.",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(rows, key = { it.id }) { row ->
                                        var isSelected by remember { mutableStateOf(row.selected) }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) colors.surface else colors.surfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) colors.primary else colors.border),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = {
                                                            isSelected = it
                                                            row.selected = it
                                                        }
                                                    )
                                                    Column {
                                                        Text(row.canonicalName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                                        Text(
                                                            text = "${NumberFormatUtils.formatNumberWithCommas(row.raw.quantity, settings.usePersianDigits)} سهم | ${row.anomalyDescription}",
                                                            fontSize = 10.sp,
                                                            color = colors.loss
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = NumberFormatUtils.formatCurrency(
                                                        row.raw.totalRialValue,
                                                        settings.currencyUnit,
                                                        compact = true,
                                                        usePersianDigits = settings.usePersianDigits
                                                    ),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WizardStep.DUPLICATES -> {
                            val rows = importPlan?.duplicateRows ?: emptyList()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("نمادهای مشترک با دارایی‌های فعلی:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = skipAllDuplicates,
                                            onCheckedChange = { skipAllDuplicates = it }
                                        )
                                        Text("صرف‌نظر از همه تکراری‌ها", fontSize = 10.sp, color = colors.textSecondary)
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(rows, key = { it.id }) { row ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = colors.surfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(row.canonicalName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                                    Text("موجود در درخت", fontSize = 10.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "قبلی: ${NumberFormatUtils.formatNumberWithCommas(row.existingQuantity, settings.usePersianDigits)} سهم",
                                                        fontSize = 10.sp,
                                                        color = colors.textSecondary
                                                    )
                                                    Text(
                                                        text = "جدید فایل: ${NumberFormatUtils.formatNumberWithCommas(row.raw.quantity, settings.usePersianDigits)} سهم",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.gain
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WizardStep.NEW_SYMBOLS -> {
                            val rows = importPlan?.newSymbolsRows ?: emptyList()
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("نمادهای جدید و صنایع پیشنهادی برای ایجاد:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(rows, key = { it.id }) { row ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colors.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(row.canonicalName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                                    Text("صنعت: ${row.industry}", fontSize = 10.sp, color = colors.textSecondary)
                                                }
                                                Text(
                                                    text = NumberFormatUtils.formatCurrency(
                                                        row.raw.totalRialValue,
                                                        settings.currencyUnit,
                                                        compact = true,
                                                        usePersianDigits = settings.usePersianDigits
                                                    ),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WizardStep.ABSENT_STOCKS -> {
                            val absents = importPlan?.absentTreeNodes ?: emptyList()
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("سهامی که در فایل جدید یافت نشدند:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                if (absents.isEmpty()) {
                                    Text("تمامی سهام موجود در درخت در فایل جدید نیز حاضرند.", fontSize = 11.sp, color = colors.gain)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = confirmDeleteAbsent,
                                            onCheckedChange = { confirmDeleteAbsent = it }
                                        )
                                        Text(
                                            text = "حذف ${NumberFormatUtils.toPersianDigits(absents.size)} سهم ناموجود از پرتفوی",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.loss
                                        )
                                    }

                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(absents, key = { it.id }) { abs ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = colors.lossContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(abs.name, fontSize = 11.sp, color = colors.loss)
                                                    Text(
                                                        NumberFormatUtils.formatCurrency(
                                                            abs.totalValue,
                                                            settings.currencyUnit,
                                                            compact = true,
                                                            usePersianDigits = settings.usePersianDigits
                                                        ),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.loss
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        WizardStep.FINAL_CONFIRM -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("خلاصه عملیات واردسازی:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "• اقلام سالم و تأیید شده: ${NumberFormatUtils.toPersianDigits(importPlan?.standardRows?.size ?: 0)} سهم",
                                            fontSize = 11.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            "• نمادهای جدید برای ایجاد: ${NumberFormatUtils.toPersianDigits(importPlan?.newSymbolsRows?.size ?: 0)} سهم",
                                            fontSize = 11.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            "• نمادهای به‌روزرسانی شده: ${NumberFormatUtils.toPersianDigits(importPlan?.duplicateRows?.size ?: 0)} سهم",
                                            fontSize = 11.sp,
                                            color = colors.textPrimary
                                        )
                                        if (confirmDeleteAbsent) {
                                            Text(
                                                "• اقلام ناموجود برای حذف: ${NumberFormatUtils.toPersianDigits(importPlan?.absentTreeNodes?.size ?: 0)} سهم",
                                                fontSize = 11.sp,
                                                color = colors.loss
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.border)

                // Wizard Navigation Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep != WizardStep.INPUT) {
                        OutlinedButton(
                            onClick = {
                                currentStep = when (currentStep) {
                                    WizardStep.NEEDS_REVIEW -> WizardStep.INPUT
                                    WizardStep.DUPLICATES -> WizardStep.NEEDS_REVIEW
                                    WizardStep.NEW_SYMBOLS -> WizardStep.DUPLICATES
                                    WizardStep.ABSENT_STOCKS -> WizardStep.NEW_SYMBOLS
                                    WizardStep.FINAL_CONFIRM -> WizardStep.ABSENT_STOCKS
                                    else -> WizardStep.INPUT
                                }
                            }
                        ) {
                            Text("مرحله قبل", fontSize = 11.sp, color = colors.textPrimary)
                        }
                    } else {
                        OutlinedButton(onClick = onDismiss) {
                            Text("انصراف", fontSize = 11.sp, color = colors.textPrimary)
                        }
                    }

                    if (currentStep != WizardStep.FINAL_CONFIRM && importPlan != null) {
                        Button(
                            onClick = {
                                currentStep = when (currentStep) {
                                    WizardStep.INPUT -> WizardStep.NEEDS_REVIEW
                                    WizardStep.NEEDS_REVIEW -> WizardStep.DUPLICATES
                                    WizardStep.DUPLICATES -> WizardStep.NEW_SYMBOLS
                                    WizardStep.NEW_SYMBOLS -> WizardStep.ABSENT_STOCKS
                                    WizardStep.ABSENT_STOCKS -> WizardStep.FINAL_CONFIRM
                                    else -> WizardStep.FINAL_CONFIRM
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White)
                        ) {
                            Text("گام بعدی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (currentStep == WizardStep.FINAL_CONFIRM && importPlan != null) {
                        Button(
                            onClick = {
                                onApplyImport(importPlan!!, skipAllDuplicates, confirmDeleteAbsent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.gain, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اعمال قطعی در پرتفوی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
