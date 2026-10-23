package com.warwar13.gentai.ui.main

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.warwar13.gentai.data.TelemetrySampleEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Home-only artwork; all telemetry classification and freshness come from the hero. */
@Composable
internal fun HomeEnergyTriangle(sample: TelemetrySampleEntity, presentation: HomeFlowPresentation) {
    val charging = presentation == HomeFlowPresentation.CHARGING
    val discharging = presentation == HomeFlowPresentation.DISCHARGING
    val fresh = charging || discharging || presentation == HomeFlowPresentation.IDLE
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val motion = resumed && (charging || discharging) && ValueAnimator.areAnimatorsEnabled()
    // Read the clock inside Canvas so particles redraw without recomposing the SOC text.
    val phase: State<Float>? = if (motion) {
        rememberInfiniteTransition(label = "Home energy flow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing)),
            label = "Energy trail position",
        )
    } else null
    val lime = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.tertiary
    val quiet = MaterialTheme.colorScheme.outline
    val flowColor = when {
        charging -> lime
        discharging -> amber
        else -> quiet
    }
    val levelColor = when (socGaugeSeverity(sample.socPct)) {
        GaugeSeverity.GOOD -> lime
        GaugeSeverity.WARNING -> amber
        GaugeSeverity.CRITICAL -> MaterialTheme.colorScheme.error
    }.let { if (fresh) it else it.copy(alpha = 0.48f) }
    val stateLabel = when (presentation) {
        HomeFlowPresentation.CHARGING -> "Charging"
        HomeFlowPresentation.DISCHARGING -> "Discharging"
        HomeFlowPresentation.IDLE -> "Idle"
        HomeFlowPresentation.RECONNECTING -> "Reconnecting, last reading"
        HomeFlowPresentation.LAST_RECORDED -> "Last recorded"
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Battery ${sample.socPct} percent. $stateLabel. ${"%.0f".format(abs(sample.powerW))} watts, ${"%+.2f".format(sample.currentA)} amps. Solar charging input; output is measured at the battery."
            progressBarRangeInfo = ProgressBarRangeInfo(sample.socPct.coerceIn(0, 100).toFloat(), 0f..100f)
        },
    ) {
        val diameter = minOf(if (fontScale >= 1.3f) 220.dp else 194.dp, maxWidth * 0.68f)
        val nodeWidth = maxWidth * 0.43f
        val leftX = maxWidth * 0.215f
        val rightX = maxWidth * 0.785f
        val nodeTop = diameter + 44.dp
        val labelsHeight = ((if (fontScale >= 1.3f) 68 else 52) * fontScale.coerceAtLeast(1f)).dp
        val diagramHeight = nodeTop + 56.dp + labelsHeight
        Box(Modifier.fillMaxWidth().height(diagramHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, diameter.toPx() / 2f)
                val leftCenter = Offset(leftX.toPx(), nodeTop.toPx() + 28.dp.toPx())
                val rightCenter = Offset(rightX.toPx(), leftCenter.y)
                val radius = diameter.toPx() * 0.46f

                fun boundary(origin: Offset, destination: Offset, distance: Float): Offset {
                    val delta = destination - origin
                    return origin + delta / delta.getDistance() * distance
                }
                fun point(from: Offset, to: Offset, t: Float): Offset {
                    // Gentle bend, with the endpoints attached precisely to the node rims.
                    val u = 1f - t
                    val dy = to.y - from.y
                    val c1 = Offset(from.x, from.y + dy * 0.42f)
                    val c2 = Offset(to.x, to.y - dy * 0.42f)
                    return from * (u * u * u) + c1 * (3f * u * u * t) + c2 * (3f * u * t * t) + to * (t * t * t)
                }
                fun connection(source: Offset, target: Offset, color: Color, enabled: Boolean) {
                    val dy = target.y - source.y
                    val path = Path().apply {
                        moveTo(source.x, source.y)
                        cubicTo(source.x, source.y + dy * 0.42f, target.x, target.y - dy * 0.42f, target.x, target.y)
                    }
                    if (enabled) drawPath(path, color.copy(alpha = 0.055f), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(
                        path,
                        color.copy(alpha = if (enabled) 0.65f else 0.19f),
                        style = Stroke((if (enabled) 2f else 1.25f).dp.toPx(), cap = StrokeCap.Round),
                    )
                    if (!enabled) return
                    if (phase != null) {
                        repeat(2) { index ->
                            val progress = (phase.value + index * 0.5f) % 1f
                            repeat(7) { tail ->
                                val t = progress - tail * 0.018f
                                if (t >= 0f) drawCircle(color.copy(alpha = (1f - tail / 7f) * 0.7f), (2.8f - tail * 0.26f).dp.toPx(), point(source, target, t))
                            }
                            drawCircle(color.copy(alpha = 0.14f), 7.dp.toPx(), point(source, target, progress))
                            drawCircle(Color(0xFFFFF8DE), 2.dp.toPx(), point(source, target, progress))
                        }
                    } else {
                        // Direction remains explicit when system motion is disabled.
                        val tip = point(source, target, 0.55f)
                        val tangent = tip - point(source, target, 0.5f)
                        val unit = tangent / tangent.getDistance()
                        val back = tip - unit * 5.dp.toPx()
                        val side = Offset(-unit.y, unit.x) * 3.dp.toPx()
                        drawLine(color, back + side, tip, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color, back - side, tip, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                val leftBatteryEdge = boundary(center, leftCenter, radius)
                val rightBatteryEdge = boundary(center, rightCenter, radius)
                val leftNodeEdge = boundary(leftCenter, center, 27.dp.toPx())
                val rightNodeEdge = boundary(rightCenter, center, 27.dp.toPx())
                connection(leftNodeEdge, leftBatteryEdge, if (charging) lime else quiet, charging)
                connection(rightBatteryEdge, rightNodeEdge, if (discharging) amber else quiet, discharging)
                // A quiet base is decorative and has no direction or moving particles.
                drawLine(quiet.copy(alpha = 0.08f), leftCenter + Offset(28.dp.toPx(), 0f), rightCenter - Offset(28.dp.toPx(), 0f), strokeWidth = 1.dp.toPx())
            }
            SocEnergyOrb(
                socPct = sample.socPct,
                diameter = diameter,
                levelColor = levelColor,
                flowColor = flowColor,
                fresh = fresh,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            EnergyEndpoint(
                title = "Solar",
                subtitle = "Charging input",
                icon = Icons.Rounded.WbSunny,
                color = lime,
                enabled = charging,
                modifier = Modifier.offset(x = leftX - nodeWidth / 2, y = nodeTop).width(nodeWidth),
            )
            EnergyEndpoint(
                title = "Discharging",
                subtitle = "Battery output",
                icon = Icons.Rounded.ElectricalServices,
                color = amber,
                enabled = discharging,
                modifier = Modifier.offset(x = rightX - nodeWidth / 2, y = nodeTop).width(nodeWidth),
            )
        }
    }
}

@Composable
private fun SocEnergyOrb(socPct: Int, diameter: Dp, levelColor: Color, flowColor: Color, fresh: Boolean, modifier: Modifier) {
    val progress by animateFloatAsState(socPct.coerceIn(0, 100) / 100f, tween(550), label = "SOC ring")
    val fontScale = LocalDensity.current.fontScale
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.radialGradient(listOf(flowColor.copy(alpha = 0.13f), Color.Transparent), center, radius), radius, center)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF26352C), Color(0xFF111C17), Color(0xFF0A110E)), center - Offset(radius * 0.25f, radius * 0.3f), radius * 1.5f),
                radius * 0.82f,
                center,
            )
            // Stationary contour lines and a fine dot field give the orb depth without an extra animation.
            for (ring in 0..4) {
                drawCircle(levelColor.copy(alpha = 0.025f + ring * 0.005f), radius * (0.27f + ring * 0.12f), center, style = Stroke(1.dp.toPx()))
            }
            for (x in -7..7) for (y in -7..7) {
                val p = Offset(x * radius / 9f, y * radius / 9f)
                if (p.getDistance() < radius * 0.72f) drawCircle(levelColor.copy(alpha = 0.09f), 0.6.dp.toPx(), center + p)
            }
            val rim = radius * 0.9f
            val topLeft = center - Offset(rim, rim)
            val arcSize = Size(rim * 2, rim * 2)
            drawCircle(Color(0xFF334339), rim, center, style = Stroke(5.dp.toPx()))
            if (progress > 0f) {
                drawArc(levelColor.copy(alpha = 0.08f), -90f, progress * 360f, false, topLeft, arcSize, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(levelColor.copy(alpha = 0.65f), levelColor, levelColor.copy(alpha = 0.65f)), center), -90f, progress * 360f, false, topLeft, arcSize, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                val angle = (-90f + progress * 360f) * PI / 180
                val end = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * rim
                drawCircle(levelColor, 4.dp.toPx(), end)
                drawCircle(Color(0xFFEFFFF0).copy(alpha = if (fresh) 0.9f else 0.3f), 1.6.dp.toPx(), end)
            }
            // Small fixed index marks outside the SOC ring.
            repeat(12) { index ->
                val angle = index * PI / 6
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(flowColor.copy(alpha = 0.35f), center + direction * (radius * 0.97f), center + direction * (radius * 0.99f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("BATTERY", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    socPct.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = if (fontScale >= 1.3f) 39.sp else 66.sp,
                        lineHeight = if (fontScale >= 1.3f) 43.sp else 70.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-3).sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (fresh) 1f else 0.55f),
                    maxLines = 1,
                )
                Text("%", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = levelColor)
            }
            Text(if (fontScale >= 1.3f) "SOC" else "STATE OF CHARGE", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EnergyEndpoint(title: String, subtitle: String, icon: ImageVector, color: Color, enabled: Boolean, modifier: Modifier) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(56.dp).background(
                Brush.radialGradient(listOf(color.copy(alpha = if (enabled) 0.2f else 0.035f), Color(0xFF131E18))),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha = if (enabled) 0.65f else 0.16f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            Icon(icon, null, Modifier.size(26.dp), tint = if (enabled) color else color.copy(alpha = 0.48f))
        }
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) color else quiet, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = quiet, textAlign = TextAlign.Center)
    }
}
