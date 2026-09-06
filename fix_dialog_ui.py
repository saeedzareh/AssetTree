import re

with open("app/src/main/java/com/example/ui/dialogs/ExcelImportDialog.kt", "r") as f:
    content = f.read()

target = r"""                                    colors = OutlinedTextFieldDefaults.colors\(
                                        focusedTextColor = colors.inputText,
                                        unfocusedTextColor = colors.inputText,
                                        focusedContainerColor = colors.inputBackground,
                                        unfocusedContainerColor = colors.inputBackground,
                                        focusedBorderColor = colors.primary,
                                        unfocusedBorderColor = colors.border,
                                        cursorColor = colors.inputText
                                    \)
                                \)"""

replacement = """                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colors.inputText,
                                        unfocusedTextColor = colors.inputText,
                                        focusedContainerColor = colors.inputBackground,
                                        unfocusedContainerColor = colors.inputBackground,
                                        focusedBorderColor = colors.primary,
                                        unfocusedBorderColor = colors.border,
                                        cursorColor = colors.inputText
                                    )
                                )

                                // وظیفه جدید: کادر تطبیق ستون‌های فایل (نشان دادن بعد از استخراج سرستون‌ها)
                                if (parsedHeaders.isNotEmpty()) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "تطبیق ستون‌های فایل (تشخیص خودکار):",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "در صورت اشتباه بودن تشخیص، ستون صحیح را انتخاب کنید.",
                                                fontSize = 10.sp,
                                                color = colors.textSecondary
                                            )

                                            @Composable
                                            fun MappingRow(label: String, selectedIdx: Int, onSelect: (Int) -> Unit) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = label, fontSize = 11.5.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                                    var expanded by remember { mutableStateOf(false) }
                                                    Box(modifier = Modifier.weight(1.5f)) {
                                                        OutlinedButton(
                                                            onClick = { expanded = true },
                                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                        ) {
                                                            val selText = if (selectedIdx in parsedHeaders.indices) parsedHeaders[selectedIdx] else "انتخاب نشده"
                                                            Text(
                                                                text = selText,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                            DropdownMenuItem(text = { Text("انتخاب نشده", fontSize = 11.sp) }, onClick = { onSelect(-1); expanded = false })
                                                            parsedHeaders.forEachIndexed { idx, h ->
                                                                DropdownMenuItem(
                                                                    text = { Text("ستون ${idx+1}: $h", fontSize = 11.sp) },
                                                                    onClick = { onSelect(idx); expanded = false }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            MappingRow("نماد:", colMapSymbol) { colMapSymbol = it }
                                            MappingRow("نام شرکت:", colMapCompany) { colMapCompany = it }
                                            MappingRow("تعداد سهم:", colMapQty) { colMapQty = it }
                                            MappingRow("ارزش ریالی:", colMapRialVal) { colMapRialVal = it }
                                            MappingRow("نوع دارایی:", colMapAssetType) { colMapAssetType = it }
                                            MappingRow("وضعیت:", colMapStatus) { colMapStatus = it }
                                        }
                                    }
                                }"""

content = re.sub(target, replacement, content)

old_btn = r"""                                        val rows = TreeEngine\.parsePastedTextToRows\(
                                            text = pastedText,
                                            minRialThreshold = minThreshold,
                                            onlyTradeable = onlyTradeableAssets,
                                            groupSmallAssets = groupSmallAssets
                                        \)"""

new_btn = """                                        val columnMapping = TreeEngine.ColumnMapping(
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
                                        )"""

content = re.sub(old_btn, new_btn, content)

with open("app/src/main/java/com/example/ui/dialogs/ExcelImportDialog.kt", "w") as f:
    f.write(content)
