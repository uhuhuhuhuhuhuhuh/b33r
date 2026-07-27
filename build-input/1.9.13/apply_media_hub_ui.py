from pathlib import Path


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        raise SystemExit(f"{label}: start marker not found")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:start_index] + replacement.rstrip() + "\n\n" + text[end_index:]


ui_path = Path("app/src/main/java/com/streamdeck/iptv/ui/AppUi.kt")
ui = ui_path.read_text()

# Neutral near-black media-library palette. The amber remains B33R's own accent.
color_replacements = {
    "Color(0xFF2B1709)": "Color(0xFF171717)",
    "Color(0xFF160B05)": "Color(0xFF0C0C0C)",
    "Color(0xFF080301)": "Color(0xFF050505)",
    "Color(0xF21A0F08)": "Color(0xF2171717)",
    "Color(0xFF2A170A)": "Color(0xFF202020)",
    "Color(0xFF5B3918)": "Color(0xFF414141)",
    "Color(0xFF100804)": "Color(0xFF111111)",
    "Color(0xFF321B09)": "Color(0xFF191919)",
    "Color(0xFF170B04)": "Color(0xFF0D0D0D)",
    "Color(0xE61A0F08)": "Color(0xE61A1A1A)",
    "Color(0xFF241307)": "Color(0xFF1C1C1C)",
    "Color(0xFF1B0E06)": "Color(0xFF171717)",
    "Color(0xFF4A2B10)": "Color(0xFF292929)",
    "Color(0xFF4A2A10)": "Color(0xFF363636)",
    "Color(0xFF563316)": "Color(0xFF3A3A3A)",
    "Color(0xFFD5C2AE)": "Color(0xFFB6B6B6)",
    "Color(0xF20F0703)": "Color(0xF20E0E0E)",
    "Color(0xFA0F0703)": "Color(0xFA0E0E0E)",
    "Color(0xCC120804)": "Color(0xD90D0D0D)",
}
for old, new in color_replacements.items():
    ui = ui.replace(old, new)

ui = ui.replace("Your entertainment is on tap", "Your media, one place")
ui = ui.replace(
    "Live TV, movies, and series with premium multi-engine playback.",
    "Browse live channels, movies, and series in one focused media library.",
)
ui = ui.replace('text = "ENTER B33R"', 'text = "SIGN IN"')
ui = ui.replace('text = "B33R SMART HUB"', 'text = "B33R"')
ui = ui.replace('state.seriesTitle ?: "Premium IPTV Player"', 'state.seriesTitle ?: state.section.title')
ui = ui.replace(
    'text = "Live TV • Movies • Series • Multi-engine playback"',
    'text = "Live TV  •  Movies  •  Series"',
)

section_header = '''@Composable
private fun SmartersSectionHeader(
    sectionTitle: String,
    itemCount: Int,
    wide: Boolean,
    responsive: ResponsiveLayout,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = responsive.pagePadding),
    ) {
        val stacked = maxWidth < responsive.dp(360f)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(responsive.dp(2f))) {
                Text(
                    text = sectionTitle,
                    fontSize = responsive.sp(if (wide) 25f else 21f),
                    lineHeight = responsive.sp(if (wide) 29f else 25f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$itemCount items",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(responsive.dp(12f)),
            ) {
                Text(
                    text = sectionTitle,
                    modifier = Modifier.weight(1f),
                    fontSize = responsive.sp(if (wide) 25f else 21f),
                    lineHeight = responsive.sp(if (wide) 29f else 25f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$itemCount items",
                    modifier = Modifier.widthIn(max = responsive.dp(130f)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun SmartersSectionHeader(",
    "@Composable\nprivate fun SmartersTopBar(",
    section_header,
    "section header",
)

top_bar = '''@Composable
private fun SmartersTopBar(
    state: IptvUiState,
    wide: Boolean,
    responsive: ResponsiveLayout,
    onBackFromSeries: () -> Unit,
    onLogout: (() -> Unit)?,
    checkingUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xF20C0C0C),
        border = BorderStroke(1.dp, Color(0xFF242424)),
        shadowElevation = responsive.dp(4f),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < responsive.dp(if (wide) 680f else 520f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = responsive.pagePadding,
                        vertical = responsive.dp(if (compact) 10f else 12f),
                    ),
                verticalArrangement = Arrangement.spacedBy(responsive.dp(7f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.seriesTitle != null) {
                        IconButton(onClick = onBackFromSeries) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        text = "B33R",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        fontSize = responsive.sp(if (wide) 21f else 18f),
                        letterSpacing = 1.6.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(responsive.dp(18f)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.seriesTitle ?: state.section.title,
                            fontSize = responsive.sp(if (wide) 20f else 17f),
                            lineHeight = responsive.sp(if (wide) 24f else 21f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compact && state.seriesTitle == null) {
                            Text(
                                text = "Live TV  •  Movies  •  Series",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = responsive.sp(10f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = onCheckForUpdates,
                        enabled = !checkingUpdates,
                    ) {
                        if (checkingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(responsive.dp(20f)),
                                strokeWidth = responsive.dp(2f),
                            )
                        } else {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = "Check for updates")
                        }
                    }
                    onLogout?.let { logout ->
                        IconButton(onClick = logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                        }
                    }
                }
                if (compact && state.accountUsername.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.accountUsername,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatAccountExpiration(state.accountExpiresAtEpochSeconds),
                            modifier = Modifier.widthIn(max = responsive.dp(180f)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (!compact && state.accountUsername.isNotBlank()) {
                    Text(
                        text = "${state.accountUsername}  •  ${formatAccountExpiration(state.accountExpiresAtEpochSeconds)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun SmartersTopBar(",
    "@Composable\nprivate fun SmartersLaunchRow(",
    top_bar,
    "top bar",
)

launch_row = '''@Composable
private fun SmartersLaunchRow(
    selected: LibrarySection,
    visibleItemCount: Int,
    responsive: ResponsiveLayout,
    enabled: Boolean,
    onSection: (LibrarySection) -> Unit,
) {
    val sections = listOf(
        LibrarySection.LIVE,
        LibrarySection.MOVIES,
        LibrarySection.SERIES,
        LibrarySection.FAVORITES,
        LibrarySection.RESUME,
    )
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = responsive.pagePadding,
            vertical = responsive.dp(12f),
        ),
        horizontalArrangement = Arrangement.spacedBy(responsive.dp(8f)),
    ) {
        items(sections, key = { it.name }) { section ->
            SmartersLaunchCard(
                section = section,
                selected = selected == section,
                count = if (selected == section) visibleItemCount else null,
                responsive = responsive,
                compact = true,
                enabled = enabled,
                onClick = { onSection(section) },
            )
        }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun SmartersLaunchRow(",
    "@Composable\nprivate fun SmartersLaunchCard(",
    launch_row,
    "section tabs",
)

launch_card = '''@Composable
private fun SmartersLaunchCard(
    section: LibrarySection,
    selected: Boolean,
    count: Int?,
    responsive: ResponsiveLayout,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        label = "sectionTabScale",
    )
    val background = when {
        focused -> Color(0xFFF4F4F4)
        selected -> Color(0xFF2A2A2A)
        else -> Color(0xFF171717)
    }
    val foreground = if (focused) Color(0xFF111111) else Color.White
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(responsive.dp(if (responsive.isTelevision) 158f else 132f))
            .height(responsive.dp(if (responsive.isTelevision) 58f else 52f))
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(responsive.dp(7f)),
        border = BorderStroke(
            width = if (focused || selected) 2.dp else 1.dp,
            color = when {
                focused -> Color.White
                selected -> MaterialTheme.colorScheme.primary
                else -> Color(0xFF333333)
            },
        ),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = responsive.dp(12f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(responsive.dp(9f)),
        ) {
            Icon(
                imageVector = section.icon(),
                contentDescription = null,
                tint = if (focused) Color(0xFF111111) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(responsive.dp(21f)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    color = foreground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = responsive.sp(12f),
                    lineHeight = responsive.sp(14f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                count?.let {
                    Text(
                        text = "$it items",
                        color = if (focused) Color(0xFF555555) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = responsive.sp(9f),
                        lineHeight = responsive.sp(11f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun SmartersLaunchCard(",
    "@Composable\nprivate fun libraryFilterChipColors()",
    launch_card,
    "section tab",
)

filter_colors = '''@Composable
private fun libraryFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFF171717),
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = Color(0xFF2B2B2B),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
)'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun libraryFilterChipColors()",
    "private fun formatAccountExpiration(",
    filter_colors,
    "filter colors",
)

content_card = '''@Composable
private fun ContentCard(
    item: StreamItem,
    modifier: Modifier = Modifier,
    responsive: ResponsiveLayout,
    enabled: Boolean,
    isFavorite: Boolean,
    onFavorite: (() -> Unit)?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "cardScale")
    val posterRatio = if (item.kind == ContentKind.LIVE) 16f / 9f else 2f / 3f
    val context = LocalContext.current
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(responsive.dp(8f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = if (focused) {
            BorderStroke(3.dp, Color.White)
        } else {
            BorderStroke(1.dp, Color.Transparent)
        },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterRatio)
                    .clip(RoundedCornerShape(responsive.dp(6f)))
                    .background(Color(0xFF202020)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.kind.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                    modifier = Modifier.size(
                        responsive.dp(if (item.kind == ContentKind.LIVE) 38f else 46f),
                    ),
                )
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${item.name} artwork",
                        contentScale = if (item.kind == ContentKind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.kind == ContentKind.LIVE) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(responsive.dp(7f))
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(responsive.dp(4f)),
                            )
                            .padding(
                                horizontal = responsive.dp(6f),
                                vertical = responsive.dp(3f),
                            ),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = responsive.sp(9f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                    )
                }
                onFavorite?.let { favorite ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(responsive.dp(6f))
                            .size(responsive.dp(34f)),
                        shape = RoundedCornerShape(responsive.dp(17f)),
                        color = Color(0xD90D0D0D),
                        border = BorderStroke(1.dp, Color(0xFF4A4A4A)),
                    ) {
                        IconButton(onClick = favorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(responsive.dp(18f)),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.padding(
                    start = responsive.dp(3f),
                    end = responsive.dp(3f),
                    top = responsive.dp(9f),
                    bottom = responsive.dp(5f),
                ),
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = responsive.sp(13f),
                    lineHeight = responsive.sp(16f),
                )
                if (item.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(responsive.dp(3f)))
                    Text(
                        text = item.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = responsive.sp(10f),
                        lineHeight = responsive.sp(12f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}'''
ui = replace_between(
    ui,
    "@Composable\nprivate fun ContentCard(",
    "private fun ContentKind.icon()",
    content_card,
    "content card",
)

# Tighten the general library composition and search presentation.
ui = ui.replace(
    '''                    colors = listOf(
                        Color(0xFF191919),
                        Color(0xFF0D0D0D),
                        Color(0xFF050505),
                        MaterialTheme.colorScheme.background,
                    ),''',
    '''                    colors = listOf(
                        Color(0xFF151515),
                        Color(0xFF0B0B0B),
                        MaterialTheme.colorScheme.background,
                    ),''',
)
ui = ui.replace('shape = RoundedCornerShape(responsive.dp(14f)),\n            colors = OutlinedTextFieldDefaults.colors(', 'shape = RoundedCornerShape(responsive.dp(7f)),\n            colors = OutlinedTextFieldDefaults.colors(', 1)
ui = ui.replace('unfocusedBorderColor = Color(0xFF414141),\n                focusedContainerColor = Color(0xE61A1A1A),\n                unfocusedContainerColor = Color(0xE61A1A1A),', 'unfocusedBorderColor = Color(0xFF343434),\n                focusedContainerColor = Color(0xF21A1A1A),\n                unfocusedContainerColor = Color(0xF21A1A1A),', 1)

ui_path.write_text(ui)

theme_path = Path("app/src/main/java/com/streamdeck/iptv/ui/Theme.kt")
theme = theme_path.read_text()
start = theme.index("private val AppColors = darkColorScheme(")
end = theme.index("\n\n@Composable", start)
theme_block = '''private val AppColors = darkColorScheme(
    primary = Color(0xFFF2A900),
    onPrimary = Color(0xFF101010),
    secondary = Color(0xFFFFD36A),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF3D3D3D),
    error = Color(0xFFFFB4AB),
)'''
theme_path.write_text(theme[:start] + theme_block + theme[end:])
