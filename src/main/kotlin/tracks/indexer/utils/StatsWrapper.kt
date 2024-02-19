package tracks.indexer.utils

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics


// This is from https://github.com/thomasnield/kotlin-statistics, which seems to have been removed or made private.
// It's a wrapper around a small part of the Apache Commons statistics package

interface StatsDescriptives {
    val windowSize: Int
    val mean: Double
    val geometricMean: Double
    val variance: Double
    val standardDeviation: Double
    val skewness: Double
    val kurtosis: Double
    val max: Double
    val min: Double
    val size: Long
    val sum: Double
    val sumSquared: Double
    val values: DoubleArray
    fun percentile(percentile: Double): Double
    operator fun get(index: Int): Double
}

internal class ApacheDescriptives(private val ds: DescriptiveStatistics): StatsDescriptives {
    override val windowSize by lazy { ds.windowSize }
    override val mean by lazy { ds.mean }
    override val geometricMean by lazy { ds.geometricMean }
    override val variance by lazy { ds.variance }
    override val standardDeviation by lazy { ds.standardDeviation }
    override val skewness by lazy { ds.skewness }
    override val kurtosis by lazy { ds.kurtosis }
    override val max by lazy { ds.max }
    override val min by lazy { ds.min }
    override val size by lazy { ds.n }
    override val sum by lazy { ds.sum }
    override val sumSquared by lazy { ds.sumsq }
    override val values by lazy { ds.values }
    override fun percentile(percentile: Double) = ds.getPercentile(percentile)
    override operator fun get(index: Int) = ds.getElement(index)
}

val <N: Number> Iterable<N>.descriptiveStatistics: StatsDescriptives get() = DescriptiveStatistics().apply { forEach { addValue(it.toDouble()) } }.let(::ApacheDescriptives)
val <N: Number> Sequence<N>.descriptiveStatistics: StatsDescriptives get() = DescriptiveStatistics().apply { forEach { addValue(it.toDouble()) } }.let(::ApacheDescriptives)
val <N: Number> Array<out N>.descriptiveStatistics: StatsDescriptives get() = DescriptiveStatistics().apply { forEach { addValue(it.toDouble()) } }.let(::ApacheDescriptives)
