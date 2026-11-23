package kz.duken.mobile

import kz.duken.mobile.util.formatTenge
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    private val nbsp = " "

    @Test fun wholeTenge() = assertEquals("189${nbsp}990$nbsp₸", formatTenge(18_999_000))
    @Test fun zero() = assertEquals("0$nbsp₸", formatTenge(0))
    @Test fun withTiyn() = assertEquals("12,05$nbsp₸", formatTenge(1205))
    @Test fun millions() = assertEquals("1${nbsp}000${nbsp}000$nbsp₸", formatTenge(100_000_000))
    @Test fun lessThanTenge() = assertEquals("0,99$nbsp₸", formatTenge(99))
}
