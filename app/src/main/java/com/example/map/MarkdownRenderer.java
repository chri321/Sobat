package com.example.map;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.TypefaceSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简易 Markdown 渲染器，将常用 MD 语法转为 Android Spanned
 */
public class MarkdownRenderer {

    public static Spanned render(String markdown) {
        String html = markdown;

        // 代码块 ```...```
        html = html.replaceAll("(?s)```(\\w*)\\n?(.*?)```",
                "<br><tt>$2</tt><br>");

        // 行内代码 `...`
        html = html.replaceAll("`([^`]+)`", "<tt>$1</tt>");

        // **粗体**
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");

        // *斜体*
        html = html.replaceAll("(?<![*])\\*([^*]+)\\*(?![*])", "<i>$1</i>");

        // ### 标题
        html = html.replaceAll("(?m)^### (.+)$", "<br><b><big>$1</big></b>");
        html = html.replaceAll("(?m)^## (.+)$", "<br><b><big><big>$1</big></big></b>");
        html = html.replaceAll("(?m)^# (.+)$", "<br><b><big><big><big>$1</big></big></big></b>");

        // 无序列表 - item
        html = html.replaceAll("(?m)^- (.+)$", "<br>• $1");
        html = html.replaceAll("(?m)^\\* (.+)$", "<br>• $1");

        // 有序列表 1. item
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<br>$0");

        // 分割线 ---
        html = html.replaceAll("(?m)^---+$", "<br>──────────────<br>");

        // 链接 [text](url) → 只保留文字
        html = html.replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1");

        // 换行
        html = html.replaceAll("(?m)^$", "<br>");

        // 转 Android Spanned
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return android.text.Html.fromHtml(html,
                    android.text.Html.FROM_HTML_MODE_COMPACT);
        } else {
            @SuppressWarnings("deprecation")
            Spanned spanned = android.text.Html.fromHtml(html);
            return spanned;
        }
    }
}
