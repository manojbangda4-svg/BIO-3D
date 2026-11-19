package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Anatomy3DModel
import com.example.model.Anatomy3DPart
import com.example.model.AnatomyLayer
import com.example.model.AppLanguage
import com.example.model.PartShape
import com.example.model.RenderMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private data class Vec3(val x: Float, val y: Float, val z: Float)

private data class ProjectedPrimitive(
    val partId: String,
    val zDepth: Float,
    val screenX: Float,
    val screenY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val color: Color,
    val alpha: Float,
    val isSelected: Boolean,
    val isCenterPin: Boolean,
    val drawBlock: DrawScope.() -> Unit
)

private data class HitTarget(
    val partId: String,
    val center: Offset,
    val hitRadius: Float,
    val zDepth: Float
)

@Composable
fun Anatomy3DCanvas(
    model: Anatomy3DModel,
    selectedPartId: String?,
    language: AppLanguage,
    renderMode: RenderMode,
    cutawayProgress: Float,
    showLabels: Boolean,
    autoRotate: Boolean,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    onRotateDelta: (dYaw: Float, dPitch: Float) -> Unit,
    onPartSelected: (Anatomy3DPart) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var hitTargets by remember { mutableStateOf<List<HitTarget>>(emptyList()) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )

    LaunchedEffect(autoRotate) {
        while (autoRotate) {
            delay(16L)
            onRotateDelta(0.011f, 0f)
        }
    }

    val canvasDescription = "${model.displayTitle(language)} interactive 3D anatomy model"

    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F2B42),
                        Color(0xFF071625),
                        Color(0xFF040D17)
                    )
                )
            )
            .semantics { contentDescription = canvasDescription }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(model.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onRotateDelta(dragAmount.x * 0.0085f, dragAmount.y * 0.0085f)
                    }
                }
                .pointerInput(model.id, hitTargets) {
                    detectTapGestures { tapOffset ->
                        val hit = hitTargets
                            .filter { hypot(tapOffset.x - it.center.x, tapOffset.y - it.center.y) <= it.hitRadius }
                            .maxByOrNull { it.zDepth }
                        if (hit != null) {
                            model.parts.firstOrNull { it.id == hit.partId }?.let(onPartSelected)
                        }
                    }
                }
        ) {
            if (size.width <= 32f || size.height <= 32f) return@Canvas
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val baseScale = min(size.width, size.height) * 0.38f * zoom

            drawReferenceGrid(cx, cy, baseScale, yaw, pitch)

            val primitives = mutableListOf<ProjectedPrimitive>()
            val newHitTargets = mutableListOf<HitTarget>()

            model.parts.forEach { part ->
                val effectiveAlpha = computeLayerAlpha(part, cutawayProgress, renderMode)
                if (effectiveAlpha <= 0.04f) return@forEach

                val isSelected = part.id == selectedPartId
                val baseColor = Color(part.colorHex)

                val centerRot = rotatePoint(Vec3(part.x, part.y, part.z), yaw, pitch)
                val centerProj = projectPoint(centerRot, cx, cy, baseScale)
                val approxScreenRadius = max(part.rx, max(part.ry, part.rz)) * baseScale * perspectiveScale(centerRot.z)

                newHitTargets.add(
                    HitTarget(
                        partId = part.id,
                        center = Offset(centerProj.x, centerProj.y),
                        hitRadius = max(28.dp.toPx(), approxScreenRadius.coerceAtMost(90.dp.toPx())),
                        zDepth = centerRot.z
                    )
                )

                when (part.shape) {
                    PartShape.SPHERE, PartShape.ELLIPSOID -> {
                        buildEllipsoidPrimitive(
                            part = part,
                            baseColor = baseColor,
                            alpha = effectiveAlpha,
                            isSelected = isSelected,
                            renderMode = renderMode,
                            cutaway = cutawayProgress,
                            yaw = yaw,
                            pitch = pitch,
                            cx = cx,
                            cy = cy,
                            scale = baseScale,
                            pulse = pulse,
                            out = primitives
                        )
                    }
                    PartShape.CYLINDER, PartShape.CAPSULE -> {
                        buildTubePrimitives(
                            part = part,
                            baseColor = baseColor,
                            alpha = effectiveAlpha,
                            isSelected = isSelected,
                            renderMode = renderMode,
                            yaw = yaw,
                            pitch = pitch,
                            cx = cx,
                            cy = cy,
                            scale = baseScale,
                            out = primitives
                        )
                    }
                    PartShape.TORUS -> {
                        buildTorusPrimitives(
                            part = part,
                            baseColor = baseColor,
                            alpha = effectiveAlpha,
                            isSelected = isSelected,
                            renderMode = renderMode,
                            yaw = yaw,
                            pitch = pitch,
                            cx = cx,
                            cy = cy,
                            scale = baseScale,
                            out = primitives
                        )
                    }
                    PartShape.HELIX -> {
                        buildHelixPrimitives(
                            part = part,
                            baseColor = baseColor,
                            alpha = effectiveAlpha,
                            isSelected = isSelected,
                            renderMode = renderMode,
                            yaw = yaw,
                            pitch = pitch,
                            cx = cx,
                            cy = cy,
                            scale = baseScale,
                            out = primitives
                        )
                    }
                    PartShape.BRANCHING -> {
                        buildBranchingPrimitives(
                            part = part,
                            baseColor = baseColor,
                            alpha = effectiveAlpha,
                            isSelected = isSelected,
                            renderMode = renderMode,
                            yaw = yaw,
                            pitch = pitch,
                            cx = cx,
                            cy = cy,
                            scale = baseScale,
                            out = primitives
                        )
                    }
                }
            }

            hitTargets = newHitTargets

            // Painter's algorithm: draw farthest Z first, nearest Z last
            primitives.sortBy { it.zDepth }
            primitives.forEach { prim ->
                prim.drawBlock(this)
            }

            // Draw interactive 3D hotspot pins & labels on top
            if (showLabels || selectedPartId != null) {
                draw3DLabelsAndPins(
                    model = model,
                    selectedPartId = selectedPartId,
                    showAllLabels = showLabels,
                    language = language,
                    cutawayProgress = cutawayProgress,
                    renderMode = renderMode,
                    yaw = yaw,
                    pitch = pitch,
                    cx = cx,
                    cy = cy,
                    scale = baseScale,
                    pulse = pulse,
                    textMeasurer = textMeasurer
                )
            }

            // Bottom-left 3D Orientation Gizmo
            drawAxisGizmo(yaw, pitch, textMeasurer)
        }

        // Subtle scale badge top-left inside 3D canvas
        Text(
            text = model.scaleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color(0x9906131F), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun computeLayerAlpha(
    part: Anatomy3DPart,
    cutaway: Float,
    mode: RenderMode
): Float {
    var alpha = part.opacity
    when (part.layer) {
        AnatomyLayer.OUTER -> {
            if (cutaway > 0.05f) {
                alpha *= (1f - cutaway * 0.90f).coerceAtLeast(0.05f)
            }
        }
        AnatomyLayer.MIDDLE -> {
            if (cutaway > 0.45f) {
                alpha *= (1f - (cutaway - 0.45f) * 1.45f).coerceAtLeast(0.12f)
            }
        }
        AnatomyLayer.INNER, AnatomyLayer.CELLULAR -> {
            // Boost inner parts when dissected
            alpha = (alpha + cutaway * 0.15f).coerceAtMost(1f)
        }
    }
    if (mode == RenderMode.XRAY) {
        alpha = when (part.layer) {
            AnatomyLayer.OUTER -> min(alpha, 0.24f)
            AnatomyLayer.MIDDLE -> min(alpha, 0.42f)
            AnatomyLayer.INNER, AnatomyLayer.CELLULAR -> max(alpha, 0.82f)
        }
    }
    return alpha.coerceIn(0f, 1f)
}

private fun rotateLocal(v: Vec3, rx: Float, ry: Float, rz: Float): Vec3 {
    // Rotate around X, Y, Z
    var x = v.x
    var y = v.y
    var z = v.z

    if (rx != 0f) {
        val c = cos(rx); val s = sin(rx)
        val ny = y * c - z * s
        val nz = y * s + z * c
        y = ny; z = nz
    }
    if (ry != 0f) {
        val c = cos(ry); val s = sin(ry)
        val nx = x * c + z * s
        val nz = -x * s + z * c
        x = nx; z = nz
    }
    if (rz != 0f) {
        val c = cos(rz); val s = sin(rz)
        val nx = x * c - y * s
        val ny = x * s + y * c
        x = nx; y = ny
    }
    return Vec3(x, y, z)
}

private fun rotatePoint(v: Vec3, yaw: Float, pitch: Float): Vec3 {
    val cy = cos(yaw); val sy = sin(yaw)
    val x1 = v.x * cy + v.z * sy
    val z1 = -v.x * sy + v.z * cy

    val cp = cos(pitch); val sp = sin(pitch)
    val y2 = v.y * cp - z1 * sp
    val z2 = v.y * sp + z1 * cp
    return Vec3(x1, y2, z2)
}

private fun perspectiveScale(z: Float): Float {
    val cameraDist = 3.2f
    return cameraDist / (cameraDist - z).coerceAtLeast(0.8f)
}

private fun projectPoint(v: Vec3, cx: Float, cy: Float, scale: Float): Offset {
    val p = perspectiveScale(v.z)
    return Offset(cx + v.x * scale * p, cy + v.y * scale * p)
}

private fun DrawScope.drawReferenceGrid(
    cx: Float,
    cy: Float,
    scale: Float,
    yaw: Float,
    pitch: Float
) {
    val gridColor = Color(0xFF1E405E).copy(alpha = 0.30f)
    val ringColor = Color(0xFF38BDF8).copy(alpha = 0.16f)
    val yPlane = 0.72f

    for (i in -3..3) {
        val t = i * 0.28f
        val a1 = projectPoint(rotatePoint(Vec3(-0.85f, yPlane, t), yaw, pitch), cx, cy, scale)
        val a2 = projectPoint(rotatePoint(Vec3(0.85f, yPlane, t), yaw, pitch), cx, cy, scale)
        drawLine(gridColor, a1, a2, strokeWidth = 1.dp.toPx())

        val b1 = projectPoint(rotatePoint(Vec3(t, yPlane, -0.85f), yaw, pitch), cx, cy, scale)
        val b2 = projectPoint(rotatePoint(Vec3(t, yPlane, 0.85f), yaw, pitch), cx, cy, scale)
        drawLine(gridColor, b1, b2, strokeWidth = 1.dp.toPx())
    }

    // Equatorial orbital ring around anatomy specimen
    var prev: Offset? = null
    for (s in 0..48) {
        val ang = (s / 48f) * (2f * PI.toFloat())
        val pt = projectPoint(rotatePoint(Vec3(cos(ang) * 0.92f, yPlane, sin(ang) * 0.92f), yaw, pitch), cx, cy, scale)
        prev?.let { drawLine(ringColor, it, pt, strokeWidth = 1.2.dp.toPx()) }
        prev = pt
    }
}

private fun buildEllipsoidPrimitive(
    part: Anatomy3DPart,
    baseColor: Color,
    alpha: Float,
    isSelected: Boolean,
    renderMode: RenderMode,
    cutaway: Float,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    scale: Float,
    pulse: Float,
    out: MutableList<ProjectedPrimitive>
) {
    val centerRot = rotatePoint(Vec3(part.x, part.y, part.z), yaw, pitch)
    val centerScreen = projectPoint(centerRot, cx, cy, scale)
    val pScale = perspectiveScale(centerRot.z)

    // Estimate projected semi-axes by transforming local basis vectors
    val axisX = rotatePoint(rotateLocal(Vec3(part.rx, 0f, 0f), part.rotX, part.rotY, part.rotZ), yaw, pitch)
    val axisY = rotatePoint(rotateLocal(Vec3(0f, part.ry, 0f), part.rotX, part.rotY, part.rotZ), yaw, pitch)
    val axisZ = rotatePoint(rotateLocal(Vec3(0f, 0f, part.rz), part.rotX, part.rotY, part.rotZ), yaw, pitch)

    val radX = (hypot(axisX.x, axisY.x) + kotlin.math.abs(axisZ.x) * 0.55f) * scale * pScale
    val radY = (hypot(axisX.y, axisY.y) + kotlin.math.abs(axisZ.y) * 0.55f) * scale * pScale
    val safeRx = radX.coerceAtLeast(8f)
    val safeRy = radY.coerceAtLeast(8f)

    out.add(
        ProjectedPrimitive(
            partId = part.id,
            zDepth = centerRot.z,
            screenX = centerScreen.x,
            screenY = centerScreen.y,
            radiusX = safeRx,
            radiusY = safeRy,
            color = baseColor,
            alpha = alpha,
            isSelected = isSelected,
            isCenterPin = true
        ) {
            val topLeft = Offset(centerScreen.x - safeRx, centerScreen.y - safeRy)
            val ovalSize = Size(safeRx * 2f, safeRy * 2f)

            if (renderMode == RenderMode.WIREFRAME) {
                drawOval(
                    color = baseColor.copy(alpha = (alpha + 0.3f).coerceAtMost(0.95f)),
                    topLeft = topLeft,
                    size = ovalSize,
                    style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.4.dp.toPx())
                )
                // Latitude & longitude wireframe arcs
                drawOval(
                    color = baseColor.copy(alpha = 0.45f),
                    topLeft = Offset(centerScreen.x - safeRx * 0.5f, centerScreen.y - safeRy),
                    size = Size(safeRx, safeRy * 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawOval(
                    color = baseColor.copy(alpha = 0.45f),
                    topLeft = Offset(centerScreen.x - safeRx, centerScreen.y - safeRy * 0.42f),
                    size = Size(safeRx * 2f, safeRy * 0.84f),
                    style = Stroke(width = 1.dp.toPx())
                )
            } else {
                // 3D Phong-shaded radial gradient
                val highlight = Color(
                    red = (baseColor.red + 0.38f).coerceAtMost(1f),
                    green = (baseColor.green + 0.38f).coerceAtMost(1f),
                    blue = (baseColor.blue + 0.38f).coerceAtMost(1f),
                    alpha = alpha
                )
                val midTone = baseColor.copy(alpha = alpha)
                val shadowTone = Color(
                    red = baseColor.red * 0.35f,
                    green = baseColor.green * 0.35f,
                    blue = baseColor.blue * 0.45f,
                    alpha = alpha
                )

                val lightCenter = Offset(
                    centerScreen.x - safeRx * 0.32f,
                    centerScreen.y - safeRy * 0.32f
                )
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(highlight, midTone, shadowTone),
                        center = lightCenter,
                        radius = max(safeRx, safeRy) * 1.45f
                    ),
                    topLeft = topLeft,
                    size = ovalSize
                )

                // X-Ray / Cutaway rim glow
                if (renderMode == RenderMode.XRAY || (part.layer == AnatomyLayer.OUTER && cutaway > 0.12f)) {
                    drawOval(
                        color = baseColor.copy(alpha = 0.75f),
                        topLeft = topLeft,
                        size = ovalSize,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = if (cutaway > 0.12f && part.layer == AnatomyLayer.OUTER) {
                                PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                            } else null
                        )
                    )
                }
            }

            if (isSelected) {
                drawOval(
                    color = Color(0xFFFBBF24).copy(alpha = 0.90f),
                    topLeft = Offset(centerScreen.x - safeRx * pulse, centerScreen.y - safeRy * pulse),
                    size = Size(safeRx * 2f * pulse, safeRy * 2f * pulse),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    )
}

private fun buildTubePrimitives(
    part: Anatomy3DPart,
    baseColor: Color,
    alpha: Float,
    isSelected: Boolean,
    renderMode: RenderMode,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    scale: Float,
    out: MutableList<ProjectedPrimitive>
) {
    val steps = 11
    val isCapsule = part.shape == PartShape.CAPSULE
    val tubeRx = min(part.rx, part.rz).coerceAtLeast(0.03f)

    for (i in 0 until steps) {
        val t = -1f + 2f * (i.toFloat() / (steps - 1))
        val local = Vec3(0f, t * part.ry * 0.85f, 0f)
        val oriented = rotateLocal(local, part.rotX, part.rotY, part.rotZ)
        val world = Vec3(part.x + oriented.x, part.y + oriented.y, part.z + oriented.z)
        val rot = rotatePoint(world, yaw, pitch)
        val screen = projectPoint(rot, cx, cy, scale)
        val pScale = perspectiveScale(rot.z)
        val taper = if (isCapsule) kotlin.math.sqrt((1f - t * t * 0.55f).coerceAtLeast(0.25f)) else 1f
        val rad = (tubeRx * taper * scale * pScale).coerceAtLeast(5f)

        out.add(
            ProjectedPrimitive(
                partId = part.id,
                zDepth = rot.z,
                screenX = screen.x,
                screenY = screen.y,
                radiusX = rad,
                radiusY = rad,
                color = baseColor,
                alpha = alpha,
                isSelected = isSelected,
                isCenterPin = i == steps / 2
            ) {
                if (renderMode == RenderMode.WIREFRAME) {
                    drawCircle(
                        color = baseColor.copy(alpha = 0.85f),
                        radius = rad,
                        center = screen,
                        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.2.dp.toPx())
                    )
                } else {
                    val hi = baseColor.copy(
                        red = (baseColor.red + 0.30f).coerceAtMost(1f),
                        green = (baseColor.green + 0.30f).coerceAtMost(1f),
                        blue = (baseColor.blue + 0.30f).coerceAtMost(1f),
                        alpha = alpha
                    )
                    val sh = baseColor.copy(
                        red = baseColor.red * 0.38f,
                        green = baseColor.green * 0.38f,
                        blue = baseColor.blue * 0.45f,
                        alpha = alpha
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(hi, baseColor.copy(alpha = alpha), sh),
                            center = Offset(screen.x - rad * 0.3f, screen.y - rad * 0.3f),
                            radius = rad * 1.4f
                        ),
                        radius = rad,
                        center = screen
                    )
                }
                if (isSelected && i % 3 == 0) {
                    drawCircle(
                        color = Color(0xFFFBBF24),
                        radius = rad * 1.15f,
                        center = screen,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
        )
    }
}

private fun buildTorusPrimitives(
    part: Anatomy3DPart,
    baseColor: Color,
    alpha: Float,
    isSelected: Boolean,
    renderMode: RenderMode,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    scale: Float,
    out: MutableList<ProjectedPrimitive>
) {
    val segments = 24
    val tubeRadius = min(part.rx, min(part.ry, part.rz)).coerceIn(0.03f, 0.11f)
    for (i in 0 until segments) {
        val theta = (i.toFloat() / segments) * (2f * PI.toFloat())
        val local = Vec3(
            cos(theta) * part.rx * 0.78f,
            sin(theta) * part.ry * 0.78f,
            sin(theta * 2f) * part.rz * 0.18f
        )
        val oriented = rotateLocal(local, part.rotX, part.rotY, part.rotZ)
        val world = Vec3(part.x + oriented.x, part.y + oriented.y, part.z + oriented.z)
        val rot = rotatePoint(world, yaw, pitch)
        val screen = projectPoint(rot, cx, cy, scale)
        val rad = (tubeRadius * scale * perspectiveScale(rot.z)).coerceAtLeast(4.5f)

        out.add(
            ProjectedPrimitive(
                partId = part.id,
                zDepth = rot.z,
                screenX = screen.x,
                screenY = screen.y,
                radiusX = rad,
                radiusY = rad,
                color = baseColor,
                alpha = alpha,
                isSelected = isSelected,
                isCenterPin = i == 0
            ) {
                if (renderMode == RenderMode.WIREFRAME) {
                    drawCircle(
                        color = baseColor.copy(alpha = 0.85f),
                        radius = rad,
                        center = screen,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                baseColor.copy(
                                    red = (baseColor.red + 0.35f).coerceAtMost(1f),
                                    green = (baseColor.green + 0.35f).coerceAtMost(1f),
                                    blue = (baseColor.blue + 0.35f).coerceAtMost(1f),
                                    alpha = alpha
                                ),
                                baseColor.copy(alpha = alpha),
                                baseColor.copy(
                                    red = baseColor.red * 0.35f,
                                    green = baseColor.green * 0.35f,
                                    blue = baseColor.blue * 0.45f,
                                    alpha = alpha
                                )
                            ),
                            center = Offset(screen.x - rad * 0.3f, screen.y - rad * 0.3f),
                            radius = rad * 1.4f
                        ),
                        radius = rad,
                        center = screen
                    )
                }
                if (isSelected && i % 4 == 0) {
                    drawCircle(
                        color = Color(0xFFFBBF24),
                        radius = rad * 1.25f,
                        center = screen,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
        )
    }
}

private fun buildHelixPrimitives(
    part: Anatomy3DPart,
    baseColor: Color,
    alpha: Float,
    isSelected: Boolean,
    renderMode: RenderMode,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    scale: Float,
    out: MutableList<ProjectedPrimitive>
) {
    val count = 22
    for (i in 0 until count) {
        val t = (i.toFloat() / (count - 1)) * 2f - 1f
        val angle = t * 4.8f + part.rotY
        val local = Vec3(
            cos(angle) * part.rx * 0.72f,
            t * part.ry * 0.90f,
            sin(angle) * part.rz * 0.72f
        )
        val oriented = rotateLocal(local, part.rotX, 0f, part.rotZ)
        val world = Vec3(part.x + oriented.x, part.y + oriented.y, part.z + oriented.z)
        val rot = rotatePoint(world, yaw, pitch)
        val screen = projectPoint(rot, cx, cy, scale)
        val rad = (0.048f * scale * perspectiveScale(rot.z)).coerceAtLeast(4.5f)

        out.add(
            ProjectedPrimitive(
                partId = part.id,
                zDepth = rot.z,
                screenX = screen.x,
                screenY = screen.y,
                radiusX = rad,
                radiusY = rad,
                color = baseColor,
                alpha = alpha,
                isSelected = isSelected,
                isCenterPin = i == count / 2
            ) {
                if (renderMode == RenderMode.WIREFRAME) {
                    drawCircle(
                        color = baseColor.copy(alpha = 0.9f),
                        radius = rad,
                        center = screen,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = alpha * 0.85f), baseColor.copy(alpha = alpha)),
                            center = Offset(screen.x - rad * 0.25f, screen.y - rad * 0.25f),
                            radius = rad * 1.2f
                        ),
                        radius = rad,
                        center = screen
                    )
                }
                if (isSelected && i % 4 == 0) {
                    drawCircle(
                        color = Color(0xFFFBBF24),
                        radius = rad * 1.35f,
                        center = screen,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        )
    }
}

private fun buildBranchingPrimitives(
    part: Anatomy3DPart,
    baseColor: Color,
    alpha: Float,
    isSelected: Boolean,
    renderMode: RenderMode,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    scale: Float,
    out: MutableList<ProjectedPrimitive>
) {
    val branches = 7
    val originWorld = Vec3(part.x, part.y, part.z)
    val originRot = rotatePoint(originWorld, yaw, pitch)
    val originScreen = projectPoint(originRot, cx, cy, scale)

    for (b in 0 until branches) {
        val phi = (b.toFloat() / branches) * 2f * PI.toFloat()
        val elev = ((b % 3) - 1) * 0.55f
        val tipLocal = Vec3(
            cos(phi) * part.rx * 0.95f,
            sin(phi) * part.ry * 0.95f,
            sin(elev) * part.rz * 0.95f
        )
        val tipWorld = Vec3(part.x + tipLocal.x, part.y + tipLocal.y, part.z + tipLocal.z)
        val tipRot = rotatePoint(tipWorld, yaw, pitch)
        val tipScreen = projectPoint(tipRot, cx, cy, scale)

        // Sub-twig
        val twigLocal = Vec3(
            cos(phi + 0.45f) * part.rx * 1.15f,
            sin(phi + 0.45f) * part.ry * 1.15f,
            cos(elev) * part.rz * 0.65f
        )
        val twigRot = rotatePoint(Vec3(part.x + twigLocal.x, part.y + twigLocal.y, part.z + twigLocal.z), yaw, pitch)
        val twigScreen = projectPoint(twigRot, cx, cy, scale)

        val avgZ = (originRot.z + tipRot.z) * 0.5f
        val nodeRad = (0.042f * scale * perspectiveScale(tipRot.z)).coerceAtLeast(4f)

        out.add(
            ProjectedPrimitive(
                partId = part.id,
                zDepth = avgZ,
                screenX = tipScreen.x,
                screenY = tipScreen.y,
                radiusX = nodeRad,
                radiusY = nodeRad,
                color = baseColor,
                alpha = alpha,
                isSelected = isSelected,
                isCenterPin = b == 0
            ) {
                val lineColor = if (isSelected) Color(0xFFFBBF24) else baseColor.copy(alpha = alpha)
                drawLine(
                    color = lineColor,
                    start = originScreen,
                    end = tipScreen,
                    strokeWidth = if (renderMode == RenderMode.WIREFRAME) 1.5.dp.toPx() else 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = lineColor.copy(alpha = alpha * 0.85f),
                    start = tipScreen,
                    end = twigScreen,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = baseColor.copy(alpha = alpha),
                    radius = nodeRad,
                    center = tipScreen
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.7f),
                    radius = nodeRad * 0.55f,
                    center = twigScreen
                )
            }
        )
    }
}

private fun DrawScope.draw3DLabelsAndPins(
    model: Anatomy3DModel,
    selectedPartId: String?,
    showAllLabels: Boolean,
    language: AppLanguage,
    cutawayProgress: Float,
    renderMode: RenderMode,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    scale: Float,
    pulse: Float,
    textMeasurer: TextMeasurer
) {
    model.parts.forEachIndexed { idx, part ->
        val isSelected = part.id == selectedPartId
        if (!showAllLabels && !isSelected) return@forEachIndexed
        val alpha = computeLayerAlpha(part, cutawayProgress, renderMode)
        if (alpha <= 0.05f && !isSelected) return@forEachIndexed

        val rot = rotatePoint(Vec3(part.x, part.y, part.z), yaw, pitch)
        val anchor = projectPoint(rot, cx, cy, scale)

        // Pin dot
        val pinColor = if (isSelected) Color(0xFFFBBF24) else Color(part.colorHex)
        drawCircle(
            color = Color(0xFF06131F),
            radius = if (isSelected) 7.dp.toPx() else 4.5.dp.toPx(),
            center = anchor
        )
        drawCircle(
            color = pinColor,
            radius = if (isSelected) 5.dp.toPx() * pulse.coerceAtMost(1.15f) else 3.dp.toPx(),
            center = anchor
        )

        if (isSelected || (showAllLabels && idx < 5)) {
            val maxTextWidth = (size.width * 0.45f).toInt().coerceAtLeast(1)
            val labelText = part.displayName(language)
            val measured = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    color = Color(0xFFF8FAFC),
                    fontSize = if (isSelected) 11.sp else 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxTextWidth)
            )
            val padH = 7.dp.toPx()
            val padV = 3.dp.toPx()
            val boxW = measured.size.width + padH * 2f
            val boxH = measured.size.height + padV * 2f
            if (size.width <= boxW + 16f || size.height <= boxH + 16f) {
                return@forEachIndexed
            }
            val offsetDirX = if (anchor.x >= cx) 16.dp.toPx() else -(boxW + 16.dp.toPx())
            val offsetDirY = if (anchor.y >= cy) 10.dp.toPx() else -(boxH + 10.dp.toPx())

            val maxLabelX = (size.width - boxW - 8f).coerceAtLeast(8f)
            val maxLabelY = (size.height - boxH - 8f).coerceAtLeast(8f)
            val labelX = (anchor.x + offsetDirX).coerceIn(8f, maxLabelX)
            val labelY = (anchor.y + offsetDirY).coerceIn(8f, maxLabelY)

            val labelCenter = Offset(
                if (anchor.x >= cx) labelX else labelX + boxW,
                labelY + boxH * 0.5f
            )
            drawLine(
                color = pinColor.copy(alpha = 0.75f),
                start = anchor,
                end = labelCenter,
                strokeWidth = 1.2.dp.toPx()
            )

            val pillPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = labelX,
                        top = labelY,
                        right = labelX + boxW,
                        bottom = labelY + boxH,
                        radiusX = 8.dp.toPx(),
                        radiusY = 8.dp.toPx()
                    )
                )
            }
            drawPath(
                path = pillPath,
                color = if (isSelected) Color(0xEE0F2942) else Color(0xCC071625)
            )
            drawPath(
                path = pillPath,
                color = pinColor.copy(alpha = if (isSelected) 0.95f else 0.55f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(labelX + padH, labelY + padV)
            )
        }
    }
}

private fun DrawScope.drawAxisGizmo(yaw: Float, pitch: Float, textMeasurer: TextMeasurer) {
    if (size.width < 80f || size.height < 80f) return
    val origin = Offset(28.dp.toPx(), size.height - 28.dp.toPx())
    val len = 18.dp.toPx()
    val axes = listOf(
        Triple(Vec3(1f, 0f, 0f), Color(0xFFFB7185), "X"),
        Triple(Vec3(0f, -1f, 0f), Color(0xFF34D399), "Y"),
        Triple(Vec3(0f, 0f, 1f), Color(0xFF38BDF8), "Z")
    )
    axes.forEach { (vec, col, lbl) ->
        val r = rotatePoint(vec, yaw, pitch)
        val end = Offset(origin.x + r.x * len, origin.y + r.y * len)
        drawLine(col, origin, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val tm = textMeasurer.measure(
            lbl,
            TextStyle(color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        drawText(tm, topLeft = Offset(end.x - tm.size.width / 2f, end.y - tm.size.height / 2f))
    }
}
