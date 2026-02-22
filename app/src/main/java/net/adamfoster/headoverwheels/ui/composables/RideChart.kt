package net.adamfoster.headoverwheels.ui.composables

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Locale

@Composable
fun RideChart(
    speedData: List<Entry>,
    elevationData: List<Entry>,
    startingElevation: Float? = null,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val speedColor = AndroidColor.GREEN
    val chartForegroundColor = if (isDarkTheme) AndroidColor.LTGRAY else AndroidColor.DKGRAY

    val decimalFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                legend.isEnabled = true
                legend.textColor = chartForegroundColor

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    this.textColor = chartForegroundColor
                    setDrawLabels(false)
                }

                axisLeft.apply {
                    this.textColor = speedColor
                    setDrawGridLines(true)
                    valueFormatter = decimalFormatter
                    axisMinimum = 0f
                }

                axisRight.apply {
                    this.textColor = chartForegroundColor
                    setDrawGridLines(false)
                    valueFormatter = decimalFormatter
                }
            }
        },
        update = { chart ->
            // Update colours on recomposition (e.g. theme switch)
            chart.legend.textColor = chartForegroundColor
            chart.xAxis.textColor = chartForegroundColor
            chart.axisLeft.textColor = speedColor
            chart.axisRight.textColor = chartForegroundColor

            if (speedData.isEmpty() && elevationData.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            val speedDataSet = LineDataSet(speedData, "Speed (km/h)").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = speedColor
                setDrawCircles(false)
                lineWidth = 2f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            val elevationDataSet = LineDataSet(elevationData, "Elev (m)").apply {
                axisDependency = YAxis.AxisDependency.RIGHT
                color = chartForegroundColor
                setDrawCircles(false)
                lineWidth = 2f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            val lineData = LineData(speedDataSet, elevationDataSet)
            chart.data = lineData

            chart.axisRight.removeAllLimitLines()
            if (startingElevation != null) {
                val limitLine = LimitLine(startingElevation, "Start").apply {
                    lineColor = chartForegroundColor
                    lineWidth = 1f
                    enableDashedLine(10f, 10f, 0f)
                    this.textColor = chartForegroundColor
                    textSize = 10f
                }
                chart.axisRight.addLimitLine(limitLine)
            }

            chart.setVisibleXRangeMaximum(100f)
            chart.moveViewToX(speedData.lastOrNull()?.x ?: 0f)

            chart.invalidate()
        }
    )
}
