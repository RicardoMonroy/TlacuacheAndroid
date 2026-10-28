package io.github.ricardomonroy.tlacuache.ops

import android.content.Context
import android.text.format.Formatter
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.Rate

/** «12 MB/s · quedan 2 min» (A4.10), o `null` mientras aún no hay velocidad. */
fun rateText(
    context: Context,
    rate: Rate,
): String? {
    val res = context.resources
    val bps = rate.bytesPerSec ?: return null
    val speed = context.getString(R.string.ops_speed, Formatter.formatShortFileSize(context, bps.toLong()))
    val eta =
        rate.etaSecs?.toLong()?.let { s ->
            when {
                s < 60 -> {
                    res.getQuantityString(R.plurals.ops_eta_seconds, s.toInt(), s.toInt())
                }

                s < 3_600 -> {
                    ((s + 59) / 60).toInt().let { res.getQuantityString(R.plurals.ops_eta_minutes, it, it) }
                }

                else -> {
                    (s / 3_600).toInt().let { h ->
                        res.getQuantityString(
                            R.plurals.ops_eta_hours,
                            h,
                            h,
                            (
                                s % 3_600 /
                                    60
                            ).toInt(),
                        )
                    }
                }
            }
        }
    return listOfNotNull(speed, eta).joinToString(" · ")
}
