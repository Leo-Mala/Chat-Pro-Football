package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.AccentLime

/** A compact internal avatar catalog. No external storage, camera or gallery is used. */
data class CoachAvatarOption(
    val id: String,
    val label: String,
    val skinColor: Color,
    val hairColor: Color,
    val shirtColor: Color,
    val backgroundColor: Color
)

val CoachAvatarOptions: List<CoachAvatarOption> = listOf(
    CoachAvatarOption(
        id = "coach_1",
        label = "Clássico",
        skinColor = Color(0xFFF1C7A5),
        hairColor = Color(0xFF2C211D),
        shirtColor = Color(0xFF1565C0),
        backgroundColor = Color(0xFF10233D)
    ),
    CoachAvatarOption(
        id = "coach_2",
        label = "Executivo",
        skinColor = Color(0xFFD9A57E),
        hairColor = Color(0xFF4E342E),
        shirtColor = Color(0xFF2E7D32),
        backgroundColor = Color(0xFF17331D)
    ),
    CoachAvatarOption(
        id = "coach_3",
        label = "Veterano",
        skinColor = Color(0xFF8D5A3A),
        hairColor = Color(0xFF161616),
        shirtColor = Color(0xFF7B1F35),
        backgroundColor = Color(0xFF35131D)
    ),
    CoachAvatarOption(
        id = "coach_4",
        label = "Moderno",
        skinColor = Color(0xFFE0B28D),
        hairColor = Color(0xFFB0BEC5),
        shirtColor = Color(0xFFC69214),
        backgroundColor = Color(0xFF30280F)
    )
)

@Composable
fun CoachAvatar(
    avatarId: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val avatar = CoachAvatarOptions.firstOrNull { it.id == avatarId } ?: CoachAvatarOptions.first()
    Canvas(modifier = modifier.aspectRatio(1f).clip(CircleShape)) {
        val unit = size.minDimension
        val centerX = size.width / 2f

        drawCircle(
            color = avatar.backgroundColor,
            radius = unit / 2f,
            center = Offset(centerX, size.height / 2f)
        )

        // Torso and neck are deliberately centered so every option fits the same circular mask.
        drawRoundRect(
            color = avatar.shirtColor,
            topLeft = Offset(size.width * 0.17f, size.height * 0.63f),
            size = Size(size.width * 0.66f, size.height * 0.40f),
            cornerRadius = CornerRadius(unit * 0.20f, unit * 0.20f)
        )
        drawRoundRect(
            color = avatar.skinColor,
            topLeft = Offset(size.width * 0.43f, size.height * 0.52f),
            size = Size(size.width * 0.14f, size.height * 0.18f),
            cornerRadius = CornerRadius(unit * 0.05f, unit * 0.05f)
        )

        // Hair behind the face gives each portrait a recognizable head silhouette.
        drawCircle(
            color = avatar.hairColor,
            radius = unit * 0.235f,
            center = Offset(centerX, size.height * 0.355f)
        )
        drawCircle(
            color = avatar.skinColor,
            radius = unit * 0.205f,
            center = Offset(centerX, size.height * 0.405f)
        )

        val featureColor = Color(0xFF241914)
        drawCircle(
            color = featureColor,
            radius = unit * 0.018f,
            center = Offset(centerX - unit * 0.072f, size.height * 0.395f)
        )
        drawCircle(
            color = featureColor,
            radius = unit * 0.018f,
            center = Offset(centerX + unit * 0.072f, size.height * 0.395f)
        )
        drawLine(
            color = featureColor.copy(alpha = 0.75f),
            start = Offset(centerX - unit * 0.050f, size.height * 0.475f),
            end = Offset(centerX + unit * 0.050f, size.height * 0.475f),
            strokeWidth = unit * 0.014f
        )

        drawCircle(
            color = if (selected) AccentLime else Color.White.copy(alpha = 0.28f),
            radius = unit * 0.48f,
            center = Offset(centerX, size.height / 2f),
            style = Stroke(width = if (selected) unit * 0.035f else unit * 0.018f)
        )
    }
}
