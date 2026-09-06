import re

with open("app/src/main/java/com/example/ui/components/AppTopBar.kt", "r") as f:
    content = f.read()

target = r"""                            // راست: نماد برنامه \(Custom App Icon\)
                            Box\("""

replacement = """                            // راست: نماد برنامه (Custom App Icon)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box("""

content = re.sub(target, replacement, content)

target2 = r"""                                \}
                            \}

                            Spacer\(modifier = Modifier.weight\(1f\)\)"""

replacement2 = """                                }
                                Text(
                                    text = "نسخه ۲",
                                    fontSize = 8.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))"""

content = re.sub(target2, replacement2, content)

with open("app/src/main/java/com/example/ui/components/AppTopBar.kt", "w") as f:
    f.write(content)
