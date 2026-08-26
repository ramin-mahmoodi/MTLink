package ir.mtlink.client

object ProxyTestRunner {
    fun test(proxy: ProxyRecord): ProxyRecord {
        val tested = ProxyEngine.test(proxy)
        if (!tested.countryCode.isNullOrBlank()) return tested
        return tested.copy(countryCode = CountryLocator.lookup(tested.host))
    }
}
