package net.adamfoster.headoverwheels.ui.composables

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Locale

@Composable
fun RideChart(
    speedData: List<Entry>,
    elevationData: List<Entry>,
    modifier: Modifier = Modifier
) {
    val speedColor = AndroidColor.GREEN
    val elevationColor = AndroidColor.LTGRAY

    val decimalFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
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
                legend.textColor = AndroidColor.WHITE

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = AndroidColor.WHITE
                    setDrawLabels(false)
                }
                
                axisLeft.apply {
                    textColor = speedColor
                    setDrawGridLines(true)
                    valueFormatter = decimalFormatter
                }
                
                axisRight.apply {
                    textColor = elevationColor
                    setDrawGridLines(false)
                    valueFormatter = decimalFormatter
                }
            }
        },
        update = { chart ->
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
                color = elevationColor
                setDrawCircles(false)
                lineWidth = 2f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            val lineData = LineData(speedDataSet, elevationDataSet)
            chart.data = lineData
            
            chart.setVisibleXRangeMaximum(100f)
            chart.moveViewToX(speedData.lastOrNull()?.x ?: 0f)
            
            chart.invalidate()
        }
    )
}
