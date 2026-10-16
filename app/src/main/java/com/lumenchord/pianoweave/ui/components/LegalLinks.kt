package com.lumenchord.pianoweave.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Appends [text] as a link that opens [url] in the browser when tapped. */
fun AnnotatedString.Builder.appendLink(text: String, url: String, color: Color) {
    val style = SpanStyle(color = color, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
    withLink(LinkAnnotation.Url(url, TextLinkStyles(style = style))) { append(text) }
}
