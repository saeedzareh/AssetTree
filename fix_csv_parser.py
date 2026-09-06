import re

with open("app/src/main/java/com/example/core/TreeEngine.kt", "r") as f:
    content = f.read()

old_code = """                // ستون های درخواستی کاربر: نام دارایی(2)، تعداد سهم(4)، ارزش ریالی(6) -> ایندکس های 1, 3, 5
                val symbol = if (symbolCol in parts.indices) parts[symbolCol] else parts.getOrNull(1) ?: parts[0]
                val qty = if (qtyCol in parts.indices) parseNum(parts[qtyCol]) else parseNum(parts.getOrNull(3))
                val rialVal = if (rialValCol in parts.indices) parseNum(parts[rialValCol]) else parseNum(parts.getOrNull(5))"""

new_code = """                // ستون های درخواستی کاربر: نماد(ستون ۳=ایندکس ۲)، تعداد سهم(ستون ۵=ایندکس ۴)، ارزش ریالی(ستون ۷=ایندکس ۶)
                val symbol = if (symbolCol in parts.indices) parts[symbolCol] else parts.getOrNull(2) ?: parts.getOrNull(1) ?: parts[0]
                val qty = if (qtyCol in parts.indices) parseNum(parts[qtyCol]) else parseNum(parts.getOrNull(4))
                val rialVal = if (rialValCol in parts.indices) parseNum(parts[rialValCol]) else parseNum(parts.getOrNull(6))"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/com/example/core/TreeEngine.kt", "w") as f:
    f.write(content)
