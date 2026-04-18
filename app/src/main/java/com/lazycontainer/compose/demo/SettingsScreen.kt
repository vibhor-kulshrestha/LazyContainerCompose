package com.lazycontainer.compose.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lazycontainer.compose.lazy.container.ContainerStyle
import com.lazycontainer.compose.lazy.container.LazyContainerColumn

/**
 * Demo consumer of [LazyContainerColumn]. A settings-style screen where
 * rows are visually grouped into rounded "containers" but each row is still
 * an individual lazy slot — so scrolling, reuse and prefetching behave
 * identically to a plain `LazyColumn`, even though rows share a container.
 *
 * The third section ("Favourites") deliberately contains many items to show
 * that grouping does NOT cost virtualisation: only rows inside the viewport
 * are composed, and prefetch keeps fast fling smooth.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var cloudBackup by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(false) }

    val languages = remember {
        listOf("English", "हिन्दी", "Español", "Français", "日本語", "Deutsch", "Português", "한국어")
    }
    val favourites = remember {
        List(120) { "Favourite item #${it + 1}" }
    }

    val style = defaultContainerStyle()
    val destructiveStyle = style.copy(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyContainerColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item(key = "title", contentType = "title") { ScreenTitle("Settings") }
            item(key = "spacer-title", contentType = "spacer") { VerticalSpacer(8.dp) }

            item(key = "label-account", contentType = "sectionLabel") { SectionLabel("ACCOUNT") }

            containerSection(sectionKey = "account", style = style) {
                item(key = "profile", contentType = "profileRow") {
                    ProfileRow(name = "Your name", email = "you@example.com")
                }
                item(key = "password", contentType = "navRow") {
                    NavRow(icon = Icons.Filled.Lock, title = "Password & Security")
                }
                item(key = "notifications", contentType = "toggleRow") {
                    ToggleRow(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications",
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                    )
                }
                item(key = "cloud-backup", contentType = "toggleRow") {
                    ToggleRow(
                        icon = Icons.Filled.Cloud,
                        title = "Cloud Backup",
                        checked = cloudBackup,
                        onCheckedChange = { cloudBackup = it },
                    )
                }
            }

            item(key = "spacer-1", contentType = "spacer") { VerticalSpacer(16.dp) }
            item(key = "label-appearance", contentType = "sectionLabel") {
                SectionLabel("APPEARANCE")
            }

            containerSection(sectionKey = "appearance", style = style) {
                item(key = "dark-mode", contentType = "toggleRow") {
                    ToggleRow(
                        icon = Icons.Filled.Palette,
                        title = "Dark Mode",
                        checked = darkMode,
                        onCheckedChange = { darkMode = it },
                    )
                }
                items(
                    count = languages.size,
                    key = { i -> "lang-${languages[i]}" },
                    contentType = { "navRow" },
                ) { i ->
                    NavRow(
                        icon = Icons.Filled.Language,
                        title = languages[i],
                        subtitle = if (i == 0) "Current" else null,
                    )
                }
            }

            item(key = "spacer-2", contentType = "spacer") { VerticalSpacer(16.dp) }
            item(key = "label-favourites", contentType = "sectionLabel") {
                SectionLabel("FAVOURITES  (${favourites.size} rows, all lazy)")
            }

            containerSection(sectionKey = "favourites", style = style) {
                items(
                    count = favourites.size,
                    key = { i -> "fav-$i" },
                    contentType = { "favouriteRow" },
                ) { i ->
                    NavRow(icon = Icons.Filled.Star, title = favourites[i])
                }
            }

            item(key = "spacer-3", contentType = "spacer") { VerticalSpacer(16.dp) }
            item(key = "label-about", contentType = "sectionLabel") { SectionLabel("ABOUT") }

            containerSection(sectionKey = "about", style = style) {
                item(key = "version", contentType = "infoRow") {
                    InfoRow(icon = Icons.Filled.Policy, title = "Version", value = "1.0.0")
                }
                item(key = "terms", contentType = "navRow") {
                    NavRow(icon = Icons.Filled.Policy, title = "Terms of Service")
                }
                item(key = "privacy", contentType = "navRow") {
                    NavRow(icon = Icons.Filled.Policy, title = "Privacy Policy")
                }
                item(key = "storage-used", contentType = "navRow") {
                    NavRow(
                        icon = Icons.Filled.Storage,
                        title = "Storage",
                        subtitle = "238 MB used",
                    )
                }
            }

            item(key = "spacer-4", contentType = "spacer") { VerticalSpacer(16.dp) }

            containerSection(sectionKey = "logout", style = destructiveStyle) {
                item(key = "logout", contentType = "destructiveRow") {
                    NavRow(
                        icon = Icons.Filled.Logout,
                        title = "Log out",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item(key = "bottom-spacer", contentType = "spacer") { VerticalSpacer(24.dp) }
        }
    }
}

@Composable
private fun defaultContainerStyle(): ContainerStyle = ContainerStyle.Default.copy(
    containerColor = MaterialTheme.colorScheme.surface,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
)

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
private fun VerticalSpacer(value: Dp) {
    Spacer(modifier = Modifier.height(value))
}

@Composable
private fun ProfileRow(name: String, email: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
