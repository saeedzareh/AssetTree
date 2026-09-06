import re

with open("app/src/main/java/com/example/core/TreeEngine.kt", "r") as f:
    content = f.read()

# Add data class
new_data_class = """
    data class ColumnMapping(
        val symbolCol: Int = -1,
        val companyCol: Int = -1,
        val qtyCol: Int = -1,
        val rialValCol: Int = -1,
        val assetTypeCol: Int = -1,
        val statusCol: Int = -1
    )

    fun parsePastedTextToRows("""

content = content.replace("    fun parsePastedTextToRows(", new_data_class)

# Add columnMapping to method signature
old_sig = """    fun parsePastedTextToRows(
        text: String,
        minRialThreshold: Double = 0.0,
        onlyTradeable: Boolean = true,
        groupSmallAssets: Boolean = true
    ): List<RawBourseRow> {"""

new_sig = """    fun parsePastedTextToRows(
        text: String,
        minRialThreshold: Double = 0.0,
        onlyTradeable: Boolean = true,
        groupSmallAssets: Boolean = true,
        columnMapping: ColumnMapping? = null
    ): List<RawBourseRow> {"""

content = content.replace(old_sig, new_sig)

# Replace the column definitions
old_cols = """        val rows = mutableListOf<RawBourseRow>()
        var symbolCol = -1
        var qtyCol = -1
        var rialValCol = -1
        var companyCol = -1
        var assetTypeCol = -1
        var tradeableQtyCol = -1
        var headerDetected = false"""

new_cols = """        val rows = mutableListOf<RawBourseRow>()
        var symbolCol = columnMapping?.symbolCol ?: -1
        var qtyCol = columnMapping?.qtyCol ?: -1
        var rialValCol = columnMapping?.rialValCol ?: -1
        var companyCol = columnMapping?.companyCol ?: -1
        var assetTypeCol = columnMapping?.assetTypeCol ?: -1
        var tradeableQtyCol = columnMapping?.statusCol ?: -1
        var headerDetected = columnMapping != null"""

content = content.replace(old_cols, new_cols)

# Update fallback indices to user request
old_fallback = """                // ستون های درخواستی کاربر: نماد(ستون ۳=ایندکس ۲)، تعداد سهم(ستون ۵=ایندکس ۴)، ارزش ریالی(ستون ۷=ایندکس ۶)
                val symbol = if (symbolCol in parts.indices) parts[symbolCol] else parts.getOrNull(2) ?: parts.getOrNull(1) ?: parts[0]
                val qty = if (qtyCol in parts.indices) parseNum(parts[qtyCol]) else parseNum(parts.getOrNull(4))
                val rialVal = if (rialValCol in parts.indices) parseNum(parts[rialValCol]) else parseNum(parts.getOrNull(6))
                val company = if (companyCol in parts.indices) parts[companyCol] else parts.getOrNull(2)
                val rawAssetType = if (assetTypeCol in parts.indices) parts[assetTypeCol] else parts.getOrNull(4) ?: "قابل معامله"
                val tradeableQty = if (tradeableQtyCol in parts.indices) parseNum(parts[tradeableQtyCol]) else null"""

new_fallback = """                // ستون های درخواستی کاربر: نماد(ستون ۳=ایندکس ۲)، تعداد سهم(ستون ۵=ایندکس ۴)، ارزش ریالی(ستون ۷=ایندکس ۶)
                val symbol = if (symbolCol in parts.indices) parts[symbolCol] else parts.getOrNull(2) ?: parts.getOrNull(1) ?: parts[0]
                val qty = if (qtyCol in parts.indices) parseNum(parts[qtyCol]) else parseNum(parts.getOrNull(4))
                val rialVal = if (rialValCol in parts.indices) parseNum(parts[rialValCol]) else parseNum(parts.getOrNull(6))
                val company = if (companyCol in parts.indices) parts[companyCol] else parts.getOrNull(3)
                val rawAssetType = if (assetTypeCol in parts.indices) parts[assetTypeCol] else parts.getOrNull(8) ?: "قابل معامله"
                val tradeableQty = if (tradeableQtyCol in parts.indices) parseNum(parts[tradeableQtyCol]) else parseNum(parts.getOrNull(10))"""

content = content.replace(old_fallback, new_fallback)

# We still want to skip the header line if it looks like a header, even if headerDetected is already true from columnMapping
# We'll just add a quick check for the first line.
header_check = """            // Detect header line dynamically
            if (!headerDetected && (line.contains("نماد") || line.contains("سهم") || line.contains("ارزش") || line.contains("تعداد") || line.contains("دارایی"))) {"""

new_header_check = """            // If we have explicit mapping, we might still want to skip the header line.
            if (idx == 0 && (line.contains("نماد") || line.contains("سهم") || line.contains("ارزش") || line.contains("تعداد") || line.contains("دارایی"))) {
                continue
            }
            
            // Detect header line dynamically
            if (!headerDetected && (line.contains("نماد") || line.contains("سهم") || line.contains("ارزش") || line.contains("تعداد") || line.contains("دارایی"))) {"""

content = content.replace(header_check, new_header_check)

with open("app/src/main/java/com/example/core/TreeEngine.kt", "w") as f:
    f.write(content)
