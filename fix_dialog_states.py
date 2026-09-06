import re

with open("app/src/main/java/com/example/ui/dialogs/ExcelImportDialog.kt", "r") as f:
    content = f.read()

old_states = """    var selectedFileName by remember { mutableStateOf<String?>(null) }"""

new_states = """    var selectedFileName by remember { mutableStateOf<String?>(null) }

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
                    firstLine.contains('\t') -> '\t'
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
    }"""

content = content.replace(old_states, new_states)

with open("app/src/main/java/com/example/ui/dialogs/ExcelImportDialog.kt", "w") as f:
    f.write(content)
