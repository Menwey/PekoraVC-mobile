package pro.pekora.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Rbx {
    val Bg = Color(0xFF232527)
    val Nav = Color(0xFF191919)
    val Card = Color(0xFF393B3D)
    val CardDark = Color(0xFF2A2C2E)
    val Line = Color(0xFF4F5254)
    val Text = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFFBDBEBE)
    val TextDim = Color(0xFF8C8E90)
    val Blue = Color(0xFF335FFF)
    val BlueHover = Color(0xFF4F76FF)
    val Green = Color(0xFF02B757)
    val Red = Color(0xFFE2231A)
    val Disabled = Color(0xFF4A4C4E)

    val Radius = RoundedCornerShape(4.dp)
    val RadiusBig = RoundedCornerShape(8.dp)
    val Pill = RoundedCornerShape(50)
}

private val Sans = FontFamily.SansSerif

private val RbxTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

private val RbxColors = darkColorScheme(
    primary = Rbx.Blue,
    onPrimary = Rbx.Text,
    secondary = Rbx.Green,
    onSecondary = Rbx.Text,
    background = Rbx.Bg,
    onBackground = Rbx.Text,
    surface = Rbx.Card,
    onSurface = Rbx.Text,
    onSurfaceVariant = Rbx.TextMuted,
    outline = Rbx.Line,
    error = Rbx.Red,
)

@Composable
fun RobloxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RbxColors, typography = RbxTypography, content = content)
}

enum class RbxButtonKind { Primary, Play, Secondary, Danger }

@Composable
fun RbxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: RbxButtonKind = RbxButtonKind.Primary,
    enabled: Boolean = true,
) {
    val bg = when (kind) {
        RbxButtonKind.Primary -> Rbx.Blue
        RbxButtonKind.Play -> Rbx.Green
        RbxButtonKind.Secondary -> Color.Transparent
        RbxButtonKind.Danger -> Rbx.Red
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = Rbx.Radius,
        border = if (kind == RbxButtonKind.Secondary) BorderStroke(1.dp, if (enabled) Rbx.TextMuted else Rbx.Disabled) else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = Rbx.Text,
            disabledContainerColor = if (kind == RbxButtonKind.Secondary) Color.Transparent else Rbx.Disabled,
            disabledContentColor = Rbx.TextDim,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun RbxCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Rbx.Card, Rbx.Radius)
            .border(1.dp, Rbx.Line, Rbx.Radius)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = Rbx.Text)
        content()
    }
}

@Composable
fun RbxField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        shape = Rbx.Radius,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Rbx.Text,
            unfocusedTextColor = Rbx.Text,
            focusedContainerColor = Rbx.CardDark,
            unfocusedContainerColor = Rbx.CardDark,
            focusedBorderColor = Rbx.Blue,
            unfocusedBorderColor = Rbx.Line,
            focusedLabelColor = Rbx.BlueHover,
            unfocusedLabelColor = Rbx.TextMuted,
            cursorColor = Rbx.Blue,
        ),
    )
}

@Composable
fun RbxMeter(level: Float, modifier: Modifier = Modifier) {
    val l = level.coerceIn(0f, 1f)
    Box(modifier.background(Rbx.CardDark, Rbx.Pill).padding(0.dp)) {
        Box(
            Modifier
                .fillMaxWidth(l)
                .padding(vertical = 4.dp)
                .background(if (l > 0.85f) Rbx.Red else Rbx.Green, Rbx.Pill),
        )
    }
}
