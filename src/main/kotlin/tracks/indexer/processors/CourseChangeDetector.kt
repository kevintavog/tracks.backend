package tracks.indexer.processors

import tracks.core.models.GpsTrackPoint
import tracks.core.models.GpsVector
import tracks.core.utils.GeoCalculator
import tracks.indexer.models.GpxWorkspace
import tracks.indexer.models.LowQualityRun
import tracks.indexer.models.LowQualityType
import kotlin.math.absoluteValue

object CourseChangeDetector {
    fun process(workspace: GpxWorkspace) {
        workspace.allSegments.forEach { segment ->
            var lastPoint: GpsTrackPoint? = null
            for (current in segment.points) {
                if (current.deltaCourse.absoluteValue > 60) {
                    lastPoint?.let { lp ->
                        workspace.lowQualityRuns.add(LowQualityRun(
                            lp,
                            current,
                            LowQualityType.BIG_COURSE_CHANGES,
                        "Course changed ${current.deltaCourse.absoluteValue} to ${current.calculatedCourse}"))
                    }
                }
                lastPoint = current
            }
        }
    }

    fun processVectors(workspace: GpxWorkspace) {
        var lastVector: GpsVector? = null
        workspace.vectors.forEach { current ->
            lastVector?.let { lv ->
                val delta = GeoCalculator.bearingDelta(lv.bearing, current.bearing)
                if (delta.absoluteValue > 90) {
                    workspace.lowQualityRuns.add(LowQualityRun(
                        lv.end as GpsTrackPoint,
                        current.start as GpsTrackPoint,
                        LowQualityType.BIG_COURSE_CHANGES,
                    "Course changed ${delta.absoluteValue} to ${current.bearing}"))
                }
            }
            lastVector = current
        }
    }
}
