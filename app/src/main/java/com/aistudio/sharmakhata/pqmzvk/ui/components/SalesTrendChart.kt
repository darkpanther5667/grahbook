package com.aistudio.sharmakhata.pqmzvk.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.model.Bill
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SalesTrendChart(
    bills: List<Bill>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Prepare trailing 7 days dates
    val dailySales = remember(bills) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val last7Days = List(7) { index ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(6 - index))
            cal.time
        }
        last7Days.map { date ->
            val dateStr = dateFormat.format(date)
            // Filter bills matching the specific day prefix (e.g. 2026-06-02)
            val totalSales = bills
                .filter { it.createdAt.take(10) == dateStr }
                .sumOf { it.total }
            dateStr to totalSales
        }
    }

    val maxSales = remember(dailySales) {
        dailySales.maxOf { it.second }.coerceAtLeast(100.0)
    }

    // Animation progress for progressive line drawing
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(bills) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(GrahbookRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Chart Title & Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SALES TREND / बिक्री का ग्राफ",
                        style = TextStyle(
                            fontFamily = Syne,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Text(
                        text = "Last 7 Days Revenue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Show total sales for last 7 days as badge
                val totalTrailing7 = dailySales.sumOf { it.second }
                Text(
                    text = FormatUtils.formatShort(totalTrailing7),
                    style = TextStyle(
                        fontFamily = Syne,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = RupeeGreen
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RupeeGreen.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Chart Area
            val primaryColor = MaterialTheme.colorScheme.primary
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val gridColor = MaterialTheme.colorScheme.outlineVariant

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val paddingLeftPx = 48.dp.toPx()
                val paddingRightPx = 12.dp.toPx()
                val paddingTopPx = 16.dp.toPx()
                val paddingBottomPx = 24.dp.toPx()

                val chartWidth = size.width - paddingLeftPx - paddingRightPx
                val chartHeight = size.height - paddingTopPx - paddingBottomPx

                // 1. Draw horizontal grid lines & Y-axis labels
                val gridLines = 3 // 0%, 50%, 100%
                for (i in 0..gridLines) {
                    val ratio = i / gridLines.toFloat()
                    val y = paddingTopPx + chartHeight * (1f - ratio)
                    
                    // Grid Line
                    drawLine(
                        color = gridColor.copy(alpha = 0.5f),
                        start = Offset(paddingLeftPx, y),
                        end = Offset(paddingLeftPx + chartWidth, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )

                    // Y-Axis label
                    val labelValue = maxSales * ratio
                    val labelText = FormatUtils.formatShort(labelValue)
                    val textLayoutResult = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = labelColor
                        )
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        color = labelColor,
                        topLeft = Offset(
                            x = paddingLeftPx - textLayoutResult.size.width - 6.dp.toPx(),
                            y = y - textLayoutResult.size.height / 2f
                        )
                    )
                }

                // Map points coordinates
                val points = dailySales.mapIndexed { index, pair ->
                    val x = paddingLeftPx + index * (chartWidth / 6f)
                    val salesRatio = (pair.second / maxSales).toFloat()
                    val y = paddingTopPx + chartHeight * (1f - salesRatio * animationProgress.value)
                    Offset(x, y)
                }

                // 2. Draw smooth Bezier Path & Gradient Area Fill
                if (points.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(points[0].x, points[0].y)

                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        
                        val controlX1 = prev.x + (curr.x - prev.x) / 2f
                        val controlY1 = prev.y
                        val controlX2 = prev.x + (curr.x - prev.x) / 2f
                        val controlY2 = curr.y

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, curr.x, curr.y)
                    }

                    // Draw area gradient under the curve
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, paddingTopPx + chartHeight)
                        lineTo(points.first().x, paddingTopPx + chartHeight)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            startY = paddingTopPx,
                            endY = paddingTopPx + chartHeight
                        )
                    )

                    // Draw the line outline
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // 3. Draw dots and halos + X-axis date labels
                    points.forEachIndexed { index, point ->
                        val pair = dailySales[index]
                        
                        // Halo ring for non-zero points
                        if (pair.second > 0.0) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.2f),
                                radius = 7.dp.toPx(),
                                center = point
                            )
                        }

                        // Core data node
                        drawCircle(
                            color = if (pair.second > 0.0) primaryColor else gridColor,
                            radius = 4.dp.toPx(),
                            center = point
                        )

                        // Draw X-axis date label
                        val dateISO = pair.first + "T00:00:00"
                        val dateLabel = FormatUtils.formatShortDate(dateISO)
                        val textLayoutResult = textMeasurer.measure(
                            text = dateLabel,
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = labelColor
                            )
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            color = labelColor,
                            topLeft = Offset(
                                x = point.x - textLayoutResult.size.width / 2f,
                                y = paddingTopPx + chartHeight + 6.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}
