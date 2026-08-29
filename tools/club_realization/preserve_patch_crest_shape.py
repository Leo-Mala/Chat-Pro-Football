from pathlib import Path

path = Path("app/src/main/java/com/example/ui/screens/GameScreens.kt")
text = path.read_text(encoding="utf-8")
start = text.index("@Composable\nfun TeamBadge(")
end = text.index("\n@Composable\nfun GameApp(", start)
replacement = r'''@Composable
fun TeamBadge(
    teamName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    colorHex: String? = null
) {
    val badgeColor = getTeamColor(teamName, colorHex)
    var isSuccess by remember(logoUrl) { mutableStateOf(false) }
    val resolvedUrl = remember(logoUrl) { resolveLogoUrl(logoUrl) }
    val isBundledPatchCrest = remember(resolvedUrl) {
        BrasfootPatchCrests.isBundledAssetUri(resolvedUrl)
    }

    val containerModifier = if (isBundledPatchCrest) {
        modifier.size(size)
    } else {
        modifier
            .size(size)
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        badgeColor.copy(alpha = 0.85f),
                        badgeColor
                    )
                ),
                shape = CircleShape
            )
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
    }

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        val abbrev = teamName.take(2).uppercase()
        val context = androidx.compose.ui.platform.LocalContext.current

        if (!resolvedUrl.isNullOrEmpty()) {
            val imageRequest = remember(resolvedUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .crossfade(!isBundledPatchCrest)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }

            val painter = rememberAsyncImagePainter(
                model = imageRequest,
                onSuccess = { isSuccess = true },
                onError = { isSuccess = false }
            )
            Image(
                painter = painter,
                contentDescription = teamName,
                contentScale = ContentScale.Fit,
                modifier = if (isBundledPatchCrest) {
                    Modifier.size(size)
                } else {
                    Modifier
                        .size(size * 0.8f)
                        .clip(CircleShape)
                }
            )
        }

        if (!isSuccess) {
            val isLightColor = badgeColor.red > 0.8f && badgeColor.green > 0.8f && badgeColor.blue > 0.8f
            val textColor = if (isLightColor) Color(0xFF111111) else Color.White
            Text(
                text = abbrev,
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.35f).sp
            )
        }
    }
}
'''
path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")
