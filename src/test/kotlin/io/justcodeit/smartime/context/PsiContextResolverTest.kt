package io.justcodeit.smartime.context

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.justcodeit.smartime.model.ContextType
import kotlin.test.assertEquals

class PsiContextResolverTest : BasePlatformTestCase() {
    private val resolver = PsiContextResolver()

    fun testJavaLineCommentResolvesToCommentContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    void test() {
                        // hello<caret>
                    }
                }
            """.trimIndent(),
            expected = ContextType.LINE_COMMENT,
        )
    }

    fun testJavaLineCommentEndOffsetStillResolvesToCommentContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    void test() {
                        // hello world！你好<caret>
                    }
                }
            """.trimIndent(),
            expected = ContextType.LINE_COMMENT,
        )
    }

    fun testJavaStringLiteralResolvesToStringContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    String value = "中<caret>文";
                }
            """.trimIndent(),
            expected = ContextType.STRING_LITERAL,
        )
    }

    fun testJavaStringLiteralEndCharacterStillResolvesToStringContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    String value = "中文<caret>";
                }
            """.trimIndent(),
            expected = ContextType.STRING_LITERAL,
        )
    }

    fun testJavaStringLiteralOutsideClosingQuoteResolvesToCodeContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    String value = "中文" <caret>;
                }
            """.trimIndent(),
            expected = ContextType.CODE,
        )
    }

    fun testJavaBlockCommentEndOffsetStillResolvesToCommentContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    void test() {
                        /* 多行注释尾部<caret> */
                    }
                }
            """.trimIndent(),
            expected = ContextType.BLOCK_COMMENT,
        )
    }

    fun testJavaDocCommentEndOffsetStillResolvesToCommentContext() {
        assertContextType(
            fileName = "Sample.java",
            content = """
                class Sample {
                    /**
                     * 文档注释尾部<caret>
                     */
                    void test() {}
                }
            """.trimIndent(),
            expected = ContextType.DOC_COMMENT,
        )
    }

    fun testKotlinRawStringEndCharacterStillResolvesToStringContext() {
        assertContextType(
            fileName = "Sample.kt",
            content = """
                fun demo(): String {
                    return ${"\"\"\""}原始字符串<caret>${"\"\"\""}
                }
            """.trimIndent(),
            expected = ContextType.STRING_LITERAL,
        )
    }

    fun testXmlCommentEndOffsetStillResolvesToXmlCommentContext() {
        assertContextType(
            fileName = "sample.xml",
            content = """
                <root>
                    <!-- 注释尾部<caret> -->
                </root>
            """.trimIndent(),
            expected = ContextType.XML_COMMENT,
        )
    }

    fun testKotlinLiteralTemplateEntryResolvesToStringContext() {
        assertContextType(
            fileName = "Sample.kt",
            content = """
                fun demo(name: String): String {
                    return "你好，<caret>${'$'}name"
                }
            """.trimIndent(),
            expected = ContextType.STRING_LITERAL,
        )
    }

    fun testKotlinSimpleInterpolationResolvesToCodeContext() {
        assertContextType(
            fileName = "Sample.kt",
            content = """
                fun demo(name: String): String {
                    return "你好，${'$'}na<caret>me"
                }
            """.trimIndent(),
            expected = ContextType.CODE,
        )
    }

    fun testKotlinBlockInterpolationResolvesToCodeContext() {
        assertContextType(
            fileName = "Sample.kt",
            content = """
                fun demo(name: String): String {
                    return "你好，${'$'}{na<caret>me.uppercase()}"
                }
            """.trimIndent(),
            expected = ContextType.CODE,
        )
    }

    fun testXmlCommentResolvesToXmlCommentContext() {
        assertContextType(
            fileName = "sample.xml",
            content = """
                <root>
                    <!-- 注<caret>释 -->
                </root>
            """.trimIndent(),
            expected = ContextType.XML_COMMENT,
        )
    }

    private fun assertContextType(fileName: String, content: String, expected: ContextType) {
        myFixture.configureByText(fileName, content)
        val snapshot = resolver.resolve(project, myFixture.editor, myFixture.file, myFixture.editor.caretModel.offset)
        assertEquals(expected, snapshot.contextType)
    }
}
