package ir.mtlink.client

object ProxyTestRunner {
    fun test(proxy: ProxyRecord, timeoutSeconds: Int): ProxyRecord {
        val tested = ProxyEngine.test(proxy, timeoutSeconds)
        if (tested.status != ProxyStatus.REACHABLE || !tested.countryCode.isNullOrBlank()) return tested
        return tested.copy(countryCode = CountryLocator.lookup(tested.host))
    }
}
