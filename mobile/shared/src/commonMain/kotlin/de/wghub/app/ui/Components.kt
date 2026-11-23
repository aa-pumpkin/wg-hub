package de.wghub.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.data.UserSummary
import de.wghub.app.i18n.LocalTexts

/** A white rounded box with thin rows inside, like `.card` on the website. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Wg.colors.surface,
        border = BorderStroke(1.dp, Wg.colors.line),
    ) {
        Column(content = content)
    }
}

/** Rows of a card, with a line between them. */
@Composable
fun <T> CardList(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Card(modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Wg.colors.line)
            row(item)
        }
    }
}

@Composable
fun ListRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    Text(
        text = text,
        modifier = modifier.padding(start = 2.dp, bottom = 6.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (danger) Wg.colors.danger else Wg.colors.muted,
    )
}

@Composable
fun RowTitle(text: String, done: Boolean = false) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (done) Wg.colors.muted else Wg.colors.fg,
        textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
    )
}

@Composable
fun RowMeta(text: String, danger: Boolean = false) {
    Text(
        text = text,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (danger) Wg.colors.danger else Wg.colors.muted,
    )
}

@Composable
fun Avatar(user: UserSummary, size: Dp = 28.dp) {
    Box(
        modifier = Modifier.size(size).background(Wg.colors.avatars[user.avatarColor] ?: Wg.colors.subtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = user.displayName.trim().take(1).uppercase(),
            fontSize = (size.value * 0.42f).sp,
            lineHeight = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Wg.colors.avatarFg,
            style = androidx.compose.ui.text.TextStyle(
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

/** The round tick in front of a task. */
@Composable
fun CheckCircle(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = Wg.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onToggle)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (checked) colors.accent else colors.surface, CircleShape)
                .border(2.dp, if (checked) colors.accent else colors.muted.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Default.Check, null, tint = colors.accentFg, modifier = Modifier.size(16.dp))
        }
    }
}

data class MenuItem(val label: String, val danger: Boolean = false, val action: () -> Unit)

/** The ⋮ button with a small menu. */
@Composable
fun RowMenu(items: List<MenuItem>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, LocalTexts.current("common.actions"), tint = Wg.colors.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, color = if (item.danger) Wg.colors.danger else Wg.colors.fg) },
                    onClick = {
                        open = false
                        item.action()
                    },
                )
            }
        }
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                label = { Text(label, maxLines = 1, fontSize = 13.sp) },
            )
        }
    }
}

/** A grey box with a short message, for empty lists. */
@Composable
fun EmptyNote(title: String, text: String? = null) {
    Card {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Medium, color = Wg.colors.fg)
            if (text != null) Text(text, fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Shown on top of a screen while the phone cannot reach the server. */
@Composable
fun OfflineBanner(waiting: Int) {
    val t = LocalTexts.current
    Surface(color = Wg.colors.warnSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(t("app.offline"), fontSize = 13.sp, color = Wg.colors.warn)
            if (waiting > 0) Text(t("app.waiting", "count" to waiting), fontSize = 13.sp, color = Wg.colors.warn)
        }
    }
}

@Composable
fun LoadFailed(onRetry: () -> Unit) {
    val t = LocalTexts.current
    Card {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("common.loadFailed"), color = Wg.colors.muted)
            TextButton(onClick = onRetry) { Text(t("common.retry")) }
        }
    }
}
