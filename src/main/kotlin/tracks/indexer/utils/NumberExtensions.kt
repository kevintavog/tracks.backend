package tracks.indexer.utils

fun Double.format(scale: Int) = "%.${scale}f".format(this)
fun Double.leadSpaces(width: Int, scale: Int) = "%1$${width}.${scale}f".format(this)
fun Int.leadSpaces(width: Int) = "%1$${width}d".format(this)
