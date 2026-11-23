package kz.duken.mobile.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 18999000 тиын → «189 990 ₸». Тиыны показываются, только если они есть. */
fun formatTenge(tiyn: Long): String {
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' '; decimalSeparator = ',' }
    val whole = tiyn / 100
    val rest = tiyn % 100
    val grouped = DecimalFormat("#,##0", symbols).format(whole)
    return if (rest == 0L) "$grouped ₸" else "$grouped,${"%02d".format(rest)} ₸"
}
