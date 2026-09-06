package com.example.core

import com.example.data.local.ROOT_NODE_ID
import com.example.data.local.ROOT_NODE_NAME
import com.example.data.model.*
import java.util.UUID
import kotlin.math.abs

object TreeEngine {

    fun createDefaultRootNode(): StoredNodeEntity {
        return StoredNodeEntity(
            id = ROOT_NODE_ID,
            parentId = null,
            name = ROOT_NODE_NAME,
            quantity = 1.0,
            unit = "سبد",
            unitPrice = 0.0,
            createdAt = System.currentTimeMillis()
        )
    }

    fun generateNodeId(): String {
        return "node_${UUID.randomUUID().toString().replace("-", "").take(8)}_${System.currentTimeMillis()}"
    }

    fun isRootNode(nodeId: String?, parentId: String?): Boolean {
        return nodeId == ROOT_NODE_ID || parentId == null
    }

    data class CalculatedTreeResult(
        val rootCalculated: CalculatedNode,
        val calculatedMap: Map<String, CalculatedNode>,
        val allCalculated: List<CalculatedNode>
    )

    fun evaluateTree(nodes: List<StoredNodeEntity>): CalculatedTreeResult {
        val workingNodes = nodes.toMutableList()
        var rootStored = workingNodes.find { isRootNode(it.id, it.parentId) }
        if (rootStored == null) {
            rootStored = createDefaultRootNode()
            workingNodes.add(0, rootStored)
        }

        val nodeMap = workingNodes.associateBy { it.id }
        val childrenMap = mutableMapOf<String, MutableList<StoredNodeEntity>>()
        for (node in workingNodes) {
            val pId = node.parentId
            if (pId != null) {
                childrenMap.getOrPut(pId) { mutableListOf() }.add(node)
            }
        }

        val calculatedMap = mutableMapOf<String, CalculatedNode>()

        // Step 1: Bottom-up Post-order evaluation
        fun evaluateNodePostOrder(nodeId: String, depth: Int): CalculatedNode {
            val raw = nodeMap[nodeId] ?: StoredNodeEntity(
                id = nodeId,
                parentId = null,
                name = "نامشخص",
                quantity = 0.0,
                unit = "واحد",
                unitPrice = 0.0
            )

            val directChildrenRaw = childrenMap[nodeId] ?: emptyList()
            val directChildrenCalculated = directChildrenRaw.map { child ->
                evaluateNodePostOrder(child.id, depth + 1)
            }

            val isGroup = directChildrenCalculated.isNotEmpty()
            val quantity: Double
            val unitPrice: Double
            val totalValue: Double

            if (isGroup) {
                // Group node: value is sum of children values
                val sumChildrenValue = directChildrenCalculated.sumOf { it.totalValue }
                quantity = 1.0
                unitPrice = sumChildrenValue
                totalValue = sumChildrenValue
            } else {
                // Leaf asset: totalValue = quantity * unitPrice
                quantity = raw.quantity
                unitPrice = raw.unitPrice
                totalValue = quantity * unitPrice
            }

            val childCount = directChildrenCalculated.sumOf { 1 + it.childCount }

            val calculated = CalculatedNode(
                id = raw.id,
                parentId = raw.parentId,
                name = raw.name,
                quantity = quantity,
                unit = raw.unit,
                unitPrice = unitPrice,
                totalValue = totalValue,
                percentOfTotal = 0.0,
                percentOfGroup = 0.0,
                isGroup = isGroup,
                children = directChildrenCalculated,
                depth = depth,
                childCount = childCount,
                createdAt = raw.createdAt,
                updatedAt = raw.updatedAt,
                categoryTag = raw.categoryTag
            )
            calculatedMap[nodeId] = calculated
            return calculated
        }

        val initialRoot = evaluateNodePostOrder(rootStored.id, 0)
        val rootTotalValue = initialRoot.totalValue

        // Step 2: Assign percentages
        fun assignPercentages(node: CalculatedNode, parentTotalValue: Double): CalculatedNode {
            val pTotal = if (isRootNode(node.id, node.parentId)) {
                100.0
            } else if (rootTotalValue > 0.0) {
                (node.totalValue / rootTotalValue) * 100.0
            } else {
                0.0
            }

            val pGroup = if (isRootNode(node.id, node.parentId)) {
                100.0
            } else if (parentTotalValue > 0.0) {
                (node.totalValue / parentTotalValue) * 100.0
            } else {
                0.0
            }

            val updatedChildren = node.children.map { child ->
                assignPercentages(child, node.totalValue)
            }

            val updatedNode = node.copy(
                percentOfTotal = pTotal,
                percentOfGroup = pGroup,
                children = updatedChildren
            )
            calculatedMap[node.id] = updatedNode
            return updatedNode
        }

        val finalRoot = assignPercentages(initialRoot, rootTotalValue)
        val allCalculated = calculatedMap.values.toList()

        return CalculatedTreeResult(
            rootCalculated = finalRoot,
            calculatedMap = calculatedMap,
            allCalculated = allCalculated
        )
    }

    fun checkCycle(nodes: List<StoredNodeEntity>, movingNodeId: String, newParentId: String?): Boolean {
        if (movingNodeId == newParentId) return true
        if (newParentId == null || newParentId == ROOT_NODE_ID) return false

        val nodeMap = nodes.associateBy { it.id }
        var currentParentId: String? = newParentId
        while (currentParentId != null && currentParentId != ROOT_NODE_ID) {
            if (currentParentId == movingNodeId) {
                return true // Cycle detected!
            }
            val parentNode = nodeMap[currentParentId]
            currentParentId = parentNode?.parentId
        }
        return false
    }

    fun calculateSmartDefaultQuantity(parentNode: CalculatedNode, newUnitPrice: Double): Double {
        if (newUnitPrice <= 0) return 1.0
        return if (parentNode.children.isEmpty()) {
            if (parentNode.totalValue > 0) {
                val q = parentNode.totalValue / newUnitPrice
                if (q == q.toLong().toDouble()) q else (q * 100).toLong() / 100.0
            } else {
                1.0
            }
        } else {
            val currentChildrenSum = parentNode.children.sumOf { it.totalValue }
            val remainingValue = parentNode.totalValue - currentChildrenSum
            if (remainingValue > 0) {
                val q = remainingValue / newUnitPrice
                if (q == q.toLong().toDouble()) q else (q * 100).toLong() / 100.0
            } else {
                1.0
            }
        }
    }

    fun sortCalculatedTree(node: CalculatedNode, sortConfig: SortConfig): CalculatedNode {
        if (node.children.isEmpty()) return node

        val sortedChildren = node.children.map { sortCalculatedTree(it, sortConfig) }.toMutableList()

        sortedChildren.sortWith { a, b ->
            val res = when (sortConfig.field) {
                SortField.TOTAL_VALUE -> a.totalValue.compareTo(b.totalValue)
                SortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortField.QUANTITY -> a.quantity.compareTo(b.quantity)
                SortField.UNIT_PRICE -> a.unitPrice.compareTo(b.unitPrice)
                SortField.PERCENT_OF_TOTAL -> a.percentOfTotal.compareTo(b.percentOfTotal)
                SortField.PERCENT_OF_GROUP -> a.percentOfGroup.compareTo(b.percentOfGroup)
            }
            if (sortConfig.direction == SortDirection.ASC) res else -res
        }

        return node.copy(children = sortedChildren)
    }

    fun performTreeHealthCheck(rootCalculated: CalculatedNode): TreeHealth {
        val directChildrenSum = rootCalculated.children.sumOf { it.totalValue }
        val discrepancy = abs(rootCalculated.totalValue - directChildrenSum)
        val isValid = discrepancy < 0.1 || rootCalculated.children.isEmpty()

        var totalNodeCount = 1
        var zeroValueCount = if (rootCalculated.totalValue == 0.0) 1 else 0

        fun traverse(n: CalculatedNode) {
            for (child in n.children) {
                totalNodeCount++
                if (child.totalValue == 0.0) zeroValueCount++
                traverse(child)
            }
        }
        traverse(rootCalculated)

        return TreeHealth(
            isValid = isValid,
            rootTotal = rootCalculated.totalValue,
            directChildrenSum = directChildrenSum,
            discrepancy = discrepancy,
            totalNodeCount = totalNodeCount,
            zeroValueCount = zeroValueCount
        )
    }

    // --- Symbol Book & Market Tools ---

    fun cleanRawSymbol(rawSymbol: String): String {
        var cleaned = rawSymbol.trim()
        val prefixes = listOf("ض", "ط", "اختیار", "ح", "پ", "صندوق", "گواهی")
        for (pfx in prefixes) {
            if (cleaned.startsWith(pfx) && cleaned.length > pfx.length + 1) {
                cleaned = cleaned.removePrefix(pfx).trim()
            }
        }
        return cleaned
    }

    data class LookupResult(
        val canonicalName: String,
        val industry: String,
        val foundInBook: Boolean
    )

    fun lookupSymbolInBook(
        rawSymbol: String,
        symbolBook: List<SymbolEntryEntity>,
        companyNameHint: String? = null
    ): LookupResult {
        val trimmed = rawSymbol.trim()
        val exact = symbolBook.find {
            it.rawSymbol.equals(trimmed, ignoreCase = true) || it.canonicalName.equals(trimmed, ignoreCase = true)
        }
        if (exact != null) {
            return LookupResult(exact.canonicalName, exact.industry, true)
        }

        val cleaned = cleanRawSymbol(trimmed)
        val cleanMatch = symbolBook.find {
            it.rawSymbol.equals(cleaned, ignoreCase = true) || it.canonicalName.equals(cleaned, ignoreCase = true)
        }
        if (cleanMatch != null) {
            return LookupResult(cleanMatch.canonicalName, cleanMatch.industry, true)
        }

        if (!companyNameHint.isNullOrBlank()) {
            return LookupResult(companyNameHint.trim(), "سایر صنایع", false)
        }

        return LookupResult(cleaned.ifBlank { trimmed }, "سایر صنایع", false)
    }

    fun detectRowAnomaly(raw: RawBourseRow): Pair<BourseAnomalyType, String> {
        if (raw.quantity > 0 && raw.totalRialValue == raw.quantity) {
            return Pair(BourseAnomalyType.TABEI_OPTION, "ارزش ریالی با تعداد برابر است (اوراق تبعی / اختیار ۱ ریالی)")
        }
        if (raw.quantity > 0 && raw.totalRialValue == 0.0) {
            return Pair(BourseAnomalyType.ZERO_VALUE, "ارزش کل صفر ریال است (نماد متوقف یا بدون قیمت پایانی)")
        }
        if (raw.totalRialValue > 0 && raw.tradeableQuantity != null && raw.tradeableQuantity == 0.0 && raw.quantity > 0) {
            return Pair(BourseAnomalyType.PENDING_CAPITAL_INCREASE, "سهم غیرقابل معامله (حق تقدم / افزایش سرمایه در جریان)")
        }
        return Pair(BourseAnomalyType.STANDARD, "اطلاعات سهم نرمال و آماده ورود است")
    }

    fun buildImportPlan(
        rawRows: List<RawBourseRow>,
        currentStoredNodes: List<StoredNodeEntity>,
        calculatedTree: CalculatedNode,
        symbolBook: List<SymbolEntryEntity>
    ): ImportPlan {
        val standardRows = mutableListOf<ParsedImportRow>()
        val needsReviewRows = mutableListOf<ParsedImportRow>()
        val duplicateRows = mutableListOf<ParsedImportRow>()
        val newSymbolsRows = mutableListOf<ParsedImportRow>()

        val existingNodeMapByName = currentStoredNodes
            .filter { it.parentId != null }
            .associateBy { it.name.trim().lowercase() }

        val processedNames = mutableSetOf<String>()

        rawRows.forEachIndexed { index, raw ->
            val (anomalyType, anomalyDesc) = detectRowAnomaly(raw)
            val lookup = lookupSymbolInBook(raw.symbolRaw, symbolBook, raw.companyName)
            val unitPrice = if (raw.quantity > 0) raw.totalRialValue / raw.quantity else 0.0

            val matchedExisting = existingNodeMapByName[lookup.canonicalName.lowercase()]
            val isDuplicate = matchedExisting != null

            val parsedRow = ParsedImportRow(
                id = "import_row_${index}_${System.currentTimeMillis()}",
                raw = raw,
                canonicalName = lookup.canonicalName,
                industry = lookup.industry,
                unitPriceCalculated = unitPrice,
                anomalyType = anomalyType,
                anomalyDescription = anomalyDesc,
                isDuplicateInTree = isDuplicate,
                matchedExistingNodeId = matchedExisting?.id,
                matchedExistingNodeName = matchedExisting?.name,
                existingQuantity = matchedExisting?.quantity,
                existingUnitPrice = matchedExisting?.unitPrice,
                selected = anomalyType == BourseAnomalyType.STANDARD,
                duplicateResolution = if (isDuplicate) {
                    if (lookup.canonicalName == "سایر سهام" || raw.symbolRaw == "سایر سهام") DuplicateResolution.SUM else DuplicateResolution.REPLACE
                } else DuplicateResolution.REPLACE
            )

            processedNames.add(lookup.canonicalName.lowercase())

            if (anomalyType == BourseAnomalyType.STANDARD) {
                standardRows.add(parsedRow)
            } else {
                needsReviewRows.add(parsedRow)
            }

            if (isDuplicate) {
                duplicateRows.add(parsedRow)
            } else {
                newSymbolsRows.add(parsedRow)
            }
        }

        val absentTreeNodes = mutableListOf<AbsentTreeNode>()
        fun checkAbsents(n: CalculatedNode) {
            if (!n.isGroup && n.parentId != null) {
                val clean = n.name.lowercase().trim()
                if (!processedNames.contains(clean) && (n.unit.contains("سهم") || n.unit.contains("واحد"))) {
                    absentTreeNodes.add(
                        AbsentTreeNode(
                            id = n.id,
                            name = n.name,
                            totalValue = n.totalValue
                        )
                    )
                }
            }
            for (child in n.children) checkAbsents(child)
        }
        checkAbsents(calculatedTree)

        return ImportPlan(
            standardRows = standardRows,
            needsReviewRows = needsReviewRows,
            duplicateRows = duplicateRows,
            newSymbolsRows = newSymbolsRows,
            absentTreeNodes = absentTreeNodes
        )
    }


    data class ColumnMapping(
        val symbolCol: Int = -1,
        val companyCol: Int = -1,
        val qtyCol: Int = -1,
        val rialValCol: Int = -1,
        val assetTypeCol: Int = -1,
        val statusCol: Int = -1
    )

    fun parsePastedTextToRows(
        text: String,
        minRialThreshold: Double = 0.0,
        onlyTradeable: Boolean = true,
        groupSmallAssets: Boolean = true,
        columnMapping: ColumnMapping? = null
    ): List<RawBourseRow> {
        val lines = text.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val delimiter = when {
            lines[0].contains('\t') -> '\t'
            lines[0].contains(';') -> ';'
            lines[0].contains(',') -> ','
            else -> ' '
        }

        val rows = mutableListOf<RawBourseRow>()
        var symbolCol = columnMapping?.symbolCol ?: -1
        var qtyCol = columnMapping?.qtyCol ?: -1
        var rialValCol = columnMapping?.rialValCol ?: -1
        var companyCol = columnMapping?.companyCol ?: -1
        var assetTypeCol = columnMapping?.assetTypeCol ?: -1
        var tradeableQtyCol = columnMapping?.statusCol ?: -1
        var headerDetected = columnMapping != null

        var otherAssetsTotalRial = 0.0
        var otherAssetsTotalQty = 0.0

        fun parseNum(s: String?): Double {
            if (s == null) return 0.0
            val clean = s.replace(",", "").replace("،", "").replace(" ", "").trim()
            return clean.toDoubleOrNull() ?: 0.0
        }

        for ((idx, line) in lines.withIndex()) {
            val parts = line.split(delimiter).map { it.trim() }
            if (parts.isEmpty() || parts.all { it.isEmpty() }) continue

            // If we have explicit mapping, we might still want to skip the header line.
            if (idx == 0 && (line.contains("نماد") || line.contains("سهم") || line.contains("ارزش") || line.contains("تعداد") || line.contains("دارایی"))) {
                continue
            }
            
            // Detect header line dynamically
            if (!headerDetected && (line.contains("نماد") || line.contains("سهم") || line.contains("ارزش") || line.contains("تعداد") || line.contains("دارایی"))) {
                headerDetected = true
                parts.forEachIndexed { pIdx, headerTitle ->
                    val h = headerTitle.replace(" ", "")
                    when {
                        h.contains("نماد") && symbolCol == -1 -> symbolCol = pIdx
                        h.contains("شرکت") || h.contains("نام") || h.contains("شرح") -> if (companyCol == -1 && !h.contains("نوع")) companyCol = pIdx
                        h.contains("نوع") || h.contains("وضعیت") -> assetTypeCol = pIdx
                        h.contains("قابل") && h.contains("تعداد") -> tradeableQtyCol = pIdx
                        h.contains("تعداد") || h.contains("حجم") -> if (qtyCol == -1) qtyCol = pIdx
                        h.contains("ارزش") || h.contains("مبلغ") || h.contains("خالص") -> if (rialValCol == -1) rialValCol = pIdx
                    }
                }
                continue
            }

            if (parts.size >= 2) {
                // ستون های درخواستی کاربر: نماد(ستون ۳=ایندکس ۲)، تعداد سهم(ستون ۵=ایندکس ۴)، ارزش ریالی(ستون ۷=ایندکس ۶)
                val symbol = if (symbolCol in parts.indices) parts[symbolCol] else parts.getOrNull(2) ?: parts.getOrNull(1) ?: parts[0]
                val qty = if (qtyCol in parts.indices) parseNum(parts[qtyCol]) else parseNum(parts.getOrNull(4))
                val rialVal = if (rialValCol in parts.indices) parseNum(parts[rialValCol]) else parseNum(parts.getOrNull(6))
                val company = if (companyCol in parts.indices) parts[companyCol] else parts.getOrNull(3)
                val rawAssetType = if (assetTypeCol in parts.indices) parts[assetTypeCol] else parts.getOrNull(8) ?: "قابل معامله"
                val tradeableQty = if (tradeableQtyCol in parts.indices) parseNum(parts[tradeableQtyCol]) else parseNum(parts.getOrNull(10))

                // Filter 1: Check if "نوع دارایی" is "قابل معامله" (وظیفه ۷)
                if (onlyTradeable && rawAssetType.isNotBlank()) {
                    val isTradeable = rawAssetType.contains("قابل معامله") ||
                            rawAssetType.contains("عادی") ||
                            rawAssetType.contains("صندوق") ||
                            rawAssetType.contains("معامله") ||
                            rawAssetType.equals("بله", ignoreCase = true) ||
                            rawAssetType == "1"

                    val isExplicitlyNonTradeable = rawAssetType.contains("غیرقابل") ||
                            rawAssetType.contains("غیر قابل") ||
                            rawAssetType.contains("متوقف") ||
                            rawAssetType.contains("مسدود")

                    if (!isTradeable || isExplicitlyNonTradeable) {
                        continue // Skip non-tradeable assets as requested
                    }
                }

                // وظیفه ۲: دارایی‌هایی که ستون «ارزش ریالی» آن‌ها کمتر یا مساوی عدد وارد شده باشد
                val effectiveRial = if (rialVal > 0) rialVal else (qty * 5000.0)
                if (minRialThreshold > 0.0 && effectiveRial <= minRialThreshold) {
                    if (groupSmallAssets) {
                        otherAssetsTotalRial += effectiveRial
                        otherAssetsTotalQty += qty
                    }
                    continue // تجمیع در سایر سهام یا حذف شدن
                }

                if (symbol.isNotBlank() && (qty > 0 || effectiveRial > 0)) {
                    rows.add(
                        RawBourseRow(
                            symbolRaw = symbol,
                            quantity = qty,
                            totalRialValue = effectiveRial,
                            companyName = company,
                            assetType = rawAssetType,
                            tradeableQuantity = tradeableQty,
                            rawRowNumber = idx + 1
                        )
                    )
                }
            }
        }

        if (groupSmallAssets && otherAssetsTotalRial > 0.0) {
            rows.add(
                RawBourseRow(
                    symbolRaw = "سایر سهام",
                    quantity = if (otherAssetsTotalQty > 0) otherAssetsTotalQty else 1.0,
                    totalRialValue = otherAssetsTotalRial,
                    companyName = "سایر سهام تجمیع شده",
                    assetType = "قابل معامله",
                    tradeableQuantity = null,
                    rawRowNumber = lines.size + 1
                )
            )
        }

        return rows
    }

    fun getSampleBourseRows(): List<RawBourseRow> {
        return listOf(
            RawBourseRow("فولاد", 45000.0, 279000000.0, "فولاد مبارکه اصفهان", "سهم عادی", 45000.0),
            RawBourseRow("فملی", 30000.0, 234000000.0, "ملی صنایع مس ایران", "سهم عادی", 30000.0),
            RawBourseRow("شپنا", 20000.0, 96000000.0, "پالایش نفت اصفهان", "سهم عادی", 20000.0),
            RawBourseRow("خودرو", 60000.0, 180000000.0, "ایران خودرو", "سهم عادی", 60000.0),
            RawBourseRow("وبملت", 80000.0, 208000000.0, "بانک ملت", "سهم عادی", 80000.0),
            RawBourseRow("طلا", 25000.0, 462500000.0, "صندوق طلای لوتوس", "صندوق سرمایه‌گذاری", 25000.0),
            // ناهنجاری‌ها:
            RawBourseRow("ضفولاد۸۰۲", 10000.0, 10000.0, "اختیار خرید فولاد", "اختیار معامله", 10000.0),
            RawBourseRow("حفملی", 15000.0, 90000000.0, "حق تقدم فملی", "حق تقدم", 0.0),
            RawBourseRow("شتران", 0.0, 0.0, "پالایش نفت تهران", "سهم عادی", 0.0)
        )
    }
}
